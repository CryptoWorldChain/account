package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.trie.StateTrie;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;
import static java.util.Arrays.copyOfRange;

import java.security.KeyPair;

public class ActuatorCreateContract extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateContract(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		if (senders.size() != 1) {
			throw new Exception("不允许存在多个发送方地址");
		}
		super.onPrepareExecute(oMultiTransaction, senders, receivers);
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {
		// 创建
		oAccountHelper.CreateContract(oTransactionHelper.getContractAddressByTransaction(oMultiTransaction), null,
				oMultiTransaction.getTxBody().getData().toByteArray());
	}
}
