package org.brewchain.account.sample;

import java.math.BigInteger;
import java.util.Date;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCreateToken;
import org.brewchain.account.gens.TxTest.ReqCreateTransactionTest;
import org.brewchain.account.gens.TxTest.ReqTransactionAccount;
import org.brewchain.account.gens.TxTest.ReqTransactionSignature;
import org.brewchain.account.gens.TxTest.RespCreateToken;
import org.brewchain.account.gens.TxTest.RespCreateTransactionTest;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.brewchain.rcvm.utils.ByteUtil;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.UnitUtil;

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
public class CreateTokenTransactionSample extends SessionModules<ReqCreateTransactionTest> {
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
	// @ActorRequire(name = "BlockStore_UnStable", scope = "global")
	// BlockUnStableStore unStableStore;
	@ActorRequire(name = "BlockStore_Helper", scope = "global")
	BlockStore blockStore;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.TCT.name() };
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
				oMultiTransactionInput4.setAmount(ByteString
						.copyFrom(ByteUtil.bigIntegerToBytes(UnitUtil.toWei(input.getAmount()))));
				int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(input.getAddress())));
				oMultiTransactionInput4.setNonce(nonce);
				oMultiTransactionInput4.setToken(input.getErc20Symbol());

				oMultiTransactionBody.addInputs(oMultiTransactionInput4);

				oRespCreateTransactionTest
						.addTrace("add input address::" + input.getAddress() + " nonce::" + nonce + " balance::"
								+ accountHelper.getBalance(ByteString.copyFrom(encApi.hexDec(input.getAddress()))));
			}

			oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(pb.getData())));
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
			// 签名
			for (ReqTransactionSignature input : pb.getSignatureList()) {
				MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
				oMultiTransactionSignature21.setSignature(ByteString
						.copyFrom(encApi.ecSign(input.getPrivKey(), oMultiTransactionBody.build().toByteArray())));
				oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
			}
			oMultiTransaction.setTxBody(oMultiTransactionBody);

			String txHash = transactionHelper.CreateMultiTransaction(oMultiTransaction);
			oRespCreateTransactionTest.setTxhash(txHash);
		} catch (Exception e) {
			e.printStackTrace();
			oRespCreateTransactionTest.setRetcode(-1);
			oRespCreateTransactionTest.setRetmsg(e.getMessage());
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTransactionTest.build()));
		return;
	}
}
