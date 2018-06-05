package org.brewchain.account.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

	protected final LinkedHashMap<Integer, List<byte[]>> storage;
	protected final LRUCache<String, BlockEntity> blocks;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());
	
	private final int CACHE_SIZE = 1000;

	public BlockChainStoreWithLRU() {
		this.storage = new LinkedHashMap<Integer, List<byte[]>>();
		this.blocks = new LRUCache<String, BlockEntity>(CACHE_SIZE);
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
			if(hashs != null){
				BlockEntity blockEntity = getBlockEntityByHash(hashs.get(0));
				return blockEntity;
			}				
			return null;
		}
	}

	public void add(BlockEntity oBlock, String hexHash) {
		int number = oBlock.getHeader().getNumber();
		final byte[] hash = oBlock.getHeader().getBlockHash().toByteArray();

		try (ALock l = writeLock.lock()) {
			if (storage.containsKey(number)) {
				storage.get(number).add(hash);
			} else {
				storage.put(number, new ArrayList<byte[]>() {
					{
						add(hash);
					}
				});
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
	
	/**
	 * 通过 hash 查询 BlockEntity 
	 * 先从 this.blocks 中进行查询，如果查询不到，从数据库中进行查询
	 * @param hash
	 * @return
	 */
	public BlockEntity getBlockEntityByHash(byte[] hash){
		if(hash != null){
			BlockEntity blockEntity = this.blocks.get(encApi.hexEnc(hash));
			if(blockEntity == null){
				OValue v = null;
				try {
					v = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(hash)).get();
					if(v != null){
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
			List<byte[]> hashs = this.storage.get(number + i);
			if (hashs != null) {
				for (int j = 0; j < hashs.size(); j++) {
					BlockEntity child = getBlockEntityByHash(hashs.get(j));
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
					BlockEntity child = getBlockEntityByHash(hashs.get(j));
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
