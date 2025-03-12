#!/bin/bash

# Number of runs
NUM_RUNS=10
output_file="../ycsb_bench/cache/diff_keys/128.txt"

> "$output_file"

echo "Performing load phase..."
./bin/ycsb load rest -P workloads/workload_write \
    -p url.prefix=http://127.0.0.1:12380/ \
    -p recordcount=10000 -p operationcount=10000 \
    -p keyprefixsize=128 \
    -threads 400 \
    -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 \
    #| grep -E '^\[OVERALL\]|^\[INSERT\]'
echo "---------------------------------------"

for i in $(seq 1 $NUM_RUNS); do
    echo "Run #$i results:" | tee -a "$output_file"
    ./bin/ycsb run rest -P workloads/workload_write \
        -p url.prefix=http://127.0.0.1:12380/ \
        -p keyprefixsize=128 \
        -p operationcount=100000 -p fieldcount=1 2>&1 | \
        grep -E '^\[OVERALL\]|^\[UPDATE\]' | tee -a "$output_file"
    echo "---------------------------------------" | tee -a "$output_file"
    sleep 3
done
