package org.brewchain.account.sample;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCommonTest;
import org.brewchain.account.gens.TxTest.RespCommonTest;
import org.brewchain.account.gens.TxTest.RespCreateTransactionTest;
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
public class TransactionLoadTestExecImpl extends SessionModules<ReqCommonTest> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "TransactionLoadTest_Store", scope = "global")
	TransactionLoadTestStore transactionLoadTestStore;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.LTE.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCommonTest pb, final CompleteHandler handler) {
		RespCreateTransactionTest.Builder oRespCreateTransactionTest = RespCreateTransactionTest.newBuilder();

		String txHash = "";
		try {
			MultiTransaction.Builder tx = transactionLoadTestStore.getOne();
			if (tx != null) {
				txHash = transactionHelper.CreateMultiTransaction(tx);
				oRespCreateTransactionTest.setRetmsg("success");
				oRespCreateTransactionTest.setTxhash(txHash);
				oRespCreateTransactionTest
						.setFrom(encApi.hexEnc(tx.getTxBody().getInputs(0).getAddress().toByteArray()));
				oRespCreateTransactionTest
						.setTo(encApi.hexEnc(tx.getTxBody().getOutputs(0).getAddress().toByteArray()));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.error("test fail::", e);
			oRespCreateTransactionTest.clear();
			oRespCreateTransactionTest.setRetmsg("error");
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTransactionTest.build()));
		return;
	}
}
