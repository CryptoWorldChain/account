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
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OValue;
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

public class ActuatorCreateCryptoToken extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateCryptoToken(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
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

		if (oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new TransactionExecuteException("parameter invalid, inputs must be only one");
		}

		if (StringUtils.isBlank(oMultiTransaction.getTxBody().getInputs(0).getSymbol())) {
			throw new TransactionExecuteException("parameter invalid, crypto token symbol must not be null");
		}

		AccountCryptoToken.Builder oAccountCryptoToken = AccountCryptoToken
				.parseFrom(oMultiTransaction.getTxBody().getData()).toBuilder();

		if (!oAccountCryptoToken.getOwner().equals(oMultiTransaction.getTxBody().getInputs(0).getAddress())) {
			throw new TransactionExecuteException("parameter invalid, crypto token owner not equal with sender");
		}

		if (oAccountCryptoToken.getNonce() != 0) {
			throw new TransactionExecuteException("parameter invalid, crypto token nonce must be 0");
		}

		oAccountCryptoToken.clearHash();
		oAccountCryptoToken
				.setHash(ByteString.copyFrom(encApi.sha256Encode(oAccountCryptoToken.build().toByteArray())));

		if (oAccountHelper.isExistsCryptoToken(oAccountCryptoToken.getHash().toByteArray())) {
			throw new TransactionExecuteException("parameter invalid, crypto token already exists");
		}

		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		AccountCryptoToken.Builder oAccountCryptoToken = AccountCryptoToken
				.parseFrom(oMultiTransaction.getTxBody().getData()).toBuilder();

		oAccountCryptoToken.clearHash();
		oAccountCryptoToken
				.setHash(ByteString.copyFrom(encApi.sha256Encode(oAccountCryptoToken.build().toByteArray())));
		oAccountHelper.createCryptoToken(oAccountCryptoToken, oMultiTransaction.getTxBody().getInputs(0).getSymbol());

		MultiTransactionInput input = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder sender = accounts.get(encApi.hexEnc(input.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
		senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
		senderAccountValue.setBalance(ByteString.copyFrom(ByteUtil
				.bytesSubToBytes(senderAccountValue.getBalance().toByteArray(), input.getAmount().toByteArray())));

		for (int j = 0; j < senderAccountValue.getCryptosCount(); j++) {
			if (senderAccountValue.getCryptos(j).getSymbol().equals(input.getSymbol())) {
				AccountCryptoValue.Builder oAccountCryptoValue = senderAccountValue.getCryptos(j).toBuilder();
				oAccountCryptoValue.addTokens(oAccountCryptoToken);
				
				senderAccountValue.setCryptos(j, oAccountCryptoValue);
				break;
			}
		}
		sender.setValue(senderAccountValue);
		accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);

		return oAccountCryptoToken.getHash();
	}

	@Override
	public void onExecuteDone(MultiTransaction oMultiTransaction, ByteString result) throws Exception {
		// TODO Auto-generated method stub
		super.onExecuteDone(oMultiTransaction, result);
	}
}
