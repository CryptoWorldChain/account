package org.brewchain.account.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.actuator.ActuatorCallInternalFunction;
import org.brewchain.account.core.actuator.ActuatorCreateContract;
import org.brewchain.account.core.actuator.ActuatorCreateUnionAccount;
import org.brewchain.account.core.actuator.ActuatorCryptoTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorDefault;
import org.brewchain.account.core.actuator.ActuatorExecuteContract;
import org.brewchain.account.core.actuator.ActuatorLockTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorUnionAccountTransaction;
import org.brewchain.account.core.actuator.iTransactionActuator;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Act.AccountValue;
import org.brewchain.account.gens.Tx.BroadcastTransactionMsg;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionNode;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.Tx.SingleTransaction;
import org.brewchain.account.gens.Tximpl.MultiTransactionBodyImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionInputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionOutputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionSignatureImpl;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.iPojoBean;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

import static java.util.Arrays.copyOfRange;

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
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;
	@ActorRequire(name = "WaitSend_HashMapDB", scope = "global")
	WaitSendHashMapDB oSendingHashMapDB; // 保存待广播交易
	@ActorRequire(name = "WaitBlock_HashMapDB", scope = "global")
	WaitBlockHashMapDB oPendingHashMapDB; // 保存待打包block的交易
	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;

	/**
	 * 保存交易方法。 交易不会立即执行，而是等待被广播和打包。只有在Block中的交易，才会被执行。 交易签名规则 1. 清除signatures 2.
	 * txHash=ByteString.EMPTY 3. 签名内容=oMultiTransaction.toByteArray()
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	public ByteString CreateMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		// 节点
		MultiTransactionNode.Builder oNode = MultiTransactionNode.newBuilder();
		oNode.setBcuid(KeyConstant.node.getBcuid());
		oNode.setAddress(KeyConstant.node.getoAccount().getAddress());
		oNode.setNode(KeyConstant.node.getNode());
		oMultiTransaction.setTxNode(oNode);

		MultiTransaction formatMultiTransaction = verifyAndSaveMultiTransaction(oMultiTransaction);

		// 保存交易到缓存中，用于广播
		oSendingHashMapDB.put(encApi.hexEnc(formatMultiTransaction.getTxHash().toByteArray()), formatMultiTransaction.toByteArray());

		// 保存交易到缓存中，用于打包
		// 如果指定了委托，并且委托是本节点
		oPendingHashMapDB.put(encApi.hexEnc(formatMultiTransaction.getTxHash().toByteArray()), formatMultiTransaction.toByteArray());

		// {node} {component} {opt} {type} {msg}
		log.info(String.format("LOGFILTER %s %s %s %s 创建交易[%s]", KeyConstant.node.getNode(), "account", "create",
				"transaction", encApi.hexEnc(formatMultiTransaction.getTxHash().toByteArray())));

		return formatMultiTransaction.getTxHash();
	}

	public ByteString CreateGenesisMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		Map<ByteString, Account> senders = new HashMap<ByteString, Account>();
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			senders.put(oInput.getAddress(), oAccountHelper.GetAccount(oInput.getAddress().toByteArray()));
		}

		Map<ByteString, Account> receivers = new HashMap<ByteString, Account>();
		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			receivers.put(oOutput.getAddress(), oAccountHelper.GetAccount(oOutput.getAddress().toByteArray()));
		}

		iTransactionActuator oiTransactionActuator;
		oiTransactionActuator = new ActuatorDefault(this.oAccountHelper, null, null, encApi, dao, null);

		// 如果交易本身需要验证签名
		if (oiTransactionActuator.needSignature()) {
			oiTransactionActuator.onVerifySignature(oMultiTransaction.build(), senders, receivers);
		}

		// 执行交易执行前的数据校验
		// oiTransactionActuator.onPrepareExecute(oMultiTransaction.build(),
		// senders, receivers);

		oMultiTransaction.setTxHash(ByteString.EMPTY);
		// 生成交易Hash
		oMultiTransaction
				.setTxHash(ByteString.copyFrom(encApi.sha256Encode(oMultiTransaction.getTxBody().toByteArray())));

		MultiTransaction multiTransaction = oMultiTransaction.build();
		// 保存交易到db中
		dao.getTxsDao().put(OEntityBuilder.byteKey2OKey(multiTransaction.getTxHash().toByteArray()),
				OEntityBuilder.byteValue2OValue(multiTransaction.toByteArray()));

		return oMultiTransaction.getTxHash();
	}

	/**
	 * 广播交易方法。 交易广播后，节点收到的交易会保存在本地db中。交易不会立即执行，而且不被广播。只有在Block中的交易，才会被执行。
	 * 
	 * @param oTransaction
	 * @throws Exception
	 */
	public void syncTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		MultiTransaction formatMultiTransaction = verifyAndSaveMultiTransaction(oMultiTransaction);

		// 保存交易到缓存中，用于打包
		oPendingHashMapDB.put(encApi.hexEnc(formatMultiTransaction.getTxHash().toByteArray()), formatMultiTransaction.toByteArray());

	}

	/**
	 * 交易执行。交易只在接收到Block后才会被执行
	 * 
	 * @param oTransaction
	 */
	public void ExecuteTransaction(LinkedList<MultiTransaction> oMultiTransactions) throws Exception {

		for (MultiTransaction oTransaction : oMultiTransactions) {
			LinkedList<OKey> keys = new LinkedList<OKey>();
			LinkedList<AccountValue> values = new LinkedList<AccountValue>();

			iTransactionActuator oiTransactionActuator;
			switch (TransTypeEnum.transf(oTransaction.getTxBody().getType())) {
				case TYPE_CreateUnionAccount: {
					oiTransactionActuator = new ActuatorCreateUnionAccount(this.oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				case TYPE_TokenTransaction: {
					oiTransactionActuator = new ActuatorTokenTransaction(oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				case TYPE_UnionAccountTransaction: {
					oiTransactionActuator = new ActuatorUnionAccountTransaction(oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				case TYPE_CallInternalFunction: {
					oiTransactionActuator = new ActuatorCallInternalFunction(oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				case TYPE_CryptoTokenTransaction: {
					oiTransactionActuator = new ActuatorCryptoTokenTransaction(oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				case TYPE_LockTokenTransaction: {
					oiTransactionActuator = new ActuatorLockTokenTransaction(oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				case TYPE_CreateContract: {
					oiTransactionActuator = new ActuatorCreateContract(oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				case TYPE_ExcuteContract: {
					oiTransactionActuator = new ActuatorExecuteContract(oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
				}
					break;
				default:
					oiTransactionActuator = new ActuatorDefault(this.oAccountHelper, this, null, encApi, dao,
							this.stateTrie);
					break;
			}
			
			try {
				Map<ByteString, Account> senders = new HashMap<ByteString, Account>();
				for (MultiTransactionInput oInput : oTransaction.getTxBody().getInputsList()) {
					senders.put(oInput.getAddress(),
							oAccountHelper.GetAccountOrCreate(oInput.getAddress().toByteArray()));
				}

				Map<ByteString, Account> receivers = new HashMap<ByteString, Account>();
				for (MultiTransactionOutput oOutput : oTransaction.getTxBody().getOutputsList()) {
					receivers.put(oOutput.getAddress(),
							oAccountHelper.GetAccountOrCreate(oOutput.getAddress().toByteArray()));
				}

				oiTransactionActuator.onPrepareExecute(oTransaction, senders, receivers);
				oiTransactionActuator.onExecute(oTransaction, senders, receivers);

				keys.addAll(oiTransactionActuator.getKeys());
				values.addAll(oiTransactionActuator.getValues());

				for (int i = 0; i < keys.size(); i++) {
					this.stateTrie.put(keys.get(i).getData().toByteArray(), values.get(i).toByteArray());
					log.debug("put trie key::" + encApi.hexEnc(keys.get(i).getData().toByteArray()) + " values::"
							+ encApi.hexEnc(values.get(i).toByteArray()));
				}

				oAccountHelper.BatchPutAccounts(keys, values);

				oiTransactionActuator.onExecuteDone(oTransaction);
			} catch (Exception e) {
				e.printStackTrace();
				oiTransactionActuator.onExecuteError(oTransaction);
				// throw e;
				log.error("error on exec tx::" + e.getMessage(), e);
			}

		}
		// oStateTrie.flush();
		// return oStateTrie.getRootHash();
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

		for (Iterator<Map.Entry<String, byte[]>> it = oSendingHashMapDB.getStorage().entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, byte[]> item = it.next();
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

	public BroadcastTransactionMsg getWaitSendTxToSend(int count) throws InvalidProtocolBufferException {
		BroadcastTransactionMsg.Builder oBroadcastTransactionMsg = BroadcastTransactionMsg.newBuilder();
		int total = 0;

		for (Iterator<Map.Entry<String, byte[]>> it = oSendingHashMapDB.getStorage().entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<String, byte[]> item = it.next();
			MultiTransaction.Builder oTransaction = MultiTransaction.newBuilder();
			oTransaction.mergeFrom(item.getValue());
			oBroadcastTransactionMsg.addTxHexStr(encApi.hexEnc(oTransaction.build().toByteArray()));
			oPendingHashMapDB.put(item.getKey(), item.getValue());
			it.remove();
			total += 1;
			if (count == total) {
				break;
			}
		}
		return oBroadcastTransactionMsg.build();
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

		for (Iterator<Map.Entry<String, byte[]>> it = oPendingHashMapDB.getStorage().entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<String, byte[]> item = it.next();
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

	public void removeWaitBlockTx(String txHash) throws InvalidProtocolBufferException {
		for (Iterator<Map.Entry<String, byte[]>> it = oPendingHashMapDB.getStorage().entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<String, byte[]> item = it.next();
			if (item.getKey().equals(txHash)) {
				it.remove();
				return;
			}
		}
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
		if (oValue == null || oValue.getExtdata() == null) {
			throw new Exception(String.format("没有找到hash %s 的交易数据", encApi.hexEnc(txHash)));
		}
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
	public void getTransactionHash(MultiTransaction.Builder oTransaction) throws Exception {
		if (oTransaction.getTxBody().getSignaturesCount() == 0) {
			throw new Exception("交易需要签名后才能设置交易Hash");
		}
		oTransaction.setTxHash(ByteString.copyFrom(encApi.sha256Encode(oTransaction.build().toByteArray())));
	}

	// /**
	// * 获取多重交易签名后的Hash
	// *
	// * @param oMultiTransaction
	// * @throws Exception
	// */
	// public byte[] getMultiTransactionHash(MultiTransaction oMultiTransaction)
	// throws Exception {
	// // if (oMultiTransaction.getInputsCount() !=
	// // oMultiTransaction.getSignatureCount()) {
	// // throw new Exception(String.format("交易签名个数 %s 与输入个数 %s 不一致",
	// // oMultiTransaction.getSignatureCount(),
	// // oMultiTransaction.getInputsCount()));
	// // }
	// // if (oMultiTransaction.getSignaturesCount() == 0) {
	// // throw new Exception("交易需要签名后才能设置交易Hash");
	// // }
	// MultiTransaction.Builder newMultiTransaction =
	// MultiTransaction.newBuilder(oMultiTransaction);
	// newMultiTransaction.setTxHash(ByteString.EMPTY);
	// byte[] txHash =
	// encApi.sha256Encode(newMultiTransaction.build().toByteArray());
	// newMultiTransaction.setTxHash(ByteString.copyFrom(txHash));
	// return newMultiTransaction.getTxHash().toByteArray();
	// }

	// public byte[] ReHashTransaction(MultiTransaction.Builder
	// oMultiTransaction) throws Exception {
	// // if (oMultiTransaction.getInputsCount() !=
	// // oMultiTransaction.getSignatureCount()) {
	// // throw new Exception(String.format("交易签名个数 %s 与输入个数 %s 不一致",
	// // oMultiTransaction.getSignatureCount(),
	// // oMultiTransaction.getInputsCount()));
	// // }
	// // if (oMultiTransaction.getSignaturesCount() == 0) {
	// // throw new Exception("交易需要签名后才能设置交易Hash");
	// // }
	//
	// String oldHash =
	// encApi.hexEnc(oMultiTransaction.getTxHash().toByteArray());
	// MultiTransaction.Builder newMultiTransaction = oMultiTransaction.clone();
	// newMultiTransaction.setTxHash(ByteString.EMPTY);
	// String newHash =
	// encApi.hexEnc(encApi.sha256Encode(newMultiTransaction.build().toByteArray()));
	//
	// log.debug(String.format(" %s <==> %s", oldHash, newHash));
	// if (!oldHash.equals(newHash)) {
	// throw new Exception("");
	// }
	// return encApi.sha256Encode(newMultiTransaction.build().toByteArray());
	// }

	/**
	 * 将交易映射为多重交易。映射后原交易的Hash将失效，应在后续使用中重新生成多重交易的Hash。
	 * 
	 * @param oSingleTransaction
	 * @return
	 */
	public MultiTransaction.Builder ParseSingleTransactionToMultiTransaction(
			SingleTransaction.Builder oSingleTransaction) {
		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		MultiTransactionInput.Builder oMultiTransactionInput = MultiTransactionInput.newBuilder();
		MultiTransactionOutput.Builder oMultiTransactionOutput = MultiTransactionOutput.newBuilder();
		MultiTransactionSignature.Builder oMultiTransactionSignature = MultiTransactionSignature.newBuilder();

		oMultiTransactionInput.setAddress(oSingleTransaction.getSenderAddress());
		oMultiTransactionInput.setAmount(oSingleTransaction.getAmount());
		oMultiTransactionInput.setFee(oSingleTransaction.getFee());
		oMultiTransactionInput.setFeeLimit(oSingleTransaction.getFeeLimit());
		oMultiTransactionInput.setNonce(oSingleTransaction.getNonce());
		oMultiTransactionInput.setPubKey(oSingleTransaction.getPubKey());
		oMultiTransactionInput.setToken(oSingleTransaction.getToken());
		// oMultiTransactionInput.setSymbol(oSingleTransaction.gets)

		oMultiTransactionOutput.setAddress(oSingleTransaction.getReceiveAddress());
		oMultiTransactionOutput.setAmount(oSingleTransaction.getAmount());

		oMultiTransactionSignature.setSignature(oSingleTransaction.getSignature());
		oMultiTransactionSignature.setPubKey(oSingleTransaction.getPubKey());

		for (ByteString oDelegate : oSingleTransaction.getDelegateList()) {
			oMultiTransactionBody.addDelegate(oDelegate);
		}
		oMultiTransactionBody.setExdata(oSingleTransaction.getExdata());
		oMultiTransactionBody.setData(oSingleTransaction.getData());
		oMultiTransactionBody.addInputs(oMultiTransactionInput);
		oMultiTransactionBody.addOutputs(oMultiTransactionOutput);
		oMultiTransactionBody.addSignatures(oMultiTransactionSignature);
		oMultiTransactionBody.setTimestamp(oSingleTransaction.getTimestamp());
		oMultiTransaction.setTxBody(oMultiTransactionBody);
		return oMultiTransaction;
	}

	/**
	 * 映射为接口类型
	 * 
	 * @param oTransaction
	 * @return
	 */
	public MultiTransactionImpl.Builder parseToImpl(MultiTransaction oTransaction) {
		MultiTransactionBody oMultiTransactionBody = oTransaction.getTxBody();

		MultiTransactionImpl.Builder oMultiTransactionImpl = MultiTransactionImpl.newBuilder();
		oMultiTransactionImpl.setTxHash(encApi.hexEnc(oTransaction.getTxHash().toByteArray()));

		oMultiTransactionImpl
				.setStatus(StringUtils.isNotBlank(oTransaction.getStatus()) ? oTransaction.getStatus() : "");

		MultiTransactionBodyImpl.Builder oMultiTransactionBodyImpl = MultiTransactionBodyImpl.newBuilder();
		
		oMultiTransactionBodyImpl.setData(oMultiTransactionBody.getData().toStringUtf8());
		
		for (ByteString delegate : oMultiTransactionBody.getDelegateList()) {
			oMultiTransactionBodyImpl.addDelegate(encApi.hexEnc(delegate.toByteArray()));
		}
		
		oMultiTransactionBodyImpl.setExdata(oMultiTransactionBody.getExdata().toStringUtf8());
		
		for (MultiTransactionInput input : oMultiTransactionBody.getInputsList()) {
			MultiTransactionInputImpl.Builder oMultiTransactionInputImpl = MultiTransactionInputImpl.newBuilder();
			oMultiTransactionInputImpl.setAddress(encApi.hexEnc(input.getAddress().toByteArray()));
			oMultiTransactionInputImpl.setAmount(input.getAmount());
			oMultiTransactionInputImpl.setCryptoToken(encApi.hexEnc(input.getCryptoToken().toByteArray()));
			oMultiTransactionInputImpl.setFee(input.getFee());
			oMultiTransactionInputImpl.setNonce(input.getNonce());
			oMultiTransactionInputImpl.setPubKey(input.getPubKey());
			oMultiTransactionInputImpl.setSymbol(input.getSymbol());
			oMultiTransactionInputImpl.setToken(input.getToken());
			oMultiTransactionBodyImpl.addInputs(oMultiTransactionInputImpl);
		}
		for (MultiTransactionOutput output : oMultiTransactionBody.getOutputsList()) {
			MultiTransactionOutputImpl.Builder oMultiTransactionOutputImpl = MultiTransactionOutputImpl.newBuilder();
			oMultiTransactionOutputImpl.setAddress(encApi.hexEnc(output.getAddress().toByteArray()));
			oMultiTransactionOutputImpl.setAmount(output.getAmount());
			oMultiTransactionOutputImpl.setCryptoToken(encApi.hexEnc(output.getCryptoToken().toByteArray()));
			oMultiTransactionOutputImpl.setSymbol(output.getSymbol());
			oMultiTransactionBodyImpl.addOutputs(oMultiTransactionOutputImpl);
		}
		// oMultiTransactionBodyImpl.setSignatures(index, value)
		for (MultiTransactionSignature signature : oMultiTransactionBody.getSignaturesList()) {
			MultiTransactionSignatureImpl.Builder oMultiTransactionSignatureImpl = MultiTransactionSignatureImpl
					.newBuilder();
			oMultiTransactionSignatureImpl.setPubKey(signature.getPubKey());
			oMultiTransactionSignatureImpl.setSignature(signature.getSignature());
			oMultiTransactionBodyImpl.addSignatures(oMultiTransactionSignatureImpl);
		}
		oMultiTransactionBodyImpl.setTimestamp(oMultiTransactionBody.getTimestamp());
		oMultiTransactionImpl.setTxBody(oMultiTransactionBodyImpl);
		return oMultiTransactionImpl;
	}

	public MultiTransaction.Builder parse(MultiTransactionImpl oTransaction) {
		MultiTransactionBodyImpl oMultiTransactionBodyImpl = oTransaction.getTxBody();

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		oMultiTransaction.setTxHash(ByteString.copyFrom(encApi.hexDec(oTransaction.getTxHash())));

		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		oMultiTransactionBody.setData(ByteString.copyFromUtf8(oMultiTransactionBodyImpl.getData()));
		for (String delegate : oMultiTransactionBodyImpl.getDelegateList()) {
			oMultiTransactionBody.addDelegate(ByteString.copyFrom(encApi.hexDec(delegate)));
		}
		oMultiTransactionBody.setExdata(ByteString.copyFromUtf8(oMultiTransactionBodyImpl.getExdata()));
		for (MultiTransactionInputImpl input : oMultiTransactionBodyImpl.getInputsList()) {
			MultiTransactionInput.Builder oMultiTransactionInput = MultiTransactionInput.newBuilder();
			oMultiTransactionInput.setAddress(ByteString.copyFrom(encApi.hexDec(input.getAddress())));
			oMultiTransactionInput.setAmount(input.getAmount());
			oMultiTransactionInput.setCryptoToken(ByteString.copyFrom(encApi.hexDec(input.getCryptoToken())));
			oMultiTransactionInput.setFee(input.getFee());
			oMultiTransactionInput.setNonce(input.getNonce());
			oMultiTransactionInput.setPubKey(input.getPubKey());
			oMultiTransactionInput.setSymbol(input.getSymbol());
			oMultiTransactionInput.setToken(input.getToken());
			oMultiTransactionBody.addInputs(oMultiTransactionInput);
		}
		for (MultiTransactionOutputImpl output : oMultiTransactionBodyImpl.getOutputsList()) {
			MultiTransactionOutput.Builder oMultiTransactionOutput = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput.setAddress(ByteString.copyFrom(encApi.hexDec(output.getAddress())));
			oMultiTransactionOutput.setAmount(output.getAmount());
			oMultiTransactionOutput.setCryptoToken(ByteString.copyFrom(encApi.hexDec(output.getCryptoToken())));
			oMultiTransactionOutput.setSymbol(output.getSymbol());
			oMultiTransactionBody.addOutputs(oMultiTransactionOutput);
		}
		// oMultiTransactionBodyImpl.setSignatures(index, value)
		for (MultiTransactionSignatureImpl signature : oMultiTransactionBodyImpl.getSignaturesList()) {
			MultiTransactionSignature.Builder oMultiTransactionSignature = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature.setPubKey(signature.getPubKey());
			oMultiTransactionSignature.setSignature(signature.getSignature());
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature);
		}
		oMultiTransactionBody.setTimestamp(oMultiTransactionBodyImpl.getTimestamp());
		oMultiTransaction.setTxBody(oMultiTransactionBody);
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
	public MultiTransaction verifyAndSaveMultiTransaction(MultiTransaction.Builder oMultiTransaction)
			throws Exception {

		if (isExistsTransaction(oMultiTransaction.getTxHash().toByteArray())) {
			log.warn("transaction exists, drop it");
			throw new Exception("transaction exists, drop it");
		}

		Map<ByteString, Account> senders = new HashMap<ByteString, Account>();
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			senders.put(oInput.getAddress(), oAccountHelper.GetAccountOrCreate(oInput.getAddress().toByteArray()));
		}

		Map<ByteString, Account> receivers = new HashMap<ByteString, Account>();
		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			receivers.put(oOutput.getAddress(), oAccountHelper.GetAccountOrCreate(oOutput.getAddress().toByteArray()));
		}

		iTransactionActuator oiTransactionActuator;
		// TODO 枚举交易类型

		// 01 -- 创建多重签名账户交易
		// 02 -- Token交易
		// 03 -- 多重签名账户交易
		switch (TransTypeEnum.transf(oMultiTransaction.getTxBody().getType())) {
		case TYPE_CreateUnionAccount: {
			oiTransactionActuator = new ActuatorCreateUnionAccount(this.oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		case TYPE_TokenTransaction: {
			oiTransactionActuator = new ActuatorTokenTransaction(oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		case TYPE_UnionAccountTransaction: {
			oiTransactionActuator = new ActuatorUnionAccountTransaction(oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		case TYPE_CallInternalFunction: {
			oiTransactionActuator = new ActuatorCallInternalFunction(oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		case TYPE_CryptoTokenTransaction: {
			oiTransactionActuator = new ActuatorCryptoTokenTransaction(oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		case TYPE_LockTokenTransaction: {
			oiTransactionActuator = new ActuatorLockTokenTransaction(oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		case TYPE_CreateContract: {
			oiTransactionActuator = new ActuatorCreateContract(oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		case TYPE_ExcuteContract: {
			oiTransactionActuator = new ActuatorExecuteContract(oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
		}
			break;
		default:
			oiTransactionActuator = new ActuatorDefault(this.oAccountHelper, this, null, encApi, dao,
					this.stateTrie);
			break;
		}

		// 如果交易本身需要验证签名
		if (oiTransactionActuator.needSignature()) {
			oiTransactionActuator.onVerifySignature(oMultiTransaction.build(), senders, receivers);
		}

		// 执行交易执行前的数据校验
		oiTransactionActuator.onPrepareExecute(oMultiTransaction.build(), senders, receivers);

		oMultiTransaction.setTxHash(ByteString.EMPTY);
		// 生成交易Hash
		oMultiTransaction
				.setTxHash(ByteString.copyFrom(encApi.sha256Encode(oMultiTransaction.getTxBody().toByteArray())));

		MultiTransaction multiTransaction = oMultiTransaction.build();
		// 保存交易到db中
		dao.getTxsDao().put(OEntityBuilder.byteKey2OKey(multiTransaction.getTxHash().toByteArray()),
				OEntityBuilder.byteValue2OValue(multiTransaction.toByteArray()));

		return multiTransaction;
	}

	public void verifySignature(String pubKey, String signature, byte[] tx) throws Exception {
		if (encApi.ecVerify(pubKey, tx, encApi.hexDec(signature))) {

		} else {
			throw new Exception(String.format("签名 %s 使用公钥 %s 验证失败", pubKey, signature));
		}
	}

	public void setTransactionDone(byte[] txHash) throws Exception {
		MultiTransaction.Builder tx = GetTransaction(txHash).toBuilder();
		tx.setStatus("done");
		dao.getTxsDao().put(OEntityBuilder.byteKey2OKey(tx.getTxHash().toByteArray()),
				OEntityBuilder.byteValue2OValue(tx.build().toByteArray()));
	}

	public void setTransactionError(byte[] txHash) throws Exception {
		MultiTransaction.Builder tx = GetTransaction(txHash).toBuilder();
		tx.setStatus("error");
		dao.getTxsDao().put(OEntityBuilder.byteKey2OKey(tx.getTxHash().toByteArray()),
				OEntityBuilder.byteValue2OValue(tx.build().toByteArray()));
	}

	/**
	 * generate contract address by transaction
	 * 
	 * @param oMultiTransaction
	 * @return
	 */
	public byte[] getContractAddressByTransaction(MultiTransaction oMultiTransaction) throws Exception {
		if (oMultiTransaction.getTxBody().getOutputsCount() != 0
				|| oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new Exception("transaction type is wrong.");
		}
		KeyPairs pair = encApi.genKeys(String.format("%s%s",
				encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray()),
				oMultiTransaction.getTxBody().getInputs(0).getNonce()));
		
		byte[] addrHash = encApi.hexDec(pair.getAddress());
		return  copyOfRange(addrHash, 12, addrHash.length);
	}

	public boolean isExistsTransaction(byte[] txHash) {
		OValue oOValue;
		try {
			oOValue = dao.getTxsDao().get(OEntityBuilder.byteKey2OKey(txHash))
					.get();
			if (oOValue == null || oOValue.getExtdata() == null) {
				return false;
			} else {
				return true;
			}
		} catch (Exception e) {
			
		}
		return false;
	}
}