package org.brewchain.account.core.actuator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.TransactionParameterInvalidException;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.CryptoTokenValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.CryptoTokenData;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCreateCryptoToken extends AbstractTransactionActuator implements iTransactionActuator {

	private CryptoTokenValue.Builder newCryptoTokenValue;

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
			throw new TransactionParameterInvalidException("parameter invalid, inputs must be only one");
		}

		CryptoTokenData oCryptoTokenData = CryptoTokenData
				.parseFrom(oMultiTransaction.getTxBody().getData().toByteArray());
		if (oCryptoTokenData.getCodeCount() != oCryptoTokenData.getNameCount() || oCryptoTokenData.getCodeCount() == 0
				|| oCryptoTokenData.getTotal() == 0) {
			throw new TransactionParameterInvalidException("parameter invalid, crypto token count must large than 0");
		}

		if (StringUtils.isBlank(oCryptoTokenData.getSymbol())) {
			throw new TransactionParameterInvalidException("parameter invalid, crypto token symbol must not be null");
		}

		if (!oAccountHelper.canCreateCryptoToken(oCryptoTokenData.getSymbolBytes(),
				oMultiTransaction.getTxBody().getInputs(0).getAddress(), oCryptoTokenData.getTotal(),
				oCryptoTokenData.getCodeCount())) {
			throw new TransactionParameterInvalidException("parameter invalid, cannot create crypto token with name "
					+ oMultiTransaction.getTxBody().getInputs(0).getSymbol());
		}
		
		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		CryptoTokenData oCryptoTokenData = CryptoTokenData
				.parseFrom(oMultiTransaction.getTxBody().getData().toByteArray());

		MultiTransactionInput input = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder sender = accounts.get(encApi.hexEnc(input.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
		senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
		senderAccountValue.setBalance(ByteString.copyFrom(ByteUtil
				.bytesSubToBytes(senderAccountValue.getBalance().toByteArray(), input.getAmount().toByteArray())));

		CryptoTokenValue oCryptoTokenValue = oAccountHelper.getCryptoTokenValue(oCryptoTokenData.getSymbolBytes());
		if (oCryptoTokenValue == null) {
			oCryptoTokenValue = CryptoTokenValue.newBuilder().setTotal(oCryptoTokenData.getTotal())
					.setOwner(input.getAddress()).setTimestamp(oMultiTransaction.getTxBody().getTimestamp()).build();
		}
		newCryptoTokenValue = oCryptoTokenValue.toBuilder();

		List<AccountCryptoToken> tokens = new ArrayList<>();
		for (int i = 0; i < oCryptoTokenData.getNameCount(); i++) {
			AccountCryptoToken.Builder oAccountCryptoToken = AccountCryptoToken.newBuilder();
			oAccountCryptoToken.setCode(oCryptoTokenData.getCode(i));
			oAccountCryptoToken.setExtData(oCryptoTokenData.getExtData());
			oAccountCryptoToken.setIndex(oCryptoTokenValue.getCurrent() + i + 1);
			oAccountCryptoToken.setName(oCryptoTokenData.getName(i));
			oAccountCryptoToken.setNonce(0);
			oAccountCryptoToken.setOwner(input.getAddress());
			oAccountCryptoToken.setOwnertime(oMultiTransaction.getTxBody().getTimestamp());
			oAccountCryptoToken.setTotal(oCryptoTokenValue.getTotal());
			oAccountCryptoToken.setTimestamp(oMultiTransaction.getTxBody().getTimestamp());
			oAccountCryptoToken.clearHash();
			oAccountCryptoToken
					.setHash(ByteString.copyFrom(encApi.sha256Encode(oAccountCryptoToken.build().toByteArray())));

			newCryptoTokenValue.setCurrent(newCryptoTokenValue.getCurrent() + 1);
			tokens.add(oAccountCryptoToken.build());
		}

		boolean isExistsCryptoSymbol = false;
		for (int j = 0; j < senderAccountValue.getCryptosCount(); j++) {
			if (senderAccountValue.getCryptos(j).getSymbol().equals(oCryptoTokenData.getSymbol())) {
				isExistsCryptoSymbol = true;
				AccountCryptoValue.Builder oAccountCryptoValue = senderAccountValue.getCryptos(j).toBuilder();
				for (AccountCryptoToken accountCryptoToken : tokens) {
					oAccountCryptoValue.addTokens(accountCryptoToken);
				}
				senderAccountValue.setCryptos(j, oAccountCryptoValue);
				break;
			}
		}
		if (!isExistsCryptoSymbol) {
			AccountCryptoValue.Builder oAccountCryptoValue = AccountCryptoValue.newBuilder();
			oAccountCryptoValue.setSymbol(oCryptoTokenData.getSymbol());
			for (AccountCryptoToken accountCryptoToken : tokens) {
				oAccountCryptoValue.addTokens(accountCryptoToken);
			}
			// oAccountCryptoValue.addAllTokens(tokens);
			senderAccountValue.addCryptos(oAccountCryptoValue);
		}

		sender.setValue(senderAccountValue);
		accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);

		return ByteString.EMPTY;
	}

	@Override
	public void onExecuteDone(MultiTransaction oMultiTransaction, ByteString result) throws Exception {
		CryptoTokenData oCryptoTokenData = CryptoTokenData
				.parseFrom(oMultiTransaction.getTxBody().getData().toByteArray());
		oAccountHelper.updateCryptoTokenValue(oCryptoTokenData.getSymbolBytes(), newCryptoTokenValue.build());
		super.onExecuteDone(oMultiTransaction, result);
	}
}
