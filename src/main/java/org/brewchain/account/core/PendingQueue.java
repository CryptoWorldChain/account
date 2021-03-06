package org.brewchain.account.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import onight.tfw.outils.conf.PropHelper;

@Data
@Slf4j
public class PendingQueue<T> {
	protected Cache storage;
	public final static CacheManager cacheManager = new CacheManager("./conf/ehcache.xml");

	public final static String STR_COUNTER = "__idcounter";
	CounterInfoData counter = new CounterInfoData();

	public void shutdown() {
		storage.flush();
		cacheManager.shutdown();
	}

	public static String getDir() {
		String network = ".";
		try {
			File networkFile = new File(".chainnet");
			if (!networkFile.exists() || !networkFile.canRead()) {
				// read default config
				network = new PropHelper(null).get("org.bc.manage.node.net", null);
			}
			if (network == null || network.isEmpty()) {
				while (!networkFile.exists() || !networkFile.canRead()) {
					log.debug("waiting chain_net config...");
					Thread.sleep(1000);
				}

				FileReader fr = new FileReader(networkFile.getPath());
				BufferedReader br = new BufferedReader(fr);
				network = br.readLine().trim().replace("\r", "").replace("\t", "");
				br.close();
				fr.close();
			}

			// log.debug("choose the chain_net::" + network);
		} catch (Exception e) {
			log.error("error on read chain_net::" + e.getMessage());
		}
		return network;
	}

	public PendingQueue(String nameid, int maxElementsInMemory) {
		this.storage = new Cache("pendingqueue_" + nameid, maxElementsInMemory, MemoryStoreEvictionPolicy.LRU, true,
				"./pendingcache_" + nameid, true, 0, 0, true, 120, null);
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

	public void addLast(T hp) {
		addElement(hp);
	}

	public int size() {
		return (int) (counter.ptr_pending.get() - counter.ptr_sending.get());
	}

	public T pollFirst() {
		List<T> ret = poll(1);

		if (ret != null & ret.size() > 0) {
			return ret.get(0);
		}

		return null;
	}

	public synchronized List<T> poll(int size) {
		List<T> ret = new ArrayList<>();
		for (int i = 0; i < size && counter.ptr_sending.get() < counter.ptr_pending.get(); i++) {
			Element element = storage.get(counter.ptr_sending.incrementAndGet());
			if (element != null && element.getObjectValue() != null && element.getObjectValue() != null) {
				ret.add((T) element.getObjectValue());
			} else {
				// 要减下去。。。。
				log.debug("get empty sending:" + counter.ptr_sending.get() + ",p=" + counter.ptr_pending.get());
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
		PendingQueue<String> pq = new PendingQueue<>("test", 1000);
		int counter = 10000;
		for (int i = 0; i < counter; i++) {
			pq.addElement("KKK" + i);
		}
		List<String> hp = pq.poll(100);
		System.out.println("hpsize=" + hp.size());
		pq.shutdown();
	}
}
