package org.brewchain.account.trie;

import static org.apache.commons.lang3.concurrent.ConcurrentUtils.constantFuture;
import static org.brewchain.account.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.brewchain.account.util.RLP.EMPTY_ELEMENT_RLP;
import static org.brewchain.account.util.RLP.encodeElement;
import static org.brewchain.account.util.RLP.encodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.text.StrBuilder;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.BlockStateTrieRuntimeException;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.account.util.RLP;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.outils.conf.PropHelper;
import onight.tfw.outils.pool.ReusefulLoopPool;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Block_StateTrie")
@Slf4j
@Data
public class StateTrie implements ActorService {
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;

	private final static Object NULL_NODE = new Object();
	private final static int MIN_BRANCHES_CONCURRENTLY = 4;// Math.min(16,Runtime.getRuntime().availableProcessors());
	private static ExecutorService executor = new ForkJoinPool(new PropHelper(null)
			.get("org.brewchain.account.state.parallel", Runtime.getRuntime().availableProcessors() * 2));

	public static ExecutorService getExecutor() {
		return executor;
	}

	public enum NodeType {
		BranchNode, KVNodeValue, KVNodeNode
	}

	public class BatchStorage {
		public LinkedHashMap<OKey, OValue> kvs = new LinkedHashMap<>();

		public void add(byte[] key, byte[] v) {
			kvs.put(oEntityHelper.byteKey2OKey(key), oEntityHelper.byteValue2OValue(v));
		}

		public void remove(byte[] key) {
			kvs.remove(oEntityHelper.byteKey2OKey(key));
		}
	}

	ThreadLocal<BatchStorage> batchStorage = new ThreadLocal<>();
	ReusefulLoopPool<BatchStorage> bsPool = new ReusefulLoopPool<>();

	public final class Node {
		private byte[] hash = null;
		private byte[] rlp = null;
		private RLP.LList parsedRlp = null;
		private boolean dirty = false;

		private Object[] children = null;

		// new empty BranchNode
		public Node() {
			children = new Object[17];
			dirty = true;
		}

		// new KVNode with key and (value or node)
		public Node(TrieKey key, Object valueOrNode) {
			this(new Object[] { key, valueOrNode });
			dirty = true;
		}

		// new Node with hash or RLP
		public Node(byte[] hashOrRlp) {
			if (hashOrRlp.length == 32) {
				this.hash = hashOrRlp;
			} else {
				this.rlp = hashOrRlp;
			}
		}

		private Node(RLP.LList parsedRlp) {
			this.parsedRlp = parsedRlp;
			this.rlp = parsedRlp.getEncoded();
		}

		private Node(Object[] children) {
			this.children = children;
		}

		public boolean resolveCheck() {
			if (rlp != null || parsedRlp != null || hash == null)
				return true;
			rlp = getHash(hash);
			return rlp != null;
		}

		private void resolve() {
			if (!resolveCheck()) {
				throw new BlockStateTrieRuntimeException(
						"Invalid Trie state, can't resolve hash " + Hex.toHexString(hash));
			}
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

						// trace +=
						// encApi.hexEnc(kvs.getKey().getData().toByteArray()) +
						// System.lineSeparator();
						i++;
					}

					dao.getAccountDao().batchPuts(oks, ovs);
					// log.debug("state trie batch puts " + size + " trace::" +
					// trace);
					bs.kvs.clear();
				} catch (Exception e) {
					log.warn("error in flushBS" + e.getMessage(), e);
				}
				// bs.values.clear();
			}
		}

		public byte[] encode() {
			BatchStorage bs = bsPool.borrow();
			if (bs == null) {
				bs = new BatchStorage();
			}
			try {
				batchStorage.set(bs);
				byte[] ret = encode(1, true);
				flushBS(bs);
				// dao.getAccountDao().put(oEntityHelper.byteKey2OKey(hash),
				// oEntityHelsper.byteValue2OValue(ret));
				return ret;
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
		}

		private byte[] encode(final int depth, boolean forceHash) {
			// BatchStorage bs = batchStorage.get();
			if (!dirty) {
				return hash != null ? encodeElement(hash) : rlp;
			} else {
				NodeType type = getType();
				byte[] ret;
				if (type == NodeType.BranchNode) {
					if (depth == 1 && async) {
						// parallelize encode() on the first trie level only and
						// if there are at least
						// MIN_BRANCHES_CONCURRENTLY branches are modified
						final Object[] encoded = new Object[17];
						int encodeCnt = 0;
						for (int i = 0; i < 16; i++) {
							final Node child = branchNodeGetChild(i);
							if (child == null) {
								encoded[i] = EMPTY_ELEMENT_RLP;
							} else if (!child.dirty) {
								encoded[i] = child.encode(depth + 1, false);
							} else {
								encodeCnt++;
							}
						}
						for (int i = 0; i < 16; i++) {
							if (encoded[i] == null) {
								final Node child = branchNodeGetChild(i);
								if (encodeCnt >= MIN_BRANCHES_CONCURRENTLY) {
									Callable<byte[]> oCallable = new Callable<byte[]>() {
										@Override
										public byte[] call() throws Exception {
											BatchStorage bs = bsPool.borrow();
											if (bs == null) {
												bs = new BatchStorage();
											}
											try {
												if (bs != null) {
													batchStorage.set(bs);
												}
												byte[] ret = child.encode(depth + 1, false);
												// flush
												flushBS(bs);
												return ret;
											} catch (Exception e) {
												log.error("error in exec bs:" + e.getMessage(), e);
												throw e;
											} finally {
												if (bs != null) {
													batchStorage.remove();
													if (bsPool.size() < 100) {
														bs.kvs.clear();
														// bs.values.clear();
														bsPool.retobj(bs);
													}
												}
											}
										}
									};
									encoded[i] = getExecutor().submit(oCallable);
									oCallable = null;
								} else {
									encoded[i] = child.encode(depth + 1, false);
								}
							}
						}
						byte[] value = branchNodeGetValue();
						encoded[16] = constantFuture(encodeElement(value));
						try {
							ret = encodeRlpListFutures(encoded);
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					} else {
						byte[][] encoded = new byte[17][];
						for (int i = 0; i < 16; i++) {
							Node child = branchNodeGetChild(i);
							encoded[i] = child == null ? EMPTY_ELEMENT_RLP : child.encode(depth + 1, false);
						}
						byte[] value = branchNodeGetValue();
						encoded[16] = encodeElement(value);
						ret = encodeList(encoded);
					}
				} else if (type == NodeType.KVNodeNode) {
					ret = encodeList(encodeElement(kvNodeGetKey().toPacked()),
							kvNodeGetChildNode().encode(depth + 1, false));
				} else {
					byte[] value = kvNodeGetValue();
					ret = encodeList(encodeElement(kvNodeGetKey().toPacked()),
							encodeElement(value == null ? EMPTY_BYTE_ARRAY : value));
				}
				if (hash != null) {
					deleteHash(hash);
				}
				dirty = false;

				if (ret.length < 32 && !forceHash) {
					rlp = ret;
					return ret;
				} else {
					hash = encApi.sha3Encode(ret);
					// hash = ret;
					addHash(hash, ret);
					return encodeElement(hash);
				}
			}
		}

		@SafeVarargs
		private final byte[] encodeRlpListFutures(Object... list) throws ExecutionException, InterruptedException {
			byte[][] vals = new byte[list.length][];
			for (int i = 0; i < list.length; i++) {
				if (list[i] instanceof Future) {
					vals[i] = ((Future<byte[]>) list[i]).get();
				} else {
					vals[i] = (byte[]) list[i];
				}
			}
			return encodeList(vals);
		}

		private void parse() {
			if (children != null)
				return;
			resolve();

			RLP.LList list = parsedRlp == null ? RLP.decodeLazyList(rlp) : parsedRlp;

			if (list.size() == 2) {
				children = new Object[2];
				TrieKey key = TrieKey.fromPacked(list.getBytes(0));
				children[0] = key;
				if (key.isTerminal()) {
					children[1] = list.getBytes(1);
				} else {
					children[1] = list.isList(1) ? new Node(list.getList(1)) : new Node(list.getBytes(1));
				}
			} else {
				children = new Object[17];
				parsedRlp = list;
			}
		}

		public Node branchNodeGetChild(int hex) {
			parse();
			assert getType() == NodeType.BranchNode;
			Object n = children[hex];
			if (n == null && parsedRlp != null) {
				if (parsedRlp.isList(hex)) {
					n = new Node(parsedRlp.getList(hex));
				} else {
					byte[] bytes = parsedRlp.getBytes(hex);
					if (bytes.length == 0) {
						n = NULL_NODE;
					} else {
						n = new Node(bytes);
					}
				}
				children[hex] = n;
			}
			return n == NULL_NODE ? null : (Node) n;
		}

		public Node branchNodeSetChild(int hex, Node node) {
			parse();
			assert getType() == NodeType.BranchNode;
			children[hex] = node == null ? NULL_NODE : node;
			dirty = true;
			return this;
		}

		public byte[] branchNodeGetValue() {
			parse();
			assert getType() == NodeType.BranchNode;
			Object n = children[16];
			if (n == null && parsedRlp != null) {
				byte[] bytes = parsedRlp.getBytes(16);
				if (bytes.length == 0) {
					n = NULL_NODE;
				} else {
					n = bytes;
				}
				children[16] = n;
			}
			return n == NULL_NODE ? null : (byte[]) n;
		}

		public Node branchNodeSetValue(byte[] val) {
			parse();
			assert getType() == NodeType.BranchNode;
			children[16] = val == null ? NULL_NODE : val;
			dirty = true;
			return this;
		}

		public int branchNodeCompactIdx() {
			parse();
			assert getType() == NodeType.BranchNode;
			int cnt = 0;
			int idx = -1;
			for (int i = 0; i < 16; i++) {
				if (branchNodeGetChild(i) != null) {
					cnt++;
					idx = i;
					if (cnt > 1)
						return -1;
				}
			}
			return cnt > 0 ? idx : (branchNodeGetValue() == null ? -1 : 16);
		}

		public boolean branchNodeCanCompact() {
			parse();
			assert getType() == NodeType.BranchNode;
			int cnt = 0;
			for (int i = 0; i < 16; i++) {
				cnt += branchNodeGetChild(i) == null ? 0 : 1;
				if (cnt > 1)
					return false;
			}
			return cnt == 0 || branchNodeGetValue() == null;
		}

		public TrieKey kvNodeGetKey() {
			parse();
			assert getType() != NodeType.BranchNode;
			return (TrieKey) children[0];
		}

		public Node kvNodeGetChildNode() {
			parse();
			assert getType() == NodeType.KVNodeNode;
			return (Node) children[1];
		}

		public byte[] kvNodeGetValue() {
			parse();
			assert getType() == NodeType.KVNodeValue;
			return (byte[]) children[1];
		}

		public Node kvNodeSetValue(byte[] value) {
			parse();
			assert getType() == NodeType.KVNodeValue;
			children[1] = value;
			dirty = true;
			return this;
		}

		public Object kvNodeGetValueOrNode() {
			parse();
			assert getType() != NodeType.BranchNode;
			return children[1];
		}

		public Node kvNodeSetValueOrNode(Object valueOrNode) {
			parse();
			assert getType() != NodeType.BranchNode;
			children[1] = valueOrNode;
			dirty = true;
			return this;
		}

		public NodeType getType() {
			parse();

			return children.length == 17 ? NodeType.BranchNode
					: (children[1] instanceof Node ? NodeType.KVNodeNode : NodeType.KVNodeValue);
		}

		public void dispose() {
			if (hash != null) {
				deleteHash(hash);
			}
		}

		public Node invalidate() {
			dirty = true;
			return this;
		}

		/*********** Dump methods ************/

		// public String dumpStruct(String indent, String prefix) {
		// String ret = indent + prefix + getType() + (dirty ? " *" : "")
		// + (hash == null ? "" : "(hash: " + Hex.toHexString(hash).substring(0,
		// 6) + ")");
		// if (getType() == NodeType.BranchNode) {
		// byte[] value = branchNodeGetValue();
		// ret += (value == null ? "" : " [T] = " + Hex.toHexString(value)) +
		// "\n";
		// for (int i = 0; i < 16; i++) {
		// Node child = branchNodeGetChild(i);
		// if (child != null) {
		// ret += child.dumpStruct(indent + " ", "[" + i + "] ");
		// }
		// }
		//
		// } else if (getType() == NodeType.KVNodeNode) {
		// ret += " [" + kvNodeGetKey() + "]\n";
		// ret += kvNodeGetChildNode().dumpStruct(indent + " ", "");
		// } else {
		// ret += " [" + kvNodeGetKey() + "] = " +
		// Hex.toHexString(kvNodeGetValue()) + "\n";
		// }
		// return ret;
		// }

		// public List<String> dumpTrieNode(boolean compact) {
		// List<String> ret = new ArrayList<>();
		// if (hash != null) {
		// ret.add(hash2str(hash, compact) + " ==> " + dumpContent(false,
		// compact));
		// }
		//
		// if (getType() == NodeType.BranchNode) {
		// for (int i = 0; i < 16; i++) {
		// Node child = branchNodeGetChild(i);
		// if (child != null)
		// ret.addAll(child.dumpTrieNode(compact));
		// }
		// } else if (getType() == NodeType.KVNodeNode) {
		// ret.addAll(kvNodeGetChildNode().dumpTrieNode(compact));
		// }
		// return ret;
		// }
		//
		// private String dumpContent(boolean recursion, boolean compact) {
		// if (recursion && hash != null)
		// return hash2str(hash, compact);
		// String ret;
		// if (getType() == NodeType.BranchNode) {
		// ret = "[";
		// for (int i = 0; i < 16; i++) {
		// Node child = branchNodeGetChild(i);
		// ret += i == 0 ? "" : ",";
		// ret += child == null ? "" : child.dumpContent(true, compact);
		// }
		// byte[] value = branchNodeGetValue();
		// ret += value == null ? "" : ", " + val2str(value, compact);
		// ret += "]";
		// } else if (getType() == NodeType.KVNodeNode) {
		// ret = "[<" + kvNodeGetKey() + ">, " +
		// kvNodeGetChildNode().dumpContent(true, compact) + "]";
		// } else {
		// ret = "[<" + kvNodeGetKey() + ">, " + val2str(kvNodeGetValue(),
		// compact) + "]";
		// }
		// return ret;
		// }

		@Override
		public String toString() {
			return getType() + (dirty ? " *" : "") + (hash == null ? "" : "(hash: " + Hex.toHexString(hash) + " )");
		}
	}

	public interface ScanAction {

		void doOnNode(byte[] hash, Node node);

		void doOnValue(byte[] nodeHash, Node node, byte[] key, byte[] value);
	}

	// private TrieCache cache;
	private Node root;
	private boolean async = true;

	// public StateTrie() {
	// this((byte[]) null);
	// }
	//
	// public StateTrie(byte[] root) {
	// setRoot(root);
	// }

	public void setAsync(boolean async) {
		this.async = async;
	}

	private void encode() {
		if (root != null) {
			root.encode();
		}
	}

	public void setRoot(byte[] root) {
		if (root != null && !FastByteComparisons.equal(root, ByteUtil.EMPTY_BYTE_ARRAY)) {
			this.root = new Node(root);
		} else {
			this.root = null;
		}

	}

	private boolean hasRoot() {
		return root != null && root.resolveCheck();
	}

	Cache<String, byte[]> cacheByHash = CacheBuilder.newBuilder().initialCapacity(10000)
			.expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(200000)
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).build();

	private byte[] getHash(byte[] hash) {
		OKey key = oEntityHelper.byteKey2OKey(hash);
		String hexHash = encApi.hexEnc(hash);
		OValue v = null;
		// BatchStorage bs = batchStorage.get();
		// if (bs != null) {
		// v = bs.kvs.get(key);
		// }
		try {
			if (v == null) {
				byte[] body = cacheByHash.getIfPresent(hexHash);
				if (body != null) {
					return body;
				}
				v = dao.getAccountDao().get(key).get();

			}
			if (v != null && v.getExtdata() != null && !v.getExtdata().equals(ByteString.EMPTY)) {
				byte[] body = v.getExtdata().toByteArray();
				cacheByHash.put(hexHash, body);
				return body;
			}
		} catch (Exception e) {
			log.warn("getHash Error:" + e.getMessage() + ",key=" + hexHash, e);
		}
		return null;
	}

	private void addHash(byte[] hash, byte[] ret) {
		// System.out.println("addHash:" + type + ",hash=" +
		// Hex.toHexString(hash));

		BatchStorage bs = batchStorage.get();
		if (bs != null) {
			// log.debug("add into state trie key::" + encApi.hexEnc(hash));
			// if (type == NodeType.KVNodeNode || type == NodeType.KVNodeValue)
			// {
			bs.add(hash, ret);
			// }
			cacheByHash.put(encApi.hexEnc(hash), ret);
		} else {
			// if (type == NodeType.KVNodeNode || type == NodeType.KVNodeValue)
			// {

			dao.getAccountDao().put(oEntityHelper.byteKey2OKey(hash), oEntityHelper.byteValue2OValue(ret));
			// }
		}
	}

	private void deleteHash(byte[] hash) {
		BatchStorage bs = batchStorage.get();
		if (bs != null) {
			// log.debug("add into state trie key::" + encApi.hexEnc(hash));
			bs.remove(hash);
			// log.debug("state trie batch bs " + encApi.hexEnc(hash));
		}
	}

	public byte[] get(byte[] key) {
		if (!hasRoot())
			return null; // treating unknown root hash as empty trie
		TrieKey k = TrieKey.fromNormal(key);
		return get(root, k);
	}

	private byte[] get(Node n, TrieKey k) {
		if (n == null)
			return null;

		NodeType type = n.getType();
		if (type == NodeType.BranchNode) {
			if (k.isEmpty())
				return n.branchNodeGetValue();
			Node childNode = n.branchNodeGetChild(k.getHex(0));
			// childNode.hash
			return get(childNode, k.shift(1));
		} else {
			TrieKey k1 = k.matchAndShift(n.kvNodeGetKey());
			if (k1 == null)
				return null;
			if (type == NodeType.KVNodeValue) {
				return k1.isEmpty() ? n.kvNodeGetValue() : null;
			} else {
				return get(n.kvNodeGetChildNode(), k1);
			}
		}
	}

	public void put(byte[] key, byte[] value) {
		TrieKey k = TrieKey.fromNormal(key);
		if (root == null) {
			if (value != null && value.length > 0) {
				root = new Node(k, value);
			}
		} else {
			if (value == null || value.length == 0) {
				root = delete(root, k);
			} else {
				root = insert(root, k, value);
			}
		}
	}

	private Node insert(Node n, TrieKey k, Object nodeOrValue) {
		NodeType type = n.getType();
		if (type == NodeType.BranchNode) {
			if (k.isEmpty())
				return n.branchNodeSetValue((byte[]) nodeOrValue);
			Node childNode = n.branchNodeGetChild(k.getHex(0));
			if (childNode != null) {
				return n.branchNodeSetChild(k.getHex(0), insert(childNode, k.shift(1), nodeOrValue));
			} else {
				TrieKey childKey = k.shift(1);
				Node newChildNode;
				if (!childKey.isEmpty()) {
					newChildNode = new Node(childKey, nodeOrValue);
				} else {
					newChildNode = nodeOrValue instanceof Node ? (Node) nodeOrValue : new Node(childKey, nodeOrValue);
				}
				return n.branchNodeSetChild(k.getHex(0), newChildNode);
			}
		} else {
			TrieKey commonPrefix = k.getCommonPrefix(n.kvNodeGetKey());
			if (commonPrefix.isEmpty()) {
				Node newBranchNode = new Node();
				insert(newBranchNode, n.kvNodeGetKey(), n.kvNodeGetValueOrNode());
				insert(newBranchNode, k, nodeOrValue);
				n.dispose();
				return newBranchNode;
			} else if (commonPrefix.equals(k)) {
				return n.kvNodeSetValueOrNode(nodeOrValue);
			} else if (commonPrefix.equals(n.kvNodeGetKey())) {
				insert(n.kvNodeGetChildNode(), k.shift(commonPrefix.getLength()), nodeOrValue);
				return n.invalidate();
			} else {
				Node newBranchNode = new Node();
				Node newKvNode = new Node(commonPrefix, newBranchNode);
				// TODO can be optimized
				insert(newKvNode, n.kvNodeGetKey(), n.kvNodeGetValueOrNode());
				insert(newKvNode, k, nodeOrValue);
				n.dispose();
				return newKvNode;
			}
		}
	}

	public void delete(byte[] key) {
		TrieKey k = TrieKey.fromNormal(key);
		if (root != null) {
			root = delete(root, k);
		}
		cacheByHash.invalidate(key);
	}

	private Node delete(Node n, TrieKey k) {
		NodeType type = n.getType();
		Node newKvNode;
		if (type == NodeType.BranchNode) {
			if (k.isEmpty()) {
				n.branchNodeSetValue(null);
			} else {
				int idx = k.getHex(0);
				Node child = n.branchNodeGetChild(idx);
				if (child == null)
					return n; // no key found

				Node newNode = delete(child, k.shift(1));
				n.branchNodeSetChild(idx, newNode);
				if (newNode != null)
					return n; // newNode != null thus number of children didn't
								// decrease
			}

			// child node or value was deleted and the branch node may need to
			// be compacted
			int compactIdx = n.branchNodeCompactIdx();
			if (compactIdx < 0)
				return n; // no compaction is required

			// only value or a single child left - compact branch node to kvNode
			n.dispose();
			if (compactIdx == 16) { // only value left
				return new Node(TrieKey.empty(true), n.branchNodeGetValue());
			} else { // only single child left
				newKvNode = new Node(TrieKey.singleHex(compactIdx), n.branchNodeGetChild(compactIdx));
			}
		} else { // n - kvNode
			TrieKey k1 = k.matchAndShift(n.kvNodeGetKey());
			if (k1 == null) {
				// no key found
				return n;
			} else if (type == NodeType.KVNodeValue) {
				if (k1.isEmpty()) {
					// delete this kvNode
					n.dispose();
					return null;
				} else {
					// else no key found
					return n;
				}
			} else {
				Node newChild = delete(n.kvNodeGetChildNode(), k1);
				if (newChild == null)
					throw new RuntimeException("Shouldn't happen");
				newKvNode = n.kvNodeSetValueOrNode(newChild);
			}
		}

		// if we get here a new kvNode was created, now need to check
		// if it should be compacted with child kvNode
		Node newChild = newKvNode.kvNodeGetChildNode();
		if (newChild.getType() != NodeType.BranchNode) {
			// two kvNodes should be compacted into a single one
			TrieKey newKey = newKvNode.kvNodeGetKey().concat(newChild.kvNodeGetKey());
			Node newNode = new Node(newKey, newChild.kvNodeGetValueOrNode());
			newChild.dispose();
			newKvNode.dispose();
			return newNode;
		} else {
			// no compaction needed
			return newKvNode;
		}
	}

	public byte[] getRootHash() {
		encode();
		return root != null ? root.hash : ByteUtil.EMPTY_BYTE_ARRAY;
	}

	public void clear() {
		if (root != null && root.dirty) {
			// persist all dirty nodes to underlying Source
			// encode();
			// release all Trie Node instances for GC
			root = null;
			root = new Node(root.hash);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		StateTrie oStateTrie = (StateTrie) o;

		return FastByteComparisons.equal(getRootHash(), oStateTrie.getRootHash());

	}

	// public String dumpStructure() {
	// return root == null ? "<empty>" : root.dumpStruct("", "");
	// }

	// public String dumpTrie() {
	// return dumpTrie(true);
	// }

	// public String dumpTrie(boolean compact) {
	// if (root == null)
	// return "<empty>";
	// encode();
	// StrBuilder ret = new StrBuilder();
	// List<String> strings = root.dumpTrieNode(compact);
	// ret.append("Root: " + hash2str(getRootHash(), compact) + "\n");
	// for (String s : strings) {
	// ret.append(s).append('\n');
	// }
	// return ret.toString();
	// }

	public void scanTree(ScanAction scanAction) {
		scanTree(root, TrieKey.empty(false), scanAction);
	}

	public void scanTree(Node node, TrieKey k, ScanAction scanAction) {
		if (node == null)
			return;
		if (node.hash != null) {
			scanAction.doOnNode(node.hash, node);
		}
		if (node.getType() == NodeType.BranchNode) {
			if (node.branchNodeGetValue() != null)
				scanAction.doOnValue(node.hash, node, k.toNormal(), node.branchNodeGetValue());
			for (int i = 0; i < 16; i++) {
				scanTree(node.branchNodeGetChild(i), k.concat(TrieKey.singleHex(i)), scanAction);
			}
		} else if (node.getType() == NodeType.KVNodeNode) {
			scanTree(node.kvNodeGetChildNode(), k.concat(node.kvNodeGetKey()), scanAction);
		} else {
			scanAction.doOnValue(node.hash, node, k.concat(node.kvNodeGetKey()).toNormal(), node.kvNodeGetValue());
		}
	}
}
