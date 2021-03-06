package org.brewchain.account.sample;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.binary.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.processor.V2Processor;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCommonTest;
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

import lombok.AllArgsConstructor;
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
public class TransactionLoadTestExecImpl extends SessionModules<ReqCommonTest> {
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
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;
	@ActorRequire(name = "V2_Processor", scope = "global")
	V2Processor v2Processor;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.LTE.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@AllArgsConstructor
	class LoadKeyPairs {
		KeyPairs kp;
		AtomicInteger nonce = new AtomicInteger(0);
		long lastUpdate = System.currentTimeMillis();
	}

	LinkedBlockingDeque<LoadKeyPairs> kps = new LinkedBlockingDeque<>();
	// ConcurrentHashMap<String, LoadKeyPairs> kps = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, LoadKeyPairs> keystores = new ConcurrentHashMap<>();

	int maxkeys = props().get("org.bc.ttt.maxkeys", 100000);

	public void offerNewAccount(ByteString hexaddress, int nonce) {
		String address = Hex.encodeHexString(hexaddress.toByteArray());
		LoadKeyPairs kp = keystores.get(address);
		if (kp != null) {
			kp.nonce.addAndGet(10);
			kp.lastUpdate = System.currentTimeMillis();
			kps.addLast(kp);
		}
	}

	public LoadKeyPairs popKeyPair() {
		long checktime = System.currentTimeMillis();
		LoadKeyPairs kp = kps.poll();
		if (kp != null) {
			if (checktime - kp.lastUpdate > 30000) {
				return kp;
			} else {
				kps.addLast(kp);
			}
		}
		return null;
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCommonTest pb, final CompleteHandler handler) {
		RespCreateTransactionTest.Builder oRespCreateTransactionTest = RespCreateTransactionTest.newBuilder();
//		if (v2Processor != null) {
//			v2Processor.setLoadTester(this);
//		}
		if (!blockChainConfig.isDev()) {
			oRespCreateTransactionTest.setRetcode(-1);
			handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTransactionTest.build()));
			return;
		}

		String txHash = "";
		try {
			MultiTransaction.Builder tx = transactionLoadTestStore.getOne();
			if (tx != null) {
				// tx.setTxBody(tx.getTxBodyBuilder().setTimestamp(System.currentTimeMillis()));
				txHash = transactionHelper.CreateMultiTransaction(tx).getKey();
				oRespCreateTransactionTest.setRetmsg("success");
				oRespCreateTransactionTest.setTxhash(txHash);
				oRespCreateTransactionTest
						.setFrom(encApi.hexEnc(tx.getTxBody().getInputs(0).getAddress().toByteArray()));
				oRespCreateTransactionTest
						.setTo(encApi.hexEnc(tx.getTxBody().getOutputs(0).getAddress().toByteArray()));

				if (txHash.length() != 64) {
					log.error("wrong txHash::" + txHash);
				}
			} else {
				// KeyPairs from, to;
				// int nonce = 0;
				// if (kps.size() < maxkeys) {
				// // make one
				// from = encApi.genKeys();
				// to = encApi.genKeys();
				// kps.putLast(new LoadKeyPairs(from, 1));
				// kps.putLast(new LoadKeyPairs(to, 0));
				// } else {
				// LoadKeyPairs lfrom = kps.poll();
				// LoadKeyPairs lto = kps.poll();
				// //
				// accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
				// from = lfrom.kp;
				// to = lto.kp;
				// nonce = lfrom.nonce;
				// lfrom.nonce = lfrom.nonce + 1;
				// kps.putLast(lto);
				// kps.putLast(lfrom);
				// }
				LoadKeyPairs fromkp = popKeyPair();
				LoadKeyPairs tokp = popKeyPair();
				KeyPairs from;
				KeyPairs to;
				int nonce = 0;
				if (fromkp != null) {
					from = fromkp.kp;
					nonce = fromkp.nonce.get();
				} else {
					from = encApi.genKeys();
					if (keystores.size() < maxkeys) {
						keystores.putIfAbsent(from.getAddress(), new LoadKeyPairs(from, new AtomicInteger(0), 0));
					}
				}
				if (tokp != null) {
					to = tokp.kp;
				} else {
					to = encApi.genKeys();
					if (keystores.size() < maxkeys) {
						keystores.putIfAbsent(to.getAddress(), new LoadKeyPairs(to,new AtomicInteger(0), 0));
					}
				}

				tx = addDefaultTx(from, to, nonce);
				if (tx != null) {
					txHash = transactionHelper.CreateMultiTransaction(tx).getKey();
					oRespCreateTransactionTest.setRetmsg("success");
					oRespCreateTransactionTest.setTxhash(txHash);
					oRespCreateTransactionTest
							.setFrom(encApi.hexEnc(tx.getTxBody().getInputs(0).getAddress().toByteArray()));
					oRespCreateTransactionTest
							.setTo(encApi.hexEnc(tx.getTxBody().getOutputs(0).getAddress().toByteArray()));
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.error("test fail::", e);
			oRespCreateTransactionTest.clear();
			oRespCreateTransactionTest.setRetmsg("error");
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTransactionTest.build()));
		return;
	}

	private MultiTransaction.Builder addDefaultTx(KeyPairs oFrom, KeyPairs oTo, int nonce) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		try {

			// KeyPairs oFrom = encApi.genKeys();
			// KeyPairs oTo = encApi.genKeys();
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			oMultiTransactionInput4.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			// nonce = nonce + i - 1;
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);

			MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(oTo.getAddress())));
			oMultiTransactionOutput1.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());

			// 签名
			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(oFrom.getPrikey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

			oMultiTransaction.setTxBody(oMultiTransactionBody);
			return oMultiTransaction;
		} catch (Exception e) {
		}
		return null;
	}
}
