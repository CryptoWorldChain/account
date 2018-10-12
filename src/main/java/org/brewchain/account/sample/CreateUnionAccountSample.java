package org.brewchain.account.sample;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

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
public class CreateUnionAccountSample extends SessionModules<ReqCreateUnionAccount> {
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
		return new String[] { PTSTCommand.TCA.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateUnionAccount pb, final CompleteHandler handler) {
		RespCreateUnionAccount.Builder oRespCreateUnionAccount = RespCreateUnionAccount.newBuilder();
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();

		if (!blockChainConfig.isDev()) {
			oRespCreateUnionAccount.setRetCode(-1);
			handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateUnionAccount.build()));
			return;
		}
		
		try {
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getAddress())));

			oMultiTransactionInput4.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(pb.getAddress())));
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);

			oMultiTransactionBody.setType(TransTypeEnum.TYPE_CreateUnionAccount.value());
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());

			UnionAccountData.Builder oUnionAccountData = UnionAccountData.newBuilder();
			oUnionAccountData.setAcceptLimit(pb.getAcceptLimit());
			oUnionAccountData
					.setAcceptMax(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getAcceptMax()))));
			oUnionAccountData.setMax(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getMax()))));

			for (int i = 0; i < pb.getRelAddressCount(); i++) {
				oUnionAccountData.addAddress(ByteString.copyFrom(encApi.hexDec(pb.getRelAddress(i))));
			}

			oMultiTransactionBody.setData(oUnionAccountData.build().toByteString());
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();

			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(pb.getPrivKey(), oMultiTransactionBody.build().toByteArray())));

			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

			oMultiTransaction.setTxBody(oMultiTransactionBody);
			HashPair hp = transactionHelper.CreateMultiTransaction(oMultiTransaction);

			oRespCreateUnionAccount.setRetCode(1);
			oRespCreateUnionAccount.setRetMsg(hp.getKey());
		} catch (Exception e) {
			e.printStackTrace();
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateUnionAccount.build()));
		return;
	}
}
