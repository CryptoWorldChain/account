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
	protected final ConcurrentHashMap<byte[], byte[]> storage;

	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());
	protected ALock writeLock = new ALock(rwLock.writeLock());

	public CacheBlockHashMapDB() {
		this(new ConcurrentHashMap<byte[], byte[]>());
	}

	public CacheBlockHashMapDB(ConcurrentHashMap<byte[], byte[]> storage) {
		this.storage = storage;
	}

	public void put(byte[] key, byte[] val) {
		if (val == null) {
			delete(key);
		} else {
			try (ALock l = writeLock.lock()) {
				storage.put(key, val);
			}
		}
	}

	public byte[] get(byte[] key) {
		try (ALock l = readLock.lock()) {
			return storage.get(key);
		}
	}
	
	public byte[] getAndDelete(byte[] key) {
		try (ALock l = readLock.lock()) {
			byte[] ret = storage.get(key);
			storage.remove(key);
			return ret;
		}
	}

	public void delete(byte[] key) {
		try (ALock l = writeLock.lock()) {
			storage.remove(key);
		}
	}
}
