import subprocess
import math
import matplotlib.pyplot as plt

TRACE = "gcc_trace.txt"   # change path if needed

BLOCKSIZE = 32
L1_SIZE = 1024
L1_ASSOC = 4
L2_ASSOC = 8
REPLACEMENT = 0   # LRU

HT_L1 = 0.27125
MISS_PENALTY = 100

l2_sizes_kb = [2, 4, 8, 16, 32, 64]

def run_sim(l2_size_bytes, inclusion):
    cmd = (
        f"java sim_cache {BLOCKSIZE} {L1_SIZE} {L1_ASSOC} "
        f"{l2_size_bytes} {L2_ASSOC} {REPLACEMENT} {inclusion} {TRACE}"
    )
    result = subprocess.check_output(cmd, shell=True, text=True)

    l1_miss_rate = None
    l2_miss_rate = None

    for line in result.splitlines():
        if "e. L1 miss rate" in line:
            l1_miss_rate = float(line.split()[-1])
        elif "k. L2 miss rate" in line:
            l2_miss_rate = float(line.split()[-1])

    if l1_miss_rate is None or l2_miss_rate is None:
        raise RuntimeError(f"Could not parse miss rates for L2={l2_size_bytes}, inclusion={inclusion}")

    return l1_miss_rate, l2_miss_rate

def compute_aat(l1_miss_rate, l2_miss_rate):
    return HT_L1 + l1_miss_rate * (HT_L1 + l2_miss_rate * MISS_PENALTY)

x = [int(math.log2(kb * 1024)) for kb in l2_sizes_kb]
non_inclusive = []
inclusive = []

for kb in l2_sizes_kb:
    l2_size_bytes = kb * 1024
    print(f"Running L2 size = {kb}KB")

    mr1, mr2 = run_sim(l2_size_bytes, 0)
    non_inclusive.append(compute_aat(mr1, mr2))

    mr1, mr2 = run_sim(l2_size_bytes, 1)
    inclusive.append(compute_aat(mr1, mr2))

plt.plot(x, non_inclusive, label="Non-inclusive")
plt.plot(x, inclusive, label="Inclusive")

plt.xlabel("log2(L2 Cache Size)")
plt.ylabel("Average Access Time (AAT)")
plt.title("Graph 4: AAT vs L2 Cache Size")
plt.legend()
plt.grid()
plt.show()