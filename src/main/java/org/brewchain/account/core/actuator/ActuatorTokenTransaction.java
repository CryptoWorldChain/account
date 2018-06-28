package org.brewchain.account.core.actuator;

import java.util.HashMap;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.DBTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorTokenTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorTokenTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper, encApi, dao, oStateTrie);
	}

	/*
	 * 校验交易有效性。余额，索引等
	 * 
	 * @see org.brewchain.account.core.transaction.AbstractTransactionActuator#
	 * onVerify(org.brewchain.account.gens.Tx.MultiTransaction, java.util.Map,
	 * java.util.Map)
	 */
	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {
		// 交易中的Token必须一致
		String token = "";
		long inputsTotal = 0;
		long outputsTotal = 0;

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			if (oInput.getToken().isEmpty() || oInput.getToken() == "") {
				throw new Exception(String.format("Token交易中Token不允许为空"));
			}
			if (token == "") {
				token = oInput.getToken();
			} else {
				if (!token.equals(oInput.getToken())) {
					throw new Exception(String.format("交易中不允许存在多个Token %s %s ", token, oInput.getToken()));
				}
			}

			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
			long tokenBalance = 0;
			for (int i = 0; i < senderAccountValue.getTokensCount(); i++) {
				if (senderAccountValue.getTokens(i).getToken().equals(oInput.getToken())) {
					tokenBalance = senderAccountValue.getTokens(i).getBalance();
					break;
				}
			}

			inputsTotal += tokenBalance;

			if (tokenBalance < 0) {
				throw new IllegalArgumentException(String.format("账户余额 %s 小于 0，不能正常进行交易", tokenBalance));
			}

			if (oInput.getAmount() < 0) {
				throw new IllegalArgumentException(String.format("交易金额 %s 小于 0，不能正常进行交易", oInput.getAmount()));
			}

			if (tokenBalance - oInput.getAmount() < 0) {// - oInput.getFeeLimit()
				// 余额不够
				throw new Exception(String.format("用户的账户余额 %s 不满足交易的最高限额 %s", tokenBalance,
						oInput.getAmount() + oInput.getFeeLimit()));
			}

			// 判断nonce是否一致
			int nonce = senderAccountValue.getNonce();
			if (nonce != oInput.getNonce()) {
				throw new Exception(String.format("用户的交易索引 %s 与交易的索引不一致 %s", nonce, oInput.getNonce()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			if (oOutput.getAmount() < 0) {
				throw new IllegalArgumentException(String.format("交易金额 %s 小于 0， 不能正常进行交易", oOutput.getAmount()));
			}
			outputsTotal += oOutput.getAmount();
		}

		if (inputsTotal < outputsTotal) {
			throw new Exception(String.format("交易的输入 %S 小于输出 %s 金额", inputsTotal, outputsTotal));
		}
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {
		// LinkedList<OKey> keys = new LinkedList<>();
		// LinkedList<AccountValue> values = new LinkedList<>();

		String token = "";
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			token = oInput.getToken();
			boolean isExistToken = false;
			for (int i = 0; i < senderAccountValue.getTokensCount(); i++) {
				if (senderAccountValue.getTokens(i).getToken().equals(oInput.getToken())) {
					senderAccountValue.setTokens(i, senderAccountValue.getTokens(i).toBuilder()
							.setBalance(senderAccountValue.getTokens(i).getBalance() - oInput.getAmount()));
					isExistToken = true;
					break;
				}
			}
			if (!isExistToken) {
				throw new Exception(String.format("发送方账户异常，缺少token %s", oInput.getToken()));
			}

			// 不论任何交易类型，都默认执行账户余额的更改
			senderAccountValue.setBalance(senderAccountValue.getBalance() - oInput.getFee());
			senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);

			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (senderAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(senderAccountValue.getStorage().toByteArray());
			}
			oCacheTrie.put(sender.getAddress().toByteArray(), senderAccountValue.build().toByteArray());
			senderAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));

			sender.setValue(senderAccountValue);
			accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);

		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			Account.Builder receiver = accounts.get(encApi.hexEnc(oOutput.getAddress().toByteArray()));
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();

			// 不论任何交易类型，都默认执行账户余额的更改
			receiverAccountValue.setBalance(receiverAccountValue.getBalance() + oOutput.getAmount());

			boolean isExistToken = false;
			for (int i = 0; i < receiverAccountValue.getTokensCount(); i++) {
				if (receiverAccountValue.getTokens(i).getToken().equals(token)) {
					receiverAccountValue.setTokens(i, receiverAccountValue.getTokens(i).toBuilder()
							.setBalance(receiverAccountValue.getTokens(i).getBalance() + oOutput.getAmount()));
					isExistToken = true;
					break;
				}
			}

			// 如果对应账户中没有该token，则直接创建
			if (!isExistToken) {
				AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
				oAccountTokenValue.setToken(token);
				oAccountTokenValue.setBalance(oOutput.getAmount());
				receiverAccountValue.addTokens(oAccountTokenValue);
			}

			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (receiverAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(receiverAccountValue.getStorage().toByteArray());
			}
			oCacheTrie.put(receiver.getAddress().toByteArray(), receiverAccountValue.build().toByteArray());
			receiverAccountValue.setStorage(ByteString.copyFrom((oCacheTrie.getRootHash())));
			receiver.setValue(receiverAccountValue);
			accounts.put(encApi.hexEnc(receiver.getAddress().toByteArray()), receiver);
		}
	}
}
