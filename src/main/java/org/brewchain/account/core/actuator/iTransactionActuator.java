package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import com.google.protobuf.ByteString;

public interface iTransactionActuator {
	boolean needSignature();
//	LinkedList<OKey> getKeys();
//	LinkedList<AccountValue> getValues();
//	LinkedList<OKey> getTxKeys();
//	LinkedList<MultiTransaction> getTxValues();
	// Map<String, AccountValue> getAccountValues();
	Map<String, MultiTransaction> getTxValues();
	/**
	 * 交易签名校验
	 * 
	 * @param oMultiTransaction
	 * @param senders
	 * @param receivers
	 * @throws Exception
	 */
	void onVerifySignature(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception;

	/**
	 * 交易执行前的数据校验。
	 * 
	 * @param oMultiTransaction
	 * @param senders
	 * @param receivers
	 * @throws Exception
	 */
	void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception;

	/**
	 * 交易执行。
	 * 
	 * @param oMultiTransaction
	 * @param senders
	 * @param receivers
	 * @throws Exception
	 */
	ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception;

	/**
	 * 交易执行成功后。
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	void onExecuteDone(MultiTransaction oMultiTransaction, ByteString result) throws Exception;
	
	/**
	 * 交易执行失败后。
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	void onExecuteError(MultiTransaction oMultiTransaction, ByteString result) throws Exception;
}
