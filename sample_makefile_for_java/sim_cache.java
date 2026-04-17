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
	int replacementPolicy;
	List<Block[]> sets;
	long time = 0;

	int reads = 0, readMisses=0;
	int writes = 0, writeMisses = 0;
	int writeBacks = 0;

	Cache nextLevel = null;

	Cache(int size, int assoc, int blockSize, int replacementPolicy) {
		this.size = size;
		this.assoc = assoc;
		this.blockSize = blockSize;
		this.replacementPolicy = replacementPolicy;

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

	void access(int addr, char op) {
		if (size == 0)
			return;

		if (op == 'r')
			reads++;
		else
			writes++;
		
		int setIndex = getSetIndex(addr);
		int tag = getTag(addr);
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
			victim = set[0];
			for (Block block : set) {
				if (replacementPolicy == 0) { // LRU
					if (block.lastUsed < victim.lastUsed)
						victim = block;
				} else { // FIFO
					if (block.inserted < victim.inserted)
						victim = block;
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
	public static void main(String[] args) {
		new HelloWorld(args);
	}
}
