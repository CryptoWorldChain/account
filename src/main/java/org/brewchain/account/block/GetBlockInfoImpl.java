package org.brewchain.account.block;

import java.util.LinkedList;

import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.CacheBlockHashMapDB;
import org.brewchain.account.core.ConfirmTxHashMapDB;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.WaitBlockHashMapDB;
import org.brewchain.account.core.WaitSendHashMapDB;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqBlockInfo;
import org.brewchain.account.gens.Blockimpl.RespBlockInfo;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

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
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
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
	@ActorRequire(name = "ConfirmTxHashDB", scope = "global")
	ConfirmTxHashMapDB oConfirmMapDB; // 保存待打包block的交易


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
			oRespBlockInfo.setCache("sync::" + String.valueOf(KeyConstant.counter) + " exec::" + String.valueOf(KeyConstant.txCounter));
			oRespBlockInfo.setNumber(blockChainHelper.getLastBlockNumber());
			// oRespBlockInfo.setCache(blockChainHelper.getBlockCacheDump());
			oRespBlockInfo.setWaitSync(oSendingHashMapDB.size());
			oRespBlockInfo.setWaitBlock(oConfirmMapDB.size());
			oRespBlockInfo.setTxAcceptCount(dao.getStats().getAcceptTxCount().get());
			oRespBlockInfo.setTxAcceptTps(dao.getStats().getTxAcceptTps());
			oRespBlockInfo.setTxBlockCount(dao.getStats().getBlockTxCount().get());
			oRespBlockInfo.setTxBlockTps(dao.getStats().getTxBlockTps());
			
			oRespBlockInfo.setMaxBlockTps(dao.getStats().getMaxBlockTps());
			oRespBlockInfo.setMaxAcceptTps(dao.getStats().getMaxAcceptTps());
			
			oRespBlockInfo.setFirstBlockTxTime(dao.getStats().getFirstBlockTxTime());
			oRespBlockInfo.setLastBlockTxTime(dao.getStats().getLastBlockTxTime());
			oRespBlockInfo.setBlockTxTimeCostMS(dao.getStats().getLastBlockTxTime()-dao.getStats().getFirstBlockTxTime());

			
			oRespBlockInfo.setFirstAcceptTxTime(dao.getStats().getFirstAcceptTxTime());
			oRespBlockInfo.setLastAcceptTxTime(dao.getStats().getLastAcceptTxTime());
			oRespBlockInfo.setAcceptTxTimeCostMS(dao.getStats().getLastAcceptTxTime()-dao.getStats().getFirstAcceptTxTime());

//			LinkedList<BlockEntity> list = blockChainHelper.getParentsBlocks(encApi.hexEnc(dao.getBlockDao()
//					.get(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get().getExtdata().toByteArray()),
//					null, 10000);
//			int curr = 0;
//			String retCache = "";
//			String parent = "";
//			for (int i = 0; i < list.size(); i++) {
//				oRespBlockInfo.addDump(String.format("%s %s %s", list.get(i).getHeader().getNumber(),
//						list.get(i).getHeader().getBlockHash(), list.get(i).getHeader().getParentHash()));
//
//				if (parent.isEmpty()) {
//					parent = list.get(i).getHeader().getParentHash();
//
//				} else {
//					if (!parent.equals(list.get(i).getHeader().getBlockHash())) {
//						retCache += String.format("%s %s %s ;", list.get(i).getHeader().getNumber(),
//								list.get(i).getHeader().getBlockHash(), list.get(i).getHeader().getParentHash());
//					}
//					parent = list.get(i).getHeader().getParentHash();
//
//				}
//
//				// if (i == 0)
//				// retCache = list.get(i).getHeader().getNumber() + ";";
//				// else
//				// retCache = list.get(i).getHeader().getNumber() + "->" + retCache;
//				// if (curr == 0) {
//				// curr = list.get(i).getHeader().getNumber();
//				// } else {
//				// if ((curr - 1) != list.get(i).getHeader().getNumber()) {
//				// retCache += "error:" + list.get(i).getHeader().getNumber() + ";";
//				// } else {
//				// curr = list.get(i).getHeader().getNumber();
//				// }
//				// }
//			}

			//oRespBlockInfo.setCache(retCache);

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
