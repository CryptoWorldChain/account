package org.brewchain.account.block;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.CacheBlockHashMapDB;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.WaitBlockHashMapDB;
import org.brewchain.account.core.WaitSendHashMapDB;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqBlockInfo;
import org.brewchain.account.gens.Blockimpl.RespBlockInfo;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.OEntityBuilder;
import org.fc.brewchain.bcapi.EncAPI;

import com.sleepycat.utilint.StringUtils;

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
public class GetBlockInfoImpl extends SessionModules<ReqBlockInfo> {

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;

	@ActorRequire(name = "WaitSend_HashMapDB", scope = "global")
	WaitSendHashMapDB oSendingHashMapDB; // 保存待广播交易
	@ActorRequire(name = "WaitBlock_HashMapDB", scope = "global")
	WaitBlockHashMapDB oPendingHashMapDB; // 保存待打包block的交易
	@ActorRequire(name = "CacheBlock_HashMapDB", scope = "global")
	CacheBlockHashMapDB oCacheHashMapDB;

	@Override
	public String[] getCmds() {
		return new String[] { PBCTCommand.BIO.name() };
	}

	@Override
	public String getModule() {
		return PBCTModule.BCT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqBlockInfo pb, final CompleteHandler handler) {
		RespBlockInfo.Builder oRespBlockInfo = RespBlockInfo.newBuilder();
		try {
			oRespBlockInfo.setBlockCount(blockChainHelper.getLastStableBlockNumber());

			oRespBlockInfo.setNumber(blockChainHelper.getLastBlockNumber());
			//oRespBlockInfo.setCache(blockChainHelper.getBlockCacheDump());
			oRespBlockInfo.setWaitSync(oSendingHashMapDB.keys().size());
			oRespBlockInfo.setWaitBlock(oPendingHashMapDB.keys().size());
			LinkedList<BlockEntity> list = blockChainHelper.getParentsBlocks(dao.getBlockDao()
					.get(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get().getExtdata().toByteArray(),
					null, 1000000);
			int curr = 0;
			String retCache = "";
			String parent = "";
			for (int i = 0; i < list.size(); i++) {
				oRespBlockInfo.addDump(String.format("%s %s %s", list.get(i).getHeader().getNumber(),
						encApi.hexEnc(list.get(i).getHeader().getBlockHash().toByteArray()),
						encApi.hexEnc(list.get(i).getHeader().getParentHash().toByteArray())));

				if (parent.isEmpty()) {
					parent = encApi.hexEnc(list.get(i).getHeader().getParentHash().toByteArray());

				} else {
					if (!parent.equals(encApi.hexEnc(list.get(i).getHeader().getBlockHash().toByteArray()))) {
						retCache += String.format("%s %s %s ;", list.get(i).getHeader().getNumber(),
								encApi.hexEnc(list.get(i).getHeader().getBlockHash().toByteArray()),
								encApi.hexEnc(list.get(i).getHeader().getParentHash().toByteArray()));
					}
					parent = encApi.hexEnc(list.get(i).getHeader().getParentHash().toByteArray());

				}

				// if (i == 0)
				// retCache = list.get(i).getHeader().getNumber() + ";";
				// else
				// retCache = list.get(i).getHeader().getNumber() + "->" + retCache;
				// if (curr == 0) {
				// curr = list.get(i).getHeader().getNumber();
				// } else {
				// if ((curr - 1) != list.get(i).getHeader().getNumber()) {
				// retCache += "error:" + list.get(i).getHeader().getNumber() + ";";
				// } else {
				// curr = list.get(i).getHeader().getNumber();
				// }
				// }
			}

			oRespBlockInfo.setCache(retCache);

			// StateTrie oStateTrie = new StateTrie(this.dao,this.encApi);
			// oStateTrie.setRoot(list.getFirst().getHeader().getStateRoot().toByteArray());
			// oRespBlockInfo.setCache(oStateTrie.dumpTrie());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			log.error("GetBlockInfoImpl error", e);
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespBlockInfo.build()));
	}
}
