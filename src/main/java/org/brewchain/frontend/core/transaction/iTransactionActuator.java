package org.brewchain.frontend.core.transaction;

import java.util.Map;

import org.brewchain.frontend.gens.Act.Account;
import org.brewchain.frontend.gens.Tx.MultiTransaction;

import com.google.protobuf.ByteString;

public interface iTransactionActuator {
	void onVerify(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	void onPrepareExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	void onExecute(MultiTransaction oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	void onExecuteDone() throws Exception;
}
