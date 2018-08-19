package org.brewchain.account.test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.Hex;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.ecrypto.impl.EncInstance;
import org.fc.brewchain.bcapi.KeyPairs;

/**
 * mpt， 20000笔，执行大概300ms
 * 00:14:51.650 [main] INFO org.brewchain.ecrypto.impl.EncInstance - CLibs loading success:org.brewchain.core.crypto.jni.IPPCrypto@33d8341c
root=e041ef2e6f49fb7d0ac74fa96e6308bbab55a839734f15acb75718e1c8560a2e
odbs.size=26878
gen pbuffer=11505
get put to trie=77
get roothashcost=278
total=11582


 * @author brew
 *
 */
public class TestHexDec {

	DefDaos daos = new DefDaos();
	StateTrie st=new StateTrie();
	EncInstance encAPI = new EncInstance();
	MemoryODBSupport odbs=new MemoryODBSupport();
	public void init() {
		daos.setAccountDao(odbs);
		st.setAsync(true);
		st.setDao(daos);
		encAPI.startup();
		st.setEncApi(encAPI);
		st.setOEntityHelper(new OEntityBuilder());
	}

	public void batchPuts() throws Exception {
		long startTime = System.currentTimeMillis();
		int hashsize = odbs.dbs.size();
		ConcurrentHashMap<String, String> dbs = new ConcurrentHashMap<>();

		for(int i=0;i<20000;i++)
		{
			KeyPairs kp = encAPI.genKeys();
			dbs.put(kp.getAddress(),kp.getPrikey());
		}
		long end1 = System.currentTimeMillis();
		for(Map.Entry<String, String> kvs:dbs.entrySet()){
			encAPI.hexDec(kvs.getKey());
		}
		long end2 = System.currentTimeMillis();
		for(Map.Entry<String, String> kvs:dbs.entrySet()){
			Hex.decodeHex(kvs.getKey().toCharArray());
		}
		long end3 = System.currentTimeMillis();
		System.out.println("odbs.size="+odbs.dbs.size());
		System.out.println("gen pbuffer="+(end1-startTime));
		System.out.println("get hexdec cost="+(end2-end1));
		System.out.println("get hexdec cost="+(end3-end2));
		System.out.println("total="+(end2-startTime));
	}
	

	public static void main(String[] args) {
		try {
			TestHexDec test = new TestHexDec();
			test.init();
			test.batchPuts();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
