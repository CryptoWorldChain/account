package org.brewchain.account.sample;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.ept.EHelper;
import org.brewchain.account.ept.EMTree;
import org.brewchain.account.ept.ETNode;
import org.fc.brewchain.bcapi.EncAPI;
//import org.junit.Test;

public class EPTTest {
	public static void main(String[] args) {
		testCase1();
	}

	// @Test
	public static void testCase1() {

		try {
			byte[] c = Hex.encode("ccc1".getBytes());
			byte[] ca = Hex.encode("ccc2".getBytes());
			byte[] cat = Hex.encode("ccc3".getBytes());

			byte[] test = Hex.encode("test".getBytes());

			byte[] dog = Hex.encode("dog".getBytes());
			byte[] doge = Hex.encode("doge".getBytes());
			byte[] dude = Hex.encode("dude".getBytes());

			EMTree oEMTree = new EMTree();
			System.out.println("~~~~");
			EHelper.init();
			System.out.println("~~~~");

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));

			oEMTree.put(c, dog);

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));

			oEMTree.put(ca, doge);

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));

//			oEMTree.getRoot().encode();
			byte[] oRootNodeHash1 = oEMTree.getRootHash();
			oEMTree.put(cat, dude);
//			oEMTree.getRoot().encode();
			byte[] oRootNodeHash2 = oEMTree.getRootHash();

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));

			System.out.println("node1::" + Hex.toHexString(oRootNodeHash1));
			System.out.println("node2::" + Hex.toHexString(oRootNodeHash2));
			//
			System.out.println("get::" + new String(Hex.decode(oEMTree.get(c))));
			System.out.println("get::" + new String(Hex.decode(oEMTree.get(ca))));
			System.out.println("get::" + new String(Hex.decode(oEMTree.get(cat))));
			//
//			System.out.println("roll back to node1");
//
//			EMTree oRollBackTree1 = new EMTree();
//			oRollBackTree1.setRoot(oRootNode1);
//
//			System.out.println("get::" + new String(Hex.decode(oRollBackTree1.get(c))));
//			System.out.println("get::" + new String(Hex.decode(oRollBackTree1.get(ca))));
//			try {
//				System.out.println("get::" + new String(Hex.decode(oRollBackTree1.get(cat))));
//			} catch (Exception e) {
//				System.out.println("not found");
//			}
//
//			System.out.println("back to node2");
//
//			EMTree oRollBackTree2 = new EMTree();
//			oRollBackTree2.setRoot(oRootNode2);
//
//			System.out.println("get::" + new String(Hex.decode(oRollBackTree2.get(c))));
//			System.out.println("get::" + new String(Hex.decode(oRollBackTree2.get(ca))));
//			try {
//				System.out.println("get::" + new String(Hex.decode(oRollBackTree2.get(cat))));
//			} catch (Exception e) {
//				System.out.println("not found");
//			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			// TODO: handle finally clause
		}

	}
}
