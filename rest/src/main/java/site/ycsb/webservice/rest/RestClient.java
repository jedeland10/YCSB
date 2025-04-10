/**
 * Copyright (c) 2016 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.webservice.rest;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

import javax.ws.rs.HttpMethod;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.DBException;
import site.ycsb.Status;
import site.ycsb.StringByteIterator;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.reactor.IOReactorConfig;

import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;

public class RestClient extends DB {

  private static final String URL_PREFIX = "url.prefix";
  private static final String CON_TIMEOUT = "timeout.con";
  private static final String READ_TIMEOUT = "timeout.read";
  private static final String EXEC_TIMEOUT = "timeout.exec";
  private static final String LOG_ENABLED = "log.enable";
  private static final String HEADERS = "headers";
  private static final String COMPRESSED_RESPONSE = "response.compression";
  private boolean compressedResponse;
  private boolean logEnabled;
  private String urlPrefix;
  private Properties props;
  private String[] headers;
  private CloseableHttpClient client;
  private int conTimeout = 10000;
  private int readTimeout = 10000;
  private int execTimeout = 10000;
  private volatile Criteria requestTimedout = new Criteria(false);

  // Shared scheduler for timeouts.
  private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private PoolingHttpClientConnectionManager poolingConnManager;

  private CloseableHttpAsyncClient asyncClient;

  @Override
  public void init() throws DBException {
    props = getProperties();

    urlPrefix = props.getProperty(URL_PREFIX, "http://127.0.0.1:8080");
    conTimeout = Integer.valueOf(props.getProperty(CON_TIMEOUT, "10")) * 1000;
    readTimeout = Integer.valueOf(props.getProperty(READ_TIMEOUT, "10")) * 1000;
    execTimeout = Integer.valueOf(props.getProperty(EXEC_TIMEOUT, "10")) * 1000;
    logEnabled = Boolean.valueOf(props.getProperty(LOG_ENABLED, "false").trim());
    compressedResponse = Boolean.valueOf(props.getProperty(COMPRESSED_RESPONSE, "false").trim());
    headers = props.getProperty(HEADERS, "Accept */* Content-Type application/xml user-agent Mozilla/5.0 ")
        .trim().split(" ");
    setupClient();
    setupAsyncClient();
  }

  private void setupAsyncClient() {
    RequestConfig.Builder requestBuilder = RequestConfig.custom()
        .setConnectTimeout(conTimeout)
        .setConnectionRequestTimeout(readTimeout)
        .setSocketTimeout(readTimeout);

    // Optionally adjust the I/O reactor configuration if needed.
    IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
        .setIoThreadCount(Runtime.getRuntime().availableProcessors())
        .build();

    asyncClient = HttpAsyncClients.custom()
        .setDefaultRequestConfig(requestBuilder.build())
        .setDefaultIOReactorConfig(ioReactorConfig)
        .build();
    asyncClient.start();
  }

  private void setupClient() {
    RequestConfig.Builder requestBuilder = RequestConfig.custom();
    requestBuilder.setConnectTimeout(conTimeout);
    requestBuilder.setConnectionRequestTimeout(readTimeout);
    requestBuilder.setSocketTimeout(readTimeout);

    // Create and configure the PoolingHttpClientConnectionManager.
    poolingConnManager = new PoolingHttpClientConnectionManager();
    // Set maximum total connections and max per route (adjust these values as
    // needed)
    poolingConnManager.setMaxTotal(50);
    poolingConnManager.setDefaultMaxPerRoute(50);

    HttpClientBuilder clientBuilder = HttpClientBuilder.create()
        .setDefaultRequestConfig(requestBuilder.build())
        .setConnectionManager(poolingConnManager);

    // Build the persistent HttpClient with connection pooling.
    this.client = clientBuilder.build();
  }

  @Override
  public Status read(String table, String endpoint, Set<String> fields, Map<String, ByteIterator> result) {
    int responseCode;
    try {
      responseCode = httpGet(urlPrefix + endpoint, result);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + endpoint, HttpMethod.GET);
    }
    if (logEnabled) {
      System.err.println("GET Request: " + urlPrefix + endpoint + " | Response Code: " + responseCode);
    }
    return getStatus(responseCode);
  }

  @Override
  public Status insert(String table, String endpoint, Map<String, ByteIterator> values) {
    int responseCode;
    try {
      responseCode = httpExecute(new HttpPut(urlPrefix + endpoint), values.get("field0").toString());
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + endpoint, HttpMethod.PUT);
    }
    if (logEnabled) {
      System.err.println("POST Request: " + urlPrefix + endpoint + " | Response Code: " + responseCode);
    }
    return getStatus(responseCode);
  }

  @Override
  public Status delete(String table, String endpoint) {
    int responseCode;
    try {
      responseCode = httpDelete(urlPrefix + endpoint);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + endpoint, HttpMethod.DELETE);
    }
    if (logEnabled) {
      System.err.println("DELETE Request: " + urlPrefix + endpoint + " | Response Code: " + responseCode);
    }
    return getStatus(responseCode);
  }

  @Override
  public Status update(String table, String endpoint, Map<String, ByteIterator> values) {
    HttpPut request = new HttpPut(urlPrefix + endpoint);
    for (int i = 0; i < headers.length; i += 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }

    // Prepare your data payload as before.
    String data = values.get("field0").toString();
    InputStreamEntity reqEntity = new InputStreamEntity(
        new ByteArrayInputStream(data.getBytes()),
        ContentType.APPLICATION_FORM_URLENCODED);
    reqEntity.setChunked(true);
    request.setEntity(reqEntity);

    // Dispatch the request asynchronously.
    asyncClient.execute(request, new FutureCallback<HttpResponse>() {
      @Override
      public void completed(HttpResponse response) {
        int responseCode = response.getStatusLine().getStatusCode();
        if (logEnabled) {
          System.err.println("Asynchronous PUT Request: " + urlPrefix + endpoint + " | Response Code: " + responseCode);
        }
        // Optionally process the response or record latency metrics here.
        // Remember to consume and close the response entity if necessary.
      }

      @Override
      public void failed(Exception ex) {
        if (logEnabled) {
          System.err
              .println("Asynchronous PUT Request failed: " + urlPrefix + endpoint + " | Error: " + ex.getMessage());
        }
        // Handle the failure (for example, record error metrics).
      }

      @Override
      public void cancelled() {
        if (logEnabled) {
          System.err.println("Asynchronous PUT Request was cancelled: " + urlPrefix + endpoint);
        }
      }
    });

    // Immediately return a status or a placeholder since processing is async.
    return Status.OK;
  }

  @Override
  public Status scan(String table, String startkey, int recordcount, Set<String> fields,
      Vector<HashMap<String, ByteIterator>> result) {
    return Status.NOT_IMPLEMENTED;
  }

  // Maps HTTP status codes to YCSB status codes.
  private Status getStatus(int responseCode) {
    int rc = responseCode / 100;
    if (responseCode == 400) {
      return Status.BAD_REQUEST;
    } else if (responseCode == 403) {
      return Status.FORBIDDEN;
    } else if (responseCode == 404) {
      return Status.NOT_FOUND;
    } else if (responseCode == 501) {
      return Status.NOT_IMPLEMENTED;
    } else if (responseCode == 503) {
      return Status.SERVICE_UNAVAILABLE;
    } else if (rc == 5) {
      return Status.ERROR;
    }
    return Status.OK;
  }

  private int handleExceptions(Exception e, String url, String method) {
    if (logEnabled) {
      System.err.println(method + " Request: " + url + " | " + e.getClass().getName() +
          " occurred | Error message: " + e.getMessage());
    }
    if (e instanceof ClientProtocolException) {
      return 400;
    }
    return 500;
  }

  // Note: We now reuse the client, so we don't close it after every request.
  private int httpGet(String endpoint, Map<String, ByteIterator> result) throws IOException {
    requestTimedout.setIsSatisfied(false);
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    timer.start();
    int responseCode = 200;
    HttpGet request = new HttpGet(endpoint);
    for (int i = 0; i < headers.length; i += 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }
    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    HttpEntity responseEntity = response.getEntity();
    if (responseEntity != null) {
      InputStream stream = responseEntity.getContent();
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      StringBuffer responseContent = new StringBuffer();
      String line;
      while ((line = reader.readLine()) != null) {
        if (requestTimedout.isSatisfied()) {
          reader.close();
          stream.close();
          EntityUtils.consumeQuietly(responseEntity);
          response.close();
          throw new TimeoutException();
        }
        responseContent.append(line);
      }
      timer.interrupt();
      result.put("response", new StringByteIterator(responseContent.toString()));
      stream.close();
    }
    EntityUtils.consumeQuietly(responseEntity);
    response.close();
    return responseCode;
  }

  private int httpExecute(HttpEntityEnclosingRequestBase request, String data) throws IOException {
    requestTimedout.setIsSatisfied(false);
    ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
      requestTimedout.setIsSatisfied(true);
    }, execTimeout, TimeUnit.MILLISECONDS);

    int responseCode = 200;
    for (int i = 0; i < headers.length; i += 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }

    InputStreamEntity reqEntity = new InputStreamEntity(
        new ByteArrayInputStream(data.getBytes()),
        ContentType.APPLICATION_FORM_URLENCODED);
    reqEntity.setChunked(true);
    request.setEntity(reqEntity);

    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    HttpEntity responseEntity = response.getEntity();

    if (responseEntity != null) {
      InputStream stream = responseEntity.getContent();
      if (compressedResponse) {
        stream = new GZIPInputStream(stream);
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      StringBuilder responseContent = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        if (requestTimedout.isSatisfied()) {
          reader.close();
          stream.close();
          EntityUtils.consumeQuietly(responseEntity);
          response.close();
          throw new TimeoutException();
        }
        responseContent.append(line);
      }
      timeoutFuture.cancel(true);
      stream.close();
    }
    EntityUtils.consumeQuietly(responseEntity);
    response.close();

    return responseCode;
  }

  private int httpDelete(String endpoint) throws IOException {
    requestTimedout.setIsSatisfied(false);
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    timer.start();
    int responseCode = 200;
    HttpDelete request = new HttpDelete(endpoint);
    for (int i = 0; i < headers.length; i += 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }
    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    response.close();
    return responseCode;
  }

  /**
   * Marks the input {@link Criteria} as satisfied when the input time has
   * elapsed.
   */
  class Timer implements Runnable {
    private long timeout;
    private Criteria timedout;

    public Timer(long timeout, Criteria timedout) {
      this.timedout = timedout;
      this.timeout = timeout;
    }

    @Override
    public void run() {
      try {
        Thread.sleep(timeout);
        this.timedout.setIsSatisfied(true);
      } catch (InterruptedException e) {
        // Do nothing.
      }
    }
  }

  /**
   * Sets the flag when a criteria is fulfilled.
   */
  class Criteria {
    private boolean isSatisfied;

    public Criteria(boolean isSatisfied) {
      this.isSatisfied = isSatisfied;
    }

    public boolean isSatisfied() {
      return isSatisfied;
    }

    public void setIsSatisfied(boolean satisfied) {
      this.isSatisfied = satisfied;
    }
  }

  /**
   * Private exception class for execution timeout.
   */
  class TimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TimeoutException() {
      super("HTTP Request exceeded execution time limit.");
    }
  }

  @Override
  public void cleanup() throws DBException {
    try {
      cleanupClient(); // Close the persistent HTTP client
    } catch (IOException e) {
      throw new DBException("Error during HTTP client cleanup", e);
    }
  }

  public void cleanupClient() throws IOException {
    if (client != null) {
      client.close();
    }
    if (asyncClient != null) {
      asyncClient.close();
    }
  }
}
