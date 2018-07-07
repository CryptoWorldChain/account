package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.DBTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorTokenTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorTokenTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	/*
	 * 校验交易有效性。余额，索引等
	 * 
	 * @see org.brewchain.account.core.transaction.AbstractTransactionActuator#
	 * onVerify(org.brewchain.account.gens.Tx.MultiTransaction, java.util.Map,
	 * java.util.Map)
	 */
	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		// 交易中的Token必须一致
		String token = "";
		BigInteger inputsTotal = BigInteger.ZERO;
		BigInteger outputsTotal = BigInteger.ZERO;

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			if (oInput.getToken().isEmpty() || oInput.getToken() == "") {
				throw new Exception(String.format("token must not be empty"));
			}
			if (token == "") {
				token = oInput.getToken();
			} else {
				if (!token.equals(oInput.getToken())) {
					throw new Exception(String.format("not allow multi token %s %s", token, oInput.getToken()));
				}
			}

			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
			BigInteger tokenBalance = BigInteger.ZERO;
			for (int i = 0; i < senderAccountValue.getTokensCount(); i++) {
				if (senderAccountValue.getTokens(i).getToken().equals(oInput.getToken())) {
					tokenBalance = tokenBalance.add(
							ByteUtil.bytesToBigInteger(senderAccountValue.getTokens(i).getBalance().toByteArray()));
					break;
				}
			}

			inputsTotal = inputsTotal.add(tokenBalance);

			if (tokenBalance.compareTo(BigInteger.ZERO) == -1) {
				throw new IllegalArgumentException(String.format("sender balance %s less than 0", tokenBalance));
			}

			if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) == -1) {
				throw new IllegalArgumentException(
						String.format("transaction value %s less than 0", ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())));
			}

			if (tokenBalance.subtract(ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()))
					.compareTo(BigInteger.ZERO) == -1) {// - oInput.getFeeLimit()
				// 余额不够
				throw new Exception(String.format("sender balance %s less than %s", tokenBalance, ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())));
			}

			// 判断nonce是否一致
			int nonce = senderAccountValue.getNonce();
			if (nonce != oInput.getNonce()) {
				throw new Exception(String.format("sender nonce %s is not equal with transaction nonce %s", nonce,
						oInput.getNonce()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			if (ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) == -1) {
				throw new IllegalArgumentException(
						String.format("receive balance %s less than 0", ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray())));
			}
			outputsTotal = ByteUtil.bytesAdd(outputsTotal, oOutput.getAmount().toByteArray());
		}

		if (inputsTotal.compareTo(outputsTotal) == -1) {
			throw new Exception(String.format("transaction value %s less than %s", inputsTotal, outputsTotal));
		}
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
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
					senderAccountValue.setTokens(i,
							senderAccountValue.getTokens(i).toBuilder()
									.setBalance(ByteString.copyFrom(ByteUtil.bytesSubToBytes(
											senderAccountValue.getTokens(i).getBalance().toByteArray(),
											oInput.getAmount().toByteArray()))));
					isExistToken = true;
					break;
				}
			}
			if (!isExistToken) {
				throw new Exception(String.format("cannot found token %s in sender account", oInput.getToken()));
			}

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

			boolean isExistToken = false;
			for (int i = 0; i < receiverAccountValue.getTokensCount(); i++) {
				if (receiverAccountValue.getTokens(i).getToken().equals(token)) {
					receiverAccountValue
							.setTokens(i,
									receiverAccountValue.getTokens(i).toBuilder()
											.setBalance(ByteString.copyFrom(ByteUtil.bytesAddToBytes(
													receiverAccountValue.getTokens(i).getBalance().toByteArray(),
													oOutput.getAmount().toByteArray()))));
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

		return ByteString.EMPTY;
	}
}
