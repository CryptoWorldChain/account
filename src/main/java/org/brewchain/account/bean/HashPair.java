package org.brewchain.account.bean;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;

@Data
public class HashPair implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5829951203336980748L;
	String key;
	byte data[];
	transient MultiTransaction tx;
	// MultiTransaction tx;
	BigInteger bits = new BigInteger("0");
	boolean isRemoved = false;
	boolean isNeedBroadCast = false;
	long lastUpdateTime = System.currentTimeMillis();
	boolean isStoredInDisk = false;

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	public MultiTransaction getTx() {
		if (tx == null && data != null) {
			try {
				tx = MultiTransaction.parseFrom(data);
			} catch (InvalidProtocolBufferException e) {
			}
		}
		return tx;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (obj instanceof HashPair) {
			HashPair hp = (HashPair) obj;

			return StringUtils.equals(hp.getKey(), key) && Arrays.equals(hp.getData(), data);
		} else {
			return false;
		}
	}

	public synchronized void setBits(BigInteger bits) {
		this.bits = this.bits.or(bits);
		// lastUpdateTime = System.currentTimeMillis();
	}

	public byte[] getKeyBytes() {
		try {
			return Hex.decodeHex(key.toCharArray());
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		return null;
	}

	public HashPair(String key, MultiTransaction tx) {
		super();
		this.key = key;
		this.tx = tx;
		this.data = tx.toByteArray();
		this.bits = BigInteger.ZERO;
	}

	public HashPair(String key, byte[] data, MultiTransaction tx) {
		super();
		this.key = key;
		this.tx = tx;
		this.data = data;
		this.bits = BigInteger.ZERO;
	}

	public static void main(String[] args) {
		BigInteger bits = new BigInteger("0");
		bits = bits.setBit(1).setBit(2);

		BigInteger bits1 = new BigInteger("0");
		bits1 = bits1.setBit(3).setBit(4);
		System.out.println(bits.bitCount() + "==>" + bits1.bitCount());
	}

}
