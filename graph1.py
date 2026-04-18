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

def run_sim(size_bytes, assoc):
    cmd = f"java sim_cache {BLOCKSIZE} {size_bytes} {assoc} 0 0 {REPLACEMENT} {INCLUSION} {TRACE}"
    
    result = subprocess.check_output(cmd, shell=True).decode()

    for line in result.split("\n"):
        if "L1 miss rate" in line:
            return float(line.split()[-1])
    
    return None

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
        row[f"{assoc}-way"] = miss

    # FULL associativity
    full_assoc = size_bytes // BLOCKSIZE
    miss = run_sim(size_bytes, full_assoc)
    row["full"] = miss

    print(f"Done {kb}KB")
    data.append(row)

# Save CSV
# with open("graph1.csv", "w", newline="") as f:
#     writer = csv.DictWriter(f, fieldnames=["size_kb","log2","1-way","2-way","4-way","8-way","full"])
#     writer.writeheader()
    
#     for row in data:
#         writer.writerow({
#             "size_kb": row["size_kb"],
#             "log2": row["log2"],
#             "1-way": row["1-way"],
#             "2-way": row["2-way"],
#             "4-way": row["4-way"],
#             "8-way": row["8-way"],
#             "full": row["full"]
#         })

# print("Saved to graph1.csv")

x = [row["log2"] for row in data]

plt.plot(x, [row["1-way"] for row in data], label="Direct")
plt.plot(x, [row["2-way"] for row in data], label="2-way")
plt.plot(x, [row["4-way"] for row in data], label="4-way")
plt.plot(x, [row["8-way"] for row in data], label="8-way")
plt.plot(x, [row["full"] for row in data], label="Full")

plt.xlabel("log2(Cache Size)")
plt.ylabel("L1 Miss Rate")
plt.title("Graph 1: Miss Rate vs Cache Size")
plt.legend()
plt.grid()

plt.show()