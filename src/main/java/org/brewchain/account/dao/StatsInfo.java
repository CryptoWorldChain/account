package org.brewchain.account.dao;

import java.util.concurrent.atomic.AtomicLong;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class StatsInfo implements Runnable {
	// RespBlockInfo.Builder blockInfo = RespBlockInfo.newBuilder();
	AtomicLong txAcceptCount = new AtomicLong(0);
	long lastTxAcceptCount = 0;
	long lastUpdateTime = System.currentTimeMillis();
	AtomicLong txBlockCount = new AtomicLong(0);
	long lastTxBlockCount = 0;
	double txAcceptTps = 0.0;
	double txBlockTps = 0.0;
	double maxAcceptTps = 0.0;
	double maxBlockTps = 0.0;
	long lastTxTime = 0;
	long lastBlockID = 0;
	long curBlockID = 0;
	long lastBlockTime = 0;
	long curBlockTime = 0;
	boolean running = true;

	public void setCurBlockID(long blockid) {
		curBlockID = blockid;
		curBlockTime = System.currentTimeMillis();
	}

	@Override
	public void run() {

		while (running) {
			try {
				long curAcceptTxCount = txAcceptCount.get();
				long curBlockTxCount = txBlockCount.get();
				long now = System.currentTimeMillis();
				long timeDistance = now - lastUpdateTime;
				txAcceptTps = (curAcceptTxCount - lastTxAcceptCount) * 1000.f / (timeDistance + 1);
				lastTxAcceptCount = curAcceptTxCount;
				if (maxAcceptTps < txAcceptTps) {
					maxAcceptTps = txAcceptTps;
				}
				if (curBlockID > lastBlockID) {
					txBlockTps = (curBlockTxCount - lastTxBlockCount) * 1000.f
							/ (Math.abs(curBlockTime - lastBlockTime) + 1);
					txBlockTps = txBlockTps / (curBlockID - lastBlockID);
					
					lastBlockTime = curBlockTime;
					lastBlockID = curBlockID;
					lastTxBlockCount = curBlockTxCount;
				}

				if (maxBlockTps < txBlockTps) {
					maxBlockTps = txBlockTps;
				}
				lastUpdateTime = System.currentTimeMillis();
				log.info("[STATS] TxAccept[count,tps]=[{},{}] TxBlock[count,tps]=[{},{}]", curAcceptTxCount,
						txAcceptTps, curBlockTxCount, txBlockTps);
			} catch (Throwable t) {

			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
	}

}
