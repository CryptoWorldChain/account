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
	private boolean isChild = true;
	
	public BlockChainTempNode(String hash,int number,boolean isChild) {
		this.hash = hash;
		this.number = number;
		this.isChild = isChild;
	}
	
	public void setChild(boolean isChild) {
		this.isChild = isChild;
	}
}
