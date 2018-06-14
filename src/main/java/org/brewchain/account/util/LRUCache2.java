package org.brewchain.account.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LRUCache2<K, V> extends LinkedHashMap<K, V> {

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2387942637668480578L;
	
	
	private final int MAX_CACHE_SIZE;
	private ReadWriteLock  globalLock ;  
    private Lock readLock;  
    private Lock writeLock;  

	public LRUCache2(int cacheSize) {
		super((int) Math.ceil(cacheSize / 0.75) + 1, 0.75f, true);
		MAX_CACHE_SIZE = cacheSize;
		globalLock  = new ReentrantReadWriteLock();  
        readLock = globalLock.readLock();  
        writeLock = globalLock.writeLock();  
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		return size() > MAX_CACHE_SIZE;
	}

	@Override
	public V get(Object key) {
		try{
			readLock.lock();
			return super.get(key);
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public V put(K key, V value) {
		try{
			writeLock.lock();
			return super.put(key, value);
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public String toString() {
		return "LRUCache [MAX_CACHE_SIZE=" + MAX_CACHE_SIZE + ", globalLock=" + globalLock + ", readLock=" + readLock + ", writeLock=" + writeLock + "]";
	}
}
