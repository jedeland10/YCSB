# Array of keyprefix sizes
keyprefixsizes=(128 256 512 1024 2048 4096)
NUM_RUNS=5

for keyprefix in "${keyprefixsizes[@]}"; do
    output_file="../ycsb_bench/cache/latency/${keyprefix}.txt"
    
    # Clear output file for this keyprefix size
    > "$output_file"


    echo "Testing with keyprefixsize = ${keyprefix}" | tee -a "$output_file"
    

    for i in $(seq 1 $NUM_RUNS); do

        echo "Start run ${i}/${NUM_RUNS} with keyprefixsize = ${keyprefix}" | tee -a "$output_file"
        # Start the server using goreman in the background.
        pushd ../../etcd/contrib/raftexample > /dev/null
        #pushd ../etcd/contrib/raftexample > /dev/null
        rm -rf raftexample*
        go build -o raftexample
        # Start goreman in the background.
        goreman start &
        # Capture the PID (if goreman doesn't daemonize its children, this might be sufficient)
        GOREMAN_PID=$!
        popd > /dev/null

        echo "Waiting for server to start..."
        sleep 5  # adjust this as needed for your server startup time
        echo "Performing load phase..." | tee -a "$output_file"
        ./bin/ycsb load rest -P workloads/workload_write \
            -p url.prefix=http://127.0.0.1:12380/ \
            -p recordcount=10000 -p operationcount=10000 \
            -p keyprefixsize=${keyprefix} \
            -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 \
            | grep -E '^\[OVERALL\]|^\[INSERT\]' | tee -a "$output_file"
        echo "---------------------------------------" | tee -a "$output_file"

        pushd ../YCSB > /dev/null

        ./bin/ycsb run rest -P workloads/workload_write \
            -p url.prefix=http://127.0.0.1:12380/ \
            -p keyprefixsize=${keyprefix} \
            -p operationcount=500000 \
            -p threadcount=1 \
            -p fieldcount=1 \
            | grep -E '^\[OVERALL\]|^\[UPDATE\]' | tee -a "$output_file"
            echo "---------------------------------------" | tee -a "$output_file"
        sleep 5

        pkill -f goreman
    done

    popd > /dev/null

    # Stop the server by killing goreman and its child processes.
    echo "Stopping server for keyprefixsize = ${keyprefix}"
    sleep 3
done
