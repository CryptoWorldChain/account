package org.brewchain.account.transaction;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.Tximpl.PTXTCommand;
import org.brewchain.account.gens.Tximpl.PTXTModule;
import org.brewchain.account.gens.Tximpl.ReqCreateContractTransaction;
import org.brewchain.account.gens.Tximpl.RespCreateContractTransaction;
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
public class SendCreateContractTransactionImpl extends SessionModules<ReqCreateContractTransaction> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTXTCommand.SCT.name() };
	}

	@Override
	public String getModule() {
		return PTXTModule.TXT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateContractTransaction pb,
			final CompleteHandler handler) {
		RespCreateContractTransaction.Builder oRespCreateContractTransaction = RespCreateContractTransaction
				.newBuilder();

		try {
			MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();

			MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
			oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(pb.getData())));
			for (String delegate : pb.getDelegateList()) {
				oMultiTransactionBody.addDelegate(ByteString.copyFrom(encApi.hexDec(delegate)));
			}
			oMultiTransactionBody.setExdata(ByteString.copyFrom(encApi.hexDec(pb.getExdata())));

			MultiTransactionInput.Builder oMultiTransactionInput = MultiTransactionInput.newBuilder();
			oMultiTransactionInput.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getInput().getAddress())));
			oMultiTransactionInput.setAmount(pb.getInput().getAmount());
			oMultiTransactionInput.setCryptoToken(ByteString.copyFrom(encApi.hexDec(pb.getInput().getCryptoToken())));
			oMultiTransactionInput.setFee(pb.getInput().getFee());
			oMultiTransactionInput.setNonce(pb.getInput().getNonce());
			oMultiTransactionInput.setPubKey(pb.getInput().getPubKey());
			oMultiTransactionInput.setSymbol(pb.getInput().getSymbol());
			oMultiTransactionInput.setToken(pb.getInput().getToken());
			oMultiTransactionBody.addInputs(oMultiTransactionInput);
			MultiTransactionSignature.Builder oMultiTransactionSignature = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature.setPubKey(pb.getSignature().getPubKey());
			oMultiTransactionSignature.setSignature(pb.getSignature().getSignature());
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature);
			oMultiTransactionBody.setTimestamp(pb.getTimestamp());
			oMultiTransaction.setTxBody(oMultiTransactionBody);
			oMultiTransaction.setTxHash(ByteString.EMPTY);

			oRespCreateContractTransaction.setContractAddress(
					encApi.hexEnc(transactionHelper.getContractAddressByTransaction(oMultiTransaction.build())));
			oRespCreateContractTransaction.setTxHash(
					encApi.hexEnc(transactionHelper.CreateMultiTransaction(oMultiTransaction).toByteArray()));
		} catch (Exception e) {
			oRespCreateContractTransaction.clear();
			oRespCreateContractTransaction.setRetCode(-1);
			log.error("create contract error. the transaction can not send::" + e.getMessage());
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateContractTransaction.build()));
	}
}
