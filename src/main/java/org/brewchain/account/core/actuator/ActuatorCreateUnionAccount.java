package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.TransactionParameterInvalidException;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.Account.Builder;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.UnionAccountData;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;

public class ActuatorCreateUnionAccount extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateUnionAccount(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		
		if (oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new TransactionParameterInvalidException("parameter invalid, inputs must be only one");
		}
		
		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		
		if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) != 0) {
			throw new TransactionParameterInvalidException("parameter invalid, amount must be zero");
		}

		if (oMultiTransaction.getTxBody().getOutputsCount() != 0) {
			throw new TransactionParameterInvalidException("parameter invalid, inputs must be empty");
		}

		if (oMultiTransaction.getTxBody().getInputsCount() != oMultiTransaction.getTxBody().getSignaturesCount()) {
			throw new TransactionParameterInvalidException("parameter invalid, inputs count must equal with signatures count");
		}
		
		Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
		int txNonce = oInput.getNonce();
		int nonce = senderAccountValue.getNonce();
		if (nonce != txNonce) {
			throw new TransactionParameterInvalidException(
					String.format("parameter invalid, sender nonce %s is not equal with transaction nonce %s", nonce, nonce));
		}

		UnionAccountData oUnionAccountData = UnionAccountData
				.parseFrom(oMultiTransaction.getTxBody().getData().toByteArray());
		if (oUnionAccountData == null || oUnionAccountData.getMax() == null || oUnionAccountData.getAcceptLimit() < 0
				|| oUnionAccountData.getAcceptMax() == null || oUnionAccountData.getAddressCount() < 2) {
			throw new TransactionParameterInvalidException("parameter invalid, union account info invalidate");
		}

		if (oUnionAccountData.getAcceptLimit() > oUnionAccountData.getAddressCount()) {
			throw new TransactionParameterInvalidException(
					"parameter invalid, AcceptLimit count must smaller than address count");
		}
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Builder> accounts) throws Exception {
		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

		senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
		sender.setValue(senderAccountValue);
		accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);

		UnionAccountData oUnionAccountData = UnionAccountData
				.parseFrom(oMultiTransaction.getTxBody().getData().toByteArray());

		KeyPairs oKeyPairs = encApi.genKeys();
		Account.Builder oUnionAccount = oAccountHelper.CreateUnionAccount(
				ByteString.copyFrom(encApi.hexDec(oKeyPairs.getAddress())), oUnionAccountData.getMax(),
				oUnionAccountData.getAcceptMax(), oUnionAccountData.getAcceptLimit(),
				oUnionAccountData.getAddressList());

		accounts.put(oKeyPairs.getAddress(), oUnionAccount);

		return ByteString.copyFrom(encApi.hexDec(oKeyPairs.getAddress()));
	}
}
