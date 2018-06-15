package org.brewchain.account.trie;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.util.ALock;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Storage_TrieCache")
@Slf4j
@Data
public class StorageTrieCache implements ActorService {
	protected final ConcurrentHashMap<String, StorageTrie> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public StorageTrieCache() {
		this(new ConcurrentHashMap<String, StorageTrie>());
	}

	public StorageTrieCache(ConcurrentHashMap<String, StorageTrie> storage) {
		this.storage = storage;
	}

	public void put(String key, StorageTrie val) {
		if (val == null) {
			delete(key);
		} else {
			try (ALock l = writeLock.lock()) {
				storage.put(key, val);
			}
		}
	}

	public StorageTrie get(String key) {
		try (ALock l = readLock.lock()) {
			return storage.get(key);
		}
	}

	public void delete(String key) {
		try (ALock l = writeLock.lock()) {
			storage.remove(key);
		}
	}

	public Set<String> keys() {
		try (ALock l = readLock.lock()) {
			return getStorage().keySet();
		}
	}

	public void updateBatch(Map<String, StorageTrie> rows) {
		try (ALock l = writeLock.lock()) {
			for (Map.Entry<String, StorageTrie> entry : rows.entrySet()) {
				put(entry.getKey(), entry.getValue());
			}
		}
	}

	public Map<String, StorageTrie> getStorage() {
		return storage;
	}
}
