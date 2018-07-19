package org.brewchain.account.transaction;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.Tximpl.MultiTransactionBodyImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionInputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionOutputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionSignatureImpl;
import org.brewchain.account.gens.Tximpl.PTXTCommand;
import org.brewchain.account.gens.Tximpl.PTXTModule;
import org.brewchain.account.gens.Tximpl.ReqCreateMultiTransaction;
import org.brewchain.account.gens.Tximpl.ReqCreateTxColdPurse;
import org.brewchain.account.gens.Tximpl.RespCreateTransaction;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.apache.commons.lang3.StringUtils;
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
public class SaveColdTransactionImpl extends SessionModules<ReqCreateTxColdPurse> {
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@Override
	public String[] getCmds() {
		return new String[] { PTXTCommand.CTS.name() };
	}

	@Override
	public String getModule() {
		return PTXTModule.TXT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateTxColdPurse pb, final CompleteHandler handler) {
		RespCreateTransaction.Builder oRespCreateTx = RespCreateTransaction.newBuilder();
		
		ReqCreateMultiTransaction.Builder req = ReqCreateMultiTransaction.newBuilder();
		
		try {
			MultiTransaction.Builder oTransaction = parseToImpl(pb);
//			req.setTransaction(oTransaction);
//			MultiTransaction.Builder oTransaction = transactionHelper.parse(req.getTransaction());
			oRespCreateTx.setTxHash(transactionHelper.CreateMultiTransaction(oTransaction));
			oRespCreateTx.setRetCode(1);
		} catch (Throwable e) {
			oRespCreateTx.setRetCode(-1);
			oRespCreateTx.setRetMsg(e.getMessage());
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTx.build()));
	}

	public MultiTransaction.Builder parseToImpl(ReqCreateTxColdPurse pb) throws Exception {

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
//		oMultiTransactionImpl.setTxHash(encApi.hexEnc(oTransaction.getTxHash().toByteArray()));
//		
//		oMultiTransactionImpl.setStatus(StringUtils.isNotBlank(oTransaction.getStatus()) ? oTransaction.getStatus() : "");

		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
//		oMultiTransactionBodyImpl.setData(oMultiTransactionBody.getData().toStringUtf8());
		
//		oMultiTransactionBodyImpl.addDelegate(encApi.hexEnc(delegate.toByteArray()));
		
//		oMultiTransactionBodyImpl.setExdata(oMultiTransactionBody.getExdata().toStringUtf8());
		
		MultiTransactionInput.Builder oMultiTransactionInput = MultiTransactionInput.newBuilder();
		
		
		oMultiTransactionInput.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getInputaddress())));
		oMultiTransactionInput.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getAmount()))));
		oMultiTransactionInput.setCryptoToken(ByteString.EMPTY);
		int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(pb.getInputaddress())));
		oMultiTransactionInput.setNonce(nonce);
		oMultiTransactionInput.setSymbol("");
		oMultiTransactionInput.setToken(pb.getToken());
		oMultiTransactionBody.addInputs(oMultiTransactionInput);
		
		MultiTransactionOutput.Builder oMultiTransactionOutput = MultiTransactionOutput.newBuilder();
		oMultiTransactionOutput.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getOutputaddress())));
		oMultiTransactionOutput.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(pb.getAmount()))));
//		oMultiTransactionOutputImpl.setCryptoToken(encApi.hexEnc(output.getCryptoToken().toByteArray()));
//		oMultiTransactionOutputImpl.setSymbol(output.getSymbol());
		oMultiTransactionBody.addOutputs(oMultiTransactionOutput);
		if(StringUtils.isNotBlank(pb.getToken())){
			oMultiTransactionBody.setType(TransTypeEnum.TYPE_TokenTransaction.value());
		}
		// oMultiTransactionBodyImpl.setSignatures(index, value)
		MultiTransactionSignature.Builder oMultiTransactionSignature = MultiTransactionSignature
				.newBuilder();
		oMultiTransactionSignature.setSignature(ByteString.copyFrom(encApi.hexDec(pb.getSignature())));
		oMultiTransactionBody.addSignatures(oMultiTransactionSignature);
			
		oMultiTransactionBody.setTimestamp(pb.getTimestamp());
		oMultiTransaction.setTxBody(oMultiTransactionBody);
		
		return oMultiTransaction;
	}
}
