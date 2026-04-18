import subprocess
import math
import csv
import matplotlib.pyplot as plt


TRACE = "gcc_trace.txt"

BLOCKSIZE = 32
REPLACEMENT = 0   # LRU
INCLUSION = 0     # non-inclusive

sizes_kb = [1,2,4,8,16,32,64,128,256,512,1024]
assocs = [1,2,4,8]

HT_L1 = 0.27125 
MISS_PENALTY = 100

def run_sim(size_bytes, assoc):
    cmd = f"java sim_cache {BLOCKSIZE} {size_bytes} {assoc} 0 0 {REPLACEMENT} {INCLUSION} {TRACE}"
    
    result = subprocess.check_output(cmd, shell=True).decode()

    for line in result.split("\n"):
        if "L1 miss rate" in line:
            return float(line.split()[-1])
    
    return None

def compute_aat(miss_rate):
    return HT_L1 + miss_rate * MISS_PENALTY

data = []

for kb in sizes_kb:
    size_bytes = kb * 1024
    log_size = int(math.log2(size_bytes))

    row = {
        "size_kb": kb,
        "log2": log_size
    }

    # regular associativities
    for assoc in assocs:
        miss = run_sim(size_bytes, assoc)
        aat = compute_aat(miss)
        row[f"{assoc}-way"] = aat

    # FULL associativity
    full_assoc = size_bytes // BLOCKSIZE
    miss = run_sim(size_bytes, full_assoc)
    aat = compute_aat(miss)
    row["full"] = aat

    print(f"Done {kb}KB")
    data.append(row)

# Create plot
x = [row["log2"] for row in data]

plt.plot(x, [row["1-way"] for row in data], label="Direct")
plt.plot(x, [row["2-way"] for row in data], label="2-way")
plt.plot(x, [row["4-way"] for row in data], label="4-way")
plt.plot(x, [row["8-way"] for row in data], label="8-way")
plt.plot(x, [row["full"] for row in data], label="Full")

plt.xlabel("log2(Cache Size)")
plt.ylabel("Average Access Time (AAT)")
plt.title("Graph 2: AAT vs Cache Size")
plt.legend()
plt.grid()

plt.show()