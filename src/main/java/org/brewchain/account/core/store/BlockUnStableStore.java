package org.brewchain.account.core.store;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.store.BlockStore.BlockNotFoundInStoreException;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.ALock;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

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
public class BlockUnStableStore implements IBlockStore, ActorService {
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;

	protected final ConcurrentHashMap<String, BlockStoreNodeValue> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public BlockUnStableStore() {
		storage = new ConcurrentHashMap<String, BlockStoreNodeValue>();
	}

	@Override
	public boolean containKey(String hash) {
		try (ALock l = readLock.lock()) {
			return this.storage.containsKey(hash);
		}
	}

	@Override
	public BlockEntity get(String hash) {
		try (ALock l = writeLock.lock()) {
			BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash);
			if (oBlockStoreNodeValue != null) {
				return oBlockStoreNodeValue.getBlockEntity();
			}
			return null;
		}
	}

	@Override
	public boolean add(BlockEntity block) {
		String hash = block.getHeader().getBlockHash();
		String parentHash = block.getHeader().getParentHash();

		try (ALock l = writeLock.lock()) {
			try {
				BlockStoreNodeValue oNode = null;
				if (!this.storage.containsKey(hash)) {
					oNode = new BlockStoreNodeValue(hash, parentHash, block.getHeader().getNumber(), block);
					this.storage.put(hash, oNode);
					log.debug("add block into cache number::" + oNode.getNumber() + " hash::" + oNode.getBlockHash());
					// dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(block.getHeader().getBlockHash()),
					// OEntityBuilder.byteValue2OValue(block.toByteArray()));
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

	public BlockStoreNodeValue getNode(String hash) {
		try (ALock l = writeLock.lock()) {
			BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash);
			if (oBlockStoreNodeValue != null) {
				return oBlockStoreNodeValue;
			}
			log.warn(" not found hash::" + hash);
			return null;
		}
	}

	public int increaseRetryTimes(String hash) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.containsKey(hash)) {
				BlockStoreNodeValue oNode = this.storage.get(hash);
				oNode.increaseRetryTimes();
				this.storage.put(hash, oNode);
				return oNode.getRetryTimes();
			} else {
				return 0;
			}
		}
	}

	public void resetRetryTimes(String hash) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.containsKey(hash)) {
				BlockStoreNodeValue oNode = this.storage.get(hash);
				oNode.setRetryTimes(0);
				this.storage.put(hash, oNode);
			}
		}
	}

	public boolean isConnect(String hash) {
		try (ALock l = readLock.lock()) {
			if (this.storage.containsKey(hash)) {
				BlockStoreNodeValue oNode = this.storage.get(hash);
				return oNode.isConnect();
			}
			return false;
		}
	}

	public void connect(String hash) throws BlockNotFoundInStoreException {
		try (ALock l = writeLock.lock()) {
			if (!this.storage.containsKey(hash)) {
				throw new BlockNotFoundInStoreException("block unable to connect to block chain.");
			}
			BlockStoreNodeValue oNode = this.storage.get(hash);
			if (!oNode.isConnect()) {
				oNode.connect();
				this.storage.put(hash, oNode);

				// log.debug("====put connect block::" + hash);
				dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
						OEntityBuilder.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
				// dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
				// OEntityBuilder.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
				log.debug("success connect block number::" + oNode.getNumber() + " hash::" + oNode.getBlockHash()
						+ " stateroot::" + oNode.getBlockEntity().getHeader().getStateRoot());
			}
		}
	}

	public boolean containsUnConnectChild(String hash) {
		try (ALock l = readLock.lock()) {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getParentHash().equals(hash) && !item.getValue().isConnect()) {
					return true;
				}
			}
		}
		return false;
	}

	public List<BlockEntity> getUnConnectChild(String hash) {
		List<BlockEntity> list = new ArrayList<>();
		try (ALock l = readLock.lock()) {
			BlockStoreNodeValue oParent = this.storage.get(hash);
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getParentHash().equals(hash) && !item.getValue().isConnect()
						&& item.getValue().getNumber() == (oParent.getNumber() + 1)) {
					list.add(item.getValue().getBlockEntity());
				}
			}
		}
		return list;
	}

	public BlockStoreNodeValue tryPop(String hash) {
		BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash);
		int count = 0;
		BlockStoreNodeValue oParent = this.storage.get(oBlockStoreNodeValue.getParentHash());
		while (oParent != null) {
			count += 1;
			oBlockStoreNodeValue = oParent;
			oParent = this.storage.get(oParent.getParentHash());
		}
		if (oBlockStoreNodeValue != null && count >= blockChainConfig.getStableBlocks()) {
			storage.remove(oBlockStoreNodeValue.getBlockHash());
			log.debug("stable block number::" + oBlockStoreNodeValue.getNumber() + " hash::"
					+ oBlockStoreNodeValue.getBlockHash());
			return oBlockStoreNodeValue;
		}
		return null;
	}

	@Override
	public BlockEntity getBlockByNumber(long number) {
		try (ALock l = readLock.lock()) {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getNumber() == number && item.getValue().isConnect()) {
					return item.getValue().getBlockEntity();
				}
			}
		}
		return null;
	}

	public List<BlockEntity> getBlocksByNumber(long number) {
		try (ALock l = readLock.lock()) {
			List<BlockEntity> list = new ArrayList<>();
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
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
			BlockStoreNodeValue oBlockStoreNodeValue = this.storage.get(hash);
			if (oBlockStoreNodeValue != null) {
				oBlockStoreNodeValue.setBlockEntity(block);
				log.debug("put block number::" + oBlockStoreNodeValue.getNumber() + " hash::"
						+ oBlockStoreNodeValue.getBlockHash() + " stateroot::"
						+ oBlockStoreNodeValue.getBlockEntity().getHeader().getStateRoot());
				this.storage.put(hash, oBlockStoreNodeValue);

				// log.debug("====put save block::" + hash);
				dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(encApi.hexDec(block.getHeader().getBlockHash())),
						OEntityBuilder.byteValue2OValue(block.toByteArray(),
								String.valueOf(block.getHeader().getNumber())));
			}
		}
	}

	@Override
	public BlockEntity rollBackTo(long number) {
		BlockEntity oBlockEntity = getBlockByNumber(number);
		if (oBlockEntity != null) {
			try (ALock l = writeLock.lock()) {
				String hash = oBlockEntity.getHeader().getBlockHash();
				if (this.storage.containsKey(hash)) {
					BlockStoreNodeValue oNode = this.storage.get(hash);
					while (StringUtils.isNotBlank(hash)) {
						boolean isExistsChild = false;
						for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it
								.hasNext();) {
							Map.Entry<String, BlockStoreNodeValue> item = it.next();
							if (item.getValue().getParentHash().equals(hash) && item.getValue().isConnect()) {
								BlockStoreNodeValue newValue = item.getValue();
								newValue.disConnect();
								this.storage.put(item.getKey(), newValue);
								hash = item.getValue().getBlockHash();
								isExistsChild = true;
							}
						}
						if (!isExistsChild) {
							hash = null;
						}
					}

					// log.debug("====put rollback block::" + hash);
					dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
							OEntityBuilder.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
					return oNode.getBlockEntity();
				} else {
					return null;
				}
			}
		}
		return null;
	}

	@Override
	public void clear() {
		for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, BlockStoreNodeValue> item = it.next();
			if (!item.getValue().isConnect()) {
				this.storage.remove(item.getKey());
			}
		}
	}

	public void disconnectAll(BlockEntity block) {
		for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, BlockStoreNodeValue> item = it.next();
			if (item.getValue().isConnect()) {
				BlockStoreNodeValue newValue = item.getValue();
				newValue.disConnect();
				this.storage.put(item.getKey(), newValue);
			}
		}

		// log.debug("====put disconnect block::" + block.getHeader().getBlockHash());
		dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
				OEntityBuilder.byteValue2OValue(encApi.hexDec(block.getHeader().getBlockHash())));
	}

	public void removeForkBlock(long number) {
		try (ALock l = readLock.lock()) {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getNumber() == number) {
					dao.getBlockDao().delete(OEntityBuilder.byteKey2OKey(encApi.hexDec(item.getKey())));
					it.remove();
				}
			}
		}
	}
}
