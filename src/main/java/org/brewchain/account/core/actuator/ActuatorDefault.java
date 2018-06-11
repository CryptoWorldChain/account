package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorDefault extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorDefault(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper, BlockHelper oBlockHelper,
			EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper, encApi, dao, oStateTrie);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			if (!senders.containsKey(oInput.getAddress())) {
				throw new Exception(String.format("交易的发送方账户 %s 不存在", oInput.getAddress().toString()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			if (!receivers.containsKey(oOutput.getAddress())) {
				oAccountHelper.CreateAccount(oOutput.getAddress().toByteArray(), ByteUtil.EMPTY_BYTE_ARRAY);
			}
		}

		super.onPrepareExecute(oMultiTransaction, senders, receivers);
	}
}
