package org.brewchain.account.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.util.ALock;
import org.brewchain.account.util.LRUCache;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "BlockChainStore_LRUHashMapDB")
@Slf4j
@Data
public class BlockChainStoreWithLRU implements ActorService {
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	protected final ConcurrentHashMap<Integer, byte[]> storage;
	protected final LRUCache<String, BlockEntity> blocks;
	private int maxNumber = -1;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public BlockChainStoreWithLRU() {
		this.storage = new ConcurrentHashMap<Integer, byte[]>();
		this.blocks = new LRUCache<String, BlockEntity>(KeyConstant.CACHE_SIZE);
	}

	public BlockEntity get(String hash) {
		return this.blocks.get(hash);

	}

	public boolean isExists(String hash) {
		return this.blocks.containsKey(hash);

	}

	public void rollBackTo(int blockNumber) {
		while (getLastBlockNumber() > blockNumber) {
			this.storage.remove(getLastBlockNumber());
		}
	}

	public byte[] getBlockHashByNumber(int blockNumber) {
		return storage.get(blockNumber);
	}

	public BlockEntity getBlockByNumber(int blockNumber) {
		try (ALock l = readLock.lock()) {
			byte[] hash = this.storage.get(blockNumber);
			if (hash != null) {
				BlockEntity blockEntity = getBlockEntityByHash(hash);
				return blockEntity;
			}
			return null;
		}
	}

	public void add(BlockEntity oBlock, String hexHash) {
		int number = oBlock.getHeader().getNumber();
		final byte[] hash = oBlock.getHeader().getBlockHash().toByteArray();
		log.debug("store block number::" + number + " hash::" + hexHash);
		storage.put(number, hash);
		if (maxNumber < number) {
			maxNumber = number;
		}
		if (blocks.size() < KeyConstant.CACHE_SIZE)
			blocks.put(hexHash, oBlock);
		else {
			if (maxNumber < oBlock.getHeader().getNumber()) {
				blocks.put(hexHash, oBlock);
			}
		}
	}

	public byte[] getBestBlock() {
		try (ALock l = readLock.lock()) {
			if (getLastBlockNumber() < 0) {
				return null;
			}
			return storage.get(storage.size());
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

	/**
	 * 通过 hash 查询 BlockEntity 先从 this.blocks 中进行查询，如果查询不到，从数据库中进行查询
	 * 
	 * @param hash
	 * @return
	 */
	public BlockEntity getBlockEntityByHash(byte[] hash) {
		if (hash != null) {
			BlockEntity blockEntity = this.blocks.get(encApi.hexEnc(hash));
			if (blockEntity == null) {
				OValue v = null;
				try {
					v = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(hash)).get();
					if (v != null) {
						blockEntity = BlockEntity.parseFrom(v.getExtdata());
					}
				} catch (ODBException | InterruptedException | ExecutionException | InvalidProtocolBufferException e) {
					log.error(String.format("dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(%s)).get()", hash));
					return null;
				}
			}
			return blockEntity;
		} else {
			return null;
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
			byte[] hash = this.storage.get(number + i);
			if (hash != null) {
				BlockEntity child = getBlockEntityByHash(hash);
				if (child != null) {
					blocks.add(child);
				}
				if (encApi.hexEnc(hash).equals(endBlockHash)) {
					return blocks;
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
			byte[] hash = this.storage.get(number - i);
			BlockEntity child = getBlockEntityByHash(hash);
			if (child != null) {
				blocks.add(child);
			}
			if (StringUtils.isNotBlank(endBlockHash) && encApi.hexEnc(hash).equals(endBlockHash)) {
				return blocks;
			}
		}
		return blocks;
	}
}
