package org.brewchain.account.sample;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.ept.EMTree;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.Tximpl.ReqCreateMultiTransaction;
import org.brewchain.account.gens.Tximpl.RespCreateTransaction;
import org.brewchain.account.util.OEntityBuilder;
import org.fc.brewchain.bcapi.EncAPI;
//import org.junit.Test;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class EPTTest extends SessionModules<ReqCreateMultiTransaction> {
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
	@ActorRequire(name = "Block_EMTree", scope = "global")
	EMTree oEMTree;
	//
	// @Validate
	// public void startup() {
	// try {
	// EHelper.dao = dao;
	// testCase1();
	// } catch (Exception e) {
	// // e.printStackTrace();
	// log.error("dao注入异常", e);
	// }
	// }

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.EPT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateMultiTransaction pb, final CompleteHandler handler) {
		RespCreateTransaction.Builder oRespCreateTx = RespCreateTransaction.newBuilder();
		testCase1();
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTx.build()));
	}

	// @Test
	public void testCase1() {

		try {
			byte[] ccc1 = Hex.encode("ccc1".getBytes());
			byte[] ccc2 = Hex.encode("ccc2".getBytes());
			byte[] ccc3 = Hex.encode("ccc3".getBytes());
			byte[] cat = Hex.encode("cat".getBytes());
			byte[] xyz = Hex.encode("xyz".getBytes());

			byte[] test = Hex.encode("test".getBytes());

			byte[] dog = Hex.encode("dog".getBytes());
			byte[] doge = Hex.encode("doge".getBytes());
			byte[] dude = Hex.encode("dude".getBytes());
			byte[] uvw = Hex.encode("uvw".getBytes());

			oEMTree.setDao(dao);
			// oEMTree.setEncApi(encApi);
			oEMTree.setOEntityHelper(oEntityHelper);

			System.out.println("~~~~");

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));

			oEMTree.put(ccc1, dog);

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));
			byte[] oRootNodeHash0 = oEMTree.getRootHash();

			oEMTree.put(ccc2, doge);

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));
			// oEMTree.getRoot().encode();
			byte[] oRootNodeHash1 = oEMTree.getRootHash();
			oEMTree.put(cat, dude);
			// oEMTree.getRoot().encode();
			byte[] oRootNodeHash2 = oEMTree.getRootHash();

			oEMTree.put(xyz, uvw);
			byte[] oRootNodeHash3 = oEMTree.getRootHash();

			// System.out.println("root::" +
			// Hex.toHexString(oEMTree.getRoot().encode()));

			System.out.println("node0::" + Hex.toHexString(oRootNodeHash0));
			System.out.println("node1::" + Hex.toHexString(oRootNodeHash1));
			System.out.println("node2::" + Hex.toHexString(oRootNodeHash2));
			System.out.println("node3::" + Hex.toHexString(oRootNodeHash3));

			//
			System.out.println("get::" + new String(Hex.decode(oEMTree.get(ccc1))));
			System.out.println("get::" + new String(Hex.decode(oEMTree.get(ccc2))));
			System.out.println("get::" + new String(Hex.decode(oEMTree.get(cat))));
			System.out.println("get::" + new String(Hex.decode(oEMTree.get(xyz))));

			System.out.println("roll back to node1");

			EMTree oRollBackTree1 = new EMTree();
			oRollBackTree1.setRootHash(oRootNodeHash1);

			System.out.println("get::" + new String(Hex.decode(oRollBackTree1.get(ccc1))));
			System.out.println("get::" + new String(Hex.decode(oRollBackTree1.get(ccc2))));
			try {
				System.out.println("get::" + new String(Hex.decode(oRollBackTree1.get(cat))));
			} catch (Exception e) {
				System.out.println("not found");
			}

			System.out.println("back to node2");

			EMTree oRollBackTree2 = new EMTree();
			oRollBackTree2.setRootHash(oRootNodeHash2);

			System.out.println("get::" + new String(Hex.decode(oRollBackTree2.get(ccc1))));
			System.out.println("get::" + new String(Hex.decode(oRollBackTree2.get(cat))));
			try {
				System.out.println("get::" + new String(Hex.decode(oRollBackTree2.get(xyz))));
			} catch (Exception e) {
				System.out.println("not found");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// TODO: handle finally clause
		}

	}
}
