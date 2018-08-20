package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.bean.HashPair;
import org.brewchain.account.core.actuator.ActuatorCallContract;
import org.brewchain.account.core.actuator.ActuatorCallInternalFunction;
import org.brewchain.account.core.actuator.ActuatorCreateContract;
import org.brewchain.account.core.actuator.ActuatorCreateCryptoToken;
import org.brewchain.account.core.actuator.ActuatorCreateToken;
import org.brewchain.account.core.actuator.ActuatorCreateUnionAccount;
import org.brewchain.account.core.actuator.ActuatorCryptoTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorDefault;
import org.brewchain.account.core.actuator.ActuatorLockTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorUnionAccountTransaction;
import org.brewchain.account.core.actuator.iTransactionActuator;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.exception.TransactionException;
import org.brewchain.account.gens.Tximpl.MultiTransactionBodyImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionInputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionOutputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionSignatureImpl;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.account.util.RLP;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.BroadcastTransactionMsg;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionNode;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.brewchain.evmapi.gens.Tx.SingleTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.iPojoBean;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.outils.conf.PropHelper;

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
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
	// 10万笔交易。。。后续再改吧
	PropHelper prop = new PropHelper(null);
	Cache<String, MultiTransaction> txDBCacheByHash = CacheBuilder.newBuilder()
			.initialCapacity(prop.get("org.brewchain.account.cache.tx.init", 10000))
			.expireAfterWrite(3600, TimeUnit.SECONDS)
			.maximumSize(prop.get("org.brewchain.account.cache.tx.max", 100000))
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).build();

	/**
	 * 保存交易方法。 交易不会立即执行，而是等待被广播和打包。只有在Block中的交易，才会被执行。 交易签名规则 1. 清除signatures 2.
	 * txHash=ByteString.EMPTY 3. 签名内容=oMultiTransaction.toByteArray()
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	public HashPair CreateMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		// 节点
		MultiTransactionNode.Builder oNode = MultiTransactionNode.newBuilder();
		oNode.setBcuid(KeyConstant.node.getBcuid());
		oNode.setAddress(KeyConstant.node.getoAccount().getAddress());
		oNode.setNode(KeyConstant.node.getNode());
		oMultiTransaction.setTxNode(oNode);

		HashPair hp = verifyAndSaveMultiTransaction(oMultiTransaction);

		// 保存交易到缓存中，用于广播
		oSendingHashMapDB.put(hp.getHexKey(), hp);

		// 保存交易到缓存中，用于打包
		// 如果指定了委托，并且委托是本节点
		oPendingHashMapDB.put(hp.getHexKey(), hp);

		// {node} {component} {opt} {type} {msg}
		// log.info("LOGFILTER {} {} {} {} CreateTX[%s]",
		// encApi.hexEnc(KeyConstant.node.getoAccount().getAddress().toByteArray()),
		// "account", "create",
		// "transaction", encApi.hexEnc(hp.getKey()));

		return hp;
	}

	public String CreateGenesisMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {

		// Map<String, Account> accounts =
		// getTransactionAccounts(oMultiTransaction);
		//
		// iTransactionActuator oiTransactionActuator =
		// getActuator(oMultiTransaction.getTxBody().getType());
		//
		// // 如果交易本身需要验证签名
		// if (oiTransactionActuator.needSignature()) {
		// oiTransactionActuator.onVerifySignature(oMultiTransaction.build(),
		// accounts);
		// }

		oMultiTransaction.clearStatus();
		oMultiTransaction.clearTxHash();
		// 生成交易Hash
		oMultiTransaction.setTxHash(encApi.hexEnc(encApi.sha256Encode(oMultiTransaction.getTxBody().toByteArray())));

		if (isExistsTransaction(oMultiTransaction.getTxHash())) {
			throw new Exception("transaction exists, drop it txhash::" + oMultiTransaction.getTxHash());
		}
		oMultiTransaction.setStatus("done");
		MultiTransaction multiTransaction = oMultiTransaction.build();
		// 保存交易到db中
		// log.debug("====put genesis transaction::"+
		// multiTransaction.getTxHash());

		dao.getTxsDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(multiTransaction.getTxHash())),
				oEntityHelper.byteValue2OValue(multiTransaction.toByteArray()));

		return multiTransaction.getTxHash();
	}

	/**
	 * 广播交易方法。 交易广播后，节点收到的交易会保存在本地db中。交易不会立即执行，而且不被广播。只有在Block中的交易，才会被执行。
	 * 
	 * @param oTransaction
	 * @throws Exception
	 */
	public void syncTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		syncTransaction(oMultiTransaction, true);
	}

	public void syncTransaction(MultiTransaction.Builder oMultiTransaction, boolean isBroadCast) {
		try {
			// oMultiTransaction.clearStatus();
			// oMultiTransaction.clearTxHash();
			// Future<OValue> f = dao.getTxsDao().put(
			// oEntityHelper.byteKey2OKey(encApi.hexDec(oMultiTransaction.getTxHash())),
			// oEntityHelper.byteValue2OValue(oMultiTransaction.build().toByteArray()));
			// if (f != null && f.get() != null && isBroadCast) {
			// oPendingHashMapDB.put(oMultiTransaction.getTxHash(),
			// oMultiTransaction.build());
			// }
			MultiTransaction cacheTx = txDBCacheByHash.getIfPresent(oMultiTransaction.getTxHash());
			if (cacheTx != null) {
				log.warn("transaction " + oMultiTransaction.getTxHash() + "exists in Cached, drop it");
				return;
			}

			byte keyByte[] = encApi.hexDec(oMultiTransaction.getTxHash());
			OKey key = oEntityHelper.byteKey2OKey(keyByte);

			OValue oValue = dao.getTxsDao().get(key).get();
			if (oValue != null) {
				log.warn("transaction " + oMultiTransaction.getTxHash() + "exists in DB, drop it");
			} else {
				oMultiTransaction.clearStatus();
				oMultiTransaction.clearResult();
				HashPair hp = new HashPair(keyByte, oMultiTransaction.getTxHash(), oMultiTransaction.build());
				dao.getTxsDao().put(key, oEntityHelper.byteValue2OValue(hp.getTx().toByteArray()));
				txDBCacheByHash.put(hp.getHexKey(), hp.getTx());
				if (isBroadCast) {
					oPendingHashMapDB.put(hp.getHexKey(), hp);
				}
				KeyConstant.counter.incrementAndGet();
			}
		} catch (Exception e) {
			log.error("fail to sync transaction::" + oMultiTransaction.getTxHash() + " error::" + e);
		}
	}

	public void syncTransaction(List<MultiTransaction.Builder> oMultiTransaction) throws Exception {
		syncTransaction(oMultiTransaction, true);
	}

	public void syncTransaction(List<MultiTransaction.Builder> oMultiTransaction, boolean isBroadCast) {
		try {
			OKey[] keys = new OKey[oMultiTransaction.size()];
			OValue[] values = new OValue[oMultiTransaction.size()];
			int i = 0;
			HashMap<String, HashPair> buffer = new HashMap<>();
			for (MultiTransaction.Builder mtb : oMultiTransaction) {// db没有
				MultiTransaction cacheTx = txDBCacheByHash.getIfPresent(mtb.getTxHash());
				if (cacheTx == null) {// 缓存没有的时候再添加进去
					MultiTransaction mt = mtb.build();
					ByteString mts = mt.toByteString();
					HashPair hp = new HashPair(mt.getTxHashBytes().toByteArray(), mts.toByteArray(), mt);
					keys[i] = oEntityHelper.byteKey2OKey(mtb.getTxHashBytes());
					values[i] = OValue.newBuilder().setExtdata(mts).setInfo(mtb.getTxHash()).build();
					buffer.put(mtb.getTxHash(), hp);
					i++;
				} else {
					//缓存有的，证明之前已经存在了
				}
			}

			Future<OValue[]> f = dao.getTxsDao().putIfNotExist(keys, values);//返回DB里面不存在的,但是数据库已经存进去的
			if (f != null && f.get() != null && isBroadCast) {
				for (OValue ov : f.get()) {
					HashPair hp = buffer.get(ov.getInfo());
					oPendingHashMapDB.put(ov.getInfo(), hp);
					txDBCacheByHash.put(hp.getHexKey(), hp.getTx());
					KeyConstant.counter.incrementAndGet();
				}
			}

			// for (MultiTransaction.Builder mtb : oMultiTransaction) {
			// OValue oValue =
			// dao.getTxsDao().get(oEntityHelper.byteKey2OKey(encApi.hexDec(mtb.getTxHash()))).get();
			// if (oValue != null) {
			// // log.warn("transaction " + mtb.getTxHash() + "exists, drop
			// it");
			// } else {
			// mtb.clearStatus();
			// mtb.clearResult();
			//
			// dao.getTxsDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(mtb.getTxHash())),
			// oEntityHelper.byteValue2OValue(mtb.build().toByteArray()));
			//
			// if (isBroadCast) {
			// oPendingHashMapDB.put(mtb.getTxHash(), mtb.build());
			// }
			//
			// KeyConstant.counter += 1;
			// }
			// }

		} catch (Exception e) {
			log.error("fail to sync transaction::" + oMultiTransaction.size() + " error::" + e);
		}
	}

	// /**
	// * 从待广播交易列表中，查询出交易进行广播。广播后，不论是否各节点接收成功，均放入待打包列表
	// *
	// * @param count
	// * @return
	// * @throws InvalidProtocolBufferException
	// */
	// public List<HashPair> getWaitSendTx(int count) throws
	// InvalidProtocolBufferException {
	// List<HashPair> list = new LinkedList<HashPair>();
	// int total = 0;
	//
	// for (Iterator<Map.Entry<byte[], byte[]>> it =
	// oSendingHashMapDB.getStorage().entrySet().iterator(); it
	// .hasNext();) {
	// Map.Entry<byte[], byte[]> item = it.next();
	// list.add(new HashPair(item.getKey(), item.getValue()));
	// it.remove();
	// total += 1;
	// if (count == total) {
	// break;
	// }
	// }
	// return list;
	// }

	public BroadcastTransactionMsg getWaitSendTxToSend(int count) throws InvalidProtocolBufferException {
		BroadcastTransactionMsg.Builder oBroadcastTransactionMsg = BroadcastTransactionMsg.newBuilder();
		int total = 0;

		for (Iterator<Map.Entry<String, HashPair>> it = oSendingHashMapDB.getStorage().entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<String, HashPair> item = it.next();
			// oBroadcastTransactionMsg.addTxHexStr(encApi.hexEnc(item.getValue().toByteArray()));
			oBroadcastTransactionMsg.addTxHash(ByteString.copyFrom(encApi.hexDec(item.getKey())));
			oBroadcastTransactionMsg.addTxDatas(ByteString.copyFrom(item.getValue().getData()));
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

		for (Iterator<Map.Entry<String, HashPair>> it = oPendingHashMapDB.getStorage().entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<String, HashPair> item = it.next();
			list.add(item.getValue().getTx());
			it.remove();
			total += 1;
			if (count == total) {
				break;
			}
		}
		return list;
	}

	public HashPair removeWaitBlockTx(String txHash) throws InvalidProtocolBufferException {
		HashPair hpBlk = oPendingHashMapDB.getStorage().remove(txHash);
		HashPair hpSend = oSendingHashMapDB.getStorage().remove(txHash);
		if (hpBlk != null)
			return hpBlk;
		return hpSend;
	}

	public boolean isExistsWaitBlockTx(String txHash) throws InvalidProtocolBufferException {
		return oPendingHashMapDB.getStorage().containsKey(txHash);
	}

	/**
	 * 根据交易Hash，返回交易实体。
	 * 
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	public MultiTransaction GetTransaction(String txHash) throws Exception {
		MultiTransaction cacheTx = txDBCacheByHash.getIfPresent(txHash);
		if(cacheTx!=null){
			return cacheTx;
		}
		OValue oValue = dao.getTxsDao().get(oEntityHelper.byteKey2OKey(encApi.hexDec(txHash))).get();

		if (oValue == null || oValue.getExtdata() == null) {
			// throw new Exception(String.format("没有找到hash %s 的交易数据", txHash));
			return null;
		}
		MultiTransaction oTransaction = MultiTransaction.parseFrom(oValue.getExtdata().toByteArray());
		txDBCacheByHash.put(txHash, oTransaction);
		return oTransaction;
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
		oTransaction.setTxHash(encApi.hexEnc(encApi.sha256Encode(oTransaction.build().toByteArray())));
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

		oMultiTransactionInput.setAddress(ByteString.copyFrom(encApi.hexDec(oSingleTransaction.getSenderAddress())));
		oMultiTransactionInput.setAmount(oSingleTransaction.getAmount());
		oMultiTransactionInput.setNonce(oSingleTransaction.getNonce());
		oMultiTransactionInput.setToken(oSingleTransaction.getToken());
		// oMultiTransactionInput.setSymbol(oSingleTransaction.gets)

		oMultiTransactionOutput.setAddress(ByteString.copyFrom(encApi.hexDec(oSingleTransaction.getReceiveAddress())));
		oMultiTransactionOutput.setAmount(oSingleTransaction.getAmount());

		oMultiTransactionSignature.setSignature(ByteString.copyFrom(encApi.hexDec(oSingleTransaction.getSignature())));

		for (String oDelegate : oSingleTransaction.getDelegateList()) {
			oMultiTransactionBody.addDelegate(ByteString.copyFrom(encApi.hexDec(oDelegate)));
		}
		oMultiTransactionBody.setExdata(ByteString.copyFrom(encApi.hexDec(oSingleTransaction.getExdata())));
		oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(oSingleTransaction.getData())));
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
		oMultiTransactionImpl.setTxHash(oTransaction.getTxHash());

		oMultiTransactionImpl
				.setStatus(StringUtils.isNotBlank(oTransaction.getStatus()) ? oTransaction.getStatus() : "");

		oMultiTransactionImpl.setResult(encApi.hexEnc(oTransaction.getResult().toByteArray()));

		MultiTransactionBodyImpl.Builder oMultiTransactionBodyImpl = MultiTransactionBodyImpl.newBuilder();

		oMultiTransactionBodyImpl.setType(oMultiTransactionBody.getType());
		oMultiTransactionBodyImpl.setData(encApi.hexEnc(oMultiTransactionBody.getData().toByteArray()));

		for (ByteString delegate : oMultiTransactionBody.getDelegateList()) {
			oMultiTransactionBodyImpl.addDelegate(encApi.hexEnc(delegate.toByteArray()));
		}

		oMultiTransactionBodyImpl.setExdata(encApi.hexEnc(oMultiTransactionBody.getExdata().toByteArray()));

		for (MultiTransactionInput input : oMultiTransactionBody.getInputsList()) {
			MultiTransactionInputImpl.Builder oMultiTransactionInputImpl = MultiTransactionInputImpl.newBuilder();
			oMultiTransactionInputImpl.setAddress(encApi.hexEnc(input.getAddress().toByteArray()));
			oMultiTransactionInputImpl
					.setAmount(ByteUtil.bytesToBigInteger(input.getAmount().toByteArray()).toString());
			oMultiTransactionInputImpl.setCryptoToken(encApi.hexEnc(input.getCryptoToken().toByteArray()));
			oMultiTransactionInputImpl.setNonce(input.getNonce());
			oMultiTransactionInputImpl.setSymbol(input.getSymbol());
			oMultiTransactionInputImpl.setToken(input.getToken());
			oMultiTransactionBodyImpl.addInputs(oMultiTransactionInputImpl);
		}
		for (MultiTransactionOutput output : oMultiTransactionBody.getOutputsList()) {
			MultiTransactionOutputImpl.Builder oMultiTransactionOutputImpl = MultiTransactionOutputImpl.newBuilder();
			oMultiTransactionOutputImpl.setAddress(encApi.hexEnc(output.getAddress().toByteArray()));
			oMultiTransactionOutputImpl
					.setAmount(ByteUtil.bytesToBigInteger(output.getAmount().toByteArray()).toString());
			oMultiTransactionOutputImpl.setCryptoToken(encApi.hexEnc(output.getCryptoToken().toByteArray()));
			oMultiTransactionOutputImpl.setSymbol(output.getSymbol());
			oMultiTransactionBodyImpl.addOutputs(oMultiTransactionOutputImpl);
		}
		// oMultiTransactionBodyImpl.setSignatures(index, value)
		for (MultiTransactionSignature signature : oMultiTransactionBody.getSignaturesList()) {
			MultiTransactionSignatureImpl.Builder oMultiTransactionSignatureImpl = MultiTransactionSignatureImpl
					.newBuilder();
			// oMultiTransactionSignatureImpl.setPubKey(encApi.hexEnc(signature.getPubKey().toByteArray()));
			oMultiTransactionSignatureImpl.setSignature(encApi.hexEnc(signature.getSignature().toByteArray()));
			oMultiTransactionBodyImpl.addSignatures(oMultiTransactionSignatureImpl);
		}
		oMultiTransactionBodyImpl.setTimestamp(oMultiTransactionBody.getTimestamp());
		oMultiTransactionImpl.setTxBody(oMultiTransactionBodyImpl);
		return oMultiTransactionImpl;
	}

	public MultiTransaction.Builder parse(MultiTransactionImpl oTransaction) throws Exception {
		MultiTransactionBodyImpl oMultiTransactionBodyImpl = oTransaction.getTxBody();

		MultiTransaction.Builder oMultiTransaction = MultiTransaction.newBuilder();
		oMultiTransaction.setTxHash(oTransaction.getTxHash());

		MultiTransactionBody.Builder oMultiTransactionBody = MultiTransactionBody.newBuilder();
		oMultiTransactionBody.setType(oMultiTransactionBodyImpl.getType());
		oMultiTransactionBody.setData(ByteString.copyFrom(encApi.hexDec(oMultiTransactionBodyImpl.getData())));
		for (String delegate : oMultiTransactionBodyImpl.getDelegateList()) {
			oMultiTransactionBody.addDelegate(ByteString.copyFrom(encApi.hexDec(delegate)));
		}
		oMultiTransactionBody.setExdata(ByteString.copyFrom(encApi.hexDec(oMultiTransactionBodyImpl.getExdata())));
		for (MultiTransactionInputImpl input : oMultiTransactionBodyImpl.getInputsList()) {
			MultiTransactionInput.Builder oMultiTransactionInput = MultiTransactionInput.newBuilder();
			oMultiTransactionInput.setAddress(ByteString.copyFrom(encApi.hexDec(input.getAddress())));
			if (new BigInteger(input.getAmount()).compareTo(BigInteger.ZERO) < 0) {
				throw new TransactionException("amount must large than 0");
			}
			oMultiTransactionInput
					.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(input.getAmount()))));
			oMultiTransactionInput.setCryptoToken(ByteString.copyFrom(encApi.hexDec(input.getCryptoToken())));
			oMultiTransactionInput.setNonce(input.getNonce());
			oMultiTransactionInput.setSymbol(input.getSymbol());
			oMultiTransactionInput.setToken(input.getToken());
			oMultiTransactionBody.addInputs(oMultiTransactionInput);
		}
		for (MultiTransactionOutputImpl output : oMultiTransactionBodyImpl.getOutputsList()) {
			MultiTransactionOutput.Builder oMultiTransactionOutput = MultiTransactionOutput.newBuilder();
			oMultiTransactionOutput.setAddress(ByteString.copyFrom(encApi.hexDec(output.getAddress())));
			if (new BigInteger(output.getAmount()).compareTo(BigInteger.ZERO) < 0) {
				throw new TransactionException("amount must large than 0");
			}
			oMultiTransactionOutput
					.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(output.getAmount()))));
			oMultiTransactionOutput.setCryptoToken(ByteString.copyFrom(encApi.hexDec(output.getCryptoToken())));
			oMultiTransactionOutput.setSymbol(output.getSymbol());
			oMultiTransactionBody.addOutputs(oMultiTransactionOutput);
		}
		for (MultiTransactionSignatureImpl signature : oMultiTransactionBodyImpl.getSignaturesList()) {
			MultiTransactionSignature.Builder oMultiTransactionSignature = MultiTransactionSignature.newBuilder();
			oMultiTransactionSignature.setSignature(ByteString.copyFrom(encApi.hexDec(signature.getSignature())));
			oMultiTransactionBody.addSignatures(oMultiTransactionSignature);
		}
		oMultiTransactionBody.setTimestamp(oMultiTransactionBodyImpl.getTimestamp());
		oMultiTransaction.setTxBody(oMultiTransactionBody);
		return oMultiTransaction;
	}

	/**
	 * 校验并保存交易。该方法不会执行交易，用户的账户余额不会发生变化。
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	public HashPair verifyAndSaveMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		Map<String, Account.Builder> accounts = getTransactionAccounts(oMultiTransaction);

		iTransactionActuator oiTransactionActuator = getActuator(oMultiTransaction.getTxBody().getType(), null);

		// 如果交易本身需要验证签名
		if (oiTransactionActuator.needSignature()) {
			oiTransactionActuator.onVerifySignature(oMultiTransaction.build(), accounts);
		}

		// 执行交易执行前的数据校验
		oiTransactionActuator.onPrepareExecute(oMultiTransaction.build(), accounts);

		oMultiTransaction.clearStatus();
		oMultiTransaction.clearTxHash();
		// 生成交易Hash
		oMultiTransaction.setTxHash(encApi.hexEnc(encApi.sha256Encode(oMultiTransaction.getTxBody().toByteArray())));

		if (isExistsTransaction(oMultiTransaction.getTxHash())) {
			throw new Exception("transaction exists, drop it txhash::" + oMultiTransaction.getTxHash());
		}

		MultiTransaction multiTransaction = oMultiTransaction.build();
		// 保存交易到db中

		// log.debug("====put verify and save transaction::"+
		// multiTransaction.getTxHash());
		byte hashKey[] = encApi.hexDec(multiTransaction.getTxHash());
		HashPair ret = new HashPair(hashKey, multiTransaction);
		dao.getTxsDao().put(oEntityHelper.byteKey2OKey(hashKey), oEntityHelper.byteValue2OValue(ret.getData()));
		return ret;
	}

	/**
	 * @param transactionType
	 * @return
	 */
	public iTransactionActuator getActuator(int transactionType, BlockEntity oCurrentBlock) {
		iTransactionActuator oiTransactionActuator;
		switch (TransTypeEnum.transf(transactionType)) {
		case TYPE_CreateUnionAccount:
			oiTransactionActuator = new ActuatorCreateUnionAccount(this.oAccountHelper, this, oCurrentBlock, encApi,
					dao, this.stateTrie);
			break;
		case TYPE_TokenTransaction:
			oiTransactionActuator = new ActuatorTokenTransaction(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_UnionAccountTransaction:
			oiTransactionActuator = new ActuatorUnionAccountTransaction(oAccountHelper, this, oCurrentBlock, encApi,
					dao, this.stateTrie);
			break;
		case TYPE_CallInternalFunction:
			oiTransactionActuator = new ActuatorCallInternalFunction(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_CryptoTokenTransaction:
			oiTransactionActuator = new ActuatorCryptoTokenTransaction(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_LockTokenTransaction:
			oiTransactionActuator = new ActuatorLockTokenTransaction(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_CreateContract:
			oiTransactionActuator = new ActuatorCreateContract(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_CreateToken:
			oiTransactionActuator = new ActuatorCreateToken(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_CallContract:
			oiTransactionActuator = new ActuatorCallContract(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_CreateCryptoToken:
			oiTransactionActuator = new ActuatorCreateCryptoToken(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		default:
			oiTransactionActuator = new ActuatorDefault(this.oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		}

		return oiTransactionActuator;
	}

	/**
	 * 
	 * @param oMultiTransaction
	 */
	public Map<String, Account.Builder> getTransactionAccounts(MultiTransaction.Builder oMultiTransaction) {
		Map<String, Account.Builder> accounts = new HashMap<>();
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			accounts.put(encApi.hexEnc(oInput.getAddress().toByteArray()),
					oAccountHelper.GetAccountOrCreate(oInput.getAddress()).toBuilder());
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			accounts.put(encApi.hexEnc(oOutput.getAddress().toByteArray()),
					oAccountHelper.GetAccountOrCreate(oOutput.getAddress()).toBuilder());
		}

		if (StringUtils.isNotBlank(blockChainConfig.getLock_account_address())) {
			accounts.put(blockChainConfig.getLock_account_address(),
					oAccountHelper
							.GetAccountOrCreate(
									ByteString.copyFrom(encApi.hexDec(blockChainConfig.getLock_account_address())))
							.toBuilder());
		}
		return accounts;
	}

	public byte[] getTransactionContent(MultiTransaction oTransaction) {
		// MultiTransaction newTx = oTransaction.toBuilder().
		MultiTransaction.Builder newTx = MultiTransaction.newBuilder();
		newTx.setTxBody(oTransaction.getTxBody());
		newTx.setTxHash(oTransaction.getTxHash());
		newTx.setTxNode(oTransaction.getTxNode());
		return newTx.build().toByteArray();
	}

	public void verifySignature(String pubKey, String signature, byte[] tx) throws Exception {
		if (encApi.ecVerify(pubKey, tx, encApi.hexDec(signature))) {

		} else {
			throw new Exception(String.format("签名 %s 使用公钥 %s 验证失败", pubKey, signature));
		}
	}

	public void setTransactionDone(MultiTransaction tx, ByteString result) throws Exception {
		MultiTransaction.Builder oTransaction = tx.toBuilder();
		oTransaction.setStatus("done");
		oTransaction.setResult(result);
		// log.debug("====put transaction done::"+ txHash);

		dao.getTxsDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(oTransaction.getTxHash())),
				oEntityHelper.byteValue2OValue(oTransaction.build().toByteArray()));
	}

	public void setTransactionError(MultiTransaction tx, ByteString result) throws Exception {
		MultiTransaction.Builder oTransaction = tx.toBuilder();
		oTransaction.setStatus("error");
		oTransaction.setResult(result);
		// log.debug("====put transaction error::"+ txHash);

		dao.getTxsDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(oTransaction.getTxHash())),
				oEntityHelper.byteValue2OValue(oTransaction.build().toByteArray()));
	}

	/**
	 * generate contract address by transaction
	 * 
	 * @param oMultiTransaction
	 * @return
	 */
	public ByteString getContractAddressByTransaction(MultiTransaction oMultiTransaction) throws Exception {
		if (oMultiTransaction.getTxBody().getOutputsCount() != 0
				|| oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new Exception("transaction type is wrong.");
		}
		// KeyPairs pair = encApi.genKeys(String.format("%s%s",
		// encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray()),
		// oMultiTransaction.getTxBody().getInputs(0).getNonce()));
		// log.debug(String.format("gen contract with::%s%s address::%s",
		// encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray()),
		// oMultiTransaction.getTxBody().getInputs(0).getNonce(),
		// pair.getAddress()));
		// return ByteString.copyFrom(encApi.hexDec(pair.getAddress()));
		return ByteString.copyFrom(
				encApi.sha3Encode(RLP.encodeList(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray(),
						ByteUtil.intToBytes(oMultiTransaction.getTxBody().getInputs(0).getNonce()))));
		// KeyPairs pair = encApi.genKeys();
		// byte[] addrHash = encApi.hexDec(pair.getAddress());
		// return copyOfRange(addrHash, 12, addrHash.length);
	}

	public boolean isExistsTransaction(String txHash) {
		OValue oOValue;
		try {
			oOValue = dao.getTxsDao().get(oEntityHelper.byteKey2OKey(encApi.hexDec(txHash))).get();
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