package org.brewchain.account.core.store;

import org.brewchain.evmapi.gens.Block.BlockEntity;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class BlockStoreNodeValue {
	private String blockHash;
	private String parentHash;
	private long number;
	private boolean isStable = false;
	private boolean isConnect = false;
	private int retryTimes = 0;
	private BlockEntity blockEntity;

	public BlockStoreNodeValue(String blockHash, String parentHash, long number, BlockEntity blockEntity) {
		this.blockHash = blockHash;
		this.parentHash = parentHash;
		this.number = number;
		this.blockEntity = blockEntity;
	}

	public void setStable() throws Exception {
		if (this.isConnect) {
			this.isStable = true;
		} else {
			throw new BlockStore.IllegalOperationException("can not set unconnected node to stable.");
		}
	}

	public int increaseRetryTimes() {
		retryTimes += 1;
		return retryTimes;
	}

	public void connect() {
//		log.debug("connect hash::" + this.blockHash + " number::" + this.number);
		this.isConnect = true;
	}

	public void disConnect() {
//		log.debug("disconnect hash::" + this.blockHash + " number::" + this.number);
		this.isConnect = false;
	}
}
