# Ensure we have exactly four parameters
if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <output_file_path> <run_index> <keyprefixsize> <url_prefix>"
  exit 1
fi

# Parse the command-line arguments
output_file_path="$1"   # e.g. "../ycsb_bench/cache/once"
run_index="$2"          # e.g. "1"
keyprefixsize="$3"      # e.g. "4096"
url_prefix="$4"         # e.g. "http://127.0.0.1:12380/"

# Build the output file name: output_file_path/{keyprefixsize}_{run_index}.txt
output_file="${output_file_path}/${keyprefixsize}_${run_index}.txt"

# Clear the output file
> "$output_file"

echo "TEST Performing load phase..." | tee -a "$output_file"

./bin/ycsb load rest -P workloads/workload_write \
    -p url.prefix="$url_prefix" \
    -p recordcount=10000 -p operationcount=1 \
    -p keyprefixsize="$keyprefixsize" \
    -p insertproportion=1 -p updateproportion=0 -p fieldcount=1 \
    | grep -E '^\[OVERALL\]|^\[INSERT\]' | tee -a "$output_file"

echo "---------------------------------------" | tee -a "$output_file"

./bin/ycsb run rest -P workloads/workload_write \
    -p url.prefix="$url_prefix" \
    -p keyprefixsize="$keyprefixsize" \
    -p recordcount=10000 -p operationcount=500000 -p fieldcount=1 2>&1 \
    | grep -E '^\[OVERALL\]|^\[UPDATE\]' | tee -a "$output_file"
