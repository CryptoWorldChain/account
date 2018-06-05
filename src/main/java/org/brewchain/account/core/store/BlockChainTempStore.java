package org.brewchain.account.core.store;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.gens.Block.BlockEntity;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.util.ALock;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "BlockChainTempStore_HashMapDB")
@Slf4j
@Data
public class BlockChainTempStore implements ActorService {
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	private int maxNumber = 0;
	protected final TreeMap<String, BlockChainTempNode> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public BlockChainTempStore() {
		this.storage = new TreeMap<String, BlockChainTempNode>();
	}

	public void tryAdd(String hash, String parentHash, int number) {
		try (ALock l = writeLock.lock()) {
			if (this.storage.containsKey(parentHash)) {
				BlockChainTempNode oParent = this.storage.get(parentHash);
				oParent.setChild(true);
				this.storage.put(parentHash, oParent);
			}
			if (!this.storage.containsKey(hash)) {
				this.storage.put(hash, new BlockChainTempNode(hash, number, false));
			}

			if (maxNumber < number) {
				maxNumber = number;
			}

			if (this.storage.firstEntry().getValue().getNumber() < (number - KeyConstant.ROLLBACK_BLOCK)) {
				this.storage.remove(this.storage.firstEntry().getKey());
			}
		}
	}

	public BlockChainTempNode getAndDelete(String hash) {
		try (ALock l = readLock.lock()) {
			if (this.storage.containsKey(hash)) {
				BlockChainTempNode ret = this.storage.get(hash);
				this.storage.remove(hash);
				return ret;
			}
			return null;
		}
	}

	public int getBlockCount() {
		return storage.size();
	}

	public void clear() {
		try (ALock l = writeLock.lock()) {
			storage.clear();
		}
	}
}
