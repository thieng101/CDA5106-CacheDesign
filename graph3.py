import subprocess
import math
import matplotlib.pyplot as plt

TRACE = "gcc_trace.txt"   # change path if needed

BLOCKSIZE = 32
L1_ASSOC = 4
L2_SIZE = 0
L2_ASSOC = 0
INCLUSION = 0

HT_L1 = 0.27125
MISS_PENALTY = 100

sizes_kb = [1, 2, 4, 8, 16, 32, 64, 128, 256]
policies = {
    0: "LRU",
    1: "FIFO",
    2: "Optimal"
}

def run_sim(size_bytes, policy):
    cmd = (
        f"java sim_cache {BLOCKSIZE} {size_bytes} {L1_ASSOC} "
        f"{L2_SIZE} {L2_ASSOC} {policy} {INCLUSION} {TRACE}"
    )
    result = subprocess.check_output(cmd, shell=True, text=True)

    for line in result.splitlines():
        if "L1 miss rate" in line:
            return float(line.split()[-1])

    raise RuntimeError(f"Could not find L1 miss rate in output for size={size_bytes}, policy={policy}")

def compute_aat(miss_rate):
    return HT_L1 + miss_rate * MISS_PENALTY

x = [int(math.log2(kb * 1024)) for kb in sizes_kb]
results = {name: [] for name in policies.values()}

for kb in sizes_kb:
    size_bytes = kb * 1024
    print(f"Running {kb}KB...")
    for policy, name in policies.items():
        miss_rate = run_sim(size_bytes, policy)
        aat = compute_aat(miss_rate)
        results[name].append(aat)

plt.plot(x, results["LRU"], label="LRU")
plt.plot(x, results["FIFO"], label="FIFO")
plt.plot(x, results["Optimal"], label="Optimal")

plt.xlabel("log2(Cache Size)")
plt.ylabel("Average Access Time (AAT)")
plt.title("Graph 3: AAT vs Cache Size")
plt.legend()
plt.grid()
plt.show()