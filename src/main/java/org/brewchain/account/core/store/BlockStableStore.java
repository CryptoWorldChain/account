package org.brewchain.account.core.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.KeyConstant;
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
@Instantiate(name = "BlockStore_Stable")
@Slf4j
@Data
public class BlockStableStore implements IBlockStore, ActorService {

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
	
	public BlockStableStore() {
		this.storage = new ConcurrentHashMap<Integer, byte[]>();
		this.blocks = new LRUCache<String, BlockEntity>(KeyConstant.CACHE_SIZE);
	}

	@Override
	public boolean containKey(String hash) {
		boolean flag = this.blocks.containsKey(hash);
		if (!flag) {
			if (getFromDB(hash) != null) {
				flag = true;
			}
		}
		return flag;
	}

	@Override
	public BlockEntity get(String hash) {
		BlockEntity block = this.blocks.get(hash);
		if (block == null) {
			block = getFromDB(hash);
		}
		return block;
	}

	@Override
	public boolean add(BlockEntity block) {
		int number = block.getHeader().getNumber();
		byte[] hash = block.getHeader().getBlockHash().toByteArray();

		// storage
		this.storage.put(number, hash);
		if (maxNumber < number) {
			maxNumber = number;
		}

		// blocks
		this.blocks.put(encApi.hexEnc(hash), block);

		log.debug("stable block number::" + block.getHeader().getNumber() + " hash::" + encApi.hexEnc(hash));
		dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK),
				OEntityBuilder.byteValue2OValue(hash));
		return true;
	}

	@Override
	public BlockEntity getBlockByNumber(int number) {
		byte[] hash = this.storage.get(number);
		BlockEntity block = null;
		if (hash != null) {
			block = get(encApi.hexEnc(hash));
		}
		return block;
	}

	@Override
	public BlockEntity rollBackTo(int number) {
		BlockEntity block = null;
		byte[] hash = null;
		int lastBlockNumber = 0;
		while ((lastBlockNumber = getLastBlockNumber()) > number) {
			hash = this.storage.remove(lastBlockNumber);
		}
		if (hash != null) {
			block = get(encApi.hexEnc(hash));
		}
		dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK),
				OEntityBuilder.byteValue2OValue(block.getHeader().getBlockHash().toByteArray()));
		return block;
	}

	public int getLastBlockNumber() {
		try (ALock l = readLock.lock()) {
			if (storage.size() > 0) {
				return storage.size() - 1;
			}
			return -1;
		}
	}

	public BlockEntity getFromDB(String hash) {
		BlockEntity block = null;
		OValue v = null;
		try {
			v = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(encApi.hexDec(hash))).get();
			if (v != null) {
				block = BlockEntity.parseFrom(v.getExtdata());
				this.blocks.put(encApi.hexEnc(block.getHeader().getBlockHash().toByteArray()), block);
			}
		} catch (ODBException | InterruptedException | ExecutionException | InvalidProtocolBufferException e) {
			log.error("get block from db error :: " + e.getMessage());
		}

		return block;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}

}
