package org.brewchain.account.sample;

import java.util.Date;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCommonTest;
import org.brewchain.account.gens.TxTest.ReqCreateContract;
import org.brewchain.account.gens.TxTest.RespContract;
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
public class TransactionCreateContract extends SessionModules<ReqCreateContract> {
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
		return new String[] { PTSTCommand.TCC.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateContract pb, final CompleteHandler handler) {
		RespContract.Builder oRespContract = RespContract.newBuilder();

		try {
			MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
			MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();

			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getAddress())));
			oMultiTransactionInput4.setAmount(0);
			oMultiTransactionInput4.setFee(0);
			oMultiTransactionInput4.setFeeLimit(0);
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(pb.getAddress())));
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);
			oMultiTransactionBody.setType(TransTypeEnum.TYPE_CreateContract.value());
			oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(pb.getData())));
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			oMultiTransactionBody.setTimestamp((new Date()).getTime());
			// 签名
			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setPubKey(ByteString.copyFrom(encApi.hexDec(pb.getPubKey())));
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(pb.getPrivKey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
			oMultiTransaction.setTxBody(oMultiTransactionBody);

			oRespContract.setContractHash(encApi.hexEnc(
					transactionHelper.getContractAddressByTransaction(oMultiTransaction.build()).toByteArray()));

			String txHash = transactionHelper.CreateMultiTransaction(oMultiTransaction);
			oRespContract.setTxHash(txHash);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespContract.build()));
		return;
	}
}
