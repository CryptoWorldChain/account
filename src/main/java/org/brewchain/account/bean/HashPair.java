package org.brewchain.account.bean;

import org.apache.commons.codec.binary.Hex;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import lombok.Data;

@Data
public class HashPair {
	byte key[];
	byte data[];

	transient String hexKey;

	transient MultiTransaction tx;

	public String getHexKey() {
		if (hexKey == null && key != null) {
			hexKey = Hex.encodeHexString(key);
		}
		return hexKey;
	}

	public void reset() {
		hexKey = null;
	}

	public HashPair(byte[] key, MultiTransaction tx) {
		super();
		this.key = key;
		this.tx = tx;
		this.data = tx.toByteArray();
	}
	
	public HashPair(byte[] key, byte [] data,MultiTransaction tx) {
		super();
		this.key = key;
		this.tx = tx;
		this.data = data;
	}

}
