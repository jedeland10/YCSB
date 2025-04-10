OPERATIONS=1000000


echo "TEST Performing load phase..."
./bin/ycsb load rest -P workloads/workload_write \
    -p url.prefix=http://127.0.0.1:12380/ \
    -p recordcount=100 -p operationcount=100 \
    -p keyprefixsize=4096 \
    -p threadcount=1 \
    -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 \
    | grep -E '^\[OVERALL\]|^\[INSERT\]'

echo "---------------------------------------"

wait

echo ">>> Starting YCSB client "
    ./bin/ycsb run rest -P workloads/workload_write \
    -p url.prefix=http://127.0.0.1:12380/ \
    -p keyprefixsize=4096 \
    -p recordcount=100 \
    -p operationcount=${OPERATIONS} \
    -p threadcount=1 \
    -p fieldcount=1 \
    | grep -E '^\[OVERALL\]|^\[UPDATE\]' 

echo "---------------------------------------"
