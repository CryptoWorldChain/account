package org.brewchain.account.test;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.Tx.SingleTransaction;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
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
public class MultiSignatureTest extends SessionModules<ReqTxTest> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.MOT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();

		blockHelper.CreateGenesisBlock(new LinkedList<MultiTransaction>(), ByteUtil.EMPTY_BYTE_ARRAY);

		// 创建账户1
		KeyPairs oKeyPairs1 = encApi.genKeys();
		// 创建账户1
		accountHelper.CreateAccount(oKeyPairs1.getAddress().getBytes(), oKeyPairs1.getPubkey().getBytes());
		// 增加账户余额1
		try {
			accountHelper.addBalance(oKeyPairs1.getAddress().getBytes(), 10000000);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// 创建账户2
		KeyPairs oKeyPairs2 = encApi.genKeys();
		// 创建账户2
		accountHelper.CreateAccount(oKeyPairs2.getAddress().getBytes(), oKeyPairs2.getPubkey().getBytes());
		// 增加账户余额2
		try {
			accountHelper.addBalance(oKeyPairs2.getAddress().getBytes(), 10000000);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// 创建账户3
		KeyPairs oKeyPairs3 = encApi.genKeys();
		// 创建账户3
		accountHelper.CreateAccount(oKeyPairs3.getAddress().getBytes(), oKeyPairs3.getPubkey().getBytes());
		// 增加账户余额3
		try {
			accountHelper.addBalance(oKeyPairs3.getAddress().getBytes(), 10000000);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// 创建账户4
		KeyPairs oKeyPairs4 = encApi.genKeys();
		// 创建账户4
		accountHelper.CreateAccount(oKeyPairs4.getAddress().getBytes(), oKeyPairs4.getPubkey().getBytes());
		// 增加账户余额4
		try {
			accountHelper.addBalance(oKeyPairs4.getAddress().getBytes(), 10000000);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// 创建多重签名账户
		KeyPairs oKeyPairs5 = encApi.genKeys();
		byte[] mAddress = ByteString.copyFromUtf8(oKeyPairs5.getAddress()).toByteArray();
		List<ByteString> relAddress = new ArrayList<ByteString>();
		relAddress.add(ByteString.copyFromUtf8(oKeyPairs1.getAddress()));
		relAddress.add(ByteString.copyFromUtf8(oKeyPairs2.getAddress()));
		relAddress.add(ByteString.copyFromUtf8(oKeyPairs3.getAddress()));

		// 创建多重签名账户
		Account oAccount = accountHelper.CreateUnionAccount(mAddress, ByteUtil.EMPTY_BYTE_ARRAY, 100000, 100, 2,
				relAddress);

		// 发送多重签名账户创建交易并转账
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionInput.Builder oMultiTransactionInput1 = MultiTransactionInput.newBuilder();
		oMultiTransactionInput1.setAddress(ByteString.copyFrom(oKeyPairs1.getAddress().getBytes()));
		oMultiTransactionInput1.setAmount(16);
		oMultiTransactionInput1.setFee(0);
		oMultiTransactionInput1.setFeeLimit(0);
		oMultiTransactionInput1.setNonce(0);
		oMultiTransaction.addInputs(oMultiTransactionInput1);

		MultiTransactionInput.Builder oMultiTransactionInput2 = MultiTransactionInput.newBuilder();
		oMultiTransactionInput2.setAddress(ByteString.copyFrom(oKeyPairs2.getAddress().getBytes()));
		oMultiTransactionInput2.setAmount(17);
		oMultiTransactionInput2.setFee(0);
		oMultiTransactionInput2.setFeeLimit(0);
		oMultiTransactionInput2.setNonce(0);
		oMultiTransaction.addInputs(oMultiTransactionInput2);

		MultiTransactionInput.Builder oMultiTransactionInput3 = MultiTransactionInput.newBuilder();
		oMultiTransactionInput3.setAddress(ByteString.copyFrom(oKeyPairs3.getAddress().getBytes()));
		oMultiTransactionInput3.setAmount(18);
		oMultiTransactionInput3.setFee(0);
		oMultiTransactionInput3.setFeeLimit(0);
		oMultiTransactionInput3.setNonce(0);
		oMultiTransaction.addInputs(oMultiTransactionInput3);

		MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
		oMultiTransactionOutput1.setAddress(ByteString.copyFrom(mAddress));
		oMultiTransactionOutput1.setAmount(51);
		oMultiTransaction.addOutputs(oMultiTransactionOutput1);

		oMultiTransaction.setData(ByteString.copyFromUtf8("01"));
		oMultiTransaction.setExdata(oAccount.toByteString());
		oMultiTransaction.setTxHash(ByteString.EMPTY);
		oMultiTransaction.clearSignatures();

		// 签名
		MultiTransactionSignature.Builder oMultiTransactionSignature1 = MultiTransactionSignature.newBuilder();
		oMultiTransactionSignature1.setPubKey(oKeyPairs1.getPubkey());
		oMultiTransactionSignature1.setSignature(
				encApi.hexEnc(encApi.ecSign(oKeyPairs1.getPrikey(), oMultiTransaction.build().toByteArray())));
		oMultiTransaction.addSignatures(oMultiTransactionSignature1);

		MultiTransactionSignature.Builder oMultiTransactionSignature2 = MultiTransactionSignature.newBuilder();
		oMultiTransactionSignature1.setPubKey(oKeyPairs2.getPubkey());
		oMultiTransactionSignature1.setSignature(
				encApi.hexEnc(encApi.ecSign(oKeyPairs2.getPrikey(), oMultiTransaction.build().toByteArray())));
		oMultiTransaction.addSignatures(oMultiTransactionSignature2);

		MultiTransactionSignature.Builder oMultiTransactionSignature3 = MultiTransactionSignature.newBuilder();
		oMultiTransactionSignature1.setPubKey(oKeyPairs3.getPubkey());
		oMultiTransactionSignature1.setSignature(
				encApi.hexEnc(encApi.ecSign(oKeyPairs3.getPrikey(), oMultiTransaction.build().toByteArray())));
		oMultiTransaction.addSignatures(oMultiTransactionSignature3);

		try {
			// 测试其他节点，删除多重签名账户
			log.debug("多重签名账户信息：" + accountHelper.GetAccount(mAddress));
			accountHelper.DeleteAccount(mAddress);
			log.debug("多重签名账户已删除：" + accountHelper.GetAccount(mAddress));
			transactionHelper.CreateMultiTransaction(oMultiTransaction);
			log.debug("多重签名账户已创建：" + accountHelper.GetAccount(mAddress));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		BlockEntity.Builder oSyncBlock = BlockEntity.newBuilder();
		BlockEntity.Builder newBlock;
		try {
			newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY);
			log.debug("创建区块 " + newBlock.toString());
			oSyncBlock.setHeader(newBlock.getHeader());
			blockHelper.ApplyBlock(oSyncBlock.build());
			log.debug("block已同步");
			log.debug("多重签名账户信息：" + accountHelper.GetAccount(mAddress));
		} catch (Exception e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		//
		// // 创建多重签名交易
		// MultiTransaction.Builder oMultiTransaction2 =
		// MultiTransaction.newBuilder();
		// MultiTransactionInput.Builder oMultiTransactionInput4 =
		// MultiTransactionInput.newBuilder();
		// oMultiTransactionInput4.setAddress(ByteString.copyFrom(mAddress));
		// oMultiTransactionInput4.setAmount(12);
		// oMultiTransactionInput4.setFee(0);
		// oMultiTransactionInput4.setFeeLimit(0);
		// oMultiTransactionInput4.setNonce(0);
		// oMultiTransaction2.addInputs(oMultiTransactionInput4);
		//
		// // MultiTransactionOutput.Builder oMultiTransactionOutput =
		// // MultiTransactionOutput.newBuilder();
		// oMultiTransaction2.setData(ByteString.copyFromUtf8("01"));
		// oMultiTransaction2.setExdata(oAccount.toByteString());
		// oMultiTransaction2.setTxHash(ByteString.EMPTY);
		// oMultiTransaction2.clearSignatures();
		//
		// // 签名
		// MultiTransactionSignature.Builder oMultiTransactionSignature21 =
		// MultiTransactionSignature.newBuilder();
		// oMultiTransactionSignature21.setPubKey(oKeyPairs1.getPubkey());
		// oMultiTransactionSignature21.setSignature(
		// encApi.hexEnc(encApi.ecSign(oKeyPairs1.getPrikey(),
		// oMultiTransaction.build().toByteArray())));
		// oMultiTransaction2.addSignatures(oMultiTransactionSignature21);
		//
		// MultiTransactionSignature.Builder oMultiTransactionSignature22 =
		// MultiTransactionSignature.newBuilder();
		// oMultiTransactionSignature22.setPubKey(oKeyPairs2.getPubkey());
		// oMultiTransactionSignature22.setSignature(
		// encApi.hexEnc(encApi.ecSign(oKeyPairs2.getPrikey(),
		// oMultiTransaction.build().toByteArray())));
		// oMultiTransaction2.addSignatures(oMultiTransactionSignature22);
		//
		// MultiTransactionSignature.Builder oMultiTransactionSignature23 =
		// MultiTransactionSignature.newBuilder();
		// oMultiTransactionSignature23.setPubKey(oKeyPairs3.getPubkey());
		// oMultiTransactionSignature23.setSignature(
		// encApi.hexEnc(encApi.ecSign(oKeyPairs3.getPrikey(),
		// oMultiTransaction.build().toByteArray())));
		// oMultiTransaction2.addSignatures(oMultiTransactionSignature23);

		oRespTxTest.setRetCode(-1);
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}
}