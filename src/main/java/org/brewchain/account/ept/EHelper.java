package org.brewchain.account.ept;

import java.math.BigInteger;
import java.util.HashMap;

import org.brewchain.account.dao.DefDaos;
import org.brewchain.ecrypto.impl.EncInstance;
import org.fc.brewchain.bcapi.EncAPI;

public class EHelper {
	public final static String AlphbetMap = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	public static EncAPI encAPI = null;
	public static DefDaos dao = null;

	public final static int radix = AlphbetMap.length();
	public final static BigInteger modx = new BigInteger("" + radix);
	public final static HashMap<Character, Integer> charAtIdx = new HashMap<>();

	public static void init() {
		if (encAPI == null) {
			encAPI = new EncInstance();
			((EncInstance) encAPI).startup();
		}
		int i = 0;
		for (char c : AlphbetMap.toCharArray()) {
			charAtIdx.put(c, i);
			i++;
		}
	}
	public static int findIdx(char ch){
		return charAtIdx.get(ch);
	}
	public static String bytesToMapping(byte[] bb) {
		return bigIntToMapping(new BigInteger(bb));
	}

	public static String bytesToMapping(String hexEnc) {
		return bigIntToMapping(new BigInteger(hexEnc, 16));
	}

	public static String bigIntToMapping(BigInteger v) {
		StringBuffer sb = new StringBuffer();
		while (v.bitCount() > 0) {
			sb.append(AlphbetMap.charAt(v.mod(modx).intValue()));
			v = v.divide(modx);
		}
		return sb.reverse().toString();
	}

}
