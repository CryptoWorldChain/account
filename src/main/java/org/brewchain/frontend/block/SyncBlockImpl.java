package org.brewchain.frontend.block;

import java.util.LinkedList;
import java.util.List;

import org.brewchain.frontend.core.BlockHelper;
import org.brewchain.frontend.core.TransactionHelper;
import org.brewchain.frontend.gens.Block.BlockEntity;
import org.brewchain.frontend.gens.Block.PBCTCommand;
import org.brewchain.frontend.gens.Block.PBCTModule;
import org.brewchain.frontend.gens.Block.ReqSyncBlock;
import org.brewchain.frontend.gens.Block.RespSyncBlock;

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
			oBlockEntity.setHeader(pb.getHeader());
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
