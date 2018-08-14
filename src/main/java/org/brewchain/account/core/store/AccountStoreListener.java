package org.brewchain.account.core.store;

import java.util.Observable;
import java.util.Observer;

import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class AccountStoreListener implements Observer {
	DefDaos dao;
	OEntityBuilder oEntityHelper;
	
	@Override
	public void update(Observable o, Object arg) {
		log.warn("AccountStore stop! try to restart it");
		AccountStore run = new AccountStore();
		run.setDao(dao);
		run.setOEntityHelper(oEntityHelper);
		run.addObserver(this);
		new Thread(run).start();
		log.warn("AccountStore restart");
	}

}
