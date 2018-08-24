package org.brewchain.account.core.store;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.tfw.outils.pool.ReusefulLoopPool;

@Slf4j
@Data
public class AccountStore implements Runnable {
	private DefDaos dao;
	private OEntityBuilder oEntityHelper;
	boolean isStop = false;
	Executor exec = new ForkJoinPool(100);
	int objPoolSize = 1000;

	class AccountTask implements Runnable {
		Map<String, Account.Builder> accountValues;

		@Override
		public void run() {
			try {
				OKey[] keysArray = new OKey[accountValues.size()];
				OValue[] valuesArray = new OValue[accountValues.size()];
				Set<String> keySets = accountValues.keySet();
				Iterator<String> iterator = keySets.iterator();
				int i = 0;
				while (iterator.hasNext()) {
					String key = iterator.next();
					keysArray[i] = oEntityHelper.byteKey2OKey(accountValues.get(key).getAddress());
					valuesArray[i] = oEntityHelper.byteValue2OValue(accountValues.get(key).getValue().toByteArray());

					i = i + 1;
				}
				dao.getAccountDao().batchPuts(keysArray, valuesArray);
			} finally {
				if (acctPool.size() < objPoolSize) {
					acctPool.retobj(this);
				}

			}
		}
	}

	ReusefulLoopPool<AccountTask> acctPool = new ReusefulLoopPool<>();

	@Override
	public void run() {
		log.debug("start to listen account batch queue....");
		while (!isStop) {
			AccountTask task = null;
			try {
				Map<String, Account.Builder> accountValues;
				accountValues = KeyConstant.QUEUE.poll(10, TimeUnit.SECONDS);

				if (accountValues != null) {
					task = acctPool.borrow();
					if (task == null) {
						task = new AccountTask();
					}
					task.accountValues = accountValues;
					exec.execute(task);
				}
			} catch (Exception e) {
				log.error("error on batch put account::" + e);
			} finally {
			}
		}
	}
}
