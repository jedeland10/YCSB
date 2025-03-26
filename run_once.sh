output_file="../ycsb_bench/cache/once/test.txt"

NUM_CLIENTS=5
OPERATIONS=30000

# Clear output file for this keyprefix size
> "$output_file"

echo "TEST Performing load phase..." | tee -a "$output_file"
./bin/ycsb load rest -P workloads/workload_write \
    -p url.prefix=http://127.0.0.1:12380/ \
    -p recordcount=3000 -p operationcount=3000 \
    -p keyprefixsize=128 \
    -p threadcount=1 \
    -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 \
    | grep -E '^\[OVERALL\]|^\[INSERT\]' | tee -a "$output_file"

echo "---------------------------------------" | tee -a "$output_file"

wait

echo ">>> Starting YCSB client $i"
    ./bin/ycsb run rest -P workloads/workload_write \
    -p url.prefix=http://127.0.0.1:12380/ \
    -p keyprefixsize=128 \
    -p recordcount=1000 \
    -p operationcount=${OPERATIONS} \
    -p threadcount=1 \
    -p fieldcount=1 \
    | grep -E '^\[OVERALL\]|^\[UPDATE\]' | tee -a "results_$i.txt" 

echo "---------------------------------------"
