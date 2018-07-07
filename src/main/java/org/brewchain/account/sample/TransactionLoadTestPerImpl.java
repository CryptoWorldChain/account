package org.brewchain.account.sample;

import java.math.BigInteger;
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
import org.brewchain.rcvm.utils.ByteUtil;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

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
public class TransactionLoadTestPerImpl extends SessionModules<ReqCreateTransactionTest> {
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
		return new String[] { PTSTCommand.LTP.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateTransactionTest pb, final CompleteHandler handler) {
		RespCreateTransactionTest.Builder oRespCreateTransactionTest = RespCreateTransactionTest.newBuilder();

		for (int i = 0; i < 50000; i++) {
			MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
			MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
			try {
				// KeyPairs oFrom = encApi.genKeys( "a" + i);
				// KeyPairs oTo = encApi.genKeys("b" + i);
				KeyPairs oFrom = encApi.genKeys();
				KeyPairs oTo = encApi.genKeys();
				// accountHelper.addBalance(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())),
				// 10);

				//accountHelper.CreateAccount(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
				// if (i % 2 == 1) {
				//accountHelper.CreateAccount(ByteString.copyFrom(encApi.hexDec(oTo.getAddress())));
				// }
				MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
				oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
				oMultiTransactionInput4.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
				int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
				// nonce = nonce + i - 1;
				oMultiTransactionInput4.setNonce(nonce);
				oMultiTransactionBody.addInputs(oMultiTransactionInput4);

				MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
				oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(oTo.getAddress())));
				oMultiTransactionOutput1.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
				oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

				oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(pb.getData())));
				oMultiTransaction.clearTxHash();
				oMultiTransactionBody.clearSignatures();
				oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
				// 签名
				MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
				oMultiTransactionSignature21.setPubKey(ByteString.copyFrom(encApi.hexDec(oFrom.getPubkey())));
				oMultiTransactionSignature21.setSignature(ByteString
						.copyFrom(encApi.ecSign(oFrom.getPrikey(), oMultiTransactionBody.build().toByteArray())));
				oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

				oMultiTransaction.setTxBody(oMultiTransactionBody);
				transactionLoadTestStore.getLoads().add(oMultiTransaction);
				log.debug("gen per tx::" + oMultiTransaction.getTxHash() + " sender::" + oFrom.getAddress()
						+ " receiver::" + oTo.getAddress());
				// String txHash = transactionHelper.CreateMultiTransaction(oMultiTransaction);
			} catch (Exception e) {

			}
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTransactionTest.build()));
		return;
	}
}