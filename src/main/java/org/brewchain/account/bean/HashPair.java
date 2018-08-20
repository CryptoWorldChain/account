package org.brewchain.account.bean;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import lombok.Data;

@Data
public class HashPair {
	String key;
	byte data[];
	transient MultiTransaction tx;
	
	public byte[] getKeyBytes(){
		try {
			return Hex.decodeHex(key.toCharArray());
		} catch (DecoderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public HashPair(String key, MultiTransaction tx) {
		super();
		this.key = key;
		this.tx = tx;
		this.data = tx.toByteArray();
	}

	public HashPair(String key, byte[] data, MultiTransaction tx) {
		super();
		this.key = key;
		this.tx = tx;
		this.data = data;
	}

}
