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
	// protected ALock readLock = new ALock(rwLock.readLock());
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
				log.debug("find hash::" + hash + " size::" + this.storage.row(hash).values().toArray().length);
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
					log.debug("add block into cache number::" + oNode.getNumber() + " hash::" + oNode.getBlockHash());
				} else if (!oNode.isConnect()) {
					oNode.setBlockEntity(block); 
					this.storage.put(hash, number, oNode);
					log.debug("update block in cache number::" + oNode.getNumber() + " hash::" + oNode.getBlockHash());
				} else {
					log.debug("block already connect in cache number::" + oNode.getNumber() + " hash::"
							+ oNode.getBlockHash());
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
			log.warn(" not found hash::" + hash + " number::" + number);
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

				// log.debug("====put connect block::" + hash);
				dao.getBlockDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
						oEntityHelper.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
				// dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
				// OEntityBuilder.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
				log.debug("success connect block number::" + oNode.getNumber() + " hash::" + oNode.getBlockHash()
						+ " stateroot::" + oNode.getBlockEntity().getHeader().getStateRoot());
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

				log.debug("success append block number::" + oNode.getNumber() + " hash::" + oNode.getBlockHash()
						+ " stateroot::" + oNode.getBlockEntity().getHeader().getStateRoot());
			}
		}
	}

	public boolean containsUnConnectChild(String hash, long number) {
		log.debug("try to find UnConnect Child number::" + number + " parentHash::" + hash);
		try (ALock l = writeLock.lock()) {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.column(number).entrySet().iterator(); it
					.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				log.debug("find child in cache, hash::" + item.getKey() + " parent::" + item.getValue().getParentHash()
						+ " number::" + item.getValue().getNumber() + " connect::" + item.getValue().isConnect());
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
				log.debug("find child in cache, hash::" + item.getKey() + " parent::" + item.getValue().getParentHash()
						+ " number::" + item.getValue().getNumber() + " connect::" + item.getValue().isConnect());
				if (item.getValue().getParentHash().equals(hash) && !item.getValue().isConnect()) {
					list.add(item.getValue().getBlockEntity());
				}
			}
		}
		return list;
	}

	// public BlockEntity getUnConnectChild(String paretHash, long number) {
	//
	// try (ALock l = readLock.lock()) {
	//
	//
	// return this.storage.get(paretHash, number + 1).getBlockEntity();
	// //
	// // for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it =
	// // storage.entrySet().iterator(); it.hasNext();) {
	// // Map.Entry<String, BlockStoreNodeValue> item = it.next();
	// // if (item.getValue().getParentHash().equals(hash) &&
	// // !item.getValue().isConnect()
	// // && item.getValue().getNumber() == (oParent.getNumber() + 1)) {
	// // list.add(item.getValue().getBlockEntity());
	// // }
	// // }
	// }
	// // return list;
	// }

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
			log.debug("stable block number::" + oBlockStoreNodeValue.getNumber() + " hash::"
					+ oBlockStoreNodeValue.getBlockHash());
			return oBlockStoreNodeValue;
		}
		return null;
	}

	public BlockEntity getBlockByNumber(long number) {
		try (ALock l = writeLock.lock()) {
			if (storage.containsColumn(number)) {
				return ((BlockStoreNodeValue) storage.column(number).values().toArray()[0]).getBlockEntity();
			}
			return null;
		}
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

	public void put(String hash, BlockEntity block) {
		try (ALock l = writeLock.lock()) {
			BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash, block.getHeader().getNumber());
			if (oBlockStoreNodeValue != null) {
				oBlockStoreNodeValue.setBlockEntity(block);
				log.debug("put block number::" + oBlockStoreNodeValue.getNumber() + " hash::"
						+ oBlockStoreNodeValue.getBlockHash() + " stateroot::"
						+ oBlockStoreNodeValue.getBlockEntity().getHeader().getStateRoot());
				this.storage.put(hash, block.getHeader().getNumber(), oBlockStoreNodeValue);

				// log.debug("====put save block::" + hash);
				dao.getBlockDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(block.getHeader().getBlockHash())),
						oEntityHelper.byteValue2OValue(block.toByteArray(),
								String.valueOf(block.getHeader().getNumber())));
			}
		}
	}

	public BlockEntity rollBackTo(long number, BlockEntity maxConnectBlock) {
		if (maxConnectBlock != null) {
			try (ALock l = writeLock.lock()) {
				String fHash = maxConnectBlock.getHeader().getParentHash();
				long fNumber = maxConnectBlock.getHeader().getNumber() - 1;
				BlockStoreNodeValue oNode = null;
				log.debug("roll back from hash::" + maxConnectBlock.getHeader().getBlockHash() + " parent::"
						+ maxConnectBlock.getHeader().getParentHash() + " number::"
						+ maxConnectBlock.getHeader().getNumber());
				while (fHash != null && this.storage.contains(fHash, fNumber) && fNumber >= number) {
					boolean isExistsChild = false;
					log.debug("roll back to::" + fHash + " number::" + fNumber);
					BlockStoreNodeValue currentNode = this.storage.get(fHash, fNumber);
					if (currentNode != null && currentNode.isConnect()) {
						if (fNumber != number) {
							currentNode.disConnect();
							this.storage.put(currentNode.getBlockHash(), currentNode.getNumber(), currentNode);
							log.debug("disconnect unstable cache number::" + currentNode.getNumber() + " hash::"
									+ currentNode.getBlockHash());
						}
						fHash = currentNode.getBlockEntity().getHeader().getParentHash();
						fNumber = currentNode.getBlockEntity().getHeader().getNumber() - 1;
						isExistsChild = true;

						oNode = currentNode;
					}

					if (!isExistsChild) {
						fHash = null;
					}
				}

				if (oNode != null) {
					log.debug(" dump node::" + oNode.getBlockHash() + " number::" + oNode.getNumber() + " connect::"
							+ oNode.isConnect());
					dao.getBlockDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
							oEntityHelper.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
					return oNode.getBlockEntity();
				} else {
					return null;
				}
			}
		}
		return null;
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
		// log.debug("====put disconnect block::" +
		// block.getHeader().getBlockHash());
		dao.getBlockDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
				oEntityHelper.byteValue2OValue(encApi.hexDec(block.getHeader().getBlockHash())));
	}

	public void removeForkBlock(long number) {
		try (ALock l = writeLock.lock()) {
			for (Iterator<Cell<String, Long, BlockStoreNodeValue>> it = storage.cellSet().iterator(); it.hasNext();) {
				Cell<String, Long, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getNumber() <= number) {
					log.debug("remove fork block number::" + item.getColumnKey() + " hash::" + item.getRowKey());
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
