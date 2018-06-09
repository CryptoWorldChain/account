package org.brewchain.account.core.store;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.store.BlockStore.BlockNotFoundInStoreException;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.util.ALock;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
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
			return this.storage.contains(hash);
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
		String hash = encApi.hexEnc(block.getHeader().getBlockHash().toByteArray());
		String parentHash = encApi.hexEnc(block.getHeader().getParentHash().toByteArray());

		try (ALock l = writeLock.lock()) {
			try {
				BlockStoreNodeValue oNode = null;
				if (!this.storage.containsKey(hash)) {
					oNode = new BlockStoreNodeValue(hash, parentHash, block.getHeader().getNumber(), block);
					this.storage.put(hash, oNode);
					dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(block.getHeader().getBlockHash()),
							OEntityBuilder.byteValue2OValue(block.toByteArray()));
				}
				return true;
			} catch (Exception e) {
				log.error("try to store block error::" + e.getMessage());
			}
		}
		return false;
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

	public boolean isConnect(String hash) {
		try (ALock l = readLock.lock()) {
			if (this.storage.contains(hash)) {
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
				oNode.setConnect();
				this.storage.put(hash, oNode);
				dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK),
						OEntityBuilder.byteValue2OValue(encApi.hexDec(oNode.getBlockHash())));
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

	public boolean tryPop(String hash, BlockStoreNodeValue oBlockStoreNodeValue) {
		int count = 0;
		while (this.storage.containsKey(hash)) {
			count += 1;
			oBlockStoreNodeValue = this.storage.get(hash);
			hash = oBlockStoreNodeValue.getParentHash();
		}
		if (oBlockStoreNodeValue != null && count >= KeyConstant.STABLE_BLOCK) {
			storage.remove(hash);
			return true;
		}
		return false;
	}

	@Override
	public BlockEntity getBlockByNumber(int number) {
		try (ALock l = readLock.lock()) {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = storage.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				if (item.getValue().getNumber() == number) {
					return item.getValue().getBlockEntity();
				}
			}
		}
		return null;
	}

}
