package org.brewchain.account.core.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

	@Override
	public boolean containKey(String hash) {
		boolean flag = this.blocks.containsKey(hash);
		if(!flag) {
			if(getFromDB(hash) != null){
				flag = true;
			}
		}
		return flag;
	}

	@Override
	public BlockEntity get(String hash) {
		BlockEntity block = this.blocks.get(hash);
		if(block == null){
			block = getFromDB(hash);
		}
		return block;
	}

	@Override
	public boolean add(BlockEntity block) {
		int number = block.getHeader().getNumber();
		byte[] hash  = block.getHeader().getBlockHash().toByteArray();
		//storage
		this.storage.put(number, hash);
		if(maxNumber < number){
			maxNumber = number;
		}
		
		//blocks
		this.blocks.put(encApi.hexEnc(hash), block);
		
		return true;
	}

	@Override
	public BlockEntity getBlockByNumber(int number) {
		byte[] hash = this.storage.get(number);
		BlockEntity block = null;
		if(hash != null){
			block = get(encApi.hexEnc(hash));
		}
		return block;
	}
	
	public BlockEntity getFromDB(String hash){
		BlockEntity block = null;
		OValue v = null;
		try {
			v = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(encApi.hexDec(hash))).get();
			if (v != null) {
				block = BlockEntity.parseFrom(v.getExtdata());
				add(block);
			}
		} catch (ODBException | InterruptedException | ExecutionException | InvalidProtocolBufferException e) {
			log.error("get block from db error :: " + e.getMessage());
		}
		
		return block;
	}

}
