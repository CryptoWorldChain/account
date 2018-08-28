package org.brewchain.account.core.processor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.actuator.iTransactionActuator;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import com.google.protobuf.ByteString;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class MisV2TransactionRunner implements Runnable {

	LinkedBlockingQueue<MultiTransaction> queue;
	TransactionHelper transactionHelper;
	BlockEntity currentBlock;
	Map<String, Account.Builder> accounts;
	Map<String, ByteString> results;
	CountDownLatch cdl;

	@Override
	public void run() {
		try {
			Map<Integer, iTransactionActuator> actorByType = new HashMap<>();
			while (cdl.getCount() > 0) {
				MultiTransaction oTransaction = queue.poll();
				if (oTransaction != null) {
					iTransactionActuator oiTransactionActuator = actorByType.get(oTransaction.getTxBody().getType());
					if (oiTransactionActuator == null) {
						oiTransactionActuator = transactionHelper.getActuator(oTransaction.getTxBody().getType(),
								currentBlock);
						actorByType.put(oTransaction.getTxBody().getType(), oiTransactionActuator);
					} else {
						transactionHelper.resetActuator(oiTransactionActuator, currentBlock);
					}

					try {
						oiTransactionActuator.onPrepareExecute(oTransaction, accounts);
						ByteString result = oiTransactionActuator.onExecute(oTransaction, accounts);
						oiTransactionActuator.onExecuteDone(oTransaction, result);
						KeyConstant.txCounter.incrementAndGet();
						results.put(oTransaction.getTxHash(), result);
					} catch (Exception e) {
						log.error("block " + currentBlock.getHeader().getBlockHash() + " exec transaction hash::"
								+ oTransaction.getTxHash() + " error::" + e.getMessage());
						try {
							oiTransactionActuator.onExecuteError(oTransaction, ByteString
									.copyFromUtf8(e.getMessage() == null ? "unknown exception" : e.getMessage()));
							results.put(oTransaction.getTxHash(), ByteString
									.copyFromUtf8(e.getMessage() == null ? "unknown exception" : e.getMessage()));
						} catch (Exception e1) {
							log.error("onexec errro:" + e1.getMessage(), e1);
						}
					} finally {
						cdl.countDown();
					}

				}
			}
		} finally {
			cdl.countDown();
		}

	}

}
