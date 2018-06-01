package org.brewchain.account.test;

import java.util.Date;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.brewchain.account.trie.StateTrie;
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
public class BlockRollbackTest extends SessionModules<ReqTxTest> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "State_Trie", scope = "global")
	StateTrie stateTrie;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.BRT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();
		try {
			stateTrie.setRoot(null);
			// 创建账户1
			KeyPairs oKeyPairs1 = encApi.genKeys();
			// 创建账户1
			accountHelper.CreateAccount(encApi.hexDec(oKeyPairs1.getAddress()), oKeyPairs1.getPubkey().getBytes());
			// 增加账户余额1
			try {
				accountHelper.addBalance(encApi.hexDec(oKeyPairs1.getAddress()), 10000);
				log.debug(String.format("创建账户1::%s Balance::10000", oKeyPairs1.getAddress()));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// 创建账户2
			KeyPairs oKeyPairs2 = encApi.genKeys();
			// 创建账户2
			accountHelper.CreateAccount(encApi.hexDec(oKeyPairs2.getAddress()), oKeyPairs2.getPubkey().getBytes());
			// 增加账户余额2
			try {
				accountHelper.addBalance(encApi.hexDec(oKeyPairs2.getAddress()), 10000);
				log.debug(String.format("创建账户2::%s Balance::10000", oKeyPairs2.getAddress()));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// 创建账户3
			KeyPairs oKeyPairs3 = encApi.genKeys();
			// 创建账户3
			accountHelper.CreateAccount(encApi.hexDec(oKeyPairs3.getAddress()), oKeyPairs3.getPubkey().getBytes());
			// 增加账户余额2
			try {
				accountHelper.addBalance(encApi.hexDec(oKeyPairs3.getAddress()), 10000);
				log.debug(String.format("创建账户3::%s Balance::10000", oKeyPairs3.getAddress()));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
			
			// 1. 1 -> 2 400
			MultiTransaction.Builder oMultiTransaction1 = createTransaction(oKeyPairs1, oKeyPairs2, 400);
			transactionHelper.CreateMultiTransaction(oMultiTransaction1);

			// 2. make block a, print account
			BlockEntity blockA = makeBlock();
			log.debug("make block " + blockA.getHeader().getNumber());
			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
			log.debug("rootA " + encApi.hexEnc(blockA.getHeader().getStateRoot().toByteArray()));
			
			// 3. 2 -> 3 300
			MultiTransaction.Builder oMultiTransaction2 = createTransaction(oKeyPairs2, oKeyPairs3, 300);
			transactionHelper.CreateMultiTransaction(oMultiTransaction2);

			// 4. make block b, print account
			BlockEntity blockB = makeBlock();
			log.debug("make block " + blockB.getHeader().getNumber());
			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
			log.debug("rootB " + encApi.hexEnc(blockB.getHeader().getStateRoot().toByteArray()));


			// 5. 3 -> 1 900
			MultiTransaction.Builder oMultiTransaction3 = createTransaction(oKeyPairs3, oKeyPairs1, 900);
			transactionHelper.CreateMultiTransaction(oMultiTransaction3);

			// 6. make block c, print account
			BlockEntity blockC = makeBlock();
			log.debug("make block " + blockC.getHeader().getNumber());
			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
			log.debug("rootC " + encApi.hexEnc(blockC.getHeader().getStateRoot().toByteArray()));

			// 7. back to block b, print account
			blockChainHelper.rollBackTo(blockB);
			log.debug("roll back to rootB " + encApi.hexEnc(stateTrie.getRootHash()));

			log.debug("roll back to " + blockB.getHeader().getNumber());
			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
//
//			// 8. back to block a, print account
//			blockChainHelper.rollBackTo(blockA);
//			log.debug("roll back to rootB " + encApi.hexEnc(stateTrie.getRootHash()));
//
//			log.debug("roll back to " + blockA.getHeader().getNumber());
//			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));

			// 9. 1 -> 3 2345
			MultiTransaction.Builder oMultiTransaction4 = createTransaction(oKeyPairs1, oKeyPairs3, 2345);
			transactionHelper.CreateMultiTransaction(oMultiTransaction4);

			// 10. make block d, print account
			BlockEntity blockD = makeBlock();
			log.debug("make block " + blockD.getHeader().getNumber());
			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));

		} catch (Exception e) {
			e.printStackTrace();
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}

	private MultiTransaction.Builder createTransaction(KeyPairs from, KeyPairs to, int amount) throws Exception {
		int nonce = accountHelper.getNonce(encApi.hexDec(from.getAddress()));

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		MultiTransactionInput.Builder oMultiTransactionInput4 = MultiTransactionInput.newBuilder();
		oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(from.getAddress())));
		oMultiTransactionInput4.setAmount(amount);
		oMultiTransactionInput4.setFee(0);
		oMultiTransactionInput4.setFeeLimit(0);
		oMultiTransactionInput4.setNonce(nonce);
		oMultiTransactionBody.addInputs(oMultiTransactionInput4);

		MultiTransactionOutput.Builder oMultiTransactionOutput1 = MultiTransactionOutput.newBuilder();
		oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(to.getAddress())));
		oMultiTransactionOutput1.setAmount(amount);
		oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);

		oMultiTransactionBody.setData(ByteString.EMPTY);
		oMultiTransaction.setTxHash(ByteString.EMPTY);
		oMultiTransactionBody.clearSignatures();

		oMultiTransactionBody.setTimestamp((new Date()).getTime());
		// 签名
		MultiTransactionSignature.Builder oMultiTransactionSignature21 = MultiTransactionSignature.newBuilder();
		oMultiTransactionSignature21.setPubKey(from.getPubkey());
		oMultiTransactionSignature21.setSignature(
				encApi.hexEnc(encApi.ecSign(from.getPrikey(), oMultiTransactionBody.build().toByteArray())));
		oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);

		oMultiTransaction.setTxBody(oMultiTransactionBody);

		return oMultiTransaction;
	}

	private void printAccount(byte[] addr) throws Exception {
		log.debug(String.format("%s %s", accountHelper.getBalance(addr), Hex.toHexString(addr)));
	}

	private BlockEntity makeBlock() throws Exception {
		BlockEntity.Builder oSyncBlock = BlockEntity.newBuilder();
		BlockEntity.Builder newBlock;
		newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY, ByteUtil.EMPTY_BYTE_ARRAY);
		return newBlock.build();
	}
}
