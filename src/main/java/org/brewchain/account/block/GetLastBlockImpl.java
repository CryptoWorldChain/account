package org.brewchain.account.block;

import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.gens.Blockimpl.BlockMinerImpl;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqBlockInfo;
import org.brewchain.account.gens.Blockimpl.RespBlockDetail;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

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
public class GetLastBlockImpl extends SessionModules<ReqBlockInfo> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PBCTCommand.GLB.name() };
	}

	@Override
	public String getModule() {
		return PBCTModule.BCT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqBlockInfo pb, final CompleteHandler handler) {
		RespBlockDetail.Builder oRespBlockDetail = RespBlockDetail.newBuilder();
		try {
			BlockEntity oBlockEntity = blockChainHelper.GetConnectBestBlock();
			BlockMinerImpl.Builder oBlockMinerImpl = BlockMinerImpl.newBuilder();
			
			oRespBlockDetail.setBlockHash(oBlockEntity.getHeader().getBlockHash());
			oRespBlockDetail.setExtraData(oBlockEntity.getHeader().getExtraData());
			oRespBlockDetail.setNumber(oBlockEntity.getHeader().getNumber());
			oRespBlockDetail.setParentHash(oBlockEntity.getHeader().getParentHash());
			oRespBlockDetail.setSliceId(oBlockEntity.getHeader().getSliceId());
			oRespBlockDetail.setTimestamp(oBlockEntity.getHeader().getTimestamp());
			oRespBlockDetail.setStateRoot(oBlockEntity.getHeader().getStateRoot());
			for (String oTxhash : oBlockEntity.getHeader().getTxHashsList()) {
				oRespBlockDetail.addTxHashs(oTxhash);
			}
			oBlockMinerImpl.setBcuid(oBlockEntity.getMiner().getBcuid());
			oBlockMinerImpl.setAddress(oBlockEntity.getMiner().getAddress());
			oBlockMinerImpl.setNode(oBlockEntity.getMiner().getNode());
			oBlockMinerImpl.setReward(oBlockEntity.getMiner().getReward());
			
			oRespBlockDetail.setMiner(oBlockMinerImpl);
			oRespBlockDetail.setRetCode(1);
		} catch (Exception e) {
			oRespBlockDetail.setRetCode(-1);
			// oRespBlockDetail.setRetMsg(e.);
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespBlockDetail.build()));
	}
}
