package org.brewchain.account.sample;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.brewchain.account.bean.HashPair;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.actuator.TransactionTypeEnum;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCreateTransactionTest;
import org.brewchain.account.gens.TxTest.ReqCreateUnionAccount;
import org.brewchain.account.gens.TxTest.ReqUnionAccountTransaction;
import org.brewchain.account.gens.TxTest.RespCreateUnionAccount;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.brewchain.evmapi.gens.Tx.UnionAccountData;
import org.brewchain.rcvm.utils.ByteUtil;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;
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
public class UnionAccountTransactionSample extends SessionModules<ReqUnionAccountTransaction> {
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
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;
	
	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.TUA.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqUnionAccountTransaction pb, final CompleteHandler handler) {
		RespCreateUnionAccount.Builder oRespCreateUnionAccount = RespCreateUnionAccount.newBuilder();

		if (!blockChainConfig.isDev()) {
			oRespCreateUnionAccount.setRetCode(-1);
			handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateUnionAccount.build()));
			return;
		}
		
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();

		try {
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getUnionAccountAddress())));

			oMultiTransactionInput4
					.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getAmount()))));
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(pb.getUnionAccountAddress())));
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);
			oMultiTransactionBody.setType(TransTypeEnum.TYPE_UnionAccountTransaction.value());
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
			if (StringUtils.isNotBlank(pb.getRelTxHash())) {
				oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(pb.getRelTxHash())));
			}
			oMultiTransactionBody.setExdata(ByteString.copyFrom(encApi.hexDec(pb.getRelAddress())));
			MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getToAddress())));
			oMultiTransactionOutput1
					.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getAmount()))));
			oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(pb.getRelKey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
			oMultiTransaction.setTxBody(oMultiTransactionBody);

			String txHash = transactionHelper.CreateMultiTransaction(oMultiTransaction).getKey();
			oRespCreateUnionAccount.setRetMsg(txHash);
			oRespCreateUnionAccount.setRetCode(1);
		} catch (Exception e) {
			e.printStackTrace();
			oRespCreateUnionAccount.setRetCode(-1);
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateUnionAccount.build()));
		return;
	}
}
