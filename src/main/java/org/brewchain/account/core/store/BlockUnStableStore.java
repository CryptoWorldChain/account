package org.brewchain.account.core.store;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.block.GetBlockByHashImpl;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.store.BlockStore.BlockNotFoundInStoreException;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.ALock;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "BlockStore_UnStable")
@Slf4j
@Data
public class BlockUnStableStore implements ActorService {
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;

	protected final Table<String, Long, BlockStoreNodeValue> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public BlockUnStableStore() {
		storage = HashBasedTable.create();
	}

	public boolean containConnectKey(String hash) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.containsRow(hash)) {
				BlockStoreNodeValue oBlockStoreNodeValue = (BlockStoreNodeValue) this.storage.row(hash).values()
						.toArray()[0];
				if (oBlockStoreNodeValue.isConnect()) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean containKey(String hash) {
		try (ALock l = writeLock.lock()) {
			return this.storage.containsRow(hash);
		}
	}

	public BlockEntity get(String hash) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.containsRow(hash)) {
				BlockStoreNodeValue oBlockStoreNodeValue = (BlockStoreNodeValue) this.storage.row(hash).values()
						.toArray()[0];
				// log.debug("find hash::" + hash + " size::" +
				// this.storage.row(hash).values().toArray().length);
				if (oBlockStoreNodeValue != null) {
					return oBlockStoreNodeValue.getBlockEntity();
				}
			}
			return null;
		}
	}

	public BlockEntity get(String hash, long number) {
		try (ALock l = writeLock.lock()) {
			BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash, number);
			if (oBlockStoreNodeValue != null) {
				return oBlockStoreNodeValue.getBlockEntity();
			}
			return null;
		}
	}

	public boolean add(BlockEntity block) {
		String hash = block.getHeader().getBlockHash();
		long number = block.getHeader().getNumber();
		String parentHash = block.getHeader().getParentHash();

		try (ALock l = writeLock.lock()) {
			try {
				BlockStoreNodeValue oNode = null;
				oNode = this.storage.get(hash, number);
				if (oNode == null) {
					oNode = new BlockStoreNodeValue(hash, parentHash, number, block);
					this.storage.put(hash, number, oNode);
					// log.debug("add block into cache number::" +
					// oNode.getNumber() + " hash::" + oNode.getBlockHash());
				} else if (!oNode.isConnect()) {
					oNode.setBlockEntity(block);
					this.storage.put(hash, number, oNode);
					// log.debug("update block in cache number::" +
					// oNode.getNumber() + " hash::" + oNode.getBlockHash());
				} else {
					// log.debug("block already connect in cache number::" +
					// oNode.getNumber() + " hash::"
					// + oNode.getBlockHash());
				}
				return true;
			} catch (Exception e) {
				log.error("try to store block error::" + e.getMessage());
			}
		}
		return false;
	}

	public int size() {
		return this.storage.size();
	}

	public BlockStoreNodeValue getNode(String hash, long number) {
		try (ALock l = writeLock.lock()) {
			BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash, number);
			if (oBlockStoreNodeValue != null) {
				return oBlockStoreNodeValue;
			}
			// log.warn(" not found hash::" + hash + " number::" + number);
			return null;
		}
	}

	public int increaseRetryTimes(String hash, long number) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.contains(hash, number)) {
				BlockStoreNodeValue oNode = this.storage.get(hash, number);
				oNode.increaseRetryTimes();
				this.storage.put(hash, number, oNode);
				return oNode.getRetryTimes();
			} else {
				return 0;
			}
		}
	}

	public void resetRetryTimes(String hash, long number) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.contains(hash, number)) {
				BlockStoreNodeValue oNode = this.storage.get(hash, number);
				oNode.setRetryTimes(0);
				this.storage.put(hash, number, oNode);
			}
		}
	}

	public boolean isConnect(String hash, long number) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.contains(hash, number)) {
				BlockStoreNodeValue oNode = this.storage.get(hash, number);
				return oNode.isConnect();
			}
			return false;
		}
	}

	public void connect(String hash, long number) throws BlockNotFoundInStoreException {
		try (ALock l = writeLock.lock()) {
			if (!this.storage.contains(hash, number)) {
				throw new BlockNotFoundInStoreException("block unable to connect to block chain.");
			}
			BlockStoreNodeValue oNode = this.storage.get(hash, number);
			if (!oNode.isConnect()) {
				oNode.connect();
				this.storage.put(hash, number, oNode);

				dao.getBlockDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
						oEntityHelper.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
				// log.debug("success connect block number::" +
				// oNode.getNumber() + " hash::" + oNode.getBlockHash()
				// + " stateroot::" +
				// oNode.getBlockEntity().getHeader().getStateRoot());
			}
		}
	}

	public void append(String hash, long number) throws BlockNotFoundInStoreException {
		try (ALock l = writeLock.lock()) {
			if (!this.storage.contains(hash, number)) {
				throw new BlockNotFoundInStoreException("block unable to connect to block chain.");
			}
			BlockStoreNodeValue oNode = this.storage.get(hash, number);
			if (!oNode.isConnect()) {
				oNode.connect();
				this.storage.put(hash, number, oNode);

				// log.debug("success append block number::" + oNode.getNumber()
				// + " hash::" + oNode.getBlockHash()
				// + " stateroot::" +
				// oNode.getBlockEntity().getHeader().getStateRoot());
			}
		}
	}

	public boolean containsUnConnectChild(String hash, long number) {
		// log.debug("try to find UnConnect Child number::" + number + "
		// parentHash::" + hash);
		try (ALock l = writeLock.lock()) {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.column(number).entrySet().iterator(); it
					.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				// log.debug("find child in cache, hash::" + item.getKey() + "
				// parent::" + item.getValue().getParentHash()
				// + " number::" + item.getValue().getNumber() + " connect::" +
				// item.getValue().isConnect());
				if (item.getValue().getParentHash().equals(hash) && !item.getValue().isConnect()) {
					return true;
				}
			}
		}
		return false;
	}

	public List<BlockEntity> getUnConnectChild(String hash, long number) {
		List<BlockEntity> list = new ArrayList<>();
		try (ALock l = writeLock.lock()) {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.column(number).entrySet().iterator(); it
					.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				// log.debug("find child in cache, hash::" + item.getKey() + "
				// parent::" + item.getValue().getParentHash()
				// + " number::" + item.getValue().getNumber() + " connect::" +
				// item.getValue().isConnect());
				if (item.getValue().getParentHash().equals(hash) && !item.getValue().isConnect()) {
					list.add(item.getValue().getBlockEntity());
				}
			}
		}
		return list;
	}

	public BlockStoreNodeValue tryPop(String hash, long number) {
		BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash, number);
		int count = 0;
		BlockStoreNodeValue oParent = this.storage.get(oBlockStoreNodeValue.getParentHash(),
				oBlockStoreNodeValue.getNumber() - 1);
		while (oParent != null) {
			count += 1;
			oBlockStoreNodeValue = oParent;
			oParent = this.storage.get(oParent.getParentHash(), oParent.getNumber() - 1);
		}
		if (oBlockStoreNodeValue != null && count >= blockChainConfig.getStableBlocks()) {
			storage.remove(oBlockStoreNodeValue.getBlockHash(), oBlockStoreNodeValue.getNumber());
			// log.debug("stable block number::" +
			// oBlockStoreNodeValue.getNumber() + " hash::"
			// + oBlockStoreNodeValue.getBlockHash());
			return oBlockStoreNodeValue;
		}
		return null;
	}

	public BlockEntity getBlockByNumber(long number) {
		if (storage.containsColumn(number)) {
			// find connected block
			BlockEntity be = null;
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.column(number).entrySet().iterator(); it
					.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				if (item.getValue().isConnect()) {
					return item.getValue().getBlockEntity();
				}
				if (be == null) {
					be = item.getValue().getBlockEntity();
				}
			}
			return be;
			// BlockStoreNodeValue[] objs =
			// storage.column(number).values().toArray(new BlockStoreNodeValue[]
			// {});
			// for (int i = 0; i < objs.length; i++) {
			// if (objs[i].isConnect()) {
			// return objs[i].getBlockEntity();
			// }
			// }
			// return objs[0].getBlockEntity();
		}
		return null;
	}

	public BlockEntity getBlockByNumberAndHash(String hash, long number) {
		try (ALock l = writeLock.lock()) {
			if (storage.contains(hash, number)) {
				return storage.get(hash, number).getBlockEntity();
			}
			return null;
		}
	}

	public List<BlockEntity> getConnectBlocksByNumber(long number) {
		try (ALock l = writeLock.lock()) {
			List<BlockEntity> list = new ArrayList<>();
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.column(number).entrySet().iterator(); it
					.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getNumber() == number && item.getValue().isConnect()) {
					list.add(item.getValue().getBlockEntity());
				}
			}
			return list;
		}
	}

	public List<BlockEntity> getBlocksByNumber(long number) {
		try (ALock l = writeLock.lock()) {
			List<BlockEntity> list = new ArrayList<>();
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.column(number).entrySet().iterator(); it
					.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				list.add(item.getValue().getBlockEntity());
			}
			return list;
		}
	}

	public void put(String hash, BlockEntity block) {
		try (ALock l = writeLock.lock()) {
			BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash, block.getHeader().getNumber());
			if (oBlockStoreNodeValue != null) {
				oBlockStoreNodeValue.setBlockEntity(block);
				// log.debug("put block number::" +
				// oBlockStoreNodeValue.getNumber() + " hash::"
				// + oBlockStoreNodeValue.getBlockHash() + " stateroot::"
				// +
				// oBlockStoreNodeValue.getBlockEntity().getHeader().getStateRoot());
				this.storage.put(hash, block.getHeader().getNumber(), oBlockStoreNodeValue);

				// log.debug("====put save block::" + hash);
				dao.getBlockDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(block.getHeader().getBlockHash())),
						oEntityHelper.byteValue2OValue(block.toByteArray(),
								String.valueOf(block.getHeader().getNumber())));
			}
		}
	}

	public BlockEntity rollBackTo(long number, long maxNumber) {
		try (ALock l = writeLock.lock()) {
			BlockStoreNodeValue oNode = null;
			boolean isContinue = true;
			for (long i = maxNumber; i >= number; i--) {
				if (isContinue) {
					// log.debug("roll back to number::" + i);
					if (i == 0) {
						// genesis block

					}
					List<BlockEntity> blocks = getBlocksByNumber(i);
					for (BlockEntity blockEntity : blocks) {
						BlockStoreNodeValue currentNode = this.storage.get(blockEntity.getHeader().getBlockHash(),
								blockEntity.getHeader().getNumber());
						if (currentNode != null && currentNode.isConnect()) {
							if (currentNode.getNumber() != number) {
								currentNode.disConnect();
								this.storage.put(currentNode.getBlockHash(), currentNode.getNumber(), currentNode);
								// log.debug("disconnect unstable cache
								// number::" + currentNode.getNumber() + "
								// hash::"
								// + currentNode.getBlockHash());
							}

							oNode = currentNode;
						} else if (currentNode != null) {
							// isContinue = false;
							// oNode.connect();
							// this.storage.put(oNode.getBlockHash(),
							// oNode.getNumber(), oNode);
							// log.debug("unable disconnect unstable cache
							// number, reconnect::" + currentNode.getNumber()
							// + " hash::" + currentNode.getBlockHash());
						}
					}
				} else {
					break;
				}
			}

			if (oNode != null) {
				// log.debug(" dump node::" + oNode.getBlockHash() + " number::"
				// + oNode.getNumber() + " connect::"
				// + oNode.isConnect());
				dao.getBlockDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
						oEntityHelper.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
				return oNode.getBlockEntity();
			} else {
				return null;
			}
		}
	}

	public void disconnectAll(BlockEntity block) {
		try (ALock l = writeLock.lock()) {
			for (Iterator<Cell<String, Long, BlockStoreNodeValue>> it = storage.cellSet().iterator(); it.hasNext();) {
				Cell<String, Long, BlockStoreNodeValue> item = it.next();
				if (item.getValue().isConnect()) {
					BlockStoreNodeValue newValue = item.getValue();
					newValue.disConnect();
					this.storage.put(item.getRowKey(), item.getColumnKey(), newValue);
				}
			}
		}
		dao.getBlockDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
				oEntityHelper.byteValue2OValue(encApi.hexDec(block.getHeader().getBlockHash())));
	}

	public void removeForkBlock(long number) {
		try (ALock l = writeLock.lock()) {
			for (Iterator<Cell<String, Long, BlockStoreNodeValue>> it = storage.cellSet().iterator(); it.hasNext();) {
				Cell<String, Long, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getNumber() <= number) {
					// log.debug("remove fork block number::" +
					// item.getColumnKey() + " hash::" + item.getRowKey());
					dao.getBlockDao().delete(oEntityHelper.byteKey2OKey(encApi.hexDec(item.getRowKey())));
					it.remove();
				}
			}
		}
	}

	public BlockStoreNodeValue tryToRestoreFromDb(String hash, long number) {
		BlockEntity block = getFromDB(hash);
		if (block != null) {
			if (number == block.getHeader().getNumber()) {
				add(block);
				return getNode(hash, block.getHeader().getNumber());
			}
		}
		return null;
	}

	public BlockEntity getFromDB(String hash) {
		BlockEntity block = null;
		OValue v = null;
		try {
			v = dao.getBlockDao().get(oEntityHelper.byteKey2OKey(encApi.hexDec(hash))).get();
			if (v != null) {
				block = BlockEntity.parseFrom(v.getExtdata());
			}
		} catch (ODBException | InterruptedException | ExecutionException | InvalidProtocolBufferException e) {
			log.error("get block from db error :: " + e.getMessage());
		}

		return block;
	}
}
