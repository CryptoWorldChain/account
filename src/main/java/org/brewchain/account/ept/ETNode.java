package org.brewchain.account.ept;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Hex;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onight.tfw.outils.pool.ReusefulLoopPool;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Slf4j
public class ETNode {

	// String hashs[] = new String[EHelper.radix];
	String childrenHashs[] = new String[EHelper.radix];
	private String key;
	private byte v[];
	private boolean dirty = true;
	private byte[] hash = null;
	private byte[] contentData = null;
	private ETNode children[] = new ETNode[EHelper.radix];
	ThreadLocal<BatchStorage> batchStorage = new ThreadLocal<>();
	ReusefulLoopPool<BatchStorage> bsPool = new ReusefulLoopPool<>();
	Cache<String, byte[]> cacheByHash = CacheBuilder.newBuilder().initialCapacity(10000)
			.expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(1000000)
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).build();
	private static ExecutorService executor = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 6);

	public static ExecutorService getExecutor() {
		return executor;
	}

	public void toJsonString(StringBuffer sb) {
		sb.append("{");
		sb.append("\"key\":\"").append(key).append("\"");
		for (int i = 0; i < EHelper.radix; i++) {
			if (children[i] != null) {
				sb.append(",\"node_").append(i).append("\":");
				children[i].toJsonString(sb);
			}
		}
		if (v != null) {
			sb.append(",\"v\":\"").append(Hex.encodeHex(v)).append("\"");
		}
		sb.append("}");
	}

	private void addHash(byte[] hash, byte[] ret) {
		// System.out.println("addHash:" + type + ",hash=" +
		// Hex.toHexString(hash));

		BatchStorage bs = batchStorage.get();
		if (bs != null) {
			bs.add(hash, ret);
			cacheByHash.put(EHelper.encAPI.hexEnc(hash), ret);
		} else {
			EHelper.dao.getAccountDao().put(oEntityHelper.byteKey2OKey(hash), oEntityHelper.byteValue2OValue(ret));
		}
	}

	public byte[] encode() throws Exception {
		if (hash != null & !dirty) {
			return hash;
		}

		contentData = this.toBytes();
		hash = EHelper.encAPI.sha3Encode(contentData);
		dirty = false;
		return hash;
	}

	public void flushBS(BatchStorage bs) {
		long start = System.currentTimeMillis();

		int size = bs.kvs.size();
		if (size > 0) {
			try {
				OKey[] oks = new OKey[size];
				OValue[] ovs = new OValue[size];
				int i = 0;

				// String trace = "";
				for (Map.Entry<OKey, OValue> kvs : bs.kvs.entrySet()) {
					oks[i] = kvs.getKey();
					ovs[i] = kvs.getValue();

					i++;
				}

				EHelper.dao.getAccountDao().batchPuts(oks, ovs);
				bs.kvs.clear();
			} catch (Exception e) {
				log.warn("error in flushBS" + e.getMessage(), e);
			}
			// bs.values.clear();
		}
	}

	public String getHashs(int idx) {
		if (childrenHashs[idx] != null)
			return childrenHashs[idx];
		else
			return null;
	}

	public String getHashs(char ch) {
		return getHashs(EHelper.findIdx(ch));
	}

	public ETNode getChild(char ch) {
		return children[EHelper.findIdx(ch)];
	}

	public void appendChildNode(ETNode node, char ch) {
		int idx = EHelper.findIdx(ch);
		this.children[idx] = node;
		// this.childrenHashs[idx] = node.getKey();
		this.dirty = true;

		// node.encode();
		// addHash(node.getHash());
	}

	public byte[] toBytes() {
		try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
			StringBuffer sb = new StringBuffer(key);
			for (int i = 0; i < children.length; i++) {
				ETNode node = this.children[i];
				if (node != null) {
					byte[] bb = node.encode();
					childrenHashs[i] = EHelper.encAPI.hexEnc(bb);
					addHash(bb, node.getContentData());
					sb.append(",");
					sb.append(childrenHashs[i]);
				} else {
					sb.append(",");
				}
			}
			byte[] hashBB = sb.toString().getBytes();
			bout.write(hashBB.length & 0xFF);
			bout.write((hashBB.length >> 8) & 0xFF);
			bout.write(hashBB);
			if (v != null && v.length > 0) {
				bout.write(v);
			}
			return bout.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static ETNode fromBytes(byte[] bb) {
		try (ByteArrayInputStream bin = new ByteArrayInputStream(bb);) {
			int lenbb1 = bin.read();
			int lenbb2 = bin.read();
			int len = (lenbb1 & 0xff) | ((lenbb2 << 8) & 0xff00);
			byte hashBB[] = new byte[len];
			bin.read(hashBB);
			String[] strs = new String(hashBB).split(",");
			ETNode node = new ETNode();
			byte[] bbv;
			node.key = strs[0];
			for (int i = 1; i < strs.length; i++) {
				node.childrenHashs[i - 1] = strs[i];
			}
			if (bin.available() > 0) {
				bbv = new byte[bin.available()];
				bin.read(bbv);
				node.v = bbv;
			}

			return node;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public ETNode(String key) {
		this.key = key;
	}

	public ETNode(String key, byte[] v) {
		this.key = key;
		this.v = v;
		// appendChildNode(this, key.charAt(0));
	}

	class BatchStorage {
		LinkedHashMap<OKey, OValue> kvs = new LinkedHashMap<>();

		public void add(byte[] key, byte[] v) {
			kvs.put(oEntityHelper.byteKey2OKey(key), oEntityHelper.byteValue2OValue(v));
		}

		public void remove(byte[] key) {
			kvs.remove(oEntityHelper.byteKey2OKey(key));
		}
	}
}
