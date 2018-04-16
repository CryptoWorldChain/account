package org.brewchain.account.core.transaction;

import java.util.LinkedList;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Act.AccountValue;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransaction.Builder;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;

import com.google.protobuf.ByteString;

public class ActuatorTokenTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorTokenTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper);
	}

	/*
	 * 校验交易有效性。余额，索引等
	 * 
	 * @see org.brewchain.account.core.transaction.AbstractTransactionActuator#
	 * onVerify(org.brewchain.account.gens.Tx.MultiTransaction, java.util.Map,
	 * java.util.Map)
	 */
	@Override
	public void onPrepareExecute(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		// 交易中的Token必须一致
		String token = "";
		long inputsTotal = 0;
		long outputsTotal = 0;

		for (MultiTransactionInput oInput : oMultiTransaction.getInputsList()) {
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
			Account sender = senders.get(oInput.getAddress());
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

		for (MultiTransactionOutput oOutput : oMultiTransaction.getOutputsList()) {
			outputsTotal += oOutput.getAmount();
		}

		if (inputsTotal < outputsTotal) {
			throw new Exception(String.format("交易的输入 %S 小于输出 %s 金额", inputsTotal, outputsTotal));
		}
	}

	@Override
	public void onExecute(Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		LinkedList<OKey> keys = new LinkedList<OKey>();
		LinkedList<OValue> values = new LinkedList<OValue>();

		String token = "";
		for (MultiTransactionInput oInput : oMultiTransaction.getInputsList()) {
			// 取发送方账户
			Account sender = senders.get(oInput.getAddress());
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			token = oInput.getToken();

			for (int i = 0; i < senderAccountValue.getTokensCount(); i++) {
				if (senderAccountValue.getTokens(i).getToken().equals(oInput.getToken())) {
					senderAccountValue.getTokens(i).toBuilder().setBalance(
							senderAccountValue.getTokens(i).getBalance() - oInput.getAmount() - oInput.getFee());
					break;
				}
			}

			keys.add(OEntityBuilder.byteKey2OKey(sender.getAddress().toByteArray()));
			values.add(OEntityBuilder.byteValue2OValue(senderAccountValue.build().toByteArray()));
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getOutputsList()) {
			Account receiver = receivers.get(oOutput.getAddress());
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();
			receiverAccountValue.setBalance(receiverAccountValue.getBalance() + oOutput.getAmount());

			for (int i = 0; i < receiverAccountValue.getTokensCount(); i++) {
				if (receiverAccountValue.getTokens(i).getToken().equals(token)) {
					receiverAccountValue.getTokens(i).toBuilder()
							.setBalance(receiverAccountValue.getTokens(i).getBalance() + oOutput.getAmount());
					break;
				}
			}

			keys.add(OEntityBuilder.byteKey2OKey(receiver.getAddress().toByteArray()));
			values.add(OEntityBuilder.byteValue2OValue(receiverAccountValue.build().toByteArray()));
		}

		oAccountHelper.BatchPutAccounts(keys, values);
	}
}
