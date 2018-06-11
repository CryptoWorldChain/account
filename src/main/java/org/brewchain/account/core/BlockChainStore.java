package org.brewchain.account.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.util.ALock;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "BlockChainStore_HashMapDB")
@Slf4j
@Data
public class BlockChainStore implements ActorService {
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	protected final ConcurrentHashMap<Integer, List<byte[]>> storage;
	protected final ConcurrentHashMap<String, BlockEntity> blocks;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public BlockChainStore() {
		this.storage = new ConcurrentHashMap<Integer, List<byte[]>>();
		this.blocks = new ConcurrentHashMap<String, BlockEntity>();
	}

	public BlockEntity get(String hash) {
		try (ALock l = readLock.lock()) {
			return this.blocks.get(hash);
		}
	}

	public boolean isExists(String hash) {
		try (ALock l = readLock.lock()) {
			return this.blocks.containsKey(hash);
		}
	}

	public void rollBackTo(int blockNumber) {
		try (ALock lw = writeLock.lock()) {
			while (getLastBlockNumber() > blockNumber) {
				this.storage.remove(getLastBlockNumber());
			}
		}
	}

	public byte[] getBlockHashByNumber(int blockNumber) {
		try (ALock l = readLock.lock()) {
			return storage.get(blockNumber).get(0);
		}
	}

	public BlockEntity getBlockByNumber(int blockNumber) {
		try (ALock l = readLock.lock()) {
			List<byte[]> hashs = this.storage.get(blockNumber);
			if (hashs != null) {
				return this.blocks.get(encApi.hexEnc(hashs.get(0)));
			}
			return null;
		}
	}

	public void add(BlockEntity oBlock, String hexHash) {
		int number = oBlock.getHeader().getNumber();
		final byte[] hash = oBlock.getHeader().getBlockHash().toByteArray();

		try (ALock l = writeLock.lock()) {
			if (storage.containsKey(number)) {
				log.debug("storage exists block::" + number + " count::" + storage.get(number).size());
				storage.get(number).add(hash);
			} else {
				log.debug("storage add block::" + number);
				storage.put(number, new ArrayList<byte[]>() {
					{
						add(hash);
					}
				});
			}
			if (blocks.size() == KeyConstant.CACHE_SIZE) {
				
			}
			blocks.put(hexHash, oBlock);
		}
	}

	public byte[] getBestBlock() {
		try (ALock l = readLock.lock()) {
			if (getLastBlockNumber() < 0) {
				return null;
			}
			return storage.get(storage.size()).get(0);
		}
	}

	public int getBlockCount() {
		return storage.size();
	}

	public int getLastBlockNumber() {
		try (ALock l = readLock.lock()) {
			if (storage.size() > 0) {
				return storage.size() - 1;
			}
			return -1;
		}
	}

	public void clear() {
		try (ALock l = writeLock.lock()) {
			storage.clear();
		}
	}

	public List<BlockEntity> getChildListBlocksEndWith(String blockHash, String endBlockHash, int maxCount) {
		BlockEntity firstBlock = this.blocks.get(blockHash);
		if (firstBlock == null)
			return new ArrayList<>();

		List<BlockEntity> blocks = new ArrayList<>();
		int number = firstBlock.getHeader().getNumber();
		blocks.add(firstBlock);

		for (int i = 1; i <= maxCount; ++i) {
			List<byte[]> hashs = this.storage.get(number + i);
			if (hashs != null) {
				for (int j = 0; j < hashs.size(); j++) {
					BlockEntity child = this.blocks.get(encApi.hexEnc(hashs.get(j)));
					if (child != null) {
						blocks.add(child);
					}
					if (encApi.hexEnc(hashs.get(j)).equals(endBlockHash)) {
						return blocks;
					}
				}
			}
		}

		return blocks;
	}

	public List<BlockEntity> getParentListBlocksEndWith(String blockHash, String endBlockHash, int maxCount) {
		BlockEntity firstBlock = this.blocks.get(blockHash);
		if (firstBlock == null)
			return new ArrayList<>();

		List<BlockEntity> blocks = new ArrayList<>();
		int number = firstBlock.getHeader().getNumber();
		blocks.add(firstBlock);

		for (int i = 1; i <= maxCount; ++i) {
			List<byte[]> hashs = this.storage.get(number - i);
			if (hashs != null) {
				for (int j = 0; j < hashs.size(); j++) {
					BlockEntity child = this.blocks.get(encApi.hexEnc(hashs.get(j)));
					if (child != null) {
						blocks.add(child);
					}
					if (StringUtils.isNotBlank(endBlockHash) && encApi.hexEnc(hashs.get(j)).equals(endBlockHash)) {
						return blocks;
					}
				}
			}
		}

		return blocks;
	}
}
