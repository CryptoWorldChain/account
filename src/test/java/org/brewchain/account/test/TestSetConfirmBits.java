package org.brewchain.account.test;

import java.math.BigInteger;

import org.brewchain.account.bean.HashPair;

public class TestSetConfirmBits {

	public static void main(String[] args) {
		HashPair hp = new HashPair("abc", new byte[] { (byte) 0 }, null);
		hp.setBits(new BigInteger("10"));
		System.out.println(hp.getBits().toString(2));
	}
}
