package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static java.util.Arrays.copyOfRange;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Validate;
import org.brewchain.account.bean.HashPair;
import org.brewchain.account.core.actuator.AbstractTransactionActuator;
import org.brewchain.account.core.actuator.ActuatorCallContract;
import org.brewchain.account.core.actuator.ActuatorCallInternalFunction;
import org.brewchain.account.core.actuator.ActuatorCreateContract;
import org.brewchain.account.core.actuator.ActuatorCreateCryptoToken;
import org.brewchain.account.core.actuator.ActuatorCreateToken;
import org.brewchain.account.core.actuator.ActuatorCreateUnionAccount;
import org.brewchain.account.core.actuator.ActuatorCryptoTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorDefault;
import org.brewchain.account.core.actuator.ActuatorLockTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorSanctionTransaction;
import org.brewchain.account.core.actuator.ActuatorTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorUnionAccountTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorUnionAccountTransaction;
import org.brewchain.account.core.actuator.iTransactionActuator;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.exception.TransactionException;
import org.brewchain.account.gens.Tximpl.MultiTransactionBodyImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionInputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionNodeImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionOutputImpl;
import org.brewchain.account.gens.Tximpl.MultiTransactionSignatureImpl;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
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
public class TransactionHelper implements ActorService, Runnable {
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

	// @ActorRequire(name = "WaitBlock_HashMapDB", scope = "global")
	// WaitBlockHashMapDB oPendingHashMapDB; // 保存待打包block的交易

	@ActorRequire(name = "ConfirmTxHashDB", scope = "global")
	ConfirmTxHashMapDB oConfirmMapDB; // 保存待打包block的交易

	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
	PropHelper prop = new PropHelper(null);
	Cache<String, MultiTransaction> txDBCacheByHash = CacheBuilder.newBuilder()
			.initialCapacity(prop.get("org.brewchain.account.cache.tx.init", 10000))
			.expireAfterAccess(600, TimeUnit.SECONDS)
			.maximumSize(prop.get("org.brewchain.account.cache.tx.max", 100000))
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).build();

	PendingQueue<HashPair> queue = new PendingQueue<HashPair>("txaccept",
			prop.get("org.brewchain.account.tx.accept", 100000));

	public boolean isStop = false;

	@Invalidate
	public void invalid() {
		isStop = true;
		synchronized (this) {
			try {
				this.notifyAll();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		queue.shutdown();
	}

	@Validate
	public void startup() {
		log.info("about to start Tx");
		new Thread(this).start();
	}

	@Override
	public void run() {
		try {
			Thread.sleep(3000);
			// boot up wait
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		while (!isStop) {
			try {
				if (oConfirmMapDB != null && oConfirmMapDB.size() < oConfirmMapDB.getMaxElementsInMemory()
						&& oConfirmMapDB.getConfirmQueue().size() < oConfirmMapDB.getMaxElementsInMemory()) {
					List<HashPair> hps = queue
							.poll(Math.min(oConfirmMapDB.getMaxElementsInMemory() - oConfirmMapDB.size(), 10000));
					if (hps != null && hps.size() > 0) {
						for (HashPair hp : hps) {
							oSendingHashMapDB.put(hp.getKey(), hp);
							oConfirmMapDB.confirmTx(hp, BigInteger.ZERO);
						}
						dao.getStats().signalAcceptTx(hps.size());
					}
				}
			} catch (Throwable t) {
				log.error("error in tx to pending:", t);
			}
			try {
				if (queue.getCounter().getPtr_sending().get() < queue.getCounter().getPtr_pending().get()) {
					Thread.sleep(100);
				} else {
					Thread.sleep(2000);
				}
			} catch (Throwable e) {
			}
		}
	}

	/**
	 * 保存交易方法。 交易不会立即执行，而是等待被广播和打包。只有在Block中的交易，才会被执行。 交易签名规则 1. 清除signatures 2.
	 * txHash=ByteString.EMPTY 3. 签名内容=oMultiTransaction.toByteArray()
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	public HashPair CreateMultiTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		if (KeyConstant.node == null) {
			return null;
		}
		MultiTransactionNode.Builder oNode = MultiTransactionNode.newBuilder();
		oNode.setBcuid(KeyConstant.node.getBcuid());
		oNode.setAddress(KeyConstant.node.getoAccount().getAddress());
		oNode.setNode(KeyConstant.node.getNode());
		oMultiTransaction.setTxNode(oNode);

		HashPair hp = verifyAndSaveMultiTransaction(oMultiTransaction);

		hp.setNeedBroadCast(true);

		queue.addElement(hp);
		// synchronized (this) {
		// try {
		// this.notifyAll();
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		// oSendingHashMapDB.put(hp.getKey(), hp);
		//
		// oConfirmMapDB.confirmTx(hp, BigInteger.ZERO);
		//
		// dao.getStats().signalAcceptTx();
		
		log.info(String.format("%s %s %s %s 创建交易[%s]", KeyConstant.node.getNode(), "account", "create", "transaction",
				hp.getKey()));

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
		TXStatus.setDone(oMultiTransaction);
		MultiTransaction multiTransaction = oMultiTransaction.build();
		// 保存交易到db中
		// log.debug("====put genesis transaction::"+
		// multiTransaction.getTxHash());
		dao.getStats().signalAcceptTx(1);

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
	public void syncTransaction(MultiTransaction.Builder oMultiTransaction, BigInteger bits) throws Exception {
		syncTransaction(oMultiTransaction, true, bits);
	}

	public void syncTransaction(MultiTransaction.Builder oMultiTransaction) throws Exception {
		syncTransaction(oMultiTransaction, true, zeroBits);
	}

	BigInteger zeroBits = new BigInteger("0");

	public void syncTransaction(MultiTransaction.Builder oMultiTransaction, boolean isBroadCast) {
		syncTransaction(oMultiTransaction, isBroadCast, zeroBits);
	}

	public void syncTransaction(MultiTransaction.Builder oMultiTransaction, boolean isBroadCast, BigInteger bits) {
		try {

			// dao.getStats().signalAcceptTx();
			log.debug("sync tx count x::" + oMultiTransaction.getTxHash());
			// MultiTransaction cacheTx =
			// txDBCacheByHash.getIfPresent(oMultiTransaction.getTxHash());
			// if (cacheTx != null) {
			// log.warn("transaction " + oMultiTransaction.getTxHash() + "exists
			// in Cached, drop it");
			// // oConfirmMapDB.confirmTx(cacheTx.getTxHash(), bits, false);
			// return;
			// }
			//
			// byte keyByte[] = encApi.hexDec(oMultiTransaction.getTxHash());
			// OKey key = oEntityHelper.byteKey2OKey(keyByte);
			//
			// OValue oValue = dao.getTxsDao().get(key).get();

			MultiTransaction cacheTx = GetTransaction(oMultiTransaction.getTxHash());
			if (cacheTx != null) {
				log.warn("transaction " + oMultiTransaction.getTxHash() + "exists in DB, drop it");
				// oConfirmMapDB.confirmTx(oMultiTransaction.getTxHash(), bits,
				// false);
			} else {
				oMultiTransaction.clearStatus();
				oMultiTransaction.clearResult();

				iTransactionActuator oiTransactionActuator = getActuator(oMultiTransaction.getTxBody().getType(), null);
				if (oiTransactionActuator.needSignature()) {
					Map<String, Account.Builder> accounts = null;
					if (oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_UnionAccountTransaction.value()
							|| oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_UnionAccountTokenTransaction.value()) {
						accounts = getTransactionAccounts(oMultiTransaction);
					}
					oiTransactionActuator.onVerifySignature(oMultiTransaction.build(), accounts);
				}

				byte keyByte[] = encApi.hexDec(oMultiTransaction.getTxHash());
				OKey key = oEntityHelper.byteKey2OKey(keyByte);
				byte validKeyByte[] = encApi.sha256Encode(oMultiTransaction.getTxBody().toByteArray());
				if (!FastByteComparisons.equal(validKeyByte, keyByte)) {
					log.error("fail to sync transaction::" + oMultiTransaction.getTxHash() + ", content invalid");
				} else {
					MultiTransaction mt = oMultiTransaction.build();

					HashPair hp = new HashPair(mt.getTxHash(), mt.toByteArray(), mt);
					hp.setNeedBroadCast(isBroadCast);
					dao.getTxsDao().put(key, OValue.newBuilder().setExtdata(ByteString.copyFrom(hp.getData()))
							.setInfo(mt.getTxHash()).build());
					txDBCacheByHash.put(hp.getKey(), hp.getTx());

					dao.getStats().signalSyncTx();
					if (isBroadCast) {
						oConfirmMapDB.confirmTx(hp, bits);
					}
				}
			}
		} catch (Exception e) {
			log.error("fail to sync transaction::" + oMultiTransaction.getTxHash() + " error::" + e, e);
		}
	}

	public void syncTransactionBatch(List<MultiTransaction.Builder> oMultiTransaction, BigInteger bits)
			throws Exception {
		syncTransactionBatch(oMultiTransaction, true, bits);
	}

	public boolean containConfirm(String txhash, int bit) {
		HashPair hp = oConfirmMapDB.getHP(txhash);
		if (hp != null) {
			return hp.getBits().testBit(bit);
		}
		return false;
	}

	public void syncTransactionBatch(List<MultiTransaction.Builder> oMultiTransaction, boolean isBroadCast,
			BigInteger bits) {
		if (oMultiTransaction.size() > 0) {

			// log.error("start batch put count::" + oMultiTransaction.size() +
			// " isBroadCast::" + isBroadCast + " bits::" + bits);
			List<OKey> keys = new ArrayList<>();
			List<OValue> values = new ArrayList<>();
			for (MultiTransaction.Builder mtb : oMultiTransaction) {
				// log.debug("sync tx count y::" + mtb.getTxHash());
				try {
					// dao.getStats().signalAcceptTx();
					// MultiTransaction cacheTx =
					// txDBCacheByHash.getIfPresent(mtb.getTxHash());
					MultiTransaction cacheTx = GetTransaction(mtb.getTxHash());
					boolean isDone = false;
					if (cacheTx != null) {
						isDone = TXStatus.isDone(cacheTx);
					}
					if (!isDone) {
						MultiTransaction mt = mtb.clearStatus().clearResult().build();
						if (cacheTx == null) {
							iTransactionActuator oiTransactionActuator = getActuator(mt.getTxBody().getType(), null);
							if (oiTransactionActuator.needSignature()) {
								Map<String, Account.Builder> accounts = null;
								if (mt.getTxBody().getType() == TransTypeEnum.TYPE_UnionAccountTransaction.value()
										|| mt.getTxBody().getType() == TransTypeEnum.TYPE_UnionAccountTokenTransaction
												.value()) {
									accounts = getTransactionAccounts(mt);
								}
								oiTransactionActuator.onVerifySignature(mt, accounts);
							}
							ByteString mts = mt.toByteString();
							HashPair hp = new HashPair(mt.getTxHash(), mts.toByteArray(), mt);
							hp.setNeedBroadCast(false);
							keys.add(oEntityHelper.byteKey2OKey(encApi.hexDec(mtb.getTxHash())));
							values.add(OValue.newBuilder().setExtdata(mts).setInfo(mtb.getTxHash()).build());
							if (isBroadCast) {
								oConfirmMapDB.confirmTx(hp, bits);
							}
							txDBCacheByHash.put(hp.getKey(), hp.getTx());
							dao.getStats().signalSyncTx();
						} else if (!isDone) {
							oConfirmMapDB.confirmTx(mtb.getTxHash(), bits);
						}
					}
				} catch (Exception e) {
					log.error("fail to sync transaction::" + oMultiTransaction.size() + " error::" + e, e);
				}
			}

			try {
				OKey ks[] = new OKey[keys.size()];
				OValue vs[] = new OValue[keys.size()];
				for (int i = 0; i < ks.length; i++) {
					ks[i] = keys.get(i);
					vs[i] = values.get(i);
				}
				// log.error("batch save
				// tx:ks.size="+ks.length+",vs.size="+vs.length);
				Future<OValue[]> f = dao.getTxsDao().batchPuts(ks, vs);// 返回DB里面不存在的,但是数据库已经存进去的
				// if (f != null && f.get() != null) {
				// log.debug("sync tx:: batch::" + keys.size() + " new::" +
				// f.get().length);
				// for (OValue ov : f.get()) {
				// if (ov != null) {
				//
				// }
				// }
				// }
			} catch (Exception e) {
				log.error("fail to sync transaction::" + oMultiTransaction.size() + " error::" + e, e);
			}
		}
	}

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
			// log.debug("start sync tx getWaitSendTxToSend::" + item.getKey());
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
	public List<MultiTransaction> getWaitBlockTx(int count, int confirmTimes) {
		// log.debug("start sync tx count::" + count + " confirmTimes::" +
		// confirmTimes);
		return oConfirmMapDB.poll(count, confirmTimes);
	}

	public void confirmRecvTx(String key, BigInteger fromBits) {
		try {
			MultiTransaction otx = GetTransaction(key);
			if (otx != null && otx.getTxBody() != null) {
				if (TXStatus.isDone(otx)) {
					return;
				}
			}
			oConfirmMapDB.confirmTx(key, fromBits);
		} catch (Exception e) {
			log.error("error in confirmRecvTx:" + key + ",fromBits=" + fromBits, e);
		}
	}

	public HashPair removeWaitingSendOrBlockTx(String txHash) throws InvalidProtocolBufferException {
		// HashPair hpBlk = oPendingHashMapDB.getStorage().remove(txHash);
		HashPair hpBlk = oConfirmMapDB.invalidate(txHash);
		HashPair hpSend = oSendingHashMapDB.getStorage().remove(txHash);
		if (hpBlk != null && hpBlk.getTx() != null) {
			return hpBlk;
		} else {
			return hpSend;
		}
	}

	public boolean isExistsWaitBlockTx(String txHash) throws InvalidProtocolBufferException {
		// return oPendingHashMapDB.getStorage().containsKey(txHash);
		return oConfirmMapDB.containsKey(txHash);
	}

	/**
	 * 根据交易Hash，返回交易实体。
	 * 
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	public MultiTransaction GetTransaction(String txHash) throws Exception {
		if (StringUtils.isBlank(txHash)) {
			return null;
		}
		MultiTransaction cacheTx = txDBCacheByHash.getIfPresent(txHash);
		if (cacheTx != null) {
			return cacheTx;
		}
		// if (!transactionHelper.isExistsWaitBlockTx(txHash)
		// && !transactionHelper.isExistsTransaction(txHash)) {
		HashPair hp = oConfirmMapDB.getHP(txHash);
		if (hp != null && hp.getTx() != null) {
			txDBCacheByHash.put(txHash, hp.getTx());
			return hp.getTx();
		}

		OValue oValue = dao.getTxsDao().get(oEntityHelper.byteKey2OKey(encApi.hexDec(txHash))).get();

		if (oValue == null || oValue.getExtdata() == null) {
			// throw new Exception(String.format("没有找到hash %s 的交易数据", txHash));
			// log.error("txHash not found:" + txHash);
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

		if (TXStatus.isDone(oTransaction)) {
			oMultiTransactionImpl.setResult(encApi.hexEnc(oTransaction.getResult().toByteArray()));
		} else {
			oMultiTransactionImpl.setResult(oTransaction.getResult().toStringUtf8());
		}

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

		MultiTransactionNode oMultiTransactionNode = oTransaction.getTxNode();

		MultiTransactionNodeImpl.Builder oNode = MultiTransactionNodeImpl.newBuilder();
		oNode.setBcuid(oMultiTransactionNode.getBcuid());
		oNode.setNode(oMultiTransactionNode.getNode());
		oMultiTransactionImpl.setNode(oNode);
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
			if (StringUtils.isNotBlank(output.getAmount())) {
				if (new BigInteger(output.getAmount()).compareTo(BigInteger.ZERO) < 0) {
					throw new TransactionException("amount must large than 0");
				}
				oMultiTransactionOutput
						.setAmount(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(new BigInteger(output.getAmount()))));
				oMultiTransactionOutput.setCryptoToken(ByteString.copyFrom(encApi.hexDec(output.getCryptoToken())));
				oMultiTransactionOutput.setSymbol(output.getSymbol());
			} else {
				oMultiTransactionOutput.setAmount(ByteString.copyFrom(ByteUtil.intToBytes(0)));
				// oMultiTransactionOutput.setCryptoToken(ByteString.copyFrom(encApi.hexDec(output.getCryptoToken())));
				oMultiTransactionOutput.setSymbol(output.getSymbol());

			}
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
		HashPair ret = new HashPair(multiTransaction.getTxHash(), multiTransaction.toByteArray(), multiTransaction);
		dao.getTxsDao().put(oEntityHelper.byteKey2OKey(ret.getKeyBytes()),
				oEntityHelper.byteValue2OValue(ret.getData()));
		return ret;
	}

	public void resetActuator(iTransactionActuator ata, BlockEntity oCurrentBlock) {
		((AbstractTransactionActuator) ata).reset(this.oAccountHelper, this, oCurrentBlock, encApi, dao,
				this.stateTrie);
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
		case TYPE_Sanction:
			oiTransactionActuator = new ActuatorSanctionTransaction(oAccountHelper, this, oCurrentBlock, encApi, dao,
					this.stateTrie);
			break;
		case TYPE_UnionAccountTokenTransaction:
			oiTransactionActuator = new ActuatorUnionAccountTokenTransaction(oAccountHelper, this, oCurrentBlock,
					encApi, dao, this.stateTrie);
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
					oAccountHelper.GetAccountOrCreate(oInput.getAddress()));
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			accounts.put(encApi.hexEnc(oOutput.getAddress().toByteArray()),
					oAccountHelper.GetAccountOrCreate(oOutput.getAddress()));
		}

		if ((oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateContract.value()
				|| oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateCryptoToken.value()
				|| oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateToken.value())
				&& StringUtils.isNotBlank(blockChainConfig.getLock_account_address())) {
			accounts.put(blockChainConfig.getLock_account_address(), oAccountHelper.GetAccountOrCreate(
					ByteString.copyFrom(encApi.hexDec(blockChainConfig.getLock_account_address()))));
		}
		return accounts;
	}

	public Map<String, Account.Builder> getTransactionAccounts(MultiTransaction oMultiTransaction) {
		Map<String, Account.Builder> accounts = new HashMap<>();
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			accounts.put(encApi.hexEnc(oInput.getAddress().toByteArray()),
					oAccountHelper.GetAccountOrCreate(oInput.getAddress()));
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			accounts.put(encApi.hexEnc(oOutput.getAddress().toByteArray()),
					oAccountHelper.GetAccountOrCreate(oOutput.getAddress()));
		}

		if ((oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateContract.value()
				|| oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateCryptoToken.value()
				|| oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateToken.value())
				&& StringUtils.isNotBlank(blockChainConfig.getLock_account_address())) {
			accounts.put(blockChainConfig.getLock_account_address(), oAccountHelper.GetAccountOrCreate(
					ByteString.copyFrom(encApi.hexDec(blockChainConfig.getLock_account_address()))));
		}
		return accounts;
	}

	public Map<String, Account.Builder> merageTransactionAccounts(MultiTransaction.Builder oMultiTransaction,
			Map<String, Account.Builder> current) {
		return merageTransactionAccounts(oMultiTransaction.build(), current);
	}

	public Map<String, Account.Builder> merageTransactionAccounts(MultiTransaction oMultiTransaction,
			Map<String, Account.Builder> current) {
		// Map<String, Account.Builder> accounts = new HashMap<>();
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			String key = encApi.hexEnc(oInput.getAddress().toByteArray());
			if (!current.containsKey(key)) {
				current.put(key, oAccountHelper.GetAccountOrCreate(oInput.getAddress()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			String key = encApi.hexEnc(oOutput.getAddress().toByteArray());
			if (!current.containsKey(key)) {
				current.put(key, oAccountHelper.GetAccountOrCreate(oOutput.getAddress()));
			}
		}

		if ((oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateContract.value()
				|| oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateCryptoToken.value()
				|| oMultiTransaction.getTxBody().getType() == TransTypeEnum.TYPE_CreateToken.value())
				&& StringUtils.isNotBlank(blockChainConfig.getLock_account_address())) {
			if (!current.containsKey(blockChainConfig.getLock_account_address())) {
				current.put(blockChainConfig.getLock_account_address(), oAccountHelper.GetAccountOrCreate(
						ByteString.copyFrom(encApi.hexDec(blockChainConfig.getLock_account_address()))));
			}
		}
		return current;
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

	public void setTransactionDone(MultiTransaction tx, BlockEntity be, ByteString result) throws Exception {
		MultiTransaction.Builder oTransaction = tx.toBuilder();
		TXStatus.setDone(oTransaction, result);
		txDBCacheByHash.put(oTransaction.getTxHash(), oTransaction.build());

		dao.getTxsDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(oTransaction.getTxHash())), oEntityHelper
				.byteValue2OValue(oTransaction.build().toByteArray(), String.valueOf(be.getHeader().getNumber())));
	}

	public void setTransactionError(MultiTransaction tx, BlockEntity be, ByteString result) throws Exception {
		MultiTransaction.Builder oTransaction = tx.toBuilder();
		TXStatus.setError(oTransaction, result);

		txDBCacheByHash.put(oTransaction.getTxHash(), oTransaction.build());

		dao.getTxsDao().put(oEntityHelper.byteKey2OKey(encApi.hexDec(oTransaction.getTxHash())), oEntityHelper
				.byteValue2OValue(oTransaction.build().toByteArray(), String.valueOf(be.getHeader().getNumber())));
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

		byte[] addr = encApi
				.sha3Encode(RLP.encodeList(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray(),
						ByteUtil.intToBytes(oMultiTransaction.getTxBody().getInputs(0).getNonce())));
		return ByteString.copyFrom(copyOfRange(addr, 12, addr.length));
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
