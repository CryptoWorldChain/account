package org.brewchain.account.core.transaction;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Tx.MultiTransaction;

import com.google.protobuf.ByteString;

public class ActuatorCreateUnionAccount extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateUnionAccount(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper) {
		// this.accountHelper = oAccountHelper;
		super(oAccountHelper, oTransactionHelper, oBlockHelper);
	}

	@Override
	public void onPrepareExecute(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		// if (oMultiTransaction.getd)
		// 如果data为空，直接抛出交易内容错误
		if (oMultiTransaction.getData().equals(ByteString.EMPTY)) {
			throw new Exception("交易内容错误，data为null");
		}

		Account oUnionAccount = Account.parseFrom(oMultiTransaction.getData());
		if (!oAccountHelper.isExist(oUnionAccount.getAddress().toByteArray())) {
			// 如果账户不存在
			oAccountHelper.CreateUnionAccount(oUnionAccount);
		} else {
			// 如果账户存在
			throw new Exception(String.format("账户 %s 已存在", oUnionAccount.getAddress().toString()));
		}
	}
}
