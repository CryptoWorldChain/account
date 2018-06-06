package org.brewchain.account.core.store;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.bouncycastle.util.encoders.Hex;
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

	private int maxNumber = -1;
	private int maxStableNumber = -1;
	private BlockChainTempNode maxBlock = null;
	private BlockChainTempNode maxStableBlock = null;
	protected final TreeMap<String, BlockChainTempNode> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public BlockChainTempStore() {
		this.storage = new TreeMap<String, BlockChainTempNode>();
	}

	public void tryAdd(String hash, String parentHash, int number) {
		tryAdd(hash, parentHash, number, false);
	}

	public void tryAdd(String hash, String parentHash, int number, boolean isStable) {
		try (ALock l = writeLock.lock()) {
			BlockChainTempNode oNode = new BlockChainTempNode(hash, parentHash, number, isStable);
			if (!this.storage.containsKey(hash)) {
				this.storage.put(hash, oNode);
			}

			if (maxNumber < number) {
				maxNumber = number;
				maxBlock = oNode;
			}

			if (isStable) {
				if (maxStableNumber < number) {
					maxStableNumber = number;
					maxStableBlock = oNode;
				}
			}
		}
	}

	public BlockChainTempNode tryAddAndPop(String hash, String parentHash, int number) {
		try (ALock l = writeLock.lock()) {
			tryAdd(hash, parentHash, number, true);
			// loop to stable node and pop

			int count = 0;
			BlockChainTempNode oStableNode = null;
			while (this.storage.containsKey(parentHash)) {
				count += 1;
				oStableNode = this.storage.get(parentHash);
				parentHash = oStableNode.getParentHash();

			}
			if (oStableNode != null && count >= KeyConstant.ROLLBACK_BLOCK) {
				storage.remove(oStableNode.getHash());
				return oStableNode;
			}
		}
		return null;
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

	public BlockChainTempNode getAndDeleteByParent(String parentHash) {
		try (ALock l = readLock.lock()) {
			BlockChainTempNode oNode = getByParent(parentHash);
			if (oNode != null) {
				this.storage.remove(oNode.getHash());
				return oNode;
			}
			return null;
		}
	}

	public BlockChainTempNode get(String hash) {
		try (ALock l = readLock.lock()) {
			return this.storage.get(hash);
		}
	}

	public BlockChainTempNode getByParent(String parentHash) {
		try (ALock l = readLock.lock()) {
			for (Iterator<Map.Entry<String, BlockChainTempNode>> it = storage.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, BlockChainTempNode> item = it.next();
				if (item.getValue().getParentHash().equals(parentHash)) {
					return item.getValue();
				}
			}
			return null;
		}
	}

	public BlockChainTempNode getByNumber(int number) {
		try (ALock l = readLock.lock()) {
			for (Iterator<Map.Entry<String, BlockChainTempNode>> it = storage.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, BlockChainTempNode> item = it.next();
				if (item.getValue().getNumber() == number) {
					return item.getValue();
				}
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

	public String dump() {
		String dump = "";
		for (Iterator<Map.Entry<String, BlockChainTempNode>> it = storage.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, BlockChainTempNode> item = it.next();
			dump += String.format("%s : %s\\n", item.getKey(), item.getValue().toString());

		}
		return dump;
	}
}
