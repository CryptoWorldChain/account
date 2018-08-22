package org.brewchain.account.core.store;

import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.ALock;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OPair;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	protected final LoadingCache<String, BlockEntity> blocks = CacheBuilder.newBuilder()
			.maximumSize(KeyConstant.CACHE_SIZE).build(new CacheLoader<String, BlockEntity>() {
				public BlockEntity load(String key) {
					try {
						List<OPair> oPairs = dao.getBlockDao().listBySecondKey(key).get();
						if (oPairs.size() > 0) {
							BlockEntity block = BlockEntity.parseFrom(oPairs.get(0).getValue().getExtdata());
							return block;
						}
					} catch (Exception e) {
						log.error("cannot get block");
					}
					return null;
				}
			});
	protected final LoadingCache<Long, BlockEntity> blocksByNumber = CacheBuilder.newBuilder()
			.maximumSize(KeyConstant.CACHE_SIZE).build(new CacheLoader<Long, BlockEntity>() {
				public BlockEntity load(Long key) {
					try {
						List<OPair> oPairs = dao.getBlockDao().listBySecondKey(String.valueOf(key)).get();
						if (oPairs.size() > 0) {
							BlockEntity block = BlockEntity.parseFrom(oPairs.get(0).getValue().getExtdata());
							return block;
						}
					} catch (Exception e) {
						log.error("cannot get block");
					}
					return null;
				}
			});
	private long maxNumber = -1;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public BlockStableStore() {
	}

	@Override
	public boolean containKey(String hash) {
		boolean flag;
		try {
			flag = this.blocks.get(hash) == null;
		} catch (Exception e) {
			flag = false;
		}
		if (!flag) {
			if (getFromDB(hash) != null) {
				flag = true;
			}
		}
		return flag;
	}


	@Override
	public BlockEntity get(String hash) {
		BlockEntity block;
		try {
			block = (BlockEntity) this.blocks.get(hash);
		} catch (Exception e) {
			block = null;
		}
		if (block==null) {
			block = getFromDB(hash);
		}
		return block;
	}

	@Override
	public boolean add(BlockEntity block) {
		long number = block.getHeader().getNumber();
		try (ALock l = writeLock.lock()) {
			this.blocks.put(block.getHeader().getBlockHash(), block);
			this.blocksByNumber.put(block.getHeader().getNumber(), block);
		}

		if (block.getBody() != null) {
			LinkedList<OKey> txBlockKeyList = new LinkedList<OKey>();
			LinkedList<OValue> txBlockValueList = new LinkedList<OValue>();

			for (MultiTransaction oMultiTransaction : block.getBody().getTxsList()) {
				txBlockKeyList.add(oEntityHelper.byteKey2OKey(encApi.hexDec(oMultiTransaction.getTxHash())));
				txBlockValueList.add(oEntityHelper.byteValue2OValue(encApi.hexDec(block.getHeader().getBlockHash())));
			}

			// log.debug("====put transaction rel block::"+
			// block.getHeader().getBlockHash());
			dao.getTxblockDao().batchPuts(txBlockKeyList.toArray(new OKey[0]), txBlockValueList.toArray(new OValue[0]));
		}

		log.debug(
				"stable block number::" + block.getHeader().getNumber() + " hash::" + block.getHeader().getBlockHash());

		// log.debug("====put stable block::"+
		// block.getHeader().getBlockHash());

		if (maxNumber < number) {
			maxNumber = number;
			dao.getBlockDao().batchPuts(
					new OKey[] { oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK),
							oEntityHelper.byteKey2OKey(encApi.hexDec(block.getHeader().getBlockHash())) },
					new OValue[] { oEntityHelper.byteValue2OValue(encApi.hexDec(block.getHeader().getBlockHash())),
							oEntityHelper.byteValue2OValue(block.toByteArray(),
									String.valueOf(block.getHeader().getNumber())) });
		}
		return true;
	}

	@Override
	public BlockEntity getBlockByNumber(long number) {
		BlockEntity block = null;
		try {
			block = this.blocksByNumber.getIfPresent(number);
			if (block==null) {
				List<OPair> oPairs = dao.getBlockDao().listBySecondKey(String.valueOf(number)).get();
				if (oPairs.size() > 0) {
					block = BlockEntity.parseFrom(oPairs.get(0).getValue().getExtdata());
					this.blocks.put(block.getHeader().getBlockHash(), block);
					this.blocksByNumber.put(number, block);
					return block;
				}
			}
		} catch (ODBException | InterruptedException | ExecutionException | InvalidProtocolBufferException e) {
			log.error("get block from db error :: " + e.getMessage());
		}

		return block;
	}

	@Override
	public BlockEntity rollBackTo(long number) {
		BlockEntity block = null;
		block = getBlockByNumber(number);
		dao.getBlockDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK),
				oEntityHelper.byteValue2OValue(encApi.hexDec(block.getHeader().getBlockHash())));
		return block;
	}

	// public int getLastBlockNumber() {
	// try (ALock l = readLock.lock()) {
	// if (storage.size() > 0) {
	// return storage.size() - 1;
	// }
	// return -1;
	// }
	// }

	public BlockEntity getFromDB(String hash) {
		BlockEntity block = null;
		OValue v = null;
		try {
			v = dao.getBlockDao().get(oEntityHelper.byteKey2OKey(encApi.hexDec(hash))).get();
			if (v != null) {
				block = BlockEntity.parseFrom(v.getExtdata());
				try (ALock l = writeLock.lock()) {
					this.blocks.put(block.getHeader().getBlockHash(), block);
					this.blocksByNumber.put(block.getHeader().getNumber(), block);
				}
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
