package org.brewchain.account.core.transaction;

import java.util.Map;

import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Tx.MultiTransaction;

import com.google.protobuf.ByteString;

public interface iTransactionActuator {
	void onVerify(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	void onPrepareExecute(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	void onExecute(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	void onExecuteDone() throws Exception;
}
