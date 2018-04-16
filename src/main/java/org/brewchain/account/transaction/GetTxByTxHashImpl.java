package org.brewchain.account.transaction;

import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.PTXTCommand;
import org.brewchain.account.gens.Tx.PTXTModule;
import org.brewchain.account.gens.Tx.ReqGetTxByHash;
import org.brewchain.account.gens.Tx.RespGetTxByHash;

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
