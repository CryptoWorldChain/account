package org.brewchain.account.ept;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Hex;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.ept.EMTree.ETNode.BatchStorage;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.trie.StateTrie.Node;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.outils.pool.ReusefulLoopPool;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Block_EMTree")
@Data
@Slf4j
public class EMTree implements ActorService {
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;

	private final String AlphbetMap = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private final HashMap<Character, Integer> charAtIdx = init();
	private final int radix = AlphbetMap.length();
	private final BigInteger modx = new BigInteger("" + radix);

	private HashMap<Character, Integer> init() {
		HashMap<Character, Integer> hm = new HashMap<Character, Integer>();
		int i = 0;
		for (char c : AlphbetMap.toCharArray()) {
			hm.put(c, i);
			i++;
		}
		return hm;
	}

	ThreadLocal<BatchStorage> batchStorage = new ThreadLocal<>();
	ReusefulLoopPool<BatchStorage> bsPool = new ReusefulLoopPool<>();
	Cache<String, byte[]> cacheByHash = CacheBuilder.newBuilder().initialCapacity(10000)
			.expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(1000000)
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).build();
	private static ExecutorService executor = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 6);

	public static ExecutorService getExecutor() {
		return executor;
	}

	ETNode root;

	public ETNode delete(ETNode node, String key) {
		return root;
	}

	public byte[] getRootHash() throws Exception {
		return root.encode();
	}

	public void setRootHash(byte[] hash) {
		root = new ETNode().fromBytes(getHash(hash));
	}

	public ETNode insert(ETNode node, String key, byte[] value, int deep) {
		char ch = key.charAt(deep);
		ETNode child = node.getChild(ch);
		if (child == null) {
			ETNode oETNode = new ETNode(key, value);
			node.appendChildNode(oETNode, ch);
		} else {
			node.dirty = true;
			insert(child, key, value, deep + 1);
		}
		return node;
	}

	public byte[] get(byte[] key) {
		if (root == null) {
			return null;
		} else {
			return get(bytesToMapping(key), 0, root);
		}
	}

	public byte[] get(String key, int deep, ETNode lastNode) {
		char ch = key.charAt(deep);
		ETNode oETNode = lastNode.getChild(ch);
		if (oETNode == null) {
			return lastNode.contentData;
		} else if (oETNode.key.equals(key)) {
			return oETNode.v;
		} else {
			return get(key, deep + 1, oETNode);
		}
	}

	public void put(byte[] key, byte[] value) {
		putM(bytesToMapping(key), value);
	}

	public void put(String hexEnc, byte[] value) {
		putM(bytesToMapping(hexEnc), value);
	}

	public void putM(String mapKey, byte[] value) {
		if (root == null) {
			root = new ETNode(mapKey, value);
			root = insert(root, mapKey, value, 0);
		} else {
			if (value == null || value.length == 0) {
				root = delete(root, mapKey);
			} else {
				root = insert(root, mapKey, value, 0);
			}
		}
	}

	private int findIdx(char ch) {
		return charAtIdx.get(ch);
	}

	private String bytesToMapping(byte[] bb) {
		return bigIntToMapping(new BigInteger(bb));
	}

	private String bytesToMapping(String hexEnc) {
		return bigIntToMapping(new BigInteger(hexEnc, 16));
	}

	private String bigIntToMapping(BigInteger v) {
		StringBuffer sb = new StringBuffer();
		while (v.bitCount() > 0) {
			sb.append(AlphbetMap.charAt(v.mod(modx).intValue()));
			v = v.divide(modx);
		}
		return sb.reverse().toString();
	}

	public void addHash(byte[] hash, byte[] ret) {
		// System.out.println("addHash:" + type + ",hash=" +
		// Hex.toHexString(hash));

		BatchStorage bs = batchStorage.get();
		if (bs != null) {
			bs.add(hash, ret);
			cacheByHash.put(encApi.hexEnc(hash), ret);
		} else {
			// EHelper.dao.getAccountDao().put(oEntityHelper.byteKey2OKey(hash),
			// oEntityHelper.byteValue2OValue(ret));
		}
	}

	public byte[] getHash(byte[] hash) {
		byte[] content = cacheByHash.getIfPresent(encApi.hexEnc(hash));
		if (content == null) {
			try {
				OValue oOValue = dao.getAccountDao().get(oEntityHelper.byteKey2OKey(hash)).get();
				return oOValue.getExtdata().toByteArray();
			} catch (Exception e) {
				log.error("cannot resolve tree hash");
			}
		} else {
			return content;
		}
		return null;
	}

	class ETNode {
		String childrenHashs[] = new String[radix];
		private String key;
		private byte v[];
		private boolean dirty = true;
		private byte[] hash = null;
		private byte[] contentData = null;
		private ETNode children[] = new ETNode[radix];

		public void toJsonString(StringBuffer sb) {
			sb.append("{");
			sb.append("\"key\":\"").append(key).append("\"");
			for (int i = 0; i < radix; i++) {
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

		public byte[] encode() throws Exception {
			if (hash != null & !dirty) {
				return hash;
			}

			contentData = this.toBytes();
			hash = encApi.sha3Encode(contentData);
			dirty = false;

			BatchStorage bs = bsPool.borrow();
			if (bs == null) {
				bs = new BatchStorage();
			}
			try {
				batchStorage.set(bs);
				flushBS(bs);
			} catch (Exception e) {
				log.warn("error encode:" + e.getMessage(), e);
				throw e;
			} finally {
				if (bs != null) {
					if (bsPool.size() < 100) {
						bs.kvs.clear();
						// bs.values.clear();
						bsPool.retobj(bs);
					}
				}
				batchStorage.remove();
			}

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

					dao.getAccountDao().batchPuts(oks, ovs);
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
			return getHashs(findIdx(ch));
		}

		public ETNode getChild(char ch) {
			return children[findIdx(ch)];
		}

		public void appendChildNode(ETNode node, char ch) {
			int idx = findIdx(ch);
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
						childrenHashs[i] = encApi.hexEnc(bb);
						addHash(bb, node.contentData);
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

		public ETNode fromBytes(byte[] bb) {
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

		public ETNode() {
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
}
