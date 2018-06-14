package org.brewchain.account.util;

import java.util.concurrent.ConcurrentHashMap;

public class LRUCache<K, V> {

	private ConcurrentHashMap<K, CacheEntity> caches;
	/**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 10; // default: 1024

    public int initialCapacity;
    public float loadFactor;
	private CacheEntity first;
	private CacheEntity last;
	
	public LRUCache(int initialCapacity){
		if(initialCapacity <= 0){
			throw new IllegalArgumentException("Illegal initial capacity : " + initialCapacity);
		}
		
		this.initialCapacity = initialCapacity;
		
		caches = new ConcurrentHashMap<>(initialCapacity);
	}

	public LRUCache(){
		this(DEFAULT_INITIAL_CAPACITY);
	}

	private static class CacheEntity {
		Object key;
		Object value;
		CacheEntity prev;
		CacheEntity next;
	}

	public synchronized void put(K k, V v) {
		CacheEntity cacheEntity = caches.get(k);
		if(cacheEntity == null){
			if(caches.size() >= this.initialCapacity){
				caches.remove(k);
				removeLast();
			}
			
			cacheEntity = new CacheEntity();
			cacheEntity.key = k;
		}
		
		cacheEntity.value = v;
		move2First(cacheEntity);
		caches.put(k, cacheEntity);
	}

	public synchronized Object get(K k) {
		if(first != null){
			if(first.key != null && (first.key.equals(k) || first.key == k))
				return first.value;
		}
		CacheEntity cacheEntity = caches.get(k);
		if(cacheEntity == null){
			return null;
		}
		move2First(cacheEntity);
		return cacheEntity.value;
	}
	
	public boolean containsKey(K k){
		return caches.contains(k);
	}
	
	public synchronized void removeLast(){
		if(last != null){
			@SuppressWarnings("unused")
			CacheEntity oldLast = last;
			last = last.prev;
			if(last == null){
				first = null;
			}else {
				last.next = null;
			}
			oldLast = null;//回收
		}
	}
	
	public synchronized void move2First(CacheEntity cache){
		if(cache == first){
			return;
		}
		if(cache.next != null){
			cache.next.prev = cache.prev;
		}
		if(cache.prev != null){
			cache.prev.next = cache.next;
		}
		if(cache == last){
			last = last.prev;
		}
		if(first == null || last == null){
			first = last = cache;
			return;
		}
		cache.next = first;
		first.prev = cache;
		first = cache;
		cache.prev = null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		CacheEntity node = first;
		while (node != null) {
			sb.append(String.format("%s:%s ", node.key, node.value));
			node = node.next;
		}

		return sb.toString();
	}
}
