package org.brewchain.account.dao;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.brewchain.bcapi.backend.ODBSupport;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.Message;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.ojpa.api.DomainDaoSupport;
import onight.tfw.ojpa.api.annotations.StoreDAO;

@NActorProvider
@Data
@Slf4j
@Instantiate(name = "Def_Daos")
public class DefDaos extends SessionModules<Message> {
	@StoreDAO(target = "bc_bdb", daoClass = AccoutDomain.class)
	ODBSupport accountDao;

	@StoreDAO(target = "bc_bdb", daoClass = ContractDomain.class)
	ODBSupport contractDao;

	// @StoreDAO(target = "bc_bdb", daoClass = TxDomain.class)
	// ODBSupport txDao;

	@StoreDAO(target = "bc_bdb", daoClass = TxSecondaryDomain.class)
	ODBSupport txsDao;

	@StoreDAO(target = "bc_bdb", daoClass = BlockDomain.class)
	ODBSupport blockDao;

	@Override
	public void onDaoServiceAllReady() {
		//log.debug("EncAPI==" + enc);
	}

	@Override
	public void onDaoServiceReady(DomainDaoSupport arg0) {
	}

	public void setAccountDao(DomainDaoSupport accountDao) {
		this.accountDao = (ODBSupport) accountDao;
	}

	public ODBSupport getAccountDao() {
		return accountDao;
	}

	public void setContractDao(DomainDaoSupport contractDao) {
		this.contractDao = (ODBSupport) contractDao;
	}

	public ODBSupport getContractDao() {
		return contractDao;
	}

	// public void setTxDao(DomainDaoSupport txDao) {
	// this.txDao = (ODBSupport) txDao;
	// }

	// public ODBSupport getTxDao() {
	// return txDao;
	// }

	public void setBlockDao(DomainDaoSupport blockDao) {
		this.blockDao = (ODBSupport) blockDao;
	}

	public ODBSupport getBlockDao() {
		return blockDao;
	}

	public void setTxsDao(DomainDaoSupport txsDao) {
		this.txsDao = (ODBSupport) txsDao;
	}

	public ODBSupport getTxsDao() {
		return txsDao;
	}
}
