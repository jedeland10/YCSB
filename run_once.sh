output_file="../ycsb_bench/cache/once/test.txt"

# Clear output file for this keyprefix size
> "$output_file"

echo "TEST Performing load phase..." | tee -a "$output_file"
./bin/ycsb load rest -P workloads/workload_write \
    -p url.prefix=http://127.0.0.1:12380/ \
    -p recordcount=10000 -p operationcount=1 \
    -p keyprefixsize=4096 \
    -p threadcount=8 \
    -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 \
    | grep -E '^\[OVERALL\]|^\[INSERT\]' | tee -a "$output_file"
echo "---------------------------------------" | tee -a "$output_file"

./bin/ycsb run rest -P workloads/workload_write \
    -p url.prefix=http://127.0.0.1:12380/ \
    -p keyprefixsize=4096 \
    -p threadcount=8 \
    -p recordcount=10000 -p operationcount=500000 -p fieldcount=1 2>&1 

