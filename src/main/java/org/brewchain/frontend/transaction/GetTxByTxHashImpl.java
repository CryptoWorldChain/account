package org.brewchain.frontend.transaction;

import java.util.concurrent.ExecutionException;

import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.frontend.core.TransactionHelper;
import org.brewchain.frontend.gens.Tx.MultiTransaction;
import org.brewchain.frontend.gens.Tx.PTXTCommand;
import org.brewchain.frontend.gens.Tx.PTXTModule;
import org.brewchain.frontend.gens.Tx.ReqGetTxByHash;
import org.brewchain.frontend.gens.Tx.RespGetTxByHash;

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
public class GetTxByTxHashImpl extends SessionModules<ReqGetTxByHash> {
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@Override
	public String[] getCmds() {
		return new String[] { PTXTCommand.GTX.name() };
	}

	@Override
	public String getModule() {
		return PTXTModule.TXT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetTxByHash pb, final CompleteHandler handler) {
		RespGetTxByHash.Builder oRespGetTxByHash = RespGetTxByHash.newBuilder();

		try {
			MultiTransaction oTransaction = transactionHelper.GetTransaction(pb.getTxHash().toByteArray());
			oRespGetTxByHash.setTransaction(oTransaction);
			oRespGetTxByHash.setRetCode(1);
		} catch (Exception e) {
			oRespGetTxByHash.setRetCode(-1);
			e.printStackTrace();
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetTxByHash.build()));
	}
}
