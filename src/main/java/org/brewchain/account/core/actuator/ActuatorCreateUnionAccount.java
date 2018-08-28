package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCreateUnionAccount extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateUnionAccount(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		// this.accountHelper = oAccountHelper;
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {
		// if (oMultiTransaction.getd)
		// 如果data为空，直接抛出交易内容错误
		if (oMultiTransaction.getTxBody().getData().equals(ByteString.EMPTY)) {
			throw new Exception("data must not by empty");
		}

		Account oUnionAccount = Account.parseFrom(oMultiTransaction.getTxBody().getExdata());
		if (!oAccountHelper.isExist(oUnionAccount.getAddress())) {
			// 如果账户不存在
			oAccountHelper.CreateUnionAccount(oUnionAccount.toBuilder());
			accounts.put(encApi.hexEnc(oUnionAccount.getAddress().toByteArray()), oUnionAccount.toBuilder());
		} else {
			// 如果账户存在
			// throw new Exception(String.format("账户 %s 已存在",
			// oUnionAccount.getAddress().toString()));
		}

		super.onPrepareExecute(oMultiTransaction, accounts);
	}
}
