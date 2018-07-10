package org.brewchain.account.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.util.ALock;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "WaitBlock_HashMapDB")
@Slf4j
@Data
public class WaitBlockHashMapDB implements ActorService {
	protected final ConcurrentHashMap<String, MultiTransaction> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public WaitBlockHashMapDB() {
		this(new ConcurrentHashMap<String, MultiTransaction>());
	}

	public WaitBlockHashMapDB(ConcurrentHashMap<String, MultiTransaction> storage) {
		this.storage = storage;
	}

	public void put(String key, MultiTransaction val) {
		if (val == null) {
			delete(key);
		} else {
			try (ALock l = writeLock.lock()) {
				storage.put(key, val);
			}
		}
	}

	public MultiTransaction get(String key) {
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

	public void updateBatch(Map<String, MultiTransaction> rows) {
		try (ALock l = writeLock.lock()) {
			for (Map.Entry<String, MultiTransaction> entry : rows.entrySet()) {
				put(entry.getKey(), entry.getValue());
			}
		}
	}

	public Map<String, MultiTransaction> getStorage() {
		return storage;
	}
}
