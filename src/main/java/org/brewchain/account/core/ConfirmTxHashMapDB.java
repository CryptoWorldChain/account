package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.ArrayList;
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
		confirmTx(hp, zeroBit);
	}

	public boolean containsKey(String txhash) {
		return storage.get(txhash) != null;
	}

	public synchronized void confirmTx(HashPair hp, BigInteger bits) {
		rwLock.readLock().lock();
		boolean put = false;
		try {
			HashPair _hp = storage.get(hp.getKey());
			if (_hp == null) {
				rwLock.readLock().unlock();
				put = true;
				rwLock.writeLock().lock();
				try {// second entry.
					HashPair _shp = storage.get(hp.getKey());
					if (_shp != null) {
						_hp = _shp;
					} else {
						storage.put(hp.getKey(), hp);
						confirmQueue.addLast(hp);
						_hp = hp;
					}
				} catch (Exception e) {

				} finally {
					rwLock.writeLock().unlock();
				}
			}
			_hp.setBits(bits);
		} catch (Exception e) {
		} finally {
			if (!put) {
				rwLock.readLock().unlock();
			}
		}
	}

	public synchronized void confirmTx(String key, BigInteger bits) {
		rwLock.readLock().lock();
		boolean put = false;
		try {
			HashPair _hp = storage.get(key);
			if (_hp == null) {
				rwLock.readLock().unlock();
				put = true;
				rwLock.writeLock().lock();
				try {// second entry.
					HashPair _shp = storage.get(key);
					if (_shp != null) {
						_hp = _shp;
					}
				} catch (Exception e) {

				} finally {
					rwLock.writeLock().unlock();
				}
			}
			if (_hp != null) {
				_hp.setBits(bits);
			}
		} catch (Exception e) {
		} finally {
			if (!put) {
				rwLock.readLock().unlock();
			}
		}
	}

	public List<MultiTransaction> poll(int maxsize) {
		return poll(maxsize, 0);
	}

	public HashPair invalidate(String key) {
		rwLock.writeLock().lock();
		try {// second entry.
			HashPair hp = storage.remove(key);
			if (hp != null && !hp.isRemoved()) {
				hp.setRemoved(true);
				confirmQueue.remove(hp);
			}

			return hp;
		} catch (Exception e) {
			return null;
		} finally {
			rwLock.writeLock().unlock();
		}
	}

	public List<MultiTransaction> poll(int maxsize, int minConfirm) {
		int i = 0;
		int maxtried = Math.min(maxsize, confirmQueue.size());
		List<MultiTransaction> ret = new ArrayList<>();
		long checkTime = System.currentTimeMillis();
		while (i < maxtried) {
			HashPair hp = confirmQueue.pollFirst();
			if (hp == null) {
				break;
			} else {
				rwLock.writeLock().lock();
				try {
					if (!hp.isRemoved()) {
						if (hp.getBits().bitCount() >= minConfirm) {
							ret.add(hp.getTx());
							hp.setRemoved(true);
						} else {
							//long time no seeee
							if (checkTime - hp.getLastUpdateTime() > 60000) 							
							{
								oSendingHashMapDB.put(hp.getKey(), hp);
							}
							confirmQueue.addLast(hp);
						}
					}
				} finally {
					rwLock.writeLock().unlock();
				}
			}
		}

		return ret;
	}

	public int size() {
		return storage.size();
	}

}
