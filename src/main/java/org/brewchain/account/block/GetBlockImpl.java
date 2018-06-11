package org.brewchain.account.block;

import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.gens.Blockimpl.BlockHeaderImpl;
import org.brewchain.account.gens.Blockimpl.BlockMinerImpl;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqGetBlock;
import org.brewchain.account.gens.Blockimpl.RespGetBlock;
import org.brewchain.account.util.ByteUtil;
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
public class GetBlockImpl extends SessionModules<ReqGetBlock> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PBCTCommand.GBC.name() };
	}

	@Override
	public String getModule() {
		return PBCTModule.BCT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetBlock pb, final CompleteHandler handler) {
		RespGetBlock.Builder oRespGetBlock = RespGetBlock.newBuilder();

		try {
			String coinBase = this.props().get("block.coinBase.hex", null);
			if (coinBase == null) {
				oRespGetBlock.setRetCode(-2);
				handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetBlock.build()));
				return;
			}
			BlockEntity.Builder oBlockEntity;
			try {
				oBlockEntity = blockHelper.CreateNewBlock(pb.getTxCount(), encApi.hexDec(pb.getExtraData()),
						ByteString.copyFromUtf8(coinBase).toByteArray());

				BlockHeaderImpl.Builder oBlockHeaderImpl = BlockHeaderImpl.newBuilder();
				oBlockHeaderImpl.setBlockHash(encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()));
				//oBlockHeaderImpl.setCoinbase(encApi.hexEnc(oBlockEntity.getHeader().getCoinbase().toByteArray()));
				oBlockHeaderImpl.setExtraData(encApi.hexEnc(oBlockEntity.getHeader().getExtraData().toByteArray()));
				oBlockHeaderImpl.setNonce(encApi.hexEnc(oBlockEntity.getHeader().getNonce().toByteArray()));
				oBlockHeaderImpl.setNumber(oBlockEntity.getHeader().getNumber());
				oBlockHeaderImpl.setParentHash(encApi.hexEnc(oBlockEntity.getHeader().getParentHash().toByteArray()));
				oBlockHeaderImpl.setReward(ByteUtil.byteArrayToInt(oBlockEntity.getHeader().getReward().toByteArray()));
				oBlockHeaderImpl.setSliceId(oBlockEntity.getHeader().getSliceId());
				oBlockHeaderImpl.setTimestamp(oBlockEntity.getHeader().getTimestamp());

				for (ByteString oTxhash : oBlockEntity.getHeader().getTxHashsList()) {
					oBlockHeaderImpl.addTxHashs(encApi.hexEnc(oTxhash.toByteArray()));
				}
				
				BlockMinerImpl.Builder oBlockMinerImpl = BlockMinerImpl.newBuilder();
				oBlockMinerImpl.setBcuid(oBlockEntity.getMiner().getBcuid());
				oBlockMinerImpl.setAddress(oBlockEntity.getMiner().getAddress());
				oBlockMinerImpl.setNode(oBlockEntity.getMiner().getNode());
				oBlockMinerImpl.setReward(oBlockEntity.getMiner().getReward());
				
				oRespGetBlock.setHeader(oBlockHeaderImpl);
				oRespGetBlock.setMiner(oBlockMinerImpl);
				oRespGetBlock.setRetCode(1);
			} catch (Exception e) {
				// TODO Auto-generated catch block
//				e.printStackTrace();
				oRespGetBlock.setRetCode(-1);
				log.error("GetBlockImpl error",e);
			}

		} catch (Exception e) {
			oRespGetBlock.setRetCode(-1);
//			e.printStackTrace();
			log.error("GetBlockImpl error",e);
		}

		oRespGetBlock.setRetCode(1);

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetBlock.build()));
	}
}
