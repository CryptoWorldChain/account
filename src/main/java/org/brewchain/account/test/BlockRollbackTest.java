package org.brewchain.account.test;

import java.util.Date;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
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
	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;

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
					encApi.hexDec("30f1f5cf57025aaa1d963f87ad4d27a6b91e73150e"),
					"0e0c5364252eea19849599c4ed6b9924c1f603fde960b1eeb07a74324bc38342",
					"0434013e934992250fb03362fdca2b908a4ea110f3cc848232a4e8fdb51f1f23aecc7c60a03171e0a972958d183158279321fbef22151b4642f6afef2ccaf16865",
					encApi.hexDec(oKeyPairs1.getAddress()), 100);
			transactionHelper.CreateMultiTransaction(oMultiTransaction01);

//			// BlockEntity blockInit1 = makeBlock();
//
//			// 账户2 转账 100000
//			MultiTransaction.Builder oMultiTransaction02 = createTransaction(
//					encApi.hexDec("30a8b66a88fcc62488c7085fcd74efc67e9f1d19fb"),
//					"1c8e87da9c96f2e9c7f22f3733c5d5be96c0f01d1253b95c170ef76c808620ee",
//					"04bb9a76d686724c1c2e79552a24daaa262225dcb98387c914ebf1c98e632fa7b1714201103e2a6d133a19ca25cb2f5959f8f2cb1b30c21cdd8b3fe98b16a0a28d",
//					encApi.hexDec(oKeyPairs2.getAddress()), 100000);
//			transactionHelper.CreateMultiTransaction(oMultiTransaction02);
//
//			// BlockEntity blockInit2 = makeBlock();
//
//			// 账户3 转账 100000
//			MultiTransaction.Builder oMultiTransaction03 = createTransaction(
//					encApi.hexDec("3086d5210d129a551b8f3e26f09c33494ab7e56ddc"),
//					"8275346be609d38274154b5f55d09d0a2f9a07bc6c38183e42f7b1e878d1da5e",
//					"04ceec13ae5fc429f8ca9dfea7a12cc84c778fc54eec6423d05b234fdd874982a5bb9db9f7ecdc5b9cb5afd5470fabccc1a34a0bf81086bd4e646c0bd15a5c9696",
//					encApi.hexDec(oKeyPairs3.getAddress()), 100000);
//			transactionHelper.CreateMultiTransaction(oMultiTransaction03);
//			// BlockEntity blockInit3 = makeBlock();
//			//
//			// printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
//
//			// 1. 1 -> 2 400
//			MultiTransaction.Builder oMultiTransaction1 = createTransaction(oKeyPairs1, oKeyPairs2, 400);
//			transactionHelper.CreateMultiTransaction(oMultiTransaction1);
//
//			// 2. make block a, print account
//			// BlockEntity blockA = makeBlock();
//			// log.debug("make block " + blockA.getHeader().getNumber());
//			// printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
//			// log.debug("rootA " +
//			// encApi.hexEnc(blockA.getHeader().getStateRoot().toByteArray()));
//			// printTrieValue(blockA.getHeader().getStateRoot().toByteArray());
//
//			// 3. 2 -> 3 300
//			MultiTransaction.Builder oMultiTransaction2 = createTransaction(oKeyPairs2, oKeyPairs3, 300);
//			transactionHelper.CreateMultiTransaction(oMultiTransaction2);
//
//			// 4. make block b, print account
//			// BlockEntity blockB = makeBlock();
//			// log.debug("make block " + blockB.getHeader().getNumber());
//			// printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
//			// log.debug("rootB " +
//			// encApi.hexEnc(blockB.getHeader().getStateRoot().toByteArray()));
//			// printTrieValue(blockB.getHeader().getStateRoot().toByteArray());
//
//			int count = 2;
//			while (count >= 0) {
//				// 5. 3 -> 1 900
//				MultiTransaction.Builder oMultiTransaction3 = createTransaction(oKeyPairs3, oKeyPairs1, 900);
//				transactionHelper.CreateMultiTransaction(oMultiTransaction3);
//
//				// 6. make block c, print account
//				// BlockEntity blockC = makeBlock();
//				// log.debug("make block " + blockC.getHeader().getNumber());
//				// printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//				// printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//				// printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
//				// log.debug("rootC" +
//				// encApi.hexEnc(blockC.getHeader().getStateRoot().toByteArray()));
//				// printTrieValue(blockB.getHeader().getStateRoot().toByteArray());
//
//				count -= 1;
//			}
//
//			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
//
//			// 7. back to block b, print account
//			// blockChainHelper.rollbackTo(blockB);
//
//			// printAccount(encApi.hexDec(oKeyPairs1.getAddress()), oRollStateTrie);
//			// printAccount(encApi.hexDec(oKeyPairs2.getAddress()), oRollStateTrie);
//			// printAccount(encApi.hexDec(oKeyPairs3.getAddress()), oRollStateTrie);
//			// printTrieValue(blockB.getHeader().getStateRoot().toByteArray());
//
//			// // 8. back to block a, print account
//			// blockChainHelper.rollBackTo(blockA);
//
//			// log.debug("roll back to " + blockA.getHeader().getNumber());
//			// printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//			// printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
//
//			// 9. 1 -> 3 2345
//			MultiTransaction.Builder oMultiTransaction4 = createTransaction(oKeyPairs1, oKeyPairs3, 2345);
//			transactionHelper.CreateMultiTransaction(oMultiTransaction4);
//
//			// 10. make block d, print account
//			// BlockEntity blockD = makeBlock();
//			// log.debug("make block " + blockD.getHeader().getNumber());
//			printAccount(encApi.hexDec(oKeyPairs1.getAddress()));
//			// a: 100000 - 400 - 2345 = 97255
//			// b: 100000 + 400 = 100400
//			// c: 100000 + 2345 = 102345
//			printAccount(encApi.hexDec(oKeyPairs2.getAddress()));
//			printAccount(encApi.hexDec(oKeyPairs3.getAddress()));
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
		log.debug(String.format("%s %s", accountHelper.getBalance(addr), encApi.hexEnc(addr)));
	}

	private BlockEntity makeBlock() throws Exception {
		BlockEntity.Builder oSyncBlock = BlockEntity.newBuilder();
		BlockEntity.Builder newBlock;
		newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY, ByteUtil.EMPTY_BYTE_ARRAY);
		return newBlock.build();
	}

	private void printTrie(byte[] root) {
		this.stateTrie.setRoot(root);
		log.debug(this.stateTrie.dumpStructure());
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
