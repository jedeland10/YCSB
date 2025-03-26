if [ "$#" -ne 5 ]; then
  echo "Usage: $0  <keyprefixsize> <url_prefix> <record_count> <operation_count> <thread_count>"
  exit 1
fi

# Parse the command-line arguments
keyprefixsize="$1"      # e.g. "4096"
url_prefix="$2"         # e.g. "http://127.0.0.1:12380/"
record_count="$3"
operation_count="$4"
thread_count="$5"


echo "TEST Performing load phase..." 

./bin/ycsb load rest -P workloads/workload_write \
    -p url.prefix="$url_prefix" \
    -p recordcount="$record_count" -p operationcount="$operation_count" \
    -p threadcount="$thread_count" \
    -p keyprefixsize="$keyprefixsize" \
    -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 2>&1 \
    | grep -E '^\[OVERALL\]|^\[INSERT\]' 

