package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.function.InternalFunction;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Call.InternalCallArguments;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCallInternalFunction extends AbstractTransactionActuator implements iTransactionActuator {

	@Override
	public boolean needSignature() {
		// 执行内部方法调用不需要进行签名
		return false;
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		InternalCallArguments.Builder oInternalCallArguments = InternalCallArguments
				.parseFrom(oMultiTransaction.getTxBody().getExdata()).toBuilder();

		for (int i = 0; i < InternalFunction.class.getMethods().length; i++) {
			if (InternalFunction.class.getMethods()[i].getName().equals(oInternalCallArguments.getMethod())) {
				if (oInternalCallArguments.getParamsCount() != 0)
					InternalFunction.class.getMethods()[i].invoke(null,
							new Object[] { oAccountHelper, oInternalCallArguments.getParamsList() });
				else
					InternalFunction.class.getMethods()[i].invoke(null,
							new Object[] { oAccountHelper, new String[] {} });
				break;
			}
		}
		return ByteString.EMPTY;
		// super.onExecute(oMultiTransaction, senders, receivers);
	}
	public ActuatorCallInternalFunction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
		// TODO Auto-generated constructor stub
	}

}
