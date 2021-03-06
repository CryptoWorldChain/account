package org.brewchain.account.dao;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Validate;
import org.brewchain.account.gens.Actimpl.PACTModule;
import org.brewchain.bcapi.backend.ODBSupport;

import com.google.protobuf.Message;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ojpa.api.DomainDaoSupport;
import onight.tfw.ojpa.api.annotations.StoreDAO;

@NActorProvider
@Data
@Slf4j
@Instantiate(name = "Def_Daos")
public class DefDaos extends SessionModules<Message> {
	@StoreDAO(target = "bc_bdb", daoClass = SliceAccoutDomain.class)
	ODBSupport accountDao;

	@StoreDAO(target = "bc_bdb", daoClass = CommonDomain.class)
	ODBSupport commonDao;

	@StoreDAO(target = "bc_bdb", daoClass = SliceTxSecondaryDomain.class)
	ODBSupport txsDao;

	@StoreDAO(target = "bc_bdb", daoClass = SliceBlockDomain.class)
	ODBSupport blockDao;

	@StoreDAO(target = "bc_bdb", daoClass = SliceTxBlockDomain.class)
	ODBSupport txblockDao;

	@StoreDAO(target = "bc_bdb", daoClass = CryptoTokenDomain.class)
	ODBSupport cryptoTokenDao;

	@Override
	public void onDaoServiceAllReady() {
		log.debug("service ready!!!!");
	}

	@Override
	public void onDaoServiceReady(DomainDaoSupport arg0) {
	}

	public void setCommonDao(DomainDaoSupport commonDao) {
		this.commonDao = (ODBSupport) commonDao;
	}

	public ODBSupport getCommonDao() {
		return commonDao;
	}

	public void setCryptoTokenDao(DomainDaoSupport cryptoTokenDao) {
		this.cryptoTokenDao = (ODBSupport) cryptoTokenDao;
	}

	public ODBSupport getCryptoTokenDao() {
		return cryptoTokenDao;
	}

	public void setAccountDao(DomainDaoSupport accountDao) {
		this.accountDao = (ODBSupport) accountDao;
	}

	public ODBSupport getAccountDao() {
		return accountDao;
	}

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

	public void setTxblockDao(DomainDaoSupport txblockDao) {
		this.txblockDao = (ODBSupport) txblockDao;
	}

	public ODBSupport getTxblockDao() {
		return txblockDao;
	}

	@Override
	public String[] getCmds() {
		return new String[] { "DEFDAOS" };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}
	
	@Validate
	public void init(){
		new Thread(stats).start();
	}
	
	@Invalidate
	public void destroy(){
		stats.running = false;
	}

	@Getter
	StatsInfo stats = new StatsInfo();
	public boolean isReady() {
		if (blockDao != null && SliceBlockDomain.class.isInstance(blockDao) && blockDao.getDaosupport() != null
				&& txblockDao != null && SliceTxBlockDomain.class.isInstance(txblockDao)
				&& txblockDao.getDaosupport() != null && txsDao != null
				&& SliceTxSecondaryDomain.class.isInstance(txsDao) && txsDao.getDaosupport() != null
				&& accountDao != null && SliceAccoutDomain.class.isInstance(accountDao)
				&& accountDao.getDaosupport() != null && CryptoTokenDomain.class.isInstance(cryptoTokenDao)
				&& cryptoTokenDao.getDaosupport() != null) {
			return true;
		}
		return false;
	}

}
