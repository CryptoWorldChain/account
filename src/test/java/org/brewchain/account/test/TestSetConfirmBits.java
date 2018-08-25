package org.brewchain.account.test;

import java.math.BigInteger;
import java.util.HashMap;

import org.brewchain.account.bean.HashPair;
import org.brewchain.bcapi.gens.Oentity.OKey;

import com.google.protobuf.ByteString;

import lombok.AllArgsConstructor;

public class TestSetConfirmBits {

	@AllArgsConstructor
	public static class KeyTest{
		String key;

		@Override
		public int hashCode() {
			return 1;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			KeyTest other = (KeyTest) obj;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			return true;
		}

	}
	public static void main(String[] args) { 
		HashPair hp = new HashPair("abc", new byte[] { (byte) 0 }, null);
		hp.setBits(new BigInteger("10"));
		System.out.println(hp.getBits().toString(2));
		HashMap<KeyTest, String> testMap = new HashMap<>();
		testMap.put(new KeyTest("abc"), "abc");
		testMap.put(new KeyTest("def"), "def");
		System.out.println(testMap.size());
		
		HashMap<OKey, String> okeyMap = new HashMap<>();
		okeyMap.put(OKey.newBuilder().setData(ByteString.copyFrom("abc".getBytes())).build(), "abc");
		okeyMap.put(OKey.newBuilder().setData(ByteString.copyFrom("abc".getBytes())).build(), "abc");
		okeyMap.put(OKey.newBuilder().setData(ByteString.copyFrom("def".getBytes())).build(), "def");
		OKey k1=OKey.newBuilder().setData(ByteString.copyFrom("abc".getBytes())).build();
		OKey k2=OKey.newBuilder().setData(ByteString.copyFrom("abc".getBytes())).build();
		System.out.println("k1=k2:"+k1.equals(k2));
		System.out.println(okeyMap.size());
	}
}
