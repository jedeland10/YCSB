# Array of keyprefix sizes
keyprefixsizes=(128 1024 4096 8192)
NUM_RUNS=10

for keyprefix in "${keyprefixsizes[@]}"; do
    output_file="../ycsb_bench/cache/open/${keyprefix}.txt"
    
    # Clear output file for this keyprefix size
    > "$output_file"

    # Start the server using goreman in the background.
    pushd ../etcd/contrib/raftexample > /dev/null
    rm -rf raftexample*
    go build -o raftexample
    # Start goreman in the background.
    goreman start &
    # Capture the PID (if goreman doesn't daemonize its children, this might be sufficient)
    GOREMAN_PID=$!
    popd > /dev/null

    echo "Waiting for server to start..."
    sleep 5  # adjust as needed

    pushd ../YCSB > /dev/null

    echo "Testing throughput with keyprefixsize = ${keyprefix}" | tee -a "$output_file"
    
    echo "Performing load phase..." | tee -a "$output_file"
    ./bin/ycsb load rest -J-Xss256k -P workloads/workload_write \
        -p url.prefix=http://127.0.0.1:12380/ \
        -p recordcount=10000 -p operationcount=10000 \
        -p keyprefixsize=${keyprefix} \
        -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 \
        | grep -E '^\[OVERALL\]|^\[INSERT\]' | tee -a "$output_file"
    echo "---------------------------------------" | tee -a "$output_file"

    for i in $(seq 1 $NUM_RUNS); do
        echo "Run #$i throughput test:" | tee -a "$output_file"
        ./bin/ycsb run rest -P workloads/workload_write \
            -p url.prefix=http://127.0.0.1:12380/ \
            -p keyprefixsize=${keyprefix} \
            -p operationcount=100000 \
            -p threadcount=1 \
            -p fieldcount=1 \
            | grep -E '^\[OVERALL\]|^\[UPDATE\]' | tee -a "$output_file"
        echo "---------------------------------------" | tee -a "$output_file"
        sleep 3
    done

    popd > /dev/null

    # Stop the server by killing goreman and its child processes.
    echo "Stopping server for keyprefixsize = ${keyprefix}"
    pkill -f goreman
    sleep 3
done
