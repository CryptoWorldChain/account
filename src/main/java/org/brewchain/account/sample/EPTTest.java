package org.brewchain.account.sample;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Validate;
import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.ept.EHelper;
import org.brewchain.account.ept.EMTree;
import org.brewchain.account.util.OEntityBuilder;
import org.fc.brewchain.bcapi.EncAPI;
//import org.junit.Test;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

//@NActorProvider
//@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
//@Instantiate(name = "EPTTest_Tree")
//@Slf4j
//@Data
public class EPTTest implements ActorService {
//	@ActorRequire(name = "bc_encoder", scope = "global")
//	static EncAPI encApi;
//	@ActorRequire(name = "Def_Daos", scope = "global")
//	static DefDaos dao;
//	@ActorRequire(name = "OEntity_Helper", scope = "global")
//	static OEntityBuilder oEntityHelper;
//
//	@Validate
//	public void startup() {
//		try {
//			EHelper.dao = dao;
//			testCase1();
//		} catch (Exception e) {
//			// e.printStackTrace();
//			log.error("dao注入异常", e);
//		}
//	}
//
////	public static void main(String[] args) {
////		testCase1();
////	}
//
//	// @Test
//	public void testCase1() {
//
//		try {
//			byte[] c = Hex.encode("ccc1".getBytes());
//			byte[] ca = Hex.encode("ccc2".getBytes());
//			byte[] cat = Hex.encode("ccc3".getBytes());
//
//			byte[] test = Hex.encode("test".getBytes());
//
//			byte[] dog = Hex.encode("dog".getBytes());
//			byte[] doge = Hex.encode("doge".getBytes());
//			byte[] dude = Hex.encode("dude".getBytes());
//
//			EMTree oEMTree = new EMTree();
//			oEMTree.setDao(dao);
//			// oEMTree.setEncApi(encApi);
//			oEMTree.setOEntityHelper(oEntityHelper);
//
//			System.out.println("~~~~");
//			EHelper.init();
//			System.out.println("~~~~");
//
//			// System.out.println("root::" +
//			// Hex.toHexString(oEMTree.getRoot().encode()));
//
//			oEMTree.put(c, dog);
//
//			// System.out.println("root::" +
//			// Hex.toHexString(oEMTree.getRoot().encode()));
//
//			oEMTree.put(ca, doge);
//
//			// System.out.println("root::" +
//			// Hex.toHexString(oEMTree.getRoot().encode()));
//			// oEMTree.getRoot().encode();
//			byte[] oRootNodeHash1 = oEMTree.getRootHash();
//			oEMTree.put(cat, dude);
//			// oEMTree.getRoot().encode();
//			byte[] oRootNodeHash2 = oEMTree.getRootHash();
//
//			// System.out.println("root::" +
//			// Hex.toHexString(oEMTree.getRoot().encode()));
//
//			System.out.println("node1::" + Hex.toHexString(oRootNodeHash1));
//			System.out.println("node2::" + Hex.toHexString(oRootNodeHash2));
//			//
//			System.out.println("get::" + new String(Hex.decode(oEMTree.get(c))));
//			System.out.println("get::" + new String(Hex.decode(oEMTree.get(ca))));
//			System.out.println("get::" + new String(Hex.decode(oEMTree.get(cat))));
//			//
//			// System.out.println("roll back to node1");
//			//
//			// EMTree oRollBackTree1 = new EMTree();
//			// oRollBackTree1.setRoot(oRootNode1);
//			//
//			// System.out.println("get::" + new
//			// String(Hex.decode(oRollBackTree1.get(c))));
//			// System.out.println("get::" + new
//			// String(Hex.decode(oRollBackTree1.get(ca))));
//			// try {
//			// System.out.println("get::" + new
//			// String(Hex.decode(oRollBackTree1.get(cat))));
//			// } catch (Exception e) {
//			// System.out.println("not found");
//			// }
//			//
//			// System.out.println("back to node2");
//			//
//			// EMTree oRollBackTree2 = new EMTree();
//			// oRollBackTree2.setRoot(oRootNode2);
//			//
//			// System.out.println("get::" + new
//			// String(Hex.decode(oRollBackTree2.get(c))));
//			// System.out.println("get::" + new
//			// String(Hex.decode(oRollBackTree2.get(ca))));
//			// try {
//			// System.out.println("get::" + new
//			// String(Hex.decode(oRollBackTree2.get(cat))));
//			// } catch (Exception e) {
//			// System.out.println("not found");
//			// }
//		} catch (Exception e) {
//			e.printStackTrace();
//		} finally {
//			// TODO: handle finally clause
//		}
//
//	}
}
