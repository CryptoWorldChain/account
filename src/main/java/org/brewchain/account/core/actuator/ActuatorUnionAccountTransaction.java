package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Tx.MultiTransaction.Builder;

import com.google.protobuf.ByteString;

public class ActuatorUnionAccountTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorUnionAccountTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper);
	}

	@Override
	public void onPrepareExecute(Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		// 校验每日最大转账金额
		// 校验每笔最大转账金额
		// 校验超过单笔转账金额后的用户签名
		
		super.onPrepareExecute(oMultiTransaction, senders, receivers);
	}
}
