package org.brewchain.account.core.actuator;

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
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorLockTokenTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {

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

			if (tokenBalance - oInput.getAmount() - oInput.getFeeLimit() >= 0) {
				// 余额足够
			} else {
				throw new Exception(String.format("用户的账户余额 %s 不满足交易的最高限额 %s", tokenBalance,
						oInput.getAmount() + oInput.getFeeLimit()));
			}

			// 判断nonce是否一致
			int nonce = senderAccountValue.getNonce();
			if (nonce != oInput.getNonce()) {
				throw new Exception(String.format("用户的交易索引 %s 与交易的索引不一致 %s", nonce, oInput.getNonce()));
			}
		}
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {
		// TODO lock 只处理了 input，未处理 output
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
			boolean isExistToken = false;
			for (int i = 0; i < senderAccountValue.getTokensCount(); i++) {
				if (senderAccountValue.getTokens(i).getToken().equals(oInput.getToken())) {
					AccountTokenValue.Builder oAccountTokenValue = senderAccountValue.getTokens(i).toBuilder();
					oAccountTokenValue.setBalance(senderAccountValue.getTokens(i).getBalance() - oInput.getAmount());
					oAccountTokenValue.setLocked(senderAccountValue.getTokens(i).getLocked() + oInput.getAmount());
					senderAccountValue.setTokens(i, oAccountTokenValue);

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
	}

	public ActuatorLockTokenTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper, encApi, dao, oStateTrie);
	}

}
