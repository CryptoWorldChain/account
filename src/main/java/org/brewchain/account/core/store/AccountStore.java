package org.brewchain.account.core.store;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;

import org.apache.felix.ipojo.util.Log;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Act.Account;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class AccountStore extends Observable implements Runnable {
	private DefDaos dao;
	private OEntityBuilder oEntityHelper;

	// public AccountStore(DefDaos dao, OEntityBuilder oEntityHelper) {
	// this.dao = dao;
	// this.oEntityHelper = oEntityHelper;
	// }

	public void listen() {
		super.setChanged();
		notifyObservers();
	}

	@Override
	public void run() {
		log.debug("start to listen account batch queue....");
		while (true) {
			try {
				Map<String, Account.Builder> accountValues;
				while ((accountValues = KeyConstant.QUEUE.poll()) != null) {
					OKey[] keysArray = new OKey[accountValues.size()];
					OValue[] valuesArray = new OValue[accountValues.size()];
					Set<String> keySets = accountValues.keySet();
					Iterator<String> iterator = keySets.iterator();
					int i = 0;
					while (iterator.hasNext()) {
						String key = iterator.next();
						log.debug("put account address::" + key);
						keysArray[i] = oEntityHelper.byteKey2OKey(accountValues.get(key).getAddress());
						valuesArray[i] = oEntityHelper
								.byteValue2OValue(accountValues.get(key).getValue().toByteArray());
						i = i + 1;
					}
					dao.getAccountDao().batchPuts(keysArray, valuesArray);
				}
				Thread.sleep(5000);
			} catch (Exception e) {
				log.error("error on batch put account::" + e);
				listen();
			} finally {
				
			}
		}
	}
}
