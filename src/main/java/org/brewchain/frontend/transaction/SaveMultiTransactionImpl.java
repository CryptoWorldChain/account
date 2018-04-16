package org.brewchain.frontend.transaction;

import java.math.BigInteger;
import org.brewchain.frontend.core.AccountHelper;
import org.brewchain.frontend.core.TransactionHelper;
import org.brewchain.frontend.gens.Tx.MultiTransaction;
import org.brewchain.frontend.gens.Tx.PTXTCommand;
import org.brewchain.frontend.gens.Tx.PTXTModule;
import org.brewchain.frontend.gens.Tx.ReqCreateMultiTransaction;
import org.brewchain.frontend.gens.Tx.ReqCreateSingleTransaction;
import org.brewchain.frontend.gens.Tx.RespCreateTransaction;
import org.brewchain.frontend.gens.Tx.SingleTransaction;
import org.brewchain.frontend.util.ByteUtil;

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
			MultiTransaction.Builder oTransaction = pb.getTransaction().toBuilder();
			transactionHelper.CreateMultiTransaction(oTransaction);
			oRespCreateTx.setRetCode(1);
		} catch (Exception e) {
			oRespCreateTx.setRetCode(-1);
			oRespCreateTx.setRetMsg(e.getMessage());
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTx.build()));
	}
}
