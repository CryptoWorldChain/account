package org.brewchain.account.test;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.ept.EHelper;
import org.brewchain.account.ept.EMTree;
import org.fc.brewchain.bcapi.EncAPI;
//import org.junit.Test;

public class EPTTest {
	public static void main(String[] args) {
		testCase1();
	}
	
//	@Test
	public static void testCase1() {
		byte[] c = Hex.encode("c".getBytes());
		byte[] ca = Hex.encode("ca".getBytes());
		byte[] cat = Hex.encode("cat".getBytes());

		byte[] test = Hex.encode("test".getBytes());

		byte[] dog = Hex.encode("dog".getBytes());
		byte[] doge = Hex.encode("doge".getBytes());
		byte[] dude = Hex.encode("dude".getBytes());
		
		EMTree oEMTree = new EMTree();
		System.out.println("~~~~");
		EHelper.init();
		System.out.println("~~~~");

		oEMTree.put(c, dog);
		oEMTree.put(ca, doge);
		
		oEMTree.getRoot();

		System.out.println(new String(Hex.decode(oEMTree.get(c))));
		System.out.println(new String(Hex.decode(oEMTree.get(ca))));

	}
}
