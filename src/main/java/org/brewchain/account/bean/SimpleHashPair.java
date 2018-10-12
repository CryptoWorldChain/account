package org.brewchain.account.bean;

import java.io.Serializable;
import java.math.BigInteger;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import lombok.Data;

@Data
public class SimpleHashPair implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 
	 */
	String key;
	BigInteger bits = new BigInteger("0");
	boolean isRemoved = false;
	boolean isNeedBroadCast = false;
	long lastUpdateTime = System.currentTimeMillis();

	public synchronized void setBits(BigInteger bits) {
		this.bits = this.bits.or(bits);
//		lastUpdateTime = System.currentTimeMillis();
	}


	public SimpleHashPair(String key) {
		super();
		this.key = key;
		this.bits = BigInteger.ZERO;
	}

}
