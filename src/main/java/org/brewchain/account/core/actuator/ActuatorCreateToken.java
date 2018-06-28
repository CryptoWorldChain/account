package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

/**
 * 发布token交易。 发送方token=发布总量
 * 
 * @author
 *
 */
public class ActuatorCreateToken extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateToken(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper, encApi, dao, oStateTrie);
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {
		MultiTransactionInput input = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder sender = accounts.get(encApi.hexEnc(input.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setBalance(input.getAmount()).setToken(input.getToken());

		senderAccountValue.addTokens(oAccountTokenValue).setBalance(
				senderAccountValue.getBalance() - this.oBlockHelper.getBlockChainConfig().getContract_lock_balance());
		sender.setValue(senderAccountValue);

		Account.Builder locker = accounts.get(encApi.hexEnc(input.getAddress().toByteArray()));
		AccountValue.Builder lockerAccountValue = locker.getValue().toBuilder();
		lockerAccountValue.setBalance(
				lockerAccountValue.getBalance() + this.oBlockHelper.getBlockChainConfig().getContract_lock_balance());
		locker.setValue(lockerAccountValue);

		accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);
		accounts.put(encApi.hexEnc(locker.getAddress().toByteArray()), locker);
		// accounts.put(key, value); 发布账户
		// accounts.put(key, value); 锁定
		// accounts.put(key, value); 转入锁定
		oAccountHelper.ICO(input.getAddress(), input.getToken(), input.getAmount());
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		if (oMultiTransaction.getTxBody().getInputsCount() != 1) {
			throw new Exception(String.format("不允许存在多个发行方"));
		}

		String token = oMultiTransaction.getTxBody().getInputs(0).getToken();
		if (token == null || token.isEmpty()) {
			throw new Exception(String.format("Token交易中Token不允许为空"));
		}

		if (token.toLowerCase().startsWith("CW")) {
			throw new Exception(String.format("Token名称无效"));
		}

		// 判断nonce是否一致
		Account.Builder sender = accounts
				.get(encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

		int nonce = senderAccountValue.getNonce();
		if (nonce != oMultiTransaction.getTxBody().getInputs(0).getNonce()) {
			throw new Exception(String.format("用户的交易索引 %s 与交易的索引不一致 %s", nonce,
					oMultiTransaction.getTxBody().getInputs(0).getNonce()));
		}
		if (senderAccountValue.getBalance() < this.oBlockHelper.getBlockChainConfig().getContract_lock_balance()) {
			throw new Exception(
					String.format("没有足够的押金 %s", this.oBlockHelper.getBlockChainConfig().getContract_lock_balance()));
		}
		// Token不允许重复
		if (oAccountHelper.isExistsToken(token)) {
			throw new Exception(String.format("不允许重复发行token %s", token));
		}

	}

}
