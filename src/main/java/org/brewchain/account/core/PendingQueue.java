package org.brewchain.account.core;

import java.util.ArrayList;
import java.util.List;

import org.brewchain.account.bean.HashPair;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

@Data
@Slf4j
public class PendingQueue <T>{
	protected Cache storage;
	final static CacheManager cacheManager = new CacheManager();

	public final static String STR_COUNTER = "__idcounter";
	CounterInfoData counter = new CounterInfoData();

	public void shutdown(){
		storage.flush();
		cacheManager.shutdown();
	}

	public PendingQueue(String nameid,int maxElementsInMemory) {
		this.storage = new Cache("pendingqueue_"+nameid, maxElementsInMemory, MemoryStoreEvictionPolicy.LRU, true,
				"./pendingcache_"+nameid, true, 0, 0, true, 120, null);
		cacheManager.addCache(this.storage);
		Element ele = this.storage.get(STR_COUNTER);
		if (ele != null && ele.getObjectValue() != null) {
			counter = (CounterInfoData) ele.getObjectValue();
		}
	}

	public void addElement(T hp) {
		while (storage.putIfAbsent(new Element(counter.ptr_pending.incrementAndGet(), hp)) != null)
			;

	}

	public synchronized List<T> poll(int size) {
		List<T> ret = new ArrayList<>();
		for (int i = 0; i < size && counter.ptr_sending.get() < counter.ptr_pending.get(); i++) {
			Element element = storage.get(counter.ptr_sending.incrementAndGet());
			if (element != null && element.getObjectValue() != null && element.getObjectValue() !=null) {
				ret.add((T) element.getObjectValue());
			}else{
				//要减下去。。。。
				log.debug("get empty sending:"+counter.ptr_sending.get()+",p="+counter.ptr_pending.get());
				counter.ptr_sending.decrementAndGet();
			}
		}
		if (counter.ptr_pending.get() > counter.ptr_saved.get()) {
			counter.ptr_saved.set(counter.ptr_pending.get() + 1000);
		}
		storage.put(new Element(STR_COUNTER, counter));
		storage.flush();
		return ret;

	}

	public static void main(String[] args) {
		PendingQueue<HashPair> pq = new PendingQueue<>("test",1000);
		int counter = 10000;
//		for (int i = 0; i < counter; i++) {
//			pq.addElement(new HashPair("kk_" + i, MultiTransaction.newBuilder().setTxHash("kk_" + i).build()));
//		}
		List<HashPair> hp = pq.poll(100);
		System.out.println("hpsize=" + hp.size());
		pq.shutdown();
	}
}
