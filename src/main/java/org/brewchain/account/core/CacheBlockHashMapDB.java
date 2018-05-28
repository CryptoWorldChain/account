package org.brewchain.account.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.util.ALock;
import org.brewchain.account.util.ByteArrayMap;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "CacheBlock_HashMapDB")
@Slf4j
@Data
public class CacheBlockHashMapDB implements ActorService {
	protected final ConcurrentHashMap<String, byte[]> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public CacheBlockHashMapDB() {
		this(new ConcurrentHashMap<String, byte[]>());
	}

	public CacheBlockHashMapDB(ConcurrentHashMap<String, byte[]> storage) {
		this.storage = storage;
	}

	public void put(String key, byte[] val) {
		if (val == null) {
			delete(key);
		} else {
			try (ALock l = writeLock.lock()) {
				storage.put(key, val);
			}
		}
	}

	public byte[] get(String key) {
		try (ALock l = readLock.lock()) {
			return storage.get(key);
		}
	}
	
	public byte[] getAndDelete(String key) {
		try (ALock l = readLock.lock()) {
			byte[] ret = storage.get(key);
			storage.remove(key);
			return ret;
		}
	}

	public void delete(String key) {
		try (ALock l = writeLock.lock()) {
			storage.remove(key);
		}
	}
}
