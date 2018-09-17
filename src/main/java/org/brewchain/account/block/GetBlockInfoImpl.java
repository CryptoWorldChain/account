package org.brewchain.account.block;

import java.math.BigInteger;
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
			if ("true".equalsIgnoreCase(pack.getExtStrProp("clear"))) {
				dao.getStats().getAcceptTxCount().set(0);
				dao.getStats().getBlockTxCount().set(0);
				dao.getStats().setFirstBlockTxTime(0);
				dao.getStats().setFirstAcceptTxTime(0);
				dao.getStats().setLastBlockTxTime(0);
				dao.getStats().setLastAcceptTxTime(0);
			}
			oRespBlockInfo.setBlockCount(blockChainHelper.getLastStableBlockNumber());
			oRespBlockInfo.setCache("sync::" + String.valueOf(KeyConstant.counter) + " exec::"
					+ String.valueOf(KeyConstant.txCounter) + " bps::" + (dao.getStats().getBlockTxCount().get()
							* 1000.0 / (dao.getStats().getLastBlockTxTime() - dao.getStats().getFirstBlockTxTime())));
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
			oRespBlockInfo
					.setBlockTxTimeCostMS(dao.getStats().getLastBlockTxTime() - dao.getStats().getFirstBlockTxTime());

			oRespBlockInfo.setFirstAcceptTxTime(dao.getStats().getFirstAcceptTxTime());
			oRespBlockInfo.setLastAcceptTxTime(dao.getStats().getLastAcceptTxTime());
			oRespBlockInfo.setAcceptTxTimeCostMS(
					dao.getStats().getLastAcceptTxTime() - dao.getStats().getFirstAcceptTxTime());

			BigInteger c = BigInteger.ZERO;

			for (int i = 1; i < blockChainHelper.getLastBlockNumber(); i++) {
				BlockEntity be = blockChainHelper.getBlockByNumber(i);
				c = c.add(new BigInteger(String.valueOf(be.getHeader().getTxHashsCount())));
			}

			oRespBlockInfo.setRealTxBlockCount(c.longValue());
			oRespBlockInfo.setRollBackBlockCount(dao.getStats().getRollBackBlockCount().intValue());
			oRespBlockInfo.setRollBackTxCount(dao.getStats().getRollBackTxCount().intValue());
			oRespBlockInfo.setTxSyncCount(dao.getStats().getTxSyncCount().intValue());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			log.error("GetBlockInfoImpl error", e);
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespBlockInfo.build()));
	}
}
