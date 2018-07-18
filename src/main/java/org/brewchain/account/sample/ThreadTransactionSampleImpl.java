package org.brewchain.account.sample;

import java.math.BigInteger;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqThreadTransaction;
import org.brewchain.account.gens.TxTest.RespCommonTest;
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
public class ThreadTransactionSampleImpl extends SessionModules<ReqThreadTransaction> {
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

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.MTT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqThreadTransaction pb, final CompleteHandler handler) {
		RespCommonTest.Builder oRespCommonTest = RespCommonTest.newBuilder();
		for (int i = 0; i < pb.getThreads(); i++) {
			ThreadTransaction oThreadTransaction = new ThreadTransaction(transactionHelper, encApi, pb.getDuration(),
					pb.getAddress(i), pb.getPrivKey(i), "");
			oThreadTransaction.start();
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCommonTest.build()));
		return;

	}

	public class ThreadTransaction extends Thread {
		private final TransactionHelper th;
		private final EncAPI encApi;
		private final int duration;
		private final String address;
		private final String privKey;
		private final String pubKey;

		public ThreadTransaction(TransactionHelper transactionHelper, EncAPI enc, int duration, String address,
				String privKey, String pubKey) {
			this.th = transactionHelper;
			this.encApi = enc;
			this.duration = duration;
			this.address = address;
			this.privKey = privKey;
			this.pubKey = pubKey;
		}

		@Override
		public void run() {
			final Timer timer = new Timer();
			// 设定定时任务
			timer.schedule(new TimerTask() {
				// 定时任务执行方法
				@Override
				public void run() {
					try {
						// KeyPairs oFrom = encApi.genKeys();
						KeyPairs oTo = encApi.genKeys();

						accountHelper.addBalance(ByteString.copyFrom(encApi.hexDec(address)), new BigInteger("100"));

						MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
						MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();

						MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
						oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(address)));
						oMultiTransactionInput4
								.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger("2"))));
						int nonce = 0;
						nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(address)));
						// nonce = nonce + i - 1;
						oMultiTransactionInput4.setNonce(nonce);
						oMultiTransactionBody.addInputs(oMultiTransactionInput4);

						MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
						oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(oTo.getAddress())));
						oMultiTransactionOutput1
								.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger("2"))));
						oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

						// oMultiTransactionBody.setData(pb.getData());
						oMultiTransaction.clearTxHash();
						oMultiTransactionBody.clearSignatures();
						oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
						// 签名
						MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature
								.newBuilder();
						oMultiTransactionSignature21.setSignature(ByteString
								.copyFrom(encApi.ecSign(privKey, oMultiTransactionBody.build().toByteArray())));
						oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

						oMultiTransaction.setTxBody(oMultiTransactionBody);
						String txHash = th.CreateMultiTransaction(oMultiTransaction);
						log.debug("Thread Transaction Test ==> txHash::" + txHash + " form::" + address + " nonce::" + nonce + " to::"
								+ oTo.getAddress());
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}, 0, this.duration);
		}
	}
}
