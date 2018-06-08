package org.brewchain.account.core.store;

import org.brewchain.account.core.store.BlockStore.IllegalOperationException;
import org.brewchain.account.gens.Block.BlockEntity;
import lombok.Data;

@Data
public class BlockStoreNodeValue {
	private String blockHash;
	private String parentHash;
	private int number;
	private boolean isStable = false;
	private boolean isConnect = false;
	private int retryTimes = 0;
	private BlockEntity blockEntity;

	public BlockStoreNodeValue(String blockHash, String parentHash, int number, BlockEntity blockEntity) {
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

	public void increaseRetryTimes() {
		retryTimes += 1;
	}

	public void setConnect() {
		this.isConnect = true;
	}

	public void setDisConnect() {
		this.isConnect = false;
	}
}
