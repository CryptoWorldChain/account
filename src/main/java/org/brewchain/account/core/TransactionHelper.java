package org.brewchain.account.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.actuator.*;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.Tx.SingleTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.iPojoBean;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

/**
 * @author
 *
 */
@iPojoBean
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Transaction_Helper")
@Slf4j
@Data
public class TransactionHelper implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "WaitSend_HashMapDB", scope = "global")
	WaitSendHashMapDB oSendingHashMapDB; // 保存待广播交易
	@ActorRequire(name = "WaitBlock_HashMapDB", scope = "global")
	WaitBlockHashMapDB oPendingHashMapDB; // 保存待打包block的交易

	/**
	 * 保存交易方法。 交易不会立即执行，而是等待被广播和打包。只有在Block中的交易，才会被执行。 交易签名规则 1. 清除signatures 2.
	 * txHash=ByteString.EMPTY 3. 签名内容=oMultiTransaction.toByteArray()
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	public void CreateMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		verifyAndSaveMultiTransaction(oMultiTransaction);

		// 保存交易到缓存中，用于广播
		oSendingHashMapDB.put(oMultiTransaction.getTxHash().toByteArray(), oMultiTransaction.build().toByteArray());

		// 保存交易到缓存中，用于打包
		if (oMultiTransaction.getDelegateCount() == 0
				|| oMultiTransaction.getDelegateList().indexOf(ByteString.copyFromUtf8(dao.getCoinBase())) != -1) {
			// 如果指定了委托，并且委托是本节点
			oPendingHashMapDB.put(oMultiTransaction.getTxHash().toByteArray(), oMultiTransaction.build().toByteArray());
		}
	}

	public void CreateGenesisMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		verifyAndSaveMultiTransaction(oMultiTransaction);

		// 保存交易到缓存中，用于广播
		// oSendingHashMapDB.put(oMultiTransaction.getTxHash().toByteArray(),
		// oMultiTransaction.build().toByteArray());

		// 保存交易到缓存中，用于打包
		if (oMultiTransaction.getDelegateCount() == 0
				|| oMultiTransaction.getDelegateList().indexOf(ByteString.copyFromUtf8(dao.getCoinBase())) != -1) {
			// 如果指定了委托，并且委托是本节点
			oPendingHashMapDB.put(oMultiTransaction.getTxHash().toByteArray(), oMultiTransaction.build().toByteArray());
		}
	}

	/**
	 * 广播交易方法。 交易广播后，节点收到的交易会保存在本地db中。交易不会立即执行，而且不被广播。只有在Block中的交易，才会被执行。
	 * 
	 * @param oTransaction
	 * @throws Exception
	 */
	public void SyncTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		verifyAndSaveMultiTransaction(oMultiTransaction);

		// 交易的发送地址和接收地址如果不存在，则创建
		// 如果交易是多重签名交易，根据extraData创建

		// 保存交易到缓存中，用于打包
		if (oMultiTransaction.getDelegateCount() == 0
				|| oMultiTransaction.getDelegateList().indexOf(ByteString.copyFromUtf8(dao.getCoinBase())) != -1) {
			oPendingHashMapDB.put(oMultiTransaction.getTxHash().toByteArray(), oMultiTransaction.build().toByteArray());
		}
	}

	/**
	 * 交易执行。交易只在接收到Block后才会被执行
	 * 
	 * @param oTransaction
	 */
	public void ExecuteTransaction(LinkedList<MultiTransaction.Builder> oMultiTransactions) throws Exception {
		LinkedList<OKey> keys = new LinkedList<OKey>();
		LinkedList<OValue> values = new LinkedList<OValue>();

		for (MultiTransaction.Builder oTransaction : oMultiTransactions) {

			Map<ByteString, Account> senders = new HashMap<ByteString, Account>();
			for (MultiTransactionInput oInput : oTransaction.getInputsList()) {
				senders.put(oInput.getAddress(), oAccountHelper.GetAccount(oInput.getAddress().toByteArray()));
			}

			Map<ByteString, Account> receivers = new HashMap<ByteString, Account>();
			for (MultiTransactionOutput oOutput : oTransaction.getOutputsList()) {
				receivers.put(oOutput.getAddress(), oAccountHelper.GetAccount(oOutput.getAddress().toByteArray()));
			}

			iTransactionActuator oiTransactionActuator;
			// TODO 枚举交易类型
			if (oTransaction.getData().equals(ByteString.copyFromUtf8("01"))) {
				oiTransactionActuator = new ActuatorCreateUnionAccount(this.oAccountHelper, null, null, encApi);
			} else if (oTransaction.getData().equals(ByteString.copyFromUtf8("02"))) {
				oiTransactionActuator = new ActuatorTokenTransaction(oAccountHelper, null, null, encApi);
			} else if (oTransaction.getData().equals(ByteString.copyFromUtf8("03"))) {
				oiTransactionActuator = new ActuatorUnionAccountTransaction(oAccountHelper, null, null, encApi);
			} else if (oTransaction.getData().equals(ByteString.copyFromUtf8("04"))) {
				oiTransactionActuator = new ActuatorCallInternalFunction(oAccountHelper, null, null, encApi);
			} else {
				oiTransactionActuator = new ActuatorDefault(this.oAccountHelper, null, null, encApi);
			}

			oiTransactionActuator.onPrepareExecute(oTransaction, senders, receivers);
			oiTransactionActuator.onExecute(oTransaction, senders, receivers);
			oiTransactionActuator.onExecuteDone(oTransaction);
		}
	}

	/**
	 * 从待广播交易列表中，查询出交易进行广播。广播后，不论是否各节点接收成功，均放入待打包列表
	 * 
	 * @param count
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	public List<MultiTransaction> getWaitSendTx(int count) throws InvalidProtocolBufferException {
		List<MultiTransaction> list = new LinkedList<MultiTransaction>();
		int total = 0;

		for (Iterator<Map.Entry<byte[], byte[]>> it = oSendingHashMapDB.getStorage().entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<byte[], byte[]> item = it.next();
			MultiTransaction.Builder oTransaction = MultiTransaction.newBuilder();
			oTransaction.mergeFrom(item.getValue());
			list.add(oTransaction.build());
			oPendingHashMapDB.put(item.getKey(), item.getValue());
			it.remove();
			total += 1;
			if (count == total) {
				break;
			}
		}
		return list;
	}

	/**
	 * 从待打包交易列表中，查询出等待打包的交易。
	 * 
	 * @param count
	 * @return
	 * @throws InvalidProtocolBufferException
	 */
	public LinkedList<MultiTransaction> getWaitBlockTx(int count) throws InvalidProtocolBufferException {
		LinkedList<MultiTransaction> list = new LinkedList<MultiTransaction>();
		int total = 0;

		for (Iterator<Map.Entry<byte[], byte[]>> it = oPendingHashMapDB.getStorage().entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<byte[], byte[]> item = it.next();
			MultiTransaction.Builder oTransaction = MultiTransaction.newBuilder();
			oTransaction.mergeFrom(item.getValue());
			list.add(oTransaction.build());
			it.remove();
			total += 1;
			if (count == total) {
				break;
			}
		}
		return list;
	}

	/**
	 * 根据交易Hash，返回交易实体。
	 * 
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	public MultiTransaction GetTransaction(byte[] txHash) throws Exception {
		OValue oValue = dao.getTxsDao().get(OEntityBuilder.byteKey2OKey(txHash)).get();
		MultiTransaction.Builder oTransaction = MultiTransaction.newBuilder();
		oTransaction.mergeFrom(oValue.getExtdata().toByteArray());
		return oTransaction.build();
	}

	/**
	 * 交易签名方法。该方法未实现
	 * 
	 * @param privKey
	 * @param oTransaction
	 * @throws Exception
	 */
	public void Signature(List<String> privKeys, MultiTransaction.Builder oTransaction) throws Exception {
		throw new RuntimeException("未实现该方法");
		// oTransaction.clearSignatures();
		// oTransaction.setTxHash(ByteString.EMPTY);
		//
		// // if (privKeys.size() != oTransaction.getInputsCount()) {
		// // throw new Exception(
		// // String.format("签名用的私钥个数 %s 与待签名的交易个数 %s 不一致", privKeys.size(),
		// // oTransaction.getInputsCount()));
		// // }
		// if (privKeys == null || privKeys.size() == 0) {
		// throw new Exception(String.format("签名用的私钥不能为空"));
		// }
		//
		// LinkedList<MultiTransactionSignature> signatures = new
		// LinkedList<MultiTransactionSignature>();
		// for (int i = 0; i < privKeys.size(); i++) {
		// MultiTransactionSignature.Builder oMultiTransactionSignature =
		// MultiTransactionSignature.newBuilder();
		// oMultiTransactionSignature.setSignature(encApi.base64Enc(encApi.ecSign(privKeys.get(i),
		// oTransaction.build().toByteArray())));
		//
		// signatures.add(encApi.base64Enc(encApi.ecSign(privKeys.get(i),
		// oTransaction.build().toByteArray())));
		// }
		//
		// oTransaction.addAllSignature(signatures);
	}

	/**
	 * 获取交易签名后的Hash
	 * 
	 * @param oTransaction
	 * @throws Exception
	 */
	private void getTransactionHash(MultiTransaction.Builder oTransaction) throws Exception {
		if (oTransaction.getSignaturesCount() == 0) {
			throw new Exception("交易需要签名后才能设置交易Hash");
		}
		oTransaction.setTxHash(ByteString.copyFrom(encApi.sha256Encode(oTransaction.build().toByteArray())));
	}

	/**
	 * 获取多重交易签名后的Hash
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	private void getMultiTransactionHash(MultiTransaction.Builder oMultiTransaction) throws Exception {
		// if (oMultiTransaction.getInputsCount() !=
		// oMultiTransaction.getSignatureCount()) {
		// throw new Exception(String.format("交易签名个数 %s 与输入个数 %s 不一致",
		// oMultiTransaction.getSignatureCount(),
		// oMultiTransaction.getInputsCount()));
		// }
		// if (oMultiTransaction.getSignaturesCount() == 0) {
		// throw new Exception("交易需要签名后才能设置交易Hash");
		// }
		oMultiTransaction.setTxHash(ByteString.EMPTY);
		oMultiTransaction.setTxHash(ByteString.copyFrom(encApi.sha256Encode(oMultiTransaction.build().toByteArray())));
	}

	public byte[] ReHashTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		// if (oMultiTransaction.getInputsCount() !=
		// oMultiTransaction.getSignatureCount()) {
		// throw new Exception(String.format("交易签名个数 %s 与输入个数 %s 不一致",
		// oMultiTransaction.getSignatureCount(),
		// oMultiTransaction.getInputsCount()));
		// }
		// if (oMultiTransaction.getSignaturesCount() == 0) {
		// throw new Exception("交易需要签名后才能设置交易Hash");
		// }

		String oldHash = encApi.hexEnc(oMultiTransaction.getTxHash().toByteArray());
		MultiTransaction.Builder newMultiTransaction = oMultiTransaction.clone();
		newMultiTransaction.setTxHash(ByteString.EMPTY);
		String newHash = encApi.hexEnc(encApi.sha256Encode(newMultiTransaction.build().toByteArray()));

		log.debug(String.format(" %s <==> %s", oldHash, newHash));
		if (!oldHash.equals(newHash)) {
			throw new Exception("");
		}
		return encApi.sha256Encode(newMultiTransaction.build().toByteArray());
	}

	/**
	 * 将交易映射为多重交易。映射后原交易的Hash将失效，应在后续使用中重新生成多重交易的Hash。
	 * 
	 * @param oSingleTransaction
	 * @return
	 */
	public MultiTransaction.Builder ParseSingleTransactionToMultiTransaction(
			SingleTransaction.Builder oSingleTransaction) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionInput.Builder oMultiTransactionInput = MultiTransactionInput.newBuilder();
		MultiTransactionOutput.Builder oMultiTransactionOutput = MultiTransactionOutput.newBuilder();
		MultiTransactionSignature.Builder oMultiTransactionSignature = MultiTransactionSignature.newBuilder();

		oMultiTransactionInput.setAddress(oSingleTransaction.getSenderAddress());
		oMultiTransactionInput.setAmount(oSingleTransaction.getAmount());
		oMultiTransactionInput.setFee(oSingleTransaction.getFee());
		oMultiTransactionInput.setFeeLimit(oSingleTransaction.getFeeLimit());
		oMultiTransactionInput.setNonce(oSingleTransaction.getNonce());
		oMultiTransactionInput.setPubKey(oSingleTransaction.getPubKey());

		oMultiTransactionOutput.setAddress(oSingleTransaction.getReceiveAddress());
		oMultiTransactionOutput.setAmount(oSingleTransaction.getAmount());

		oMultiTransactionSignature.setSignature(oSingleTransaction.getSignature());
		oMultiTransactionSignature.setPubKey(oSingleTransaction.getPubKey());

		oMultiTransaction.addAllDelegate(oSingleTransaction.getDelegateList());
		oMultiTransaction.setExdata(oSingleTransaction.getExdata());
		oMultiTransaction.setData(oSingleTransaction.getData());
		oMultiTransaction.addInputs(oMultiTransactionInput);
		oMultiTransaction.addOutputs(oMultiTransactionOutput);
		oMultiTransaction.addSignatures(oMultiTransactionSignature);

		return oMultiTransaction;
	}

	// private void verifyAndSaveTransaction(SingleTransaction.Builder
	// oTransaction) throws Exception {
	// // 校验交易签名
	//
	// // 判断发送方余额是否足够
	// int balance =
	// oAccountHelper.getBalance(oTransaction.getSenderAddress().toByteArray());
	// if (balance - oTransaction.getAmount() - oTransaction.getFeeLimit() > 0)
	// {
	// // 余额足够
	// } else {
	// throw new Exception(String.format("用户的账户余额 %s 不满足交易的最高限额 %s", balance,
	// oTransaction.getAmount() + oTransaction.getFeeLimit()));
	// }
	//
	// // 判断nonce是否一致
	// int nonce =
	// oAccountHelper.getNonce(oTransaction.getSenderAddress().toByteArray());
	// if (nonce != oTransaction.getNonce()) {
	// throw new Exception(String.format("用户的交易索引 %s 与交易的索引不一致 %s", nonce,
	// oTransaction.getNonce()));
	// }
	//
	// // 生成交易Hash
	// getTransactionHash(oTransaction);
	//
	// // 保存交易到db中
	// dao.getTxsDao().put(OEntityBuilder.byteKey2OKey(oTransaction.getTxHash().toByteArray()),
	// OEntityBuilder.byteValue2OValue(oTransaction.build().toByteArray()));
	// }

	/**
	 * 校验并保存交易。该方法不会执行交易，用户的账户余额不会发生变化。
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	private void verifyAndSaveMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		Map<ByteString, Account> senders = new HashMap<ByteString, Account>();
		for (MultiTransactionInput oInput : oMultiTransaction.getInputsList()) {
			senders.put(oInput.getAddress(), oAccountHelper.GetAccount(oInput.getAddress().toByteArray()));
		}

		Map<ByteString, Account> receivers = new HashMap<ByteString, Account>();
		for (MultiTransactionOutput oOutput : oMultiTransaction.getOutputsList()) {
			receivers.put(oOutput.getAddress(), oAccountHelper.GetAccount(oOutput.getAddress().toByteArray()));
		}

		iTransactionActuator oiTransactionActuator;
		// TODO 枚举交易类型

		// 01 -- 创建多重签名账户交易
		// 02 -- Token交易
		// 03 -- 多重签名账户交易
		if (oMultiTransaction.getData().equals(ByteString.copyFromUtf8("01"))) {
			oiTransactionActuator = new ActuatorCreateUnionAccount(this.oAccountHelper, null, null, encApi);
		} else if (oMultiTransaction.getData().equals(ByteString.copyFromUtf8("02"))) {
			oiTransactionActuator = new ActuatorTokenTransaction(oAccountHelper, null, null, encApi);
		} else if (oMultiTransaction.getData().equals(ByteString.copyFromUtf8("03"))) {
			oiTransactionActuator = new ActuatorUnionAccountTransaction(oAccountHelper, null, null, encApi);
		} else if (oMultiTransaction.getData().equals(ByteString.copyFromUtf8("04"))) {
			oiTransactionActuator = new ActuatorCallInternalFunction(oAccountHelper, null, null, encApi);
		} else {
			oiTransactionActuator = new ActuatorDefault(this.oAccountHelper, null, null, encApi);
		}

		// 如果交易本身需要验证签名
		if (oiTransactionActuator.needSignature()) {
			oiTransactionActuator.onVerifySignature(oMultiTransaction, senders, receivers);
		}

		// 执行交易执行前的数据校验
		oiTransactionActuator.onPrepareExecute(oMultiTransaction, senders, receivers);

		// 生成交易Hash
		getMultiTransactionHash(oMultiTransaction);

		// 保存交易到db中
		dao.getTxsDao().put(OEntityBuilder.byteKey2OKey(oMultiTransaction.getTxHash().toByteArray()),
				OEntityBuilder.byteValue2OValue(oMultiTransaction.build().toByteArray()));
	}

	private void verifySignature(String pubKey, String signature, byte[] tx) throws Exception {
		if (encApi.ecVerify(pubKey, tx, encApi.hexDec(signature))) {

		} else {
			throw new Exception(String.format("签名 %s 使用公钥 %s 验证失败", pubKey, signature));
		}
	}

}