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
@Instantiate(name = "WaitSend_HashMapDB")
@Slf4j
@Data
public class WaitSendHashMapDB implements ActorService {
	protected ConcurrentHashMap<String, HashPair> storage;

	public WaitSendHashMapDB() {
		this(new ConcurrentHashMap<String, HashPair>());
	}

	public WaitSendHashMapDB(ConcurrentHashMap<String,HashPair> storage) {
		this.storage = storage;
	}


	public void put(String key, HashPair val) {
		if (val == null) {
			delete(key);
		} else {
			storage.put(key, val);
		}
	}
	
	public void put(Map<String,HashPair> vals) {
		storage.putAll(vals);
	}
//
//	public HashPair get(String key) {
//		return storage.get(key);
//	}

	public void delete(String key) {
		storage.remove(key);
	}

	public int size() {
		return storage.size();
	}

	public void updateBatch(Map<String, HashPair> rows) {
		storage.putAll(rows);
	}

	public Map<String, HashPair> getStorage() {
		return storage;
	}
}
