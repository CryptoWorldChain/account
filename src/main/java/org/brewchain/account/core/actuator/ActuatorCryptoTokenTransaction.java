package org.brewchain.account.core.actuator;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.DBTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCryptoTokenTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCryptoTokenTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
		// TODO Auto-generated constructor stub
	}

	/**
	 * 不校验发送方和接收方的balance的一致性
	 * 
	 * @param oMultiTransaction
	 * @param senders
	 * @param receivers
	 * @throws Exception
	 */
	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		// if (oMultiTransaction.getTxBody().getOutputsCount() !=
		// oMultiTransaction.getTxBody().getInputsCount()) {
		// throw new TransactionExecuteException("parameter invalid, inputs
		// count not equal with outputs count");
		// }

		List<String> inputSymbol = new ArrayList<>();
		List<String> inputTokens = new ArrayList<>();

		String existsSender = "";
		for (int i = 0; i < oMultiTransaction.getTxBody().getInputsCount(); i++) {
			boolean isTokenExists = false;
			MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(i);
			if (StringUtils.isBlank(existsSender)) {
				existsSender = encApi.hexEnc(oInput.getAddress().toByteArray());
			}
			if (!existsSender.equals(encApi.hexEnc(oInput.getAddress().toByteArray()))) {
				throw new TransactionExecuteException("parameter invalid, sender address must be unique");
			}
			inputSymbol.add(oInput.getSymbol());
			if (inputTokens.contains(encApi.hexEnc(oInput.getCryptoToken().toByteArray()))) {
				throw new TransactionExecuteException("parameter invalid, duplicate token");
			} else
				inputTokens.add(encApi.hexEnc(oInput.getCryptoToken().toByteArray()));

			Account.Builder oAccount = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue oAccountValue = oAccount.getValue();

			if (oInput.getCryptoToken() != null && !oInput.getCryptoToken().equals(ByteString.EMPTY)) {
				for (int j = 0; j < oAccountValue.getCryptosCount(); j++) {
					if (oAccountValue.getCryptos(j).getSymbol().equals(oInput.getSymbol())) {
						AccountCryptoValue oAccountCryptoValue = oAccountValue.getCryptos(j);
						for (int k = 0; k < oAccountCryptoValue.getTokensCount(); k++) {
							if (oAccountCryptoValue.getTokens(k).getHash().equals(oInput.getCryptoToken())) {
								isTokenExists = true;
								break;
							}
						}
					}
					if (isTokenExists) {
						break;
					}
				}
				if (!isTokenExists) {
					throw new TransactionExecuteException(
							String.format("parameter invalid, input %s not found token [%s] with hash [%s]",
									encApi.hexEnc(oInput.getAddress().toByteArray()), oInput.getSymbol(),
									encApi.hexEnc(oInput.getCryptoToken().toByteArray())));
				}

				boolean isExistsOutput = false;
				for (int j = 0; j < oMultiTransaction.getTxBody().getOutputsCount(); j++) {
					MultiTransactionOutput oOutput = oMultiTransaction.getTxBody().getOutputs(j);
					if (oOutput.getSymbol().equals(oInput.getSymbol()) && FastByteComparisons
							.equal(oOutput.getCryptoToken().toByteArray(), oInput.getCryptoToken().toByteArray())) {
						isExistsOutput = true;
						break;
					}
				}
				if (!isExistsOutput) {
					throw new TransactionExecuteException(
							String.format("parameter invalid, not found token %s with hash %s in output list",
									oInput.getSymbol(), encApi.hexEnc(oInput.getCryptoToken().toByteArray())));
				}
			}
		}

		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		Map<String, AccountCryptoToken> tokens = new HashMap<String, AccountCryptoToken>();

		boolean isIncreaseNonce = false;
		for (int i = 0; i < oMultiTransaction.getTxBody().getInputsCount(); i++) {
			MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(i);

			// tokens.put(oMultiTransaction.get, value);
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder oAccountValue = sender.getValue().toBuilder();

			oAccountValue.setBalance(ByteString.copyFrom(ByteUtil
					.bytesSubToBytes(oAccountValue.getBalance().toByteArray(), oInput.getAmount().toByteArray())));

			if (oInput.getCryptoToken() != null && !oInput.getCryptoToken().equals(ByteString.EMPTY)) {
				for (int k = 0; k < oAccountValue.getCryptosCount(); k++) {

					if (oAccountValue.getCryptosList().get(k).getSymbol().equals(oInput.getSymbol())) {
						AccountCryptoValue.Builder value = oAccountValue.getCryptosList().get(k).toBuilder();

						for (int j = 0; j < value.getTokensCount(); j++) {
							if (value.getTokensBuilderList().get(j).getHash().equals(oInput.getCryptoToken())) {
								tokens.put(encApi.hexEnc(value.getTokensBuilderList().get(j).getHash().toByteArray()),
										value.getTokensBuilderList().get(j).build());

								value.removeTokens(j);
								break;
							}
						}
						oAccountValue.setCryptos(k, value);
						break;
					}
				}
			}

			if (!isIncreaseNonce) {
				oAccountValue.setNonce(oAccountValue.getNonce() + 1);
				isIncreaseNonce = true;
			}

//			DBTrie oCacheTrie = new DBTrie(this.dao, oTransactionHelper.getOEntityHelper());
//			if (oAccountValue.getStorage() == null) {
//				oCacheTrie.setRoot(null);
//			} else {
//				oCacheTrie.setRoot(oAccountValue.getStorage().toByteArray());
//			}
//			oCacheTrie.put(oInput.getAddress().toByteArray(), oAccountValue.build().toByteArray());
//			oAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));

			// keys.add(OEntityBuilder.byteKey2OKey(oInput.getAddress().toByteArray()));
			// values.add(oAccountValue.build());
			sender.setValue(oAccountValue);
			accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);
			// this.accountValues.put(encApi.hexEnc(oInput.getAddress().toByteArray()),
			// oAccountValue.build());
		}

		// 接收方增加balance
		for (int i = 0; i < oMultiTransaction.getTxBody().getOutputsCount(); i++) {
			MultiTransactionOutput oOutput = oMultiTransaction.getTxBody().getOutputs(i);

			Account.Builder receiver = accounts.get(encApi.hexEnc(oOutput.getAddress().toByteArray()));
			if (receiver == null) {
				receiver = oAccountHelper.CreateAccount(oOutput.getAddress()).toBuilder();
			}
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();

			receiverAccountValue.setBalance(ByteString.copyFrom(ByteUtil
					.bigIntegerToBytes(ByteUtil.bytesToBigInteger(receiverAccountValue.getBalance().toByteArray())
							.add(ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray())))));

			if (oOutput.getCryptoToken() != null && !oOutput.getCryptoToken().equals(ByteString.EMPTY)) {
				boolean isExistToken = false;
				for (int k = 0; k < receiverAccountValue.getCryptosCount(); k++) {
					if (receiverAccountValue.getCryptosList().get(k).getSymbol().equals(oOutput.getSymbol())) {
						AccountCryptoValue.Builder oAccountCryptoValue = receiverAccountValue.getCryptosList().get(k)
								.toBuilder();

						AccountCryptoToken.Builder oAccountCryptoToken = tokens
								.get(encApi.hexEnc(oOutput.getCryptoToken().toByteArray())).toBuilder();
						oAccountCryptoToken.setOwner(oOutput.getAddress());
						oAccountCryptoToken.setNonce(oAccountCryptoToken.getNonce() + 1);
						oAccountCryptoToken.setOwnertime(System.currentTimeMillis());
						oAccountCryptoValue.addTokens(oAccountCryptoToken.build());
						receiverAccountValue.setCryptos(k, oAccountCryptoValue);
						isExistToken = true;

						// update token mapping acocunt
						this.dao.getAccountDao()
								.put(oTransactionHelper.getOEntityHelper()
										.byteKey2OKey(oAccountCryptoToken.getHash().toByteArray()),
										oTransactionHelper.getOEntityHelper()
												.byteValue2OValue(oAccountCryptoToken.build().toByteArray()));
						break;
					}
				}

				if (!isExistToken) {
					AccountCryptoToken.Builder oAccountCryptoToken = tokens
							.get(encApi.hexEnc(oOutput.getCryptoToken().toByteArray())).toBuilder();
					oAccountCryptoToken.setOwner(oOutput.getAddress());
					oAccountCryptoToken.setNonce(oAccountCryptoToken.getNonce() + 1);
					oAccountCryptoToken.setOwnertime(System.currentTimeMillis());

					AccountCryptoValue.Builder oAccountCryptoValue = AccountCryptoValue.newBuilder();
					oAccountCryptoValue.addTokens(oAccountCryptoToken);
					oAccountCryptoValue.setSymbol(oOutput.getSymbol());

					receiverAccountValue.addCryptos(oAccountCryptoValue);
					// update token mapping acocunt
					this.dao.getAccountDao()
							.put(oTransactionHelper.getOEntityHelper()
									.byteKey2OKey(oAccountCryptoToken.getHash().toByteArray()),
									oTransactionHelper.getOEntityHelper()
											.byteValue2OValue(oAccountCryptoToken.build().toByteArray()));
				}
			}

//			DBTrie oCacheTrie = new DBTrie(this.dao, oTransactionHelper.getOEntityHelper());
//			if (receiverAccountValue.getStorage() == null) {
//				oCacheTrie.setRoot(null);
//			} else {
//				oCacheTrie.setRoot(receiverAccountValue.getStorage().toByteArray());
//			}
//			oCacheTrie.put(receiver.getAddress().toByteArray(), receiverAccountValue.build().toByteArray());
//			receiverAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));

			// keys.add(OEntityBuilder.byteKey2OKey(oOutput.getAddress().toByteArray()));
			// values.add(receiverAccountValue.build());
			receiver.setValue(receiverAccountValue);
			accounts.put(encApi.hexEnc(receiver.getAddress().toByteArray()), receiver);
			// this.accountValues.put(encApi.hexEnc(oOutput.getAddress().toByteArray()),
			// receiverAccountValue.build());
		}

		return ByteString.EMPTY;
		// this.keys.addAll(keys);
		// this.values.addAll(values);
		// oAccountHelper.BatchPutAccounts(keys, values);
	}

	@Override
	public void onExecuteDone(MultiTransaction oMultiTransaction, ByteString result) throws Exception {
		// TODO Auto-generated method stub
		super.onExecuteDone(oMultiTransaction, result);
	}
}
