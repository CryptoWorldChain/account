package org.brewchain.account.sample;

import java.util.Date;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCreateTransactionTest;
import org.brewchain.account.gens.TxTest.ReqTransactionAccount;
import org.brewchain.account.gens.TxTest.RespCreateTransactionTest;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
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
public class TransactionSampleImpl extends SessionModules<ReqCreateTransactionTest> {
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

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.STT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateTransactionTest pb, final CompleteHandler handler) {
		RespCreateTransactionTest.Builder oRespCreateTransactionTest = RespCreateTransactionTest.newBuilder();

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();

		try {

			for (ReqTransactionAccount input : pb.getInputList()) {
				MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
				oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(input.getAddress())));
				oMultiTransactionInput4.setAmount(input.getAmount());
				oMultiTransactionInput4.setFee(0);
				oMultiTransactionInput4.setFeeLimit(0);
				int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(input.getAddress())));
				oMultiTransactionInput4.setNonce(nonce);
				oMultiTransactionInput4.setCryptoToken(ByteString.copyFrom(encApi.hexDec(input.getErc721Token())));
				oMultiTransactionInput4.setPubKey(ByteString.copyFrom(encApi.hexDec(input.getPutkey())));
				oMultiTransactionInput4.setSymbol(input.getErc721Symbol());
				oMultiTransactionInput4.setToken(input.getErc20Symbol());

				oMultiTransactionBody.addInputs(oMultiTransactionInput4);

				oRespCreateTransactionTest.addTrace("add input address::" + input.getAddress() + " nonce::" + nonce
						+ " balance::" + accountHelper.getBalance(ByteString.copyFrom(encApi.hexDec(input.getAddress()))));
			}

			for (ReqTransactionAccount output : pb.getOutputList()) {
				MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
				oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(output.getAddress())));
				oMultiTransactionOutput1.setAmount(output.getAmount());
				oMultiTransactionOutput1.setCryptoToken(ByteString.copyFrom(encApi.hexDec(output.getErc721Token())));
				oMultiTransactionOutput1.setSymbol(output.getErc721Symbol());
				oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

				oRespCreateTransactionTest.addTrace("add output address::" + output.getAddress() + " nonce::"
						+ accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(output.getAddress()))) + " balance::"
						+ accountHelper.getBalance(ByteString.copyFrom(encApi.hexDec(output.getAddress()))));
			}

			oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(pb.getData())));
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			oMultiTransactionBody.setTimestamp((new Date()).getTime());
			// 签名
			for (ReqTransactionAccount input : pb.getInputList()) {
				MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
				oMultiTransactionSignature21.setPubKey(ByteString.copyFrom(encApi.hexDec(input.getPutkey())));
				oMultiTransactionSignature21.setSignature(
						ByteString.copyFrom(encApi.ecSign(input.getPrikey(), oMultiTransactionBody.build().toByteArray())));
				oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
			}
			oMultiTransaction.setTxBody(oMultiTransactionBody);

			String txHash = transactionHelper.CreateMultiTransaction(oMultiTransaction);
			oRespCreateTransactionTest.setTxhash(txHash);
		} catch (Exception e) {
			oRespCreateTransactionTest.setRetcode(-1);
			oRespCreateTransactionTest.setRetmsg(e.getMessage());
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTransactionTest.build()));
		return;
	}
}
