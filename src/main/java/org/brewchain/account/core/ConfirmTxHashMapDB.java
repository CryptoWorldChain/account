package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
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

	public void confirmTx(HashPair hp) {
		confirmTx(hp, zeroBit, true);
	}

	public boolean containsKey(String txhash) {
		HashPair _hp = storage.get(txhash);
		return _hp != null && _hp.getTx() != null;
	}

	public void confirmTx(HashPair hp, BigInteger bits, boolean isNew) {
		try {
			// rwLock.writeLock().lock();
			HashPair _hp = storage.get(hp.getKey());
			if (_hp == null) {
				synchronized (hp.getKey().substring(0, 3).intern()) {
					_hp = storage.get(hp.getKey());// double entry
					if (_hp == null) {
						if (isNew) {
							storage.put(hp.getKey(), hp);
							confirmQueue.addLast(hp);
						}
							
						_hp = hp;
					}
				}
				// storage.put(hp.getKey(), hp);
				// confirmQueue.addLast(hp);
				// _hp = hp;
			} else {
				if (_hp.getTx() == null && hp.getTx() != null) {
					_hp.setData(hp.getData());
					_hp.setTx(hp.getTx());
//					confirmQueue.addLast(_hp);
				}
			}
			_hp.setBits(bits);
		} catch (Exception e) {
			log.error("confirmTx::" + e);
		} finally {
			// rwLock.writeLock().unlock();
		}
	}

	public void confirmTx(String key, BigInteger bits, boolean isNew) {
		try {
			// rwLock.writeLock().lock();
			HashPair _hp = storage.get(key);
			if (_hp == null) {
				synchronized (key.substring(0, 3).intern()) {
					_hp = storage.get(key);// double entry
					if (_hp == null) {
						_hp = new HashPair(key, null, null);
						if (isNew)
							storage.put(key, _hp);
						
						//confirmQueue.addLast(_hp);
					}
				}
			}
			// HashPair _hp = storage.putIfAbsent(key, new HashPair(key, null,
			// null));
			_hp.setBits(bits);
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
			HashPair hp = storage.remove(key);
			if (hp != null && !hp.isRemoved()) {
				hp.setRemoved(true);
				// confirmQueue.remove(hp);//不主动的remove，等pop的时候再检查
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
		int maxtried = Math.min(maxsize, confirmQueue.size());
		List<MultiTransaction> ret = new ArrayList<>();
		long checkTime = System.currentTimeMillis();

		log.debug("confirm tx poll maxsize::" + maxsize + " minConfirm::" + minConfirm + " checkTime::" + checkTime);
		while (i < maxtried) {
			HashPair hp = confirmQueue.pollFirst();
			if (hp == null) {
				break;
			} else {
				// rwLock.writeLock().lock();
				try {
					if (!hp.isRemoved()) {
						if (hp.getBits().bitCount() >= minConfirm) {
							ret.add(hp.getTx());
							hp.setRemoved(true);
							i++;
						} else {
							// long time no seeee
							if (hp.isNeedBroadCast() && (checkTime - hp.getLastUpdateTime() > 60000)) {
								oSendingHashMapDB.put(hp.getKey(), hp);
							}
							confirmQueue.addLast(hp);
							i++;
						}
					}
				} catch (Exception e) {
					log.error("cannot poll the tx::", e);
				} finally {

				}
			}
		}

		return ret;
	}

	public int size() {
		return storage.size();
	}

}
