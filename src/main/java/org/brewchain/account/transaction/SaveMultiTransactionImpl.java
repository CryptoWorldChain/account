package org.brewchain.account.transaction;

import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.PTXTCommand;
import org.brewchain.account.gens.Tx.PTXTModule;
import org.brewchain.account.gens.Tx.ReqCreateMultiTransaction;
import org.brewchain.account.gens.Tx.RespCreateTransaction;
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
public class SaveMultiTransactionImpl extends SessionModules<ReqCreateMultiTransaction> {
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTXTCommand.MTX.name() };
	}

	@Override
	public String getModule() {
		return PTXTModule.TXT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateMultiTransaction pb, final CompleteHandler handler) {
		RespCreateTransaction.Builder oRespCreateTx = RespCreateTransaction.newBuilder();

		try {
			MultiTransaction.Builder oTransaction = MultiTransaction.parseFrom(encApi.hexDec(pb.getMultiTxString()))
					.toBuilder();
			transactionHelper.CreateMultiTransaction(oTransaction);
			oRespCreateTx.setRetCode(1);
		} catch (Exception e) {
			oRespCreateTx.setRetCode(-1);
			oRespCreateTx.setRetMsg(e.getMessage());
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTx.build()));
	}
}
