package org.brewchain.account.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.backend.ODBHelper;
import org.brewchain.bcapi.backend.ODBSupport;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OPair;
import org.brewchain.bcapi.gens.Oentity.OValue;

import com.google.protobuf.ByteString;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.Transaction;

import lombok.extern.slf4j.Slf4j;
import onight.tfw.ojpa.api.DomainDaoSupport;
import onight.tfw.ojpa.api.ServiceSpec;

@Slf4j
public class MemoryODBSupport implements ODBSupport {

	Class domainClazz;
	String domainName;
	DomainDaoSupport ddsProxy;
	ConcurrentHashMap<OKey, OValue> dbs = new ConcurrentHashMap<>();

	@Override
	public DomainDaoSupport getDaosupport() {
		return this;
	}

	@Override
	public Class<?> getDomainClazz() {
		return domainClazz;
	}

	@Override
	public String getDomainName() {
		return domainName;
	}

	@Override
	public ServiceSpec getServiceSpec() {
		return new ServiceSpec("memory");
	}

	@Override
	public void setDaosupport(DomainDaoSupport dds) {
		ddsProxy = dds;
	}

	@Override
	public Future<OValue[]> batchCompareAndDelete(OKey[] keys, OValue[] values) throws ODBException {
		List<OValue> list = new ArrayList<OValue>();

		for (int i = 0; i < keys.length; i++) {
			if (dbs.containsKey(keys[i]) && values[i].equals(dbs.get(keys[i]))) {
				dbs.remove(keys[i]);
				list.add(values[i]);
			}
		}
		return ConcurrentUtils.constantFuture((OValue[]) list.toArray());
	}

	@Override
	public Future<OValue[]> batchCompareAndSwap(OKey[] keys, OValue[] compareValues, OValue[] newValues)
			throws ODBException {
		List<OValue> list = new ArrayList<OValue>();
		try {
			for (int i = 0; i < keys.length; i++) {
				OValue v = dbs.get(keys[i]);
				// DatabaseEntry searchEntry = new DatabaseEntry();
				if (v != null && v.equals(compareValues[i])) {
				} else {
					dbs.put(keys[i], newValues[i]);
					list.add(v);
				}
			}
		} catch (Exception e) {
			log.error("fail to batch swap::ex=" + e);
		}
		return ConcurrentUtils.constantFuture((OValue[]) list.toArray());
	}

	@Override
	public Future<OValue[]> batchDelete(OKey[] keys) throws ODBException {
		try {
			// need TransactionConfig?
			for (OKey key : keys) {
				this.dbs.remove(key);
			}
		} catch (Exception ex) {
			log.error("fail to batch delete::ex=" + ex);
		}

		return ConcurrentUtils.constantFuture(null);
	}

	@Override
	public Future<OValue[]> batchPuts(OKey[] keys, OValue[] values) throws ODBException {
		try {
			// need TransactionConfig?
			for (int i = 0; i < keys.length; i++) {
				this.dbs.put(keys[i], values[i]);
			}
		} catch (Exception ex) {
			log.error("fail to batch put::ex=" + ex);

		}
		return ConcurrentUtils.constantFuture(null);
	}

	@Override
	public Future<OValue> compareAndDelete(OKey key, OValue value) throws ODBException {
		OValue v = dbs.get(key);
		if (v.equals(value)) {
		} else {
			dbs.remove(key);
		}
		return ConcurrentUtils.constantFuture(v);
	}

	@Override
	public Future<OValue> compareAndSwap(OKey key, OValue compareValue, OValue newValue) throws ODBException {
		OValue v = dbs.get(key);
		if (v.equals(compareValue)) {
			return ConcurrentUtils.constantFuture(null);

		} else {
			dbs.put(key, newValue);
			return ConcurrentUtils.constantFuture(newValue);
		}

	}

	@Override
	public Future<OValue> delete(OKey key) throws ODBException {
		dbs.remove(key);
		return ConcurrentUtils.constantFuture(null);
	}

	@Override
	public Future<OValue> get(OKey key) throws ODBException {
		return ConcurrentUtils.constantFuture(dbs.get(key));
	}

	@Override
	public Future<OValue[]> list(OKey[] keys) throws ODBException {
		List<OValue> list = new ArrayList<OValue>();
		for (OKey key : keys) {
			OValue v = dbs.get(key);
			if (v != null) {
				list.add(v);
			}
		}
		return ConcurrentUtils.constantFuture((OValue[]) list.toArray());
	}

	@Override
	public Future<OValue> put(OKey key, OValue v) throws ODBException {
		dbs.put(key, v);
		return ConcurrentUtils.constantFuture(v);
	}

	@Override
	public Future<byte[]> putData(String key, byte[] value) throws ODBException {
		put(OKey.newBuilder().setData(ByteString.copyFrom(key.getBytes())).build(),
				OValue.newBuilder().setExtdata(ByteString.copyFrom(value)).build());
		return ConcurrentUtils.constantFuture(value);
	}

	@Override
	public Future<String> putInfo(String key, String value) throws ODBException {
		put(OKey.newBuilder().setData(ByteString.copyFrom(key.getBytes())).build(),
				OValue.newBuilder().setInfo(value).build());
		return ConcurrentUtils.constantFuture(value);
	}

	@Override
	public Future<OValue> get(String key) throws ODBException {
		return get(OKey.newBuilder().setData(ByteString.copyFrom(key.getBytes())).build());
	}

	@Override
	public Future<OValue> put(String key, OValue value) throws ODBException {
		return put(OKey.newBuilder().setData(ByteString.copyFrom(key.getBytes())).build(), value);

	}

	@Override

	public Future<List<OPair>> listBySecondKey(String secondaryName) throws ODBException {
		throw new RuntimeException("Not supported");
	}

	@Override
	public Future<List<OPair>> putBySecondKey(String arg0, OValue[] arg1) throws ODBException {
		throw new RuntimeException("Not supported");
	}

	@Override
	public Future<List<OPair>> removeBySecondKey(String secondaryName, OKey[] keys) throws ODBException {
		// 取出一个key
		try {
			List<OPair> list = listBySecondKey(secondaryName).get();
			List<OKey> existsKeys = new ArrayList<OKey>();
			List<OValue> existsValues = new ArrayList<OValue>();
			for (OPair oPair : list) {
				for (OKey oKey : keys) {
					if (oPair.getKey().equals(oKey)) {
						existsKeys.add(oKey);
						OValue.Builder oOValue = oPair.getValue().toBuilder();
						oOValue.setSecondKey("tx_deleted");
						existsValues.add(oOValue.build());
					}
				}
			}
			batchDelete((OKey[]) existsKeys.toArray());
			batchPuts((OKey[]) existsKeys.toArray(), (OValue[]) existsValues.toArray());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public Future<OValue> putIfNotExist(OKey key, OValue v) throws ODBException {
		OValue oldv = dbs.putIfAbsent(key, v);
		if (oldv != null) {
			return ConcurrentUtils.constantFuture(null);
		} else {
			return ConcurrentUtils.constantFuture(v);
		}
	}

	/**
	 * 如果存在返回null，如果不存在返回当前值
	 */
	@Override
	public Future<OValue[]> putIfNotExist(OKey[] keys, OValue[] values) throws ODBException {
		try {
			// need TransactionConfig?
			OValue[] ret = new OValue[keys.length];
			for (int i = 0; i < keys.length; i++) {
				OValue oldv = dbs.putIfAbsent(keys[i], values[i]);
				if (oldv != null) {
					ret[i] = null;
				} else {
					ret[i] = values[i];
				}
			}
			return ConcurrentUtils.constantFuture(ret);
		} catch (Exception ex) {
			log.error("fail to batch put::ex=" + ex);

		}
		return ConcurrentUtils.constantFuture(null);
	}

	@Override
	public void sync() throws ODBException {
		// TODO Auto-generated method stub

	}

}
