package org.brewchain.account.sample;

import java.math.BigInteger;

import org.brewchain.ecrypto.impl.EncInstance;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.brewchain.rcvm.utils.ByteUtil;
import org.fc.brewchain.bcapi.KeyPairs;
import com.google.protobuf.ByteString;

public class ProtoTest {

//	public static void main(String[] args) {
//		long count = 0;
//		EncInstance encAPI = new EncInstance();
//		encAPI.startup();
//		while (true) {
//			count += 1;
//			addDefaultTx(encAPI);
//			System.out.println(count);
//		}
//	}

	private static MultiTransaction.Builder addDefaultTx(EncInstance encApi) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		try {

			KeyPairs oFrom = encApi.genKeys();
			KeyPairs oTo = encApi.genKeys();
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			oMultiTransactionInput4.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			int nonce = 0;
			// nonce = nonce + i - 1;
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);

			MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(oTo.getAddress())));
			oMultiTransactionOutput1.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			// oMultiTransactionBody.setTimestamp(System.currentTimeMillis());

			// 签名
			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(oFrom.getPrikey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
			oMultiTransaction.setTxBody(oMultiTransactionBody);
			return oMultiTransaction;
		} catch (Exception e) {
		}
		return null;
	}
}
