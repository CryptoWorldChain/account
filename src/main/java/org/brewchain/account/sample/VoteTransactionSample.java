package org.brewchain.account.sample;

import java.math.BigInteger;

import org.apache.commons.lang3.StringUtils;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqUnionAccountTransaction;
import org.brewchain.account.gens.TxTest.ReqVoteTransaction;
import org.brewchain.account.gens.TxTest.RespCreateUnionAccount;
import org.brewchain.account.gens.TxTest.RespVoteTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.brewchain.evmapi.gens.Tx.SanctionData;
import org.brewchain.rcvm.utils.ByteUtil;
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
public class VoteTransactionSample extends SessionModules<ReqVoteTransaction> {
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
		return new String[] { PTSTCommand.VTS.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqVoteTransaction pb, final CompleteHandler handler) {
		RespVoteTransaction.Builder oRespVoteTransaction = RespVoteTransaction.newBuilder();

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();

		try {
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getAddress())));

			oMultiTransactionInput4
					.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getAmount()))));
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(pb.getAddress())));
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);
			oMultiTransactionBody.setType(TransTypeEnum.TYPE_UnionAccountTransaction.value());
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());

			SanctionData.Builder oSanctionData = SanctionData.newBuilder();
			oSanctionData.setContent(ByteString.copyFromUtf8(pb.getVoteContent()));
			oSanctionData.setEndBlockHeight(pb.getEndHeight());
			oSanctionData.setResult(ByteString.copyFromUtf8(pb.getResult()));
			oMultiTransactionBody.setData(oSanctionData.build().toByteString());

			MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getVoteAddress())));
			oMultiTransactionOutput1
					.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getAmount()))));
			oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(pb.getPrivKey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
			oMultiTransaction.setTxBody(oMultiTransactionBody);

			String txHash = transactionHelper.CreateMultiTransaction(oMultiTransaction).getKey();
			oRespVoteTransaction.setRetMsg(txHash);
			oRespVoteTransaction.setRetCode(1);
		} catch (Exception e) {
			e.printStackTrace();
			oRespVoteTransaction.setRetCode(-1);
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespVoteTransaction.build()));
		return;
	}
}
