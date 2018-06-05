package org.brewchain.account.core.store;

import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;

import org.brewchain.account.util.ALock;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;

@Data
public class BlockChainTempNode {
	private String hash;
	private int number;
	private String parentHash;
	private boolean isChild = true;
	private boolean isStable = false;

	public BlockChainTempNode(String hash,String parentHash, int number, boolean isChild) {
		this.hash = hash;
		this.parentHash = parentHash;
		this.number = number;
		this.isChild = isChild;
	}

	public void setChild(boolean isChild) {
		this.isChild = isChild;
	}
	
	public void setStable(boolean isStable) {
		this.isStable = isStable;
	}
}
