package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransaction.Builder;

import com.google.protobuf.ByteString;

public interface iTransactionActuator {
	boolean needSignature();

	/**
	 * 交易签名校验
	 * 
	 * @param oMultiTransaction
	 * @param senders
	 * @param receivers
	 * @throws Exception
	 */
	void onVerifySignature(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	/**
	 * 交易执行前的数据校验。
	 * 
	 * @param oMultiTransaction
	 * @param senders
	 * @param receivers
	 * @throws Exception
	 */
	void onPrepareExecute(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	/**
	 * 交易执行。
	 * 
	 * @param oMultiTransaction
	 * @param senders
	 * @param receivers
	 * @throws Exception
	 */
	void onExecute(MultiTransaction.Builder oMultiTransaction, Map<ByteString, Account> senders,
			Map<ByteString, Account> receivers) throws Exception;

	/**
	 * 交易执行成功后。
	 * 
	 * @param oMultiTransaction
	 * @throws Exception
	 */
	void onExecuteDone(Builder oMultiTransaction) throws Exception;
}
