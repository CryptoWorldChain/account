package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.bean.HashPair;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "ConfirmTxHashDB")
@Slf4j
@Data
public class ConfirmTxHashMapDB implements ActorService {
	protected ConcurrentHashMap<String, HashPair> storage;
	protected ConcurrentHashMap<String, Long> removeSavestorage = new ConcurrentHashMap<>();;
	protected LinkedBlockingDeque<HashPair> confirmQueue = new LinkedBlockingDeque<>();
	ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
	@ActorRequire(name = "WaitSend_HashMapDB", scope = "global")
	WaitSendHashMapDB oSendingHashMapDB; // 保存待广播交易

	public ConfirmTxHashMapDB() {
		this(new ConcurrentHashMap<String, HashPair>());
	}

	public ConfirmTxHashMapDB(ConcurrentHashMap<String, HashPair> storage) {
		this.storage = storage;
	}

	BigInteger zeroBit = new BigInteger("0");

	// public void confirmTx(HashPair hp) {
	// confirmTx(hp, zeroBit);
	// }

	public boolean containsKey(String txhash) {
		HashPair _hp = storage.get(txhash);
		return _hp != null && _hp.getTx() != null;
	}

	public void confirmTx(HashPair hp, BigInteger bits) {
		try {
			// rwLock.writeLock().lock();
			HashPair _hp = storage.get(hp.getKey());
			if (_hp == null) {
				synchronized (hp.getKey().substring(0, 3).intern()) {
					_hp = storage.get(hp.getKey());// double entry
					if (_hp == null) {
						storage.put(hp.getKey(), hp);
						_hp = hp;
					}
				}
			}
			if (_hp.getTx() == null && hp.getTx() != null) {
				_hp.setData(hp.getData());
				_hp.setTx(hp.getTx());
				_hp.setNeedBroadCast(hp.isNeedBroadCast());
				confirmQueue.addLast(_hp);
			}
			_hp.setBits(bits);

		} catch (Exception e) {
			log.error("confirmTx::" + e);
		} finally {
		}
	}

	public synchronized void confirmTx(String key, BigInteger bits) {
		try {
			// rwLock.writeLock().lock();
			if (removeSavestorage.containsKey(key)) {
				return;
			}
			HashPair _hp = storage.get(key);
			if (_hp == null) {
				synchronized (key.substring(0, 3).intern()) {
					_hp = storage.get(key);// double entry
					if (_hp == null) {
						_hp = new HashPair(key, null, null);
						storage.put(key, _hp);
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

	public synchronized HashPair invalidate(String key) {
		// rwLock.writeLock().lock();
		try {// second entry.
			HashPair hp = storage.get(key);
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

	public synchronized HashPair revalidate(String key) {
		// rwLock.writeLock().lock();
		try {// second entry.
			HashPair hp = storage.get(key);
			if (hp != null && hp.isRemoved()) {
				hp.setRemoved(false);
			}
			removeSavestorage.remove(key);
			return hp;
		} catch (Exception e) {
			return null;
		} finally {
			// rwLock.writeLock().unlock();
		}
	}

	public List<MultiTransaction> poll(int maxsize, int minConfirm) {
		int i = 0;
		int maxtried = Math.min(maxsize, confirmQueue.size());
		List<MultiTransaction> ret = new ArrayList<>();
		long checkTime = System.currentTimeMillis();

		// log.error("confirmQueue info poll:: maxsize::" + maxsize +
		// ",maxtried=" + maxtried + " size::"
		// + confirmQueue.size()+",storage="+storage.size());
		while (i < maxtried) {
			HashPair hp = confirmQueue.pollFirst();
			if (hp == null) {
				log.error("confirmQueue info empty;");
				break;
			} else {
				// rwLock.writeLock().lock();
				try {
					if (!hp.isRemoved()) {
						if (hp.getBits().bitCount() >= minConfirm) {
							ret.add(hp.getTx());
							removeSavestorage.put(hp.getKey(), System.currentTimeMillis());

							hp.setRemoved(true);
							i++;
						} else {
							// long time no seeee
							if (checkTime - hp.getLastUpdateTime() >= 60000) {
								if (hp.getTx() != null && hp.getData() != null && hp.isNeedBroadCast()) {
									// log.error("confirmQueue info
									// broadcast;");
									oSendingHashMapDB.put(hp.getKey(), hp);
									confirmQueue.addLast(hp);
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
				+ confirmQueue.size() + ",storage=" + storage.size() + ",try=" + i);

		// log.debug("confirm tx poll maxsize::" + maxsize + " minConfirm::" +
		// minConfirm + " checkTime::" + checkTime
		// + " ret::" + ret.size());
		return ret;
	}

	public synchronized void clear() {
		try {
			long start = System.currentTimeMillis();
			int cc = clearQueue();
			log.error("end of clearQueue::cost=" + (System.currentTimeMillis() - start) + ",clearcount=" + cc);
		} catch (Exception e1) {
			log.error("error in clearQueue:", e1);
		}
		try {
			long start = System.currentTimeMillis();
			int cc = clearStorage();
			log.error("end of clearStorage::cost=" + (System.currentTimeMillis() - start) + ",clearcount=" + cc);
		} catch (Exception e) {
			log.error("error in clearStorage:", e);
		}
		
		try {
			long start = System.currentTimeMillis();
			int cc = clearRemoveQueue();
			log.error("end of clearRemoveQueue::cost=" + (System.currentTimeMillis() - start) + ",clearcount=" + cc);
		} catch (Exception e) {
			log.error("error in clearRemoveQueue:", e);
		}
	}

	public int clearQueue() {
		int i = 0;
		int maxtried = confirmQueue.size();
		int clearcount = 0;
		while (i < maxtried) {
			try {
				HashPair hp = confirmQueue.pollFirst();
				if (hp == null) {
					break;
				}
				if (!hp.isRemoved()) {// 180
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
					if (System.currentTimeMillis() - rmTime > 60 * 1000) {
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
		Enumeration<String> en = storage.keys();
		List<String> removeKeys = new ArrayList<>();
		while (en.hasMoreElements()) {
			try {
				String key = en.nextElement();
				HashPair hp = storage.get(key);
				if (hp != null) {
					if (hp.isRemoved()) {
						removeKeys.add(key);
					} else if (hp.getTx() == null && hp.getData() == null
							&& System.currentTimeMillis() - hp.getLastUpdateTime() >= 180 * 1000) {
						// time out confirm;
						removeKeys.add(key);
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
	}

	public int size() {
		return storage.size();
	}

}
