package org.brewchain.account.dao;

import java.util.concurrent.atomic.AtomicLong;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class StatsInfo implements Runnable {
	// RespBlockInfo.Builder blockInfo = RespBlockInfo.newBuilder();
	AtomicLong acceptTxCount = new AtomicLong(0);
	long lastAcceptTxCount = 0;
	long firstAcceptTxTime = 0;
	long lastAcceptTxTime = 0;

	long lastUpdateTime = System.currentTimeMillis();
	private AtomicLong blockTxCount = new AtomicLong(0);
	

	double txAcceptTps = 0.0;
	double txBlockTps = 0.0;
	double maxAcceptTps = 0.0;
	double maxBlockTps = 0.0;
	long lastTxTime = 0;
	long lastBlockID = 0;
	long curBlockID = 0;
	long lastBlockTime = 0;
	long curBlockTime = 0;
	long firstBlockTxTime = 0;
	long lastBlockTxTime = 0;
	long lastBlockTxCount = 0;
	AtomicLong rollBackTxCount = new AtomicLong(0);
	AtomicLong rollBackBlockCount = new AtomicLong(0);
	AtomicLong txSyncCount = new AtomicLong(0);
	boolean running = true;

	public void setCurBlockID(long blockid) {
		curBlockID = blockid;
		curBlockTime = System.currentTimeMillis();
	}
	
	public void signalBlockTx(){
		blockTxCount.incrementAndGet();
		lastBlockTxTime = System.currentTimeMillis();
		
		if(firstBlockTxTime==0){
			firstBlockTxTime = lastBlockTxTime;
		}
	}
	
	public void signalSyncTx() {
		txSyncCount.incrementAndGet();
	}
	
	public void signalAcceptTx(){
		acceptTxCount.incrementAndGet();
		lastAcceptTxTime = System.currentTimeMillis();

		if(firstAcceptTxTime==0){
			firstAcceptTxTime = lastAcceptTxTime;
		}
	}
	@Override
	public void run() {

		while (running) {
			try {
				long curAcceptTxCount = acceptTxCount.get();
				long curBlockTxCount = blockTxCount.get();
				long now = System.currentTimeMillis();
				long timeDistance = now - lastUpdateTime;
				txAcceptTps = (curAcceptTxCount - lastAcceptTxCount) * 1000.f / (timeDistance + 1);
				lastAcceptTxCount = curAcceptTxCount;
				if (maxAcceptTps < txAcceptTps) {
					maxAcceptTps = txAcceptTps;
				}
				if (lastBlockTxCount == 0 && curBlockTxCount > 0) {
					firstBlockTxTime = System.currentTimeMillis();
				}
				if (curBlockID > lastBlockID) {
					long __curBlockID = curBlockID;
					long __lastBlockTxCount =   lastBlockTxCount;
					
					txBlockTps = (curBlockTxCount - __lastBlockTxCount) * 1000.f
							/ (Math.abs(curBlockTime - lastBlockTime) + 1);
					txBlockTps = txBlockTps / (__curBlockID - lastBlockID);

					lastBlockTime = curBlockTime;
					lastBlockID = curBlockID;
					lastBlockTxCount = curBlockTxCount;
				}

				if (maxBlockTps < txBlockTps) {
					maxBlockTps = txBlockTps;
				}
				lastUpdateTime = System.currentTimeMillis();
			//	log.info("[STATS] TxAccept[count,tps]=[{},{}] TxBlock[count,tps]=[{},{}]", curAcceptTxCount,
			//			txAcceptTps, curBlockTxCount, txBlockTps);
			} catch (Throwable t) {

			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
	}

}
