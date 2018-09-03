package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.TransactionParameterInvalidException;
import org.brewchain.account.trie.CacheTrie;
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

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		if (oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new TransactionParameterInvalidException("parameter invalid, inputs must be only one");
		}

		if (oMultiTransaction.getTxBody().getOutputsCount() == 0) {
			throw new TransactionParameterInvalidException("parameter invalid, outputs must not be null");
		}

		String token = "";
		BigInteger inputsTotal = BigInteger.ZERO;
		BigInteger outputsTotal = BigInteger.ZERO;

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			if (oInput.getToken().isEmpty() || oInput.getToken() == "") {
				throw new TransactionParameterInvalidException(String.format("parameter invalid, token must not be empty"));
			}
			if (token == "") {
				token = oInput.getToken();
			} else {
				if (!token.equals(oInput.getToken())) {
					throw new TransactionParameterInvalidException(String.format("parameter invalid, not allow multi token %s %s", token, oInput.getToken()));
				}
			}

			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
			BigInteger tokenBalance = BigInteger.ZERO;
			tokenBalance = tokenBalance.add(oAccountHelper.getTokenBalance(sender, oInput.getToken()));

			inputsTotal = inputsTotal.add(ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()));

			if (tokenBalance.compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionParameterInvalidException(String.format("parameter invalid, sender balance %s less than 0", tokenBalance));
			}

			if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionParameterInvalidException(String.format("parameter invalid, transaction value %s less than 0",
						ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())));
			}

			if (tokenBalance.subtract(ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()))
					.compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionParameterInvalidException(String.format("parameter invalid, sender balance %s less than %s", tokenBalance,
						ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())));
			}

			int nonce = senderAccountValue.getNonce();
			if (nonce != oInput.getNonce()) {
				throw new TransactionParameterInvalidException(String.format("parameter invalid, sender nonce %s is not equal with transaction nonce %s", nonce,
						oInput.getNonce()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			if (ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionParameterInvalidException(String.format("parameter invalid, receive balance %s less than 0",
						ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray())));
			}
			outputsTotal = ByteUtil.bytesAdd(outputsTotal, oOutput.getAmount().toByteArray());
		}

		if (inputsTotal.compareTo(outputsTotal) != 0) {
			throw new TransactionParameterInvalidException(String.format("parameter invalid, transaction value %s not equal with %s", inputsTotal, outputsTotal));
		}
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		String token = "";
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
			token = oInput.getToken();
			oAccountHelper.subTokenBalance(sender, oInput.getToken(),
					ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()));
			senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
			sender.setValue(senderAccountValue);
			accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);

		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			Account.Builder receiver = accounts.get(encApi.hexEnc(oOutput.getAddress().toByteArray()));
			if (receiver == null) {
				receiver = oAccountHelper.CreateAccount(oOutput.getAddress());
			}
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();
			oAccountHelper.addTokenBalance(receiver, token,
					ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray()));
			receiver.setValue(receiverAccountValue);
			accounts.put(encApi.hexEnc(receiver.getAddress().toByteArray()), receiver);
		}

		return ByteString.EMPTY;
	}
}
