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
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OValue;
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
			// 创建账户1
			KeyPairs oKeyPairs1 = encApi.genKeys();
			// 创建账户2
			KeyPairs oKeyPairs2 = encApi.genKeys();
			// 创建账户3
			KeyPairs oKeyPairs3 = encApi.genKeys();

			// 账户1 转账 100000
			MultiTransaction.Builder oMultiTransaction01 = createTransaction(
					encApi.hexDec("30df50dd8aa500f16b7e5fb4b093feaf8ef4c61a74"),
					"f1be077c51dc79cfdaf2fb0234bdd559295efed681481fa1c8eb6b1fa9e817b4",
					"042b9456ca586375346cb6932d6d453c6495b88e07f11a8bcd464d4dc1a2cde5bdaa5a6adb8a8c1b2c261f1515a0916285fe54d81d15aa18eda41bc18a60511003",
					encApi.hexDec(oKeyPairs1.getAddress()), 100000);
			transactionHelper.CreateMultiTransaction(oMultiTransaction01);

			BlockEntity blockInit1 = makeBlock();

			// 账户2 转账 100000
			MultiTransaction.Builder oMultiTransaction02 = createTransaction(
					encApi.hexDec("30df50dd8aa500f16b7e5fb4b093feaf8ef4c61a74"),
					"f1be077c51dc79cfdaf2fb0234bdd559295efed681481fa1c8eb6b1fa9e817b4",
					"042b9456ca586375346cb6932d6d453c6495b88e07f11a8bcd464d4dc1a2cde5bdaa5a6adb8a8c1b2c261f1515a0916285fe54d81d15aa18eda41bc18a60511003",
					encApi.hexDec(oKeyPairs2.getAddress()), 100000);
			transactionHelper.CreateMultiTransaction(oMultiTransaction02);

			BlockEntity blockInit2 = makeBlock();

			// 账户3 转账 100000
			MultiTransaction.Builder oMultiTransaction03 = createTransaction(
					encApi.hexDec("30df50dd8aa500f16b7e5fb4b093feaf8ef4c61a74"),
					"f1be077c51dc79cfdaf2fb0234bdd559295efed681481fa1c8eb6b1fa9e817b4",
					"042b9456ca586375346cb6932d6d453c6495b88e07f11a8bcd464d4dc1a2cde5bdaa5a6adb8a8c1b2c261f1515a0916285fe54d81d15aa18eda41bc18a60511003",
					encApi.hexDec(oKeyPairs3.getAddress()), 100000);
			transactionHelper.CreateMultiTransaction(oMultiTransaction03);
			BlockEntity blockInit3 = makeBlock();

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
			printTrieValue(blockA.getHeader().getStateRoot().toByteArray());

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
			printTrieValue(blockB.getHeader().getStateRoot().toByteArray());

			int count = 2;
			while (count >= 0) {
				// 5. 3 -> 1 900
				MultiTransaction.Builder oMultiTransaction3 = createTransaction(oKeyPairs3, oKeyPairs1, 900);
				transactionHelper.CreateMultiTransaction(oMultiTransaction3);

				// 6. make block c, print account
				BlockEntity blockC = makeBlock();
				log.debug("make block " + blockC.getHeader().getNumber());
				// printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
				// printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
				// printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
				// log.debug("rootC" +
				// encApi.hexEnc(blockC.getHeader().getStateRoot().toByteArray()));
				printTrieValue(blockB.getHeader().getStateRoot().toByteArray());

				count -= 1;
			}

			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));

			// 7. back to block b, print account
			blockChainHelper.rollBackTo(blockB);

			// printAccount(encApi.hexDec(oKeyPairs1.getAddress()), oRollStateTrie);
			// printAccount(encApi.hexDec(oKeyPairs2.getAddress()), oRollStateTrie);
			// printAccount(encApi.hexDec(oKeyPairs3.getAddress()), oRollStateTrie);
			// printTrieValue(blockB.getHeader().getStateRoot().toByteArray());

			// // 8. back to block a, print account
			// blockChainHelper.rollBackTo(blockA);

			// log.debug("roll back to " + blockA.getHeader().getNumber());
			// printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			// printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			// printAccount(encApi.hexDec(oKeyPairs3.getAddress()));

			// 9. 1 -> 3 2345
			MultiTransaction.Builder oMultiTransaction4 = createTransaction(oKeyPairs1, oKeyPairs3, 2345);
			transactionHelper.CreateMultiTransaction(oMultiTransaction4);

			// 10. make block d, print account
			BlockEntity blockD = makeBlock();
			log.debug("make block " + blockD.getHeader().getNumber());
			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
			// a: 100000 - 400 - 2345 = 97255
			// b: 100000 + 400 = 100400
			// c: 100000 + 2345 = 102345
			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}

	private MultiTransaction.Builder createTransaction(KeyPairs from, KeyPairs to, int amount) throws Exception {
		return createTransaction(encApi.hexDec(from.getAddress()), from.getPrikey(), from.getPubkey(),
				encApi.hexDec(to.getAddress()), amount);
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

	private void printAccount(byte[] addr) throws Exception {
		log.debug(String.format("%s %s", accountHelper.getBalance(addr), encApi.hexEnc(addr)));
	}

	private void printAccount(byte[] addr, StateTrie oStateTrie) throws Exception {
		log.debug(String.format("%s %s", accountHelper.getBalance(addr, oStateTrie), encApi.hexEnc(addr)));
	}

	private BlockEntity makeBlock() throws Exception {
		BlockEntity.Builder oSyncBlock = BlockEntity.newBuilder();
		BlockEntity.Builder newBlock;
		newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY, ByteUtil.EMPTY_BYTE_ARRAY);
		return newBlock.build();
	}

	private void printTrie(byte[] root) {
		StateTrie oRollStateTrie = new StateTrie(this.dao, this.encApi);
		oRollStateTrie.setRoot(root);
		log.debug(oRollStateTrie.dumpStructure());
	}

	private void printTrieValue(byte[] root) {
		try {
			OValue oOValue = dao.getAccountDao().get(OEntityBuilder.byteKey2OKey(root)).get();
			if (oOValue == null) {
				log.debug("get trie key::" + encApi.hexEnc(root) + " value::not found ");

			} else {
				log.debug("get trie key:: " + encApi.hexEnc(root) + " value::"
						+ encApi.hexEnc(oOValue.getExtdata().toByteArray()));

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
