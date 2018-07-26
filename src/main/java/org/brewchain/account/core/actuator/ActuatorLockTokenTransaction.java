package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.actuator.AbstractTransactionActuator.TransactionExecuteException;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorLockTokenTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		if (oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new TransactionExecuteException("parameter invalid, inputs must be only one");
		}

		if (oMultiTransaction.getTxBody().getOutputsCount() != 0) {
			throw new TransactionExecuteException("parameter invalid, outputs must be null");
		}

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);

		// 取发送方账户
		Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
		BigInteger tokenBalance = BigInteger.ZERO;
		for (int i = 0; i < senderAccountValue.getTokensCount(); i++) {
			if (senderAccountValue.getTokens(i).getToken().equals(oInput.getToken())) {
				tokenBalance = ByteUtil.bytesAdd(tokenBalance,
						senderAccountValue.getTokens(i).getBalance().toByteArray());
				break;
			}
		}
		
		BigInteger bi = ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray());
		if (bi.compareTo(BigInteger.ZERO) < 0) {
			throw new TransactionExecuteException("amount must large than 0");
		}

		if (tokenBalance.compareTo(bi) >= 0) {
			// 余额足够
		} else {
			throw new TransactionExecuteException(String.format("sender balance %s less than %s", tokenBalance,
					ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())));
		}

		// 判断nonce是否一致
		int nonce = senderAccountValue.getNonce();
		if (nonce != oInput.getNonce()) {
			throw new TransactionExecuteException(
					String.format("sender nonce %s is not equal with transaction nonce %s", nonce, oInput.getNonce()));
		}
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		// TODO lock 只处理了 input，未处理 output
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
			boolean isExistToken = false;
			for (int i = 0; i < senderAccountValue.getTokensCount(); i++) {
				if (senderAccountValue.getTokens(i).getToken().equals(oInput.getToken())) {
					AccountTokenValue.Builder oAccountTokenValue = senderAccountValue.getTokens(i).toBuilder();
					oAccountTokenValue.setBalance(ByteString.copyFrom(
							ByteUtil.bytesSubToBytes(senderAccountValue.getTokens(i).getBalance().toByteArray(),
									oInput.getAmount().toByteArray())));
					oAccountTokenValue.setLocked(ByteString.copyFrom(
							ByteUtil.bytesAddToBytes(senderAccountValue.getTokens(i).getLocked().toByteArray(),
									oInput.getAmount().toByteArray())));
					senderAccountValue.setTokens(i, oAccountTokenValue);

					isExistToken = true;
					break;
				}
			}
			if (!isExistToken) {
				throw new TransactionExecuteException(
						String.format("cannot found token %s in sender account", oInput.getToken()));
			}

			// 不论任何交易类型，都默认执行账户余额的更改
			senderAccountValue.setBalance(senderAccountValue.getBalance());
			senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);

//			DBTrie oCacheTrie = new DBTrie(this.dao, oTransactionHelper.getOEntityHelper());
//			if (senderAccountValue.getStorage() == null) {
//				oCacheTrie.setRoot(null);
//			} else {
//				oCacheTrie.setRoot(senderAccountValue.getStorage().toByteArray());
//			}
//			oCacheTrie.put(sender.getAddress().toByteArray(), senderAccountValue.build().toByteArray());
//			senderAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));

			sender.setValue(senderAccountValue);
			accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);
		}

		return ByteString.EMPTY;
	}

	public ActuatorLockTokenTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

}
