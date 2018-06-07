package org.brewchain.account.test;

import java.util.Date;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCreateContract;
import org.brewchain.account.gens.TxTest.ReqTTT;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class CreateContractTest extends SessionModules<ReqCreateContract> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.CCT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateContract pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();

		// 创建账户1
		KeyPairs oKeyPairs1 = encApi.genKeys();
		// 创建账户1
		accountHelper.CreateAccount(encApi.hexDec(oKeyPairs1.getAddress()), oKeyPairs1.getPubkey().getBytes());
		int nonce = 0;
		// 增加账户余额1
		try {
			accountHelper.addBalance(encApi.hexDec(oKeyPairs1.getAddress()), 100);
			nonce = accountHelper.getNonce(encApi.hexDec(oKeyPairs1.getAddress()));
			log.debug(String.format("创建账户1::%s Balance::100", oKeyPairs1.getAddress()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
		oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oKeyPairs1.getAddress())));
		oMultiTransactionInput4.setAmount(0);
		oMultiTransactionInput4.setFee(0);
		oMultiTransactionInput4.setFeeLimit(0);
		oMultiTransactionInput4.setNonce(nonce);
		oMultiTransactionBody.addInputs(oMultiTransactionInput4);

		// MultiTransactionOutput.Builder oMultiTransactionOutput =
		// MultiTransactionOutput.newBuilder();
		oMultiTransactionBody.setData(ByteString.copyFromUtf8(pb.getCode()));
		oMultiTransaction.setTxHash(ByteString.EMPTY);
		oMultiTransactionBody.clearSignatures();

		oMultiTransactionBody.setTimestamp((new Date()).getTime());
		// 签名
		MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
		oMultiTransactionSignature21.setPubKey(oKeyPairs1.getPubkey());
		oMultiTransactionSignature21.setSignature(
				encApi.hexEnc(encApi.ecSign(oKeyPairs1.getPrikey(), oMultiTransactionBody.build().toByteArray())));
		oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
		oMultiTransaction.setTxBody(oMultiTransactionBody);
		try {
			log.debug("合约地址："
					+ encApi.hexEnc(transactionHelper.getContractAddressByTransaction(oMultiTransaction.build())));
			transactionHelper.CreateMultiTransaction(oMultiTransaction);

//			transactionHelper.parseToImpl(oMultiTransaction.build());
//			transactionHelper.CreateMultiTransaction(
//					transactionHelper.parse(transactionHelper.parseToImpl(oMultiTransaction.build()).build()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));

	}
}
