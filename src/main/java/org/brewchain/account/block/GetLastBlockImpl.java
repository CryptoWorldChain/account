package org.brewchain.account.block;

import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Blockimpl.BlockHeaderImpl;
import org.brewchain.account.gens.Blockimpl.BlockMinerImpl;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqBlockInfo;
import org.brewchain.account.gens.Blockimpl.ReqGetBlock;
import org.brewchain.account.gens.Blockimpl.RespBlockDetail;
import org.brewchain.account.util.ByteUtil;
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
			byte[] blockHash = blockChainHelper.GetBestBlock();
			BlockEntity.Builder oBlockEntity = blockHelper.getBlock(blockHash);
			BlockMinerImpl.Builder oBlockMinerImpl = BlockMinerImpl.newBuilder();
			
			oRespBlockDetail.setBlockHash(encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()));
			//oRespBlockDetail.setCoinbase(encApi.hexEnc(oBlockEntity.getHeader().getCoinbase().toByteArray()));
			oRespBlockDetail.setExtraData(encApi.hexEnc(oBlockEntity.getHeader().getExtraData().toByteArray()));
			oRespBlockDetail.setNonce(encApi.hexEnc(oBlockEntity.getHeader().getNonce().toByteArray()));
			oRespBlockDetail.setNumber(oBlockEntity.getHeader().getNumber());
			oRespBlockDetail.setParentHash(encApi.hexEnc(oBlockEntity.getHeader().getParentHash().toByteArray()));
			// oRespBlockDetail.setReward(ByteUtil.byteArrayToInt(oBlockEntity.getHeader().getReward().toByteArray()));
			oRespBlockDetail.setSliceId(oBlockEntity.getHeader().getSliceId());
			oRespBlockDetail.setTimestamp(oBlockEntity.getHeader().getTimestamp());
			oRespBlockDetail.setStateRoot(encApi.hexEnc(oBlockEntity.getHeader().getStateRoot().toByteArray()));
			for (ByteString oTxhash : oBlockEntity.getHeader().getTxHashsList()) {
				oRespBlockDetail.addTxHashs(encApi.hexEnc(oTxhash.toByteArray()));
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
