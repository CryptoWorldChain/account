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
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

public class ActuatorDefault extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorDefault(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper, BlockEntity oBlock,
			EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			if (!accounts.containsKey(encApi.hexEnc(oInput.getAddress().toByteArray()))) {
				throw new Exception(String.format("can not find sender account %s", oInput.getAddress().toString()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			if (!accounts.containsKey(encApi.hexEnc(oOutput.getAddress().toByteArray()))) {
				oAccountHelper.CreateAccount(oOutput.getAddress());
			}
		}

		super.onPrepareExecute(oMultiTransaction, accounts);
	}
}
