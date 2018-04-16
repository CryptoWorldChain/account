package org.brewchain.frontend.core.transaction;

import java.util.Map;

import org.brewchain.frontend.core.AccountHelper;
import org.brewchain.frontend.core.BlockHelper;
import org.brewchain.frontend.core.TransactionHelper;
import org.brewchain.frontend.gens.Act.Account;
import org.brewchain.frontend.gens.Tx.MultiTransaction;
import org.brewchain.frontend.gens.Tx.MultiTransactionInput;
import org.brewchain.frontend.gens.Tx.MultiTransactionOutput;
import org.brewchain.frontend.util.ByteUtil;

import com.google.protobuf.ByteString;

public class ActuatorDefault extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorDefault(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception {

		for (MultiTransactionInput oInput : oMultiTransaction.getInputsList()) {
			if (!senders.containsKey(oInput.getAddress())) {
				throw new Exception(String.format("交易的发送方账户 %s 不存在", oInput.getAddress().toString()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getOutputsList()) {
			if (!receivers.containsKey(oOutput.getAddress())) {
				oAccountHelper.CreateAccount(oOutput.getAddress().toByteArray(), ByteUtil.EMPTY_BYTE_ARRAY);
			}
		}
	}
}
