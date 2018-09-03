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
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.UnitUtil;

import com.google.protobuf.ByteString;

/**
 * 发布token交易。 发送方token=发布总量
 * 
 * @author
 *
 */
public class ActuatorCreateToken extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateToken(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper, BlockEntity oBlock,
			EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		MultiTransactionInput input = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder sender = accounts.get(encApi.hexEnc(input.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setBalance(input.getAmount()).setToken(input.getToken());

		senderAccountValue.addTokens(oAccountTokenValue)
				.setBalance(ByteString.copyFrom(ByteUtil
						.bigIntegerToBytes(ByteUtil.bytesToBigInteger(senderAccountValue.getBalance().toByteArray())
								.subtract(this.oTransactionHelper.getBlockChainConfig().getToken_lock_balance()))));
		sender.setValue(senderAccountValue);

		Account.Builder locker = accounts.get(this.oTransactionHelper.getBlockChainConfig().getLock_account_address());
		AccountValue.Builder lockerAccountValue = locker.getValue().toBuilder();
		lockerAccountValue.setBalance(ByteString.copyFrom(
				ByteUtil.bigIntegerToBytes(ByteUtil.bytesToBigInteger(lockerAccountValue.getBalance().toByteArray())
						.add(this.oTransactionHelper.getBlockChainConfig().getToken_lock_balance()))));

		locker.setValue(lockerAccountValue);

		accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);
		accounts.put(encApi.hexEnc(locker.getAddress().toByteArray()), locker);
		oAccountHelper.createToken(input.getAddress(), input.getToken(),
				UnitUtil.fromWei(ByteUtil.bytesToBigInteger(input.getAmount().toByteArray())));

		return ByteString.EMPTY;
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		if (oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new TransactionParameterInvalidException("parameter invalid, inputs must be only one");
		}

		if (oMultiTransaction.getTxBody().getOutputsCount() != 0) {
			throw new TransactionParameterInvalidException("parameter invalid, outputs must be null");
		}

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);

		String token = oInput.getToken();
		if (token == null || token.isEmpty()) {
			throw new TransactionParameterInvalidException(String.format("parameter invalid, token name must not be empty"));
		}

		if (token.toUpperCase().startsWith("CW")) {
			throw new TransactionParameterInvalidException(String.format("parameter invalid, token name invalid"));
		}

		if (!token.toUpperCase().equals(token)) {
			throw new TransactionParameterInvalidException(String.format("parameter invalid, token name invalid"));
		}

		if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())
				.compareTo(oTransactionHelper.getBlockChainConfig().getMinerReward()) == -1
				|| ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())
						.compareTo(oTransactionHelper.getBlockChainConfig().getMaxTokenTotal()) == 1) {
			throw new TransactionParameterInvalidException(
					String.format("parameter invalid, token amount must between %s and %s ",
							UnitUtil.fromWei(oTransactionHelper.getBlockChainConfig().getMinTokenTotal()),
							UnitUtil.fromWei(oTransactionHelper.getBlockChainConfig().getMaxTokenTotal())));
		}

		Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
		if (ByteUtil.bytesToBigInteger(senderAccountValue.getBalance().toByteArray())
				.compareTo(this.oTransactionHelper.getBlockChainConfig().getToken_lock_balance()) == -1) {
			throw new TransactionParameterInvalidException(
					String.format("parameter invalid, not enough deposit %s to create token",
							this.oTransactionHelper.getBlockChainConfig().getToken_lock_balance()));
		}

		if (oAccountHelper.isExistsToken(token)) {
			throw new TransactionParameterInvalidException(String.format("parameter invalid, duplicate token name %s", token));
		}

		int nonce = senderAccountValue.getNonce();
		if (nonce != oInput.getNonce()) {
			throw new TransactionParameterInvalidException(
					String.format("sender nonce %s is not equal with transaction nonce %s", nonce, oInput.getNonce()));
		}
	}
}