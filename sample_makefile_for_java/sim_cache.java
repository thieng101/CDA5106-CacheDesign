import java.io.*;
import java.util.*;

class Block {
	boolean valid = false;
	boolean dirty = false;
	int tag = 0;
	long lastUsed = 0;
	long inserted = 0;
}

class Cache {
	int size, assoc, blockSize, numSets;
	int replacement;
	List<Block[]> sets;
	long time = 0;

	int reads = 0, readMisses=0;
	int writes = 0, writeMisses = 0;
	int writeBacks = 0;

	Cache nextLevel = null;

	Map<Integer, Queue<Integer>> futureAccesses;
	int currentIndex = 0;

	Cache(int size, int assoc, int blockSize, int replacement, Map<Integer, Queue<Integer>> futureAccesses) {
		this.size = size;
		this.assoc = assoc;
		this.blockSize = blockSize;
		this.replacement = replacement;
		this.futureAccesses = futureAccesses;

		if (size == 0)
			return;

		numSets = size / (assoc * blockSize);
		sets = new ArrayList<>();
		for (int i = 0; i < numSets; i++) {
			Block[] set = new Block[assoc];
			for (int j = 0; j < assoc; j++)
				set[j] = new Block();
			sets.add(set);
		}
	}

	int getSetIndex(int addr)
	{
		return (addr / blockSize) % numSets;
	}

	int getTag(int addr)
	{
		return (addr / blockSize) / numSets;
	}

	int getBlockAddr(int addr)
	{
		return addr / blockSize;
	}

	void access(int addr, char op) {
		if (size == 0)
			return;

		if (op == 'r')
			reads++;
		else
			writes++;
		
		int setIndex = getSetIndex(addr);
		int tag = getTag(addr);
		int blockAddr = getBlockAddr(addr);

		// update future accesses for optimal policy
		if (replacement == 2) {
			Queue<Integer> q = futureAccesses.get(blockAddr);
			if (q != null && !q.isEmpty())
				q.poll();
		}

		Block[] set = sets.get(setIndex);

		//HIT
		for (Block block : set) {
			if (block.valid && block.tag == tag) {
				if (op == 'w')
					block.dirty = true;
				block.lastUsed = ++time;
				return;
			}
		}
		
		//MISS
		if (op == 'r')
			readMisses++;
		else
			writeMisses++;
		
		//Find victim block
		Block victim = null;
		for (Block block : set) {
			if (!block.valid) {
				victim = block;
				break;
			}
		}

		if (victim == null) {
			if (replacement == 0) { // LRU
				victim = set[0];
				for (Block block : set) 
					if (block.lastUsed < victim.lastUsed)
						victim = block;
			} else if (replacement == 1) { // FIFO
				victim = set[0];
				for (Block block : set)
					if (block.inserted < victim.inserted)
						victim = block;
			} else { // Optimal
				victim = set[0];
				int farthest = -1;

				for (Block b : set) {
					int bAddr = b.tag * numSets + setIndex;
					Queue<Integer> q = futureAccesses.get(bAddr);

					if (q == null || q.isEmpty()) {
						victim = b;
						break;
					}

					int nextUse = q.peek();
					if (nextUse > farthest) {
						farthest = nextUse;
						victim = b;
					}
				}
			}
		}

		// Write back if dirty
		if (victim.valid && victim.dirty) {
			writeBacks++;
			if (nextLevel != null) {
				int victimAddr = (victim.tag * numSets + setIndex) * blockSize;
				nextLevel.access(victimAddr, 'w');
			}
		}

		// Fetch from next level
		if (nextLevel != null) {
			nextLevel.access(addr, 'r');
		}

		// Update victim block
		victim.valid = true;
		victim.tag = tag;
		victim.dirty = (op == 'w');
		victim.lastUsed = ++time;
		victim.inserted = time;
	}
}

class sim_cache {
	static Map<Integer, Queue<Integer>> buildFuture(String traceFile, int blockSize) throws Exception {
		Map<Integer, Queue<Integer>> map = new HashMap<>();
		BufferedReader br = new BufferedReader(new FileReader(traceFile));
		String line;
		int index = 0;

		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] parts = line.split(" ");
			int addr = Integer.parseInt(parts[1], 16);
			int blockAddr = addr / blockSize;

			map.putIfAbsent(blockAddr, new LinkedList<>());
			map.get(blockAddr).add(index);
			index++;
		}
		br.close();
		return map;
	}
	
	public static void main(String[] args) throws Exception {
		if (args.length != 8) {
			System.out.println(
					"Usage: sim_cache <BLOCKSIZE> <L1_SIZE> <L1_ASSOC> <L2_SIZE> <L2_ASSOC> <REPLACEMENT_POLICY> <INCLUSION_PROPERTY> <trace_file>");
			return;
		}

		int blockSize = Integer.parseInt(args[0]);
		int l1Size = Integer.parseInt(args[1]);
		int l1Assoc = Integer.parseInt(args[2]);
		int l2Size = Integer.parseInt(args[3]);
		int l2Assoc = Integer.parseInt(args[4]);
		int replacement = Integer.parseInt(args[5]);
		int inclusion = Integer.parseInt(args[6]);
		String traceFile = args[7];

		Map<Integer, Queue<Integer>> future = null;
        if (replacement == 2) future = buildFuture(traceFile, blockSize);

		Cache L1 = new Cache(l1Size, l1Assoc, blockSize, replacement, future);
		Cache L2 = null;

		if (l2Size > 0) {
			L2 = new Cache(l2Size, l2Assoc, blockSize, replacement, future);
			L1.nextLevel = L2;
		}

		BufferedReader bReader = new BufferedReader(new FileReader(traceFile));
		String line;

		while ((line = bReader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty())
				continue;

			String[] parts = line.split(" ");
			char op = parts[0].charAt(0);
			int addr = Integer.parseInt(parts[1], 16);

			L1.access(addr, op);
		}

		bReader.close();

		// Output
		String replacementPolicyString = (replacement == 0) ? "LRU" : (replacement == 1) ? "FIFO" : "optimal";
        String inclusiveString = (Integer.parseInt(args[6]) == 0) ? "non-inclusive" : "inclusive";

		System.out.println("===== Simulator configuration =====");
		System.out.printf("BLOCKSIZE:%16d%n", blockSize);
		System.out.printf("L1_SIZE:%17d%n", l1Size);
		System.out.printf("L1_ASSOC:%16d%n", l1Assoc);
		System.out.printf("L2_SIZE:%17d%n", l2Size);
		System.out.printf("L2_ASSOC:%16d%n", l2Assoc);
		System.out.printf("REPLACEMENT POLICY:%8s%n", replacementPolicyString);
		System.out.printf("INCLUSION PROPERTY:%5s%n", inclusiveString);
			System.out.printf("trace_file:%16s%n", traceFile);
	
		// L1 contents
        System.out.println("===== L1 contents =====");
		for (int i = 0; i < L1.numSets; i++) {
			System.out.printf("Set%6d:%8s", i, "");
			Block[] set = L1.sets.get(i);
			for (Block b : set) {
				if (b.valid) {
					String tagHex = Integer.toHexString(b.tag);
					if (b.dirty)
						System.out.printf("%-8s ", tagHex + " D");
					else
						System.out.printf("%-8s ", tagHex);
				} else {
					System.out.printf("%-8s ", "0");
				}
			}
			System.out.println();
		}

		// Simulation results
		System.out.println("===== Simulation results (raw) =====");
        System.out.printf("a. number of L1 reads:%12d%n", L1.reads);
        System.out.printf("b. number of L1 read misses:%6d%n", L1.readMisses);
        System.out.printf("c. number of L1 writes:%11d%n", L1.writes);
        System.out.printf("d. number of L1 write misses:%5d%n", L1.writeMisses);

        double missRate = (double)(L1.readMisses + L1.writeMisses) / (L1.reads + L1.writes);
        System.out.printf("e. L1 miss rate:%14.6f%n", missRate);
        System.out.printf("f. number of L1 writebacks:%5d%n", L1.writeBacks);
		
		if (L2 != null) {
            System.out.printf("g. number of L2 reads:%12d%n", L2.reads);
            System.out.printf("h. number of L2 read misses:%6d%n", L2.readMisses);
            System.out.printf("i. number of L2 writes:%11d%n", L2.writes);
            System.out.printf("j. number of L2 write misses:%5d%n", L2.writeMisses);

            double l2MissRate = (L2.reads == 0) ? 0 : (double)L2.readMisses / L2.reads;
            System.out.printf("k. L2 miss rate:%14.6f%n", l2MissRate);
            System.out.printf("l. number of L2 writebacks:%5d%n", L2.writeBacks);

            int traffic = L2.readMisses + L2.writeMisses + L2.writeBacks;
            System.out.printf("m. total memory traffic:%6d%n", traffic);
        } else {
            System.out.printf("g. number of L2 reads:%12d%n", 0);
            System.out.printf("h. number of L2 read misses:%6d%n", 0);
            System.out.printf("i. number of L2 writes:%11d%n", 0);
            System.out.printf("j. number of L2 write misses:%5d%n", 0);
            System.out.printf("k. L2 miss rate:%14d%n", 0);
            System.out.printf("l. number of L2 writebacks:%5d%n", 0);

            int traffic = L1.readMisses + L1.writeMisses + L1.writeBacks;
            System.out.printf("m. total memory traffic:%6d%n", traffic);
        }

		

	}
}
