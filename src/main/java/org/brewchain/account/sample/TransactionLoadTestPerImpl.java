package org.brewchain.account.sample;

import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCreateTransactionTest;
import org.brewchain.account.gens.TxTest.ReqTransactionAccount;
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
public class TransactionLoadTestPerImpl extends SessionModules<ReqCreateTransactionTest> {
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

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.LTP.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateTransactionTest pb, final CompleteHandler handler) {
		RespCreateTransactionTest.Builder oRespCreateTransactionTest = RespCreateTransactionTest.newBuilder();
		// int total = Math.max(Math.max(Math.max(pb.getContractCall(),
		// pb.getContractTx()), pb.getDefaultTx()),
		// pb.getErc20Tx());
		if(pb.getDefaultTx()<=0){
			transactionLoadTestStore.clear();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				KeyPairs[] froms=parallGenKeys(pb.getDefaultTx());
				KeyPairs[] tos=parallGenKeys(pb.getDefaultTx());
				for (int i = 0; i < pb.getDefaultTx(); i++) {
					addDefaultTx(froms[i],tos[i]);
				}
			}
		}).start();

		new Thread(new Runnable() {
			@Override
			public void run() {
				KeyPairs[] froms=parallGenKeys(pb.getDefaultTx());
				KeyPairs[] tos=parallGenKeys(pb.getDefaultTx());

				for (int i = 0; i < pb.getErc20Tx(); i++) {
					addErc20Tx(pb.getErc20TxToken(),froms[i],tos[i]);
				}
			}
		}).start();

		new Thread(new Runnable() {
			@Override
			public void run() {
				KeyPairs[] froms=parallGenKeys(pb.getDefaultTx());

				for (int i = 0; i < pb.getContractCall(); i++) {
					addCallContractTx(pb.getContractCallAddress(),froms[i]);
				}
			}
		}).start();
		new Thread(new Runnable() {
			@Override
			public void run() {
				KeyPairs[] froms=parallGenKeys(pb.getDefaultTx());
				for (int i = 0; i < pb.getContractTx(); i++) {
					addContractTx(pb.getContractTxAddress(),froms[i]);
				}
			}
		}).start();
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateTransactionTest.build()));
		return;
	}

	private void addContractTx(String contract,KeyPairs oFrom ) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		try {
//			KeyPairs oFrom = encApi.genKeys();
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			// nonce = nonce + i - 1;
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);
			oMultiTransactionBody.setData(ByteString.copyFrom(
					encApi.hexDec("040821fc0000000000000000000000000000000000000000000000000000000000000000")));
			MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(contract)));

			oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			oMultiTransactionBody.setType(TransTypeEnum.TYPE_CallContract.value());
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
			// 签名
			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(oFrom.getPrikey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

			oMultiTransaction.setTxBody(oMultiTransactionBody);
			transactionLoadTestStore.getLoads().add(oMultiTransaction);
		} catch (Exception e) {
		}
	}

	private void addCallContractTx(String contract,KeyPairs oFrom) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		try {
//			KeyPairs oFrom = encApi.genKeys();
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			// nonce = nonce + i - 1;
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);
			oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec("67e0badb")));
			MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(contract)));

			oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			oMultiTransactionBody.setType(TransTypeEnum.TYPE_CallContract.value());
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
			// 签名
			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(oFrom.getPrikey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

			oMultiTransaction.setTxBody(oMultiTransactionBody);
			transactionLoadTestStore.getLoads().add(oMultiTransaction);
		} catch (Exception e) {
		}
	}

	private void addErc20Tx(String token,KeyPairs oFrom,KeyPairs oTo) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		try {

//			KeyPairs oFrom = encApi.genKeys();
//			KeyPairs oTo = encApi.genKeys();
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			oMultiTransactionInput4.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			// nonce = nonce + i - 1;
			oMultiTransactionInput4.setNonce(nonce);
			oMultiTransactionInput4.setToken(token);
			oMultiTransactionBody.addInputs(oMultiTransactionInput4);

			MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(oTo.getAddress())));
			oMultiTransactionOutput1.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);
			oMultiTransaction.clearTxHash();
			oMultiTransactionBody.clearSignatures();
			oMultiTransactionBody.setTimestamp(System.currentTimeMillis());
			oMultiTransactionBody.setType(TransTypeEnum.TYPE_TokenTransaction.value());

			// 签名
			MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature21.setSignature(
					ByteString.copyFrom(encApi.ecSign(oFrom.getPrikey(), oMultiTransactionBody.build().toByteArray())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
			oMultiTransaction.setTxBody(oMultiTransactionBody);
			transactionLoadTestStore.getLoads().add(oMultiTransaction);
		} catch (Exception e) {
		}
	}

	ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

	public KeyPairs[] parallGenKeys(int size) {
		final AtomicInteger i = new AtomicInteger(-1);
		final KeyPairs[] ret = new KeyPairs[size];
		final CountDownLatch cdl = new CountDownLatch(size);
		pool.execute(new Runnable() {
			@Override
			public void run() {
				ret[i.incrementAndGet()] = encApi.genKeys();
				cdl.countDown();
			}
		});
		try {
			cdl.await(24, TimeUnit.HOURS);
		} catch (InterruptedException e) {
		}
		return ret;
	}

	private void addDefaultTx(KeyPairs oFrom,KeyPairs oTo) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		try {

//			KeyPairs oFrom = encApi.genKeys();
//			KeyPairs oTo = encApi.genKeys();
			MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
			oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
			oMultiTransactionInput4.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(BigInteger.ZERO)));
			int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(oFrom.getAddress())));
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
			transactionLoadTestStore.getLoads().add(oMultiTransaction);
		} catch (Exception e) {
		}
	}
}