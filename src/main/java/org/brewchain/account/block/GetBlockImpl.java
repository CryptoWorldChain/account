package org.brewchain.account.block;

import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Block.PBCTCommand;
import org.brewchain.account.gens.Block.PBCTModule;
import org.brewchain.account.gens.Block.ReqGetBlock;
import org.brewchain.account.gens.Block.RespGetBlock;

import com.google.protobuf.InvalidProtocolBufferException;

import onight.oapi.scala.commons.SessionModules;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

public class GetBlockImpl extends SessionModules<ReqGetBlock> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;

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
			BlockEntity.Builder oBlockEntity = blockHelper.CreateNewBlock(pb.getTxCount(),
					pb.getExtraData().toByteArray());
			oRespGetBlock.setHeader(oBlockEntity.getHeader());
			oRespGetBlock.setRetCode(1);
		} catch (InvalidProtocolBufferException e) {
			oRespGetBlock.setRetCode(-1);
			e.printStackTrace();
		}

		oRespGetBlock.setRetCode(1);

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetBlock.build()));
	}
}
