package org.brewchain.account.block;

import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Block.BlockHeader;
import org.brewchain.account.gens.Block.BlockMiner;
import org.brewchain.account.gens.Blockimpl.BlockHeaderImpl;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqSyncBlock;
import org.brewchain.account.gens.Blockimpl.RespSyncBlock;
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
public class SyncBlockImpl extends SessionModules<ReqSyncBlock> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PBCTCommand.SBC.name() };
	}

	@Override
	public String getModule() {
		return PBCTModule.BCT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqSyncBlock pb, final CompleteHandler handler) {
		RespSyncBlock.Builder oRespSyncBlock = RespSyncBlock.newBuilder();

		try {
			BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();

			BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
			BlockMiner.Builder oBlockMiner = BlockMiner.newBuilder();
			oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.hexDec(pb.getHeader().getBlockHash())));
			//oBlockHeader.setCoinbase(ByteString.copyFrom(encApi.hexDec(pb.getHeader().getCoinbase())));
			oBlockHeader.setExtraData(ByteString.copyFrom(encApi.hexDec(pb.getHeader().getExtraData())));
			oBlockHeader.setNonce(ByteString.copyFrom(encApi.hexDec(pb.getHeader().getNonce())));
			oBlockHeader.setNumber(pb.getHeader().getNumber());
			oBlockHeader.setParentHash(ByteString.copyFrom(encApi.hexDec(pb.getHeader().getParentHash())));
			oBlockHeader.setReward(ByteString.copyFrom(ByteUtil.intToBytes(pb.getHeader().getReward())));
			oBlockHeader.setSliceId(pb.getHeader().getSliceId());
			oBlockHeader.setTimestamp(pb.getHeader().getTimestamp());

			for (String oTxhash : pb.getHeader().getTxHashsList()) {
				oBlockHeader.addTxHashs(ByteString.copyFrom(encApi.hexDec(oTxhash)));
			}

			oBlockMiner.setBcuid(pb.getMiner().getBcuid());
			oBlockMiner.setAddress(pb.getMiner().getAddress());
			oBlockMiner.setNode(pb.getMiner().getNode());
			oBlockMiner.setReward(pb.getMiner().getReward());
			
			oBlockEntity.setHeader(oBlockHeader);
			oBlockEntity.setMiner(oBlockMiner);
			// 如果节点已经启动，则重新加载全部block
			blockHelper.ApplyBlock(oBlockEntity.build());
			oRespSyncBlock.setRetCode(1);
		} catch (Exception e) {
			oRespSyncBlock.setRetCode(-1);
			e.printStackTrace();
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespSyncBlock.build()));
	}
}
