package org.brewchain.account.test;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.felix.ipojo.util.Log;
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
import org.brewchain.account.gens.Tx.SingleTransaction;
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
public class DoubleTxTest extends SessionModules<ReqTxTest> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	// @ActorRequire(name = "State_Trie", scope = "global")
	// StateTrie stateTrie;
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

	private static String LONG_STRING = "1234567890abcdefghijklmnopqrstuvwxyzABCEFGHIJKLMNOPQRSTUVWXYZ";

	private static String c = "c-str";
	private static String ca = "ca-str";
	private static String cat = "cat-str";
	private static String dog = "dog-str";
	private static String doge = "doge-str";
	private static String test = "test-str";
	private static String dude = "dude-str";

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();
		// stateTrie.clear();
		// stateTrie.setRoot(null);
		//
		// String ROOT_HASH_BEFORE =
		// "cf1ed2b6c4b6558f70ef0ecf76bfbee96af785cb5d5e7bfc37f9804ad8d0fb56";
		// String ROOT_HASH_AFTER1 =
		// "f586af4a476ba853fca8cea1fbde27cd17d537d18f64269fe09b02aa7fe55a9e";
		// String ROOT_HASH_AFTER2 =
		// "c59fdc16a80b11cc2f7a8b107bb0c954c0d8059e49c760ec3660eea64053ac91";
		//
		// stateTrie.put(c.getBytes(), LONG_STRING.getBytes());
		// log.debug(LONG_STRING + " equals " + new
		// String(stateTrie.get(c.getBytes())));
		//
		// stateTrie.put(ca.getBytes(), LONG_STRING.getBytes());
		// log.debug(LONG_STRING + " equals " + new
		// String(stateTrie.get(ca.getBytes())));
		//
		// stateTrie.put(cat.getBytes(), LONG_STRING.getBytes());
		// log.debug(LONG_STRING + " equals " + new
		// String(stateTrie.get(cat.getBytes())));
		// log.debug(ROOT_HASH_BEFORE + " equals " +
		// encApi.hexEnc(stateTrie.getRootHash()));
		//
		// stateTrie.delete(ca.getBytes());
		// log.debug(ROOT_HASH_AFTER1 + " equals " +
		// encApi.hexEnc(stateTrie.getRootHash()));
		//
		// stateTrie.delete(cat.getBytes());
		// log.debug(ROOT_HASH_AFTER2 + " equals " +
		// encApi.hexEnc(stateTrie.getRootHash()));

		// StateTrie trie = new StateTrie();
		// newStateTrie.setRoot(null);
		this.stateTrie.put(cat.getBytes(), dog.getBytes());
		log.debug(dog + " = " + new String(this.stateTrie.get(cat.getBytes())));

		this.stateTrie.put(ca.getBytes(), dude.getBytes());
		log.debug(dude + " = " + new String(this.stateTrie.get(ca.getBytes())));

		this.stateTrie.put(doge.getBytes(), LONG_STRING.getBytes());
		log.debug(LONG_STRING + " = " + new String(this.stateTrie.get(doge.getBytes())));

		byte[] root1 = this.stateTrie.getRootHash();


		this.stateTrie.setRoot(root1);
		this.stateTrie.put("aabbcc".getBytes(), "aabbcc".getBytes());
		log.debug("root1:: " + test + " = "
				+ (this.stateTrie.get(dog.getBytes()) == null ? "" : new String(this.stateTrie.get(dog.getBytes()))));
		log.debug("root1:: " + LONG_STRING + " = "
				+ (this.stateTrie.get(doge.getBytes()) == null ? "" : new String(this.stateTrie.get(doge.getBytes()))));
		log.debug("root1:: " + "aabbcc" + " = " + (this.stateTrie.get("aabbcc".getBytes()) == null ? ""
				: new String(this.stateTrie.get("aabbcc".getBytes()))));

		byte[] root11 = this.stateTrie.getRootHash();

		this.stateTrie.setRoot(root11);
		this.stateTrie.put("aabbcc".getBytes(), "aabbccdd".getBytes());
		this.stateTrie.put("aabbccdd".getBytes(), "aabbccdd".getBytes());
		log.debug("root1:: " + test + " = "
				+ (this.stateTrie.get(dog.getBytes()) == null ? "" : new String(this.stateTrie.get(dog.getBytes()))));
		log.debug("root1:: " + LONG_STRING + " = "
				+ (this.stateTrie.get(doge.getBytes()) == null ? "" : new String(this.stateTrie.get(doge.getBytes()))));
		log.debug("root1:: " + "aabbcc" + " = " + (this.stateTrie.get("aabbcc".getBytes()) == null ? ""
				: new String(this.stateTrie.get("aabbcc".getBytes()))));
		log.debug("root1:: " + "aabbccdd" + " = " + (this.stateTrie.get("aabbccdd".getBytes()) == null ? ""
				: new String(this.stateTrie.get("aabbccdd".getBytes()))));

		// stateTrie.setRoot(null);
		this.stateTrie.put(dog.getBytes(), test.getBytes());
		log.debug(test + " = " + new String(this.stateTrie.get(dog.getBytes())));

		this.stateTrie.put(test.getBytes(), LONG_STRING.getBytes());
		log.debug(LONG_STRING + " = " + new String(this.stateTrie.get(test.getBytes())));

		byte[] root2 = this.stateTrie.getRootHash();

		this.stateTrie.setRoot(root2);
		log.debug("root2:: " + test + " = "
				+ (this.stateTrie.get(dog.getBytes()) == null ? "" : new String(this.stateTrie.get(dog.getBytes()))));
		log.debug("root2:: " + LONG_STRING + " = "
				+ (this.stateTrie.get(test.getBytes()) == null ? "" : new String(this.stateTrie.get(test.getBytes()))));

		this.stateTrie.setRoot(root2);
		log.debug("root2:: " + "aabbcc" + " = " + (this.stateTrie.get("aabbcc".getBytes()) == null ? ""
				: new String(this.stateTrie.get("aabbcc".getBytes()))));

		//
		// // 创建账户1
		// KeyPairs oKeyPairs1 = encApi.genKeys();
		// // 创建账户1
		// accountHelper.CreateAccount(encApi.hexDec(oKeyPairs1.getAddress()),
		// oKeyPairs1.getPubkey().getBytes());
		// // 增加账户余额1
		// try {
		// accountHelper.addBalance(encApi.hexDec(oKeyPairs1.getAddress()), 100);
		// log.debug(String.format("创建账户1::%s Balance::100", oKeyPairs1.getAddress()));
		// } catch (Exception e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// // 创建账户2
		// KeyPairs oKeyPairs2 = encApi.genKeys();
		// // 创建账户2
		// accountHelper.CreateAccount(encApi.hexDec(oKeyPairs2.getAddress()),
		// oKeyPairs2.getPubkey().getBytes());
		// log.debug(String.format("创建账户2::%s Balance::0", oKeyPairs2.getAddress()));
		//
		// int count = 2;
		//
		// // while (count <= 2) {
		// try {
		// // 随意抽取两个用户，相互之间发送随机大小的交易
		//
		// int nonce = accountHelper.getNonce(encApi.hexDec(oKeyPairs1.getAddress()));
		//
		// MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		// MultiTransactionBody.Builder oMultiTransactionBody =
		// MultiTransactionBody.newBuilder();
		// MultiTransactionInput.Builder oMultiTransactionInput4 =
		// MultiTransactionInput.newBuilder();
		// oMultiTransactionInput4.setAddress(ByteString.copyFrom(encApi.hexDec(oKeyPairs1.getAddress())));
		// oMultiTransactionInput4.setAmount(80);
		// oMultiTransactionInput4.setFee(0);
		// oMultiTransactionInput4.setFeeLimit(0);
		// oMultiTransactionInput4.setNonce(nonce);
		// oMultiTransactionBody.addInputs(oMultiTransactionInput4);
		//
		// MultiTransactionOutput.Builder oMultiTransactionOutput1 =
		// MultiTransactionOutput.newBuilder();
		// oMultiTransactionOutput1.setAddress(ByteString.copyFrom(encApi.hexDec(oKeyPairs2.getAddress())));
		// oMultiTransactionOutput1.setAmount(80);
		// oMultiTransactionBody.addOutputs(oMultiTransactionOutput1);
		//
		// // MultiTransactionOutput.Builder oMultiTransactionOutput =
		// // MultiTransactionOutput.newBuilder();
		// oMultiTransactionBody.setData(ByteString.EMPTY);
		// oMultiTransaction.setTxHash(ByteString.EMPTY);
		// oMultiTransactionBody.clearSignatures();
		//
		// oMultiTransactionBody.setTimestamp((new Date()).getTime());
		// // 签名
		// MultiTransactionSignature.Builder oMultiTransactionSignature21 =
		// MultiTransactionSignature.newBuilder();
		// oMultiTransactionSignature21.setPubKey(oKeyPairs1.getPubkey());
		// oMultiTransactionSignature21.setSignature(
		// encApi.hexEnc(encApi.ecSign(oKeyPairs1.getPrikey(),
		// oMultiTransactionBody.build().toByteArray())));
		// oMultiTransactionBody.addSignatures(oMultiTransactionSignature21);
		//
		// oMultiTransaction.setTxBody(oMultiTransactionBody);
		// transactionHelper.CreateMultiTransaction(oMultiTransaction);
		// // log.debug(String.format("=====> 创建交易 %s 次数 %s 金额 %s 累计执行 %s",
		// // oKeyPairs1.getAddress(), nonce, rAmount, count));
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// // count += 1;
		// // }
		//
		// BlockEntity.Builder oSyncBlock = BlockEntity.newBuilder();
		// BlockEntity.Builder newBlock;
		// try {
		// newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY,
		// ByteString.copyFromUtf8("12345").toByteArray());
		// oSyncBlock.setHeader(newBlock.getHeader());
		// // oSyncBlock.setBody(newBlock.getBody());
		// log.debug(String.format("==> 第 %s 块 hash %s 创建成功",
		// oSyncBlock.getHeader().getNumber(),
		// encApi.hexEnc(oSyncBlock.getHeader().getBlockHash().toByteArray())));
		// blockHelper.ApplyBlock(oSyncBlock);
		// log.debug(String.format("==> 第 %s 块 hash %s 父hash %s 交易 %s 笔",
		// oSyncBlock.getHeader().getNumber(),
		// encApi.hexEnc(oSyncBlock.getHeader().getBlockHash().toByteArray()),
		// encApi.hexEnc(oSyncBlock.getHeader().getParentHash().toByteArray()),
		// oSyncBlock.getHeader().getTxHashsCount()));
		// count += 1;
		//
		// for (ByteString oByteString : oSyncBlock.getHeader().getTxHashsList()) {
		// log.debug(String.format("交易执行结果 %s",
		// transactionHelper.GetTransaction(oByteString.toByteArray()).getStatus()));
		// }
		//
		// } catch (Exception e) {
		// e.printStackTrace();
		// log.debug(String.format("执行 %s 区块异常 %s", count, e.getMessage()));
		// }

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}
}
