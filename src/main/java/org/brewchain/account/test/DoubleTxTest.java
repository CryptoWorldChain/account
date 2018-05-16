package org.brewchain.account.test;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.Tx.SingleTransaction;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.brewchain.account.util.ByteUtil;
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
public class DoubleTxTest extends SessionModules<ReqTxTest> implements ActorService {
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
		return new String[] { PTSTCommand.DTT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();

		// 创建账户1
		KeyPairs oKeyPairs1 = encApi.genKeys();
		// 创建账户1
		accountHelper.CreateAccount(encApi.hexDec(oKeyPairs1.getAddress()), oKeyPairs1.getPubkey().getBytes());
		// 增加账户余额1
		try {
			accountHelper.addBalance(encApi.hexDec(oKeyPairs1.getAddress()), 100);
			log.debug(String.format("创建账户1::%s Balance::100", oKeyPairs1.getAddress()));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// 创建账户2
		KeyPairs oKeyPairs2 = encApi.genKeys();
		// 创建账户2
		accountHelper.CreateAccount(encApi.hexDec(oKeyPairs2.getAddress()), oKeyPairs2.getPubkey().getBytes());
		log.debug(String.format("创建账户2::%s Balance::0", oKeyPairs2.getAddress()));

		int count = 1;

		while (count <= 2) {
			try {
				// 随意抽取两个用户，相互之间发送随机大小的交易

				int nonce = accountHelper.getNonce(encApi.hexDec(oKeyPairs1.getAddress()));

				MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
				MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
				MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
				oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oKeyPairs1.getAddress())));
				oMultiTransactionInput4.setAmount(80);
				oMultiTransactionInput4.setFee(0);
				oMultiTransactionInput4.setFeeLimit(0);
				oMultiTransactionInput4.setNonce(nonce);
				oMultiTransactionBody.addInputs(oMultiTransactionInput4);

				MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
				oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(oKeyPairs2.getAddress())));
				oMultiTransactionOutput1.setAmount(80);
				oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

				// MultiTransactionOutput.Builder oMultiTransactionOutput =
				// MultiTransactionOutput.newBuilder();
				oMultiTransactionBody.setData(ByteString.EMPTY);
				oMultiTransaction.setTxHash(ByteString.EMPTY);
				oMultiTransactionBody.clearSignatures();

				oMultiTransactionBody.setTimestamp((new Date()).getTime());
				// 签名
				MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
				oMultiTransactionSignature21.setPubKey(oKeyPairs1.getPubkey());
				oMultiTransactionSignature21.setSignature(encApi
						.hexEnc(encApi.ecSign(oKeyPairs1.getPrikey(), oMultiTransactionBody.build().toByteArray())));
				oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

				oMultiTransaction.setTxBody(oMultiTransactionBody);
				transactionHelper.CreateMultiTransaction(oMultiTransaction);
				// log.debug(String.format("=====> 创建交易 %s 次数 %s 金额 %s 累计执行 %s",
				// oKeyPairs1.getAddress(), nonce, rAmount, count));
			} catch (Exception e) {
				e.printStackTrace();
			}
			count += 1;
		}
		
		BlockEntity.Builder oSyncBlock = BlockEntity.newBuilder();
		BlockEntity.Builder newBlock;
		try {
			newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY,
					ByteString.copyFromUtf8("12345").toByteArray());
			oSyncBlock.setHeader(newBlock.getHeader());
			//oSyncBlock.setBody(newBlock.getBody());
			log.debug(String.format("==> 第 %s 块 hash %s 创建成功", oSyncBlock.getHeader().getNumber(),
					encApi.hexEnc(oSyncBlock.getHeader().getBlockHash().toByteArray())));
			blockHelper.ApplyBlock(oSyncBlock.build());
			log.debug(String.format("==> 第 %s 块 hash %s 父hash %s 交易 %s 笔", oSyncBlock.getHeader().getNumber(),
					encApi.hexEnc(oSyncBlock.getHeader().getBlockHash().toByteArray()),
					encApi.hexEnc(oSyncBlock.getHeader().getParentHash().toByteArray()),
					oSyncBlock.getHeader().getTxHashsCount()));
			count += 1;
			
			for (ByteString oByteString : oSyncBlock.getHeader().getTxHashsList()) {
				log.debug(String.format("交易执行结果 %s", transactionHelper.GetTransaction(oByteString.toByteArray()).getStatus()));
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			log.debug(String.format("执行 %s 区块异常 %s", count, e.getMessage()));
		}
		
		

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}
}
