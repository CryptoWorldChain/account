package org.brewchain.account.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.bean.HashPair;

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
	protected ConcurrentHashMap<byte[], HashPair> storage;

	public WaitBlockHashMapDB() {
		this(new ConcurrentHashMap<byte[], HashPair>());
	}

	public WaitBlockHashMapDB(ConcurrentHashMap<byte[], HashPair> storage) {
		this.storage = storage;
	}

	public void put(byte[] key, HashPair val) {
		if (val == null) {
			delete(key);
		} else {
			storage.put(key, val);
		}
	}

	public HashPair get(byte[] key) {
		return storage.get(key);
	}

	public void delete(byte[] key) {
		storage.remove(key);
	}

	public int size() {
		return storage.size();
	}

	public void updateBatch(Map<byte[], HashPair> rows) {
		storage.putAll(rows);
	}

	public Map<byte[], HashPair> getStorage() {
		return storage;
	}
}
