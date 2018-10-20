package org.brewchain.account.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.brewchain.account.core.PendingQueue;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.backend.ODBSupport;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.Message;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.ojpa.api.DomainDaoSupport;
import onight.tfw.ojpa.api.annotations.StoreDAO;

@NActorProvider
@Data
@Slf4j
@Instantiate(name = "txsdao_wrapper")
public class TxDaoWrapper extends SessionModules<Message> {

	Cache storage;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI enc;

	@StoreDAO(target = "bc_bdb", daoClass = SliceTxSecondaryDomain.class)
	ODBSupport proxyDao;

	public void setProxyDao(DomainDaoSupport proxyDao) {
		this.proxyDao = (ODBSupport) proxyDao;
	}

	public ODBSupport getProxyDao() {
		return proxyDao;
	}

	public TxDaoWrapper() {
		// super(proxyDao);
		this.storage = new Cache("pendingqueue_txs", 100000, MemoryStoreEvictionPolicy.LRU, true,
				"./pendingcache_txs", true, 0, 0, true, 120, null);
		PendingQueue.cacheManager.addCache(this.storage);

	}

	public Future<OValue[]> batchPuts(OKey[] keys, OValue[] values) throws ODBException {
		List<Element> elements = new ArrayList<>();
		for (int i = 0; i < keys.length; i++) {
			OKey k = keys[i];
			OValue v = values[i];
			elements.add(new Element(enc.hexEnc(OEntityBuilder.oKey2byteKey(k)), OEntityBuilder.oValue2byteValue(v)));
		}
		this.storage.putAll(elements);
		return ConcurrentUtils.constantFuture(null);
		// return super.batchPuts(arg0, arg1);
	}

	public Future<OValue> delete(OKey k) throws ODBException {
		if (this.storage.remove(enc.hexEnc(OEntityBuilder.oKey2byteKey(k)))) {
			return ConcurrentUtils.constantFuture(null);
		} else {
			// return super.delete(k);
			return proxyDao.delete(k);
		}

	}

	public Future<OValue> put(OKey k, OValue v) throws ODBException {
		this.storage.put(new Element(enc.hexEnc(OEntityBuilder.oKey2byteKey(k)), OEntityBuilder.oValue2byteValue(v)));

		return ConcurrentUtils.constantFuture(null);

	}

	public Future<OValue> get(OKey k) throws ODBException {
		Element ele = this.storage.get(enc.hexEnc(OEntityBuilder.oKey2byteKey(k)));
		if (ele != null) {
			Object ov = ele.getObjectValue();
			if (ov != null && ov instanceof byte[]) {
				return ConcurrentUtils.constantFuture(OEntityBuilder.byteValue2OValue((byte[]) ov));
			}
		}
		return proxyDao.get(k);
	}

	public Future<OValue> get(String k) throws ODBException {
		Element ele = this.storage.get(k);
		if (ele != null) {
			Object ov = ele.getObjectValue();
			if (ov != null && ov instanceof byte[]) {
				return ConcurrentUtils.constantFuture(OEntityBuilder.byteValue2OValue((byte[]) ov));
			}
		}
		return proxyDao.get(k);
	}

}
