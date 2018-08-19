package org.brewchain.account.transaction;

import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.Tximpl.PTXTCommand;
import org.brewchain.account.gens.Tximpl.PTXTModule;
import org.brewchain.account.gens.Tximpl.ReqCreateMultiTransaction;
import org.brewchain.account.gens.Tximpl.RespCreateTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
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
			MultiTransaction.Builder oTransaction = transactionHelper.parse(pb.getTransaction());
			if (oTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateContract.value()) {
				oRespCreateTx.setContractHash(encApi
						.hexEnc(transactionHelper.getContractAddressByTransaction(oTransaction.build()).toByteArray()));
			}
			oRespCreateTx.setTxHash(transactionHelper.CreateMultiTransaction(oTransaction).getHexKey());
			oRespCreateTx.setRetCode(1);
		} catch (Throwable e) {
			log.error("error on create tx::" + e);
			e.printStackTrace();

			oRespCreateTx.clear();
			oRespCreateTx.setRetCode(-1);
			oRespCreateTx.setRetMsg(e == null || e.getMessage() == null ? "" : e.getMessage());
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTx.build()));
	}
}
