package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.bean.HashPair;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.outils.conf.PropHelper;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "ConfirmTxHashDB")
@Slf4j
@Data
public class ConfirmTxHashMapDB implements ActorService {
	protected ConcurrentHashMap<String, HashPair> storage;
	// protected Cache storage;
	protected ConcurrentHashMap<String, Long> removeSavestorage = new ConcurrentHashMap<>();
	protected LinkedBlockingDeque<HashPair> confirmQueue = new LinkedBlockingDeque<>();
	ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
	@ActorRequire(name = "WaitSend_HashMapDB", scope = "global")
	WaitSendHashMapDB oSendingHashMapDB; // 保存待广播交易
	// PendingQueue persistQ;

	// final CacheManager cacheManager = new CacheManager();

	public ConfirmTxHashMapDB() {
		this(new ConcurrentHashMap<String, HashPair>());
	}

	public ConfirmTxHashMapDB(ConcurrentHashMap<String, HashPair> storage) {
		// this.storage = storage;
		this(storage, new PropHelper(null).get("org.brewchain.account.confirm.memsize", 1000000));

	}

	public ConfirmTxHashMapDB(ConcurrentHashMap<String, HashPair> storage, int maxElementsInMemory) {
		// this.storage = storage;
		this.maxElementsInMemory = maxElementsInMemory;
		this.storage = storage;
		// persistQ = new PendingQueue("confirmtx", maxElementsInMemory);
		// this.storage = new Cache("storageCache", maxElementsInMemory,
		// MemoryStoreEvictionPolicy.LRU, true,
		// "./storagecache", true, 0, 0, true, 120, null);
		// cacheManager.addCache(this.storage);

	}

	@Invalidate
	public void destory() {
		// this.storage.flush();
		// cacheManager.shutdown();
		// persistQ.shutdown();
	}

	int maxElementsInMemory = 100 * 10000;

	BigInteger zeroBit = new BigInteger("0");

	public HashPair eleToHP(HashPair ele) {
		return ele;
	}

	// public HashPair eleToHP(Element ele) {
	// if (ele != null) {
	// return (HashPair) ele.getObjectValue();
	// } else {
	// return null;
	// }
	// }

	public boolean containsKey(String txhash) {
		// Element ele = storage.get(txhash);
		HashPair _hp = getHP(txhash);
		return _hp != null && _hp.getTx() != null;
	}

	private void putElement(String key, HashPair hp) {
		// storage.put(new Element(key, hp));
		if (storage.size() < this.maxElementsInMemory || hp.getTx() != null) {
			storage.put(key, hp);
		} else {
			// persistQ.addElement(hp);
			// log.error("drop storage queue:size=" + storage.size());
			// storage.put(key, hp);
			// hp.setStoredInDisk(true);
			// putElement(hp.getKey(), hp);
		}
	}

	public HashPair getHP(String txhash) {
		if (storage == null) {
			return null;
		}
		return storage.get(txhash);
		// return eleToHP(storage.get(txhash));
	}

	public void confirmTx(HashPair hp, BigInteger bits) {
		try {
			// rwLock.writeLock().lock();
			boolean put2Queue = false;
			HashPair _hp = getHP(hp.getKey());
			if (_hp == null) {
				synchronized (("acct_" + hp.getKey().substring(0, 1)).intern()) {
					_hp = eleToHP(storage.get(hp.getKey()));// double entry
					if (_hp == null) {
						putElement(hp.getKey(), hp);
						if (hp.getTx() != null) {
							put2Queue = true;
							confirmQueue.addLast(hp);
						}
						_hp = hp;
					}
				}
			}
			if (!put2Queue && _hp.getTx() == null && hp.getTx() != null) {
				_hp.setData(hp.getData());
				_hp.setTx(hp.getTx());
				_hp.setNeedBroadCast(hp.isNeedBroadCast());
				confirmQueue.addLast(_hp);
			}
			_hp.setBits(bits);

		} catch (Exception e) {
			log.error("confirmTx::", e);
		} finally {
		}
	}

	public void confirmTx(String key, BigInteger bits) {
		try {
			// rwLock.writeLock().lock();
			if (removeSavestorage.containsKey(key)) {
				return;
			}
			HashPair _hp = getHP(key);
			if (_hp == null) {
				synchronized (("acct_" + key.substring(0, 1)).intern()) {
					_hp = getHP(key);// double entry
					if (_hp == null) {
						_hp = new HashPair(key, null, null);
						putElement(key, _hp);
					}
				}
			}
			_hp.setBits(bits);
			// log.error("confirmQueue info confirm key::" + key + " c::" +
			// _hp.getBits().bitCount());

		} catch (Exception e) {
			log.error("confirmTx::" + e);
		} finally {
			// rwLock.writeLock().unlock();
		}
	}

	public List<MultiTransaction> poll(int maxsize) {
		return poll(maxsize, 0);
	}

	public HashPair invalidate(String key) {
		// rwLock.writeLock().lock();
		try {// second entry.
			HashPair hp = getHP(key);
			if (hp != null) {
				hp.setRemoved(true);
			}
			removeSavestorage.put(key, System.currentTimeMillis());

			return hp;
		} catch (Exception e) {
			return null;
		} finally {
			// rwLock.writeLock().unlock();
		}
	}

	public HashPair revalidate(String key) {
		// rwLock.writeLock().lock();
		try {// second entry.
			HashPair hp = eleToHP(storage.get(key));
			if (hp != null && hp.isRemoved()) {
				hp.setRemoved(false);
				removeSavestorage.remove(key);
			}
			return hp;
		} catch (Exception e) {
			return null;
		} finally {
			// rwLock.writeLock().unlock();
		}

	}

	public List<MultiTransaction> poll(int maxsize, int minConfirm) {
		int i = 0;
		int maxtried = confirmQueue.size();
		List<MultiTransaction> ret = new ArrayList<>();
		long checkTime = System.currentTimeMillis();

		// log.error("confirmQueue info poll:: maxsize::" + maxsize +
		// ",maxtried=" + maxtried + " size::"
		// + confirmQueue.size()+",storage="+storage.size());
		while (i < maxtried && ret.size() < maxsize) {
			HashPair hp = confirmQueue.pollFirst();
			if (hp == null) {
				log.error("confirmQueue info empty;");
				break;
			} else {
				// rwLock.writeLock().lock();
				try {
					if (!hp.isRemoved() && !removeSavestorage.containsKey(hp.getKey()) && hp.getTx() != null) {
						if (hp.getBits().bitCount() >= minConfirm) {
							ret.add(hp.getTx());
							removeSavestorage.put(hp.getKey(), System.currentTimeMillis());
							hp.setRemoved(true);
							i++;
						} else {
							// long time no seeee
							if (checkTime - hp.getLastUpdateTime() >= 60000) {
								if (hp.getTx() != null && hp.getData() != null && hp.isNeedBroadCast()) {
									log.info("confirmQueue info broadcast:" + hp.getKey());
									// oSendingHashMapDB.put(hp.getKey(), hp);
									// confirmQueue.add1Last(hp);
								} else {
									// log.error("confirmQueue info rm tx from
									// queue::" + hp.getKey());
									hp.setRemoved(true);
									removeSavestorage.put(hp.getKey(), System.currentTimeMillis());
								}
							} else {
								confirmQueue.addLast(hp);
							}
							i++;
						}
					}
				} catch (Exception e) {
					log.error("cannot poll the tx::", e);
				} finally {

				}
			}
		}

		log.error("confirmQueue info poll:: maxsize::" + maxsize + ",maxtried=" + maxtried + " queuesize::"
				+ confirmQueue.size() + ",storage=" + (storage == null ? 0 : storage.size()) + ",try=" + i);

		// log.debug("confirm tx poll maxsize::" + maxsize + " minConfirm::" +
		// minConfirm + " checkTime::" + checkTime
		// + " ret::" + ret.size());
		return ret;
	}

	public synchronized void clear() {

		int ccs[] = new int[3];
		long cost[] = new long[3];
		long tstart = System.currentTimeMillis();
		try {
			long start = System.currentTimeMillis();
			ccs[0] = clearQueue();
			cost[0] = (System.currentTimeMillis() - start);
			// log.error("end of clearQueue::cost=" +
			// (System.currentTimeMillis() - start) + ",clearcount=" + cc);
		} catch (Exception e1) {
			log.error("error in clearQueue:", e1);
		}
		try {
			long start = System.currentTimeMillis();
			ccs[1] = clearStorage();
			cost[1] = (System.currentTimeMillis() - start);

			// log.error("end of clearStorage::cost=" +
			// (System.currentTimeMillis() - start) + ",clearcount=" + cc);
		} catch (Exception e) {
			log.error("error in clearStorage:", e);
		}

		try {
			long start = System.currentTimeMillis();
			ccs[2] = clearRemoveQueue();
			cost[2] = (System.currentTimeMillis() - start);

			// log.error("end of clearRemoveQueue::cost=" +
			// (System.currentTimeMillis() - start) + ",clearcount=" + cc);
		} catch (Exception e) {
			log.error("error in clearRemoveQueue:", e);
		}
		log.error("end of clear:cost=" + (System.currentTimeMillis() - tstart) + ":[" + cost[0] + "," + cost[1] + ","
				+ cost[2] + "],count=[" + ccs[0] + "," + ccs[1] + "," + ccs[2] + "]");
	}

	public int clearQueue() {
		int i = 0;
		int maxtried = confirmQueue.size();
		int clearcount = 0;
		while (i < maxtried) {
			try {
				HashPair hp = confirmQueue.pollFirst();
				// if (hp == null) {
				// break;
				// }
				if (!hp.isRemoved() && hp.getTx() != null && !removeSavestorage.containsKey(hp.getKey())) {// 180
					confirmQueue.addLast(hp);
				} else {
					clearcount++;
					storage.remove(hp.getKey());
				}
			} catch (Exception e) {
				log.error("cannot poll the tx::", e);
			} finally {
				i++;
			}
		}
		return clearcount;
	}

	public int clearRemoveQueue() {
		Enumeration<String> en = removeSavestorage.keys();
		List<String> removeKeys = new ArrayList<>();
		while (en.hasMoreElements()) {
			try {
				String key = en.nextElement();
				Long rmTime = removeSavestorage.get(key);
				if (rmTime != null) {
					if (System.currentTimeMillis() - rmTime > 120 * 1000) {
						removeKeys.add(key);
					}
				}
			} catch (Exception e) {
				log.error("cannot remove the tx::", e);
			} finally {
			}
		}
		for (String key : removeKeys) {
			removeSavestorage.remove(key);
		}

		return removeKeys.size();
	}

	public int clearStorage() {
		// List<String> keys = storage.getKeys();
		if (storage != null) {
			List<String> removeKeys = new ArrayList<>();
			Enumeration<String> en = storage.keys();
			while (en.hasMoreElements()) {
				// for (String key : keys) {
				try {
					String key = en.nextElement();
					HashPair hp = getHP(key);
					if (hp != null) {
						if (hp.isRemoved()) {
							removeKeys.add(key);
						} else if (hp.getTx() == null || hp.getData() == null
								|| System.currentTimeMillis() - hp.getLastUpdateTime() >= 180 * 1000) {
							// time out confirm;
							removeKeys.add(key);
						} else if (hp.getTx() != null
								&& System.currentTimeMillis() - hp.getLastUpdateTime() >= 60 * 1000) {
//							oSendingHashMapDB.put(hp.getKey(), hp);
//							confirmQueue.add1Last(hp);
						}
					}
				} catch (Exception e) {
					log.error("cannot remove the tx::", e);
				} finally {
				}
			}
			for (String key : removeKeys) {
				storage.remove(key);
			}

			return removeKeys.size();
		} else {
			return 0;
		}

	}

	public int getStorageSize() {
		// return storage.getSize();
		if (storage == null) {
			return 0;
		}
		return storage.size();
	}

	public int size() {
		return getStorageSize();
	}

	public static void main(String[] args) {
		// test.
		ConfirmTxHashMapDB confirmDB = new ConfirmTxHashMapDB(null, 10000);
		// for (int i = 0; i < 100; i++) {
		// String key = "key_" + i;
		// HashPair hp = new HashPair(key,
		// MultiTransaction.newBuilder().setTxHash(key).setStatus("Done").build());
		// hp.setData(new byte[] { 0x00 });
		// confirmDB.confirmTx(hp, BigInteger.ZERO.setBit(1));
		// }
		System.out.println("size==" + confirmDB.size());
		for (int i = 0; i < 100; i++) {
			String key = "key_" + i;
			HashPair hp = confirmDB.getHP(key);
			if (hp == null || !hp.getKey().equals(key)) {
				System.out.println("error:hp=" + hp);
			}
		}
		// confirmDB.cacheManager.shutdown();
	}
}
