package org.brewchain.account.core.actuator;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Act.AccountValue;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.DBTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.gens.Tx.MultiTransaction.Builder;

import com.google.protobuf.ByteString;

public abstract class AbstractTransactionActuator implements iTransactionActuator {

	protected LinkedList<OKey> keys = new LinkedList<>();
	protected LinkedList<AccountValue> values = new LinkedList<>();

	protected LinkedList<OKey> txKeys = new LinkedList<>();
	protected LinkedList<MultiTransaction> txValues = new LinkedList<>();

	@Override
	public LinkedList<OKey> getKeys() {
		return keys;
	}

	@Override
	public LinkedList<AccountValue> getValues() {
		return values;
	}

	@Override
	public LinkedList<OKey> getTxKeys() {
		return txKeys;
	}

	@Override
	public LinkedList<MultiTransaction> getTxValues() {
		return txValues;
	}

	@Override
	public void onVerifySignature(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		// 获取交易原始encode
		MultiTransaction.Builder signatureTx = oMultiTransaction.toBuilder();
		MultiTransactionBody.Builder txBody = signatureTx.getTxBodyBuilder();
		signatureTx.setTxHash(ByteString.EMPTY);
		txBody = txBody.clearSignatures();
		byte[] oMultiTransactionEncode = txBody.build().toByteArray();
		// 校验交易签名
		for (MultiTransactionSignature oMultiTransactionSignature : oMultiTransaction.getTxBody().getSignaturesList()) {
			if (encApi.ecVerify(oMultiTransactionSignature.getPubKey(), oMultiTransactionEncode,
					encApi.hexDec(oMultiTransactionSignature.getSignature()))) {
			} else {
				throw new Exception(String.format("签名 %s 使用公钥 %s 验证失败", oMultiTransactionSignature.getSignature(),
						oMultiTransactionSignature.getPubKey()));
			}
		}
	}

	protected AccountHelper oAccountHelper;
	protected TransactionHelper oTransactionHelper;
	protected BlockHelper oBlockHelper;
	protected EncAPI encApi;
	protected DefDaos dao;
	protected StateTrie oStateTrie;

	public AbstractTransactionActuator(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		this.oAccountHelper = oAccountHelper;
		this.oTransactionHelper = oTransactionHelper;
		this.oBlockHelper = oBlockHelper;
		this.encApi = encApi;
		this.dao = dao;
		this.oStateTrie = oStateTrie;
	}

	@Override
	public boolean needSignature() {
		return true;
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		int inputsTotal = 0;
		int outputsTotal = 0;

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			inputsTotal += oInput.getAmount();

			// 取发送方账户
			Account sender = senders.get(oInput.getAddress());
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			// 判断发送方余额是否足够
			long balance = senderAccountValue.getBalance();

			if (balance < 0) {
				throw new Exception(String.format("发送金额 %s 小于 0, 不能继续交易", balance));
			}

			if (balance - oInput.getAmount() - oInput.getFeeLimit() >= 0) {
				// 余额足够
			} else {
				throw new Exception(
						String.format("用户的账户余额 %s 不满足交易的最高限额 %s", balance, oInput.getAmount() + oInput.getFeeLimit()));
			}

			// 判断nonce是否一致
			int nonce = senderAccountValue.getNonce();
			if (nonce != oInput.getNonce()) {
				throw new Exception(String.format("用户的交易索引 %s 与交易的索引不一致 %s", nonce, oInput.getNonce()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			outputsTotal += oOutput.getAmount();

			long balance = oOutput.getAmount();
			if (balance < 0) {
				throw new Exception(String.format("接收金额 %s 小于0, 不能继续交易", balance));
			}

			// 取接收方账户
			if (!receivers.containsKey(oOutput.getAddress())) {

			}
		}

		if (inputsTotal < outputsTotal) {
			throw new Exception(String.format("交易的输入 %S 小于输出 %s 金额", inputsTotal, outputsTotal));
		}
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {

		LinkedList<OKey> keys = new LinkedList<>();
		LinkedList<AccountValue> values = new LinkedList<>();

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取发送方账户
			Account sender = senders.get(oInput.getAddress());
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			senderAccountValue.setBalance(senderAccountValue.getBalance() - oInput.getAmount() );

			senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (senderAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(senderAccountValue.getStorage().toByteArray());
			}
			oCacheTrie.put(sender.getAddress().toByteArray(), senderAccountValue.build().toByteArray());
			senderAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));
			keys.add(OEntityBuilder.byteKey2OKey(sender.getAddress().toByteArray()));
			values.add(senderAccountValue.build());
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			Account receiver = receivers.get(oOutput.getAddress());
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();
			receiverAccountValue.setBalance(receiverAccountValue.getBalance() + oOutput.getAmount());

			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (receiverAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(receiverAccountValue.getStorage().toByteArray());
			}
			oCacheTrie.put(receiver.getAddress().toByteArray(), receiverAccountValue.build().toByteArray());
			receiverAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));
			keys.add(OEntityBuilder.byteKey2OKey(receiver.getAddress().toByteArray()));
			values.add(receiverAccountValue.build());
		}

		this.keys.addAll(keys);
		this.values.addAll(values);
		// oAccountHelper.BatchPutAccounts(keys, values);
	}

	@Override
	public void onExecuteDone(MultiTransaction oMultiTransaction) throws Exception {
		oTransactionHelper.setTransactionDone(oMultiTransaction.getTxHash().toByteArray());
	}

	@Override
	public void onExecuteError(MultiTransaction oMultiTransaction) throws Exception {
		oTransactionHelper.setTransactionError(oMultiTransaction.getTxHash().toByteArray());
	}
}
