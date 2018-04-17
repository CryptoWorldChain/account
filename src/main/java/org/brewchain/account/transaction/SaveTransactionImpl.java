package org.brewchain.account.transaction;

import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Tx.PTXTCommand;
import org.brewchain.account.gens.Tx.PTXTModule;
import org.brewchain.account.gens.Tx.ReqCreateSingleTransaction;
import org.brewchain.account.gens.Tx.RespCreateTransaction;
import org.brewchain.account.gens.Tx.SingleTransaction;
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
public class SaveTransactionImpl extends SessionModules<ReqCreateSingleTransaction> {
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTXTCommand.STX.name() };
	}

	@Override
	public String getModule() {
		return PTXTModule.TXT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateSingleTransaction pb, final CompleteHandler handler) {
		RespCreateTransaction.Builder oRespCreateTx = RespCreateTransaction.newBuilder();

		try {
			SingleTransaction.Builder oTransaction = SingleTransaction.parseFrom(encApi.hexDec(pb.getSingleTxString()))
					.toBuilder();
			transactionHelper
					.CreateMultiTransaction(transactionHelper.ParseSingleTransactionToMultiTransaction(oTransaction));
			oRespCreateTx.setRetCode(1);
		} catch (Exception e) {
			oRespCreateTx.setRetCode(-1);
			oRespCreateTx.setRetMsg(e.getMessage());
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTx.build()));
	}
}
