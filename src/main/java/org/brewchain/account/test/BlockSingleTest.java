package org.brewchain.account.test;

import java.util.Date;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
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
public class BlockSingleTest extends SessionModules<ReqTxTest> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.BST.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();

		try {
			// 创建账户1
			KeyPairs oKeyPairs1 = encApi.genKeys();
			// 创建账户2
			KeyPairs oKeyPairs2 = encApi.genKeys();
			// 创建账户3
			KeyPairs oKeyPairs3 = encApi.genKeys();

			// 账户1 转账 100000
			MultiTransaction.Builder oMultiTransaction01 = createTransaction(
					encApi.hexDec("307e3c985f1361488a314c47584f22406a60f9df1f"),
					"e3a6aa61d1dd844899c14664921f506c0ffb7172cea1cd6a5ccdf4a99dd6cabb",
					"040fdd87b30a77db34f5dbec226cd7b528a7dddc64680da1394ddf8b1b47b4df029b556297e0b39aa5aae038b847786a22b0fbda7bdbea6f2287c625081cb92f23",
					encApi.hexDec(oKeyPairs1.getAddress()), 100000);
			transactionHelper.CreateMultiTransaction(oMultiTransaction01);
		} catch (Exception e) {
			e.printStackTrace();
		}

		oRespTxTest.setRetCode(-1);
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}

	private MultiTransaction.Builder createTransaction(byte[] from, String priv, String pub, byte[] to, int amount)
			throws Exception {
		int nonce = accountHelper.getNonce(from);

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
		oMultiTransactionInput4.setAddress(ByteString.copyFrom(from));
		oMultiTransactionInput4.setAmount(amount);
		oMultiTransactionInput4.setFee(0);
		oMultiTransactionInput4.setFeeLimit(0);
		oMultiTransactionInput4.setNonce(nonce);
		oMultiTransactionBody.addInputs(oMultiTransactionInput4);

		MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
		oMultiTransactionOutput1.setAddress(ByteString.copyFrom(to));
		oMultiTransactionOutput1.setAmount(amount);
		oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

		oMultiTransactionBody.setData(ByteString.EMPTY);
		oMultiTransaction.setTxHash(ByteString.EMPTY);
		oMultiTransactionBody.clearSignatures();

		oMultiTransactionBody.setTimestamp((new Date()).getTime());
		// 签名
		MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
		oMultiTransactionSignature21.setPubKey(pub);
		oMultiTransactionSignature21
				.setSignature(encApi.hexEnc(encApi.ecSign(priv, oMultiTransactionBody.build().toByteArray())));
		oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

		oMultiTransaction.setTxBody(oMultiTransactionBody);

		return oMultiTransaction;
	}
}
