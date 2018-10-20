package org.brewchain.account.dao;

import java.util.List;
import java.util.concurrent.Future;

import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.backend.ODBSupport;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OPair;
import org.brewchain.bcapi.gens.Oentity.OValue;

import onight.tfw.ojpa.api.DomainDaoSupport;
import onight.tfw.ojpa.api.ServiceSpec;

public class ODBSupprtDaoWrapper  {
	ODBSupport proxyDao;

	
	public ODBSupprtDaoWrapper(ODBSupport proxyDao) {
		super();
		this.proxyDao = proxyDao;
	}

	
	public DomainDaoSupport getDaosupport() {
		return proxyDao.getDaosupport();
	}

	
	public Class<?> getDomainClazz() {
		return proxyDao.getDomainClazz();
	}

	
	public String getDomainName() {
		return proxyDao.getDomainName();
	}

	
	public ServiceSpec getServiceSpec() {
		// TODO Auto-generated method stub
		return proxyDao.getServiceSpec();
	}

	
	public void setDaosupport(DomainDaoSupport arg0) {
		proxyDao.setDaosupport(arg0);
	}

	
	public Future<OValue[]> batchCompareAndDelete(OKey[] arg0, OValue[] arg1) throws ODBException {
		return proxyDao.batchCompareAndDelete(arg0, arg1);
	}

	
	public Future<OValue[]> batchCompareAndSwap(OKey[] arg0, OValue[] arg1, OValue[] arg2) throws ODBException {
		return proxyDao.batchCompareAndSwap(arg0, arg1, arg2);
	}

	
	public Future<OValue[]> batchDelete(OKey[] arg0) throws ODBException {
		return proxyDao.batchDelete(arg0);
	}

	
	public Future<OValue[]> batchPuts(OKey[] arg0, OValue[] arg1) throws ODBException {
		return proxyDao.batchPuts(arg0, arg1);
	}

	
	public Future<OValue> compareAndDelete(OKey arg0, OValue arg1) throws ODBException {
		return proxyDao.compareAndDelete(arg0, arg1);
	}

	
	public Future<OValue> compareAndSwap(OKey arg0, OValue arg1, OValue arg2) throws ODBException {
		return proxyDao.compareAndSwap(arg0, arg1, arg2);
	}

	
	public Future<OValue> delete(OKey arg0) throws ODBException {
		return proxyDao.delete(arg0);
	}

	
	public Future<OValue> get(OKey arg0) throws ODBException {
		return proxyDao.get(arg0);
	}

	
	public Future<OValue> get(String arg0) throws ODBException {
		return proxyDao.get(arg0);
	}

	
	public Future<OValue[]> list(OKey[] arg0) throws ODBException {
		return proxyDao.list(arg0);
	}

	
	public Future<List<OPair>> listBySecondKey(String arg0) throws ODBException {
		return proxyDao.listBySecondKey(arg0);
	}

	
	public Future<OValue> put(OKey arg0, OValue arg1) throws ODBException {
		return proxyDao.put(arg0, arg1);
	}

	
	public Future<OValue> put(String arg0, OValue arg1) throws ODBException {
		return proxyDao.put(arg0, arg1);
	}

	
	public Future<List<OPair>> putBySecondKey(String arg0, OValue[] arg1) throws ODBException {
		return proxyDao.putBySecondKey(arg0, arg1);
	}

	
	public Future<byte[]> putData(String arg0, byte[] arg1) throws ODBException {
		return proxyDao.putData(arg0, arg1);
	}

	
	public Future<OValue> putIfNotExist(OKey arg0, OValue arg1) throws ODBException {
		return proxyDao.putIfNotExist(arg0, arg1);
	}

	
	public Future<OValue[]> putIfNotExist(OKey[] arg0, OValue[] arg1) throws ODBException {
		return proxyDao.putIfNotExist(arg0, arg1);
	}

	
	public Future<String> putInfo(String arg0, String arg1) throws ODBException {
		return proxyDao.putInfo(arg0, arg1);
	}

	
	public Future<List<OPair>> removeBySecondKey(String arg0, OKey[] arg1) throws ODBException {
		return proxyDao.removeBySecondKey(arg0, arg1);
	}

	
	public void sync() throws ODBException {
		proxyDao.sync();
	}
	
	
	
}
