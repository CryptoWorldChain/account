package org.brewchain.account.sample;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections.FastArrayList;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.util.ALock;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "TransactionLoadTest_Store")
@Slf4j
@Data
public class TransactionLoadTestStore implements ActorService {
	private List<MultiTransaction.Builder> loads = new FastArrayList();
	private AtomicInteger used_idx = new AtomicInteger(-1);
	private int loopCount = 0;
	protected ReadWriteLock rwLock = new ReentrantReadWriteLock();
	protected ALock readLock = new ALock(rwLock.readLock());

	public MultiTransaction.Builder getOne() {
		try {
			return loads.get(used_idx.incrementAndGet());
		} catch (Exception e) {
			return null;
		}
	}

	public void clear() {
		loads.clear();
		used_idx.set(-1);
	}

	public int remain() {
		return loads.size() - (used_idx.get() + 1);
	}
}
