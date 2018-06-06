package org.brewchain.account.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Validate;
import org.apache.felix.ipojo.util.Log;
import org.brewchain.account.core.store.BlockChainTempNode;
import org.brewchain.account.core.store.BlockChainTempStore;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.doublyll.DoubleLinkedList;
import org.brewchain.account.doublyll.Node;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.NodeDef;
import org.brewchain.account.util.NodeDef.NodeAccount;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.KeyStoreValue;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyStoreHelper;

import com.google.inject.Key;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.outils.conf.PropHelper;

@NActorProvider
@Instantiate(name = "BlockChain_Helper")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class BlockChainHelper implements ActorService {
	// @ActorRequire(name = "Block_Cache_DLL", scope = "global")
	// DoubleLinkedList blockCache;
	//
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;
	@ActorRequire(name = "BlockChainTempStore_HashMapDB", scope = "global")
	BlockChainTempStore blockChainTempStore;
	@ActorRequire(name = "BlockChainStore_LRUHashMapDB", scope = "global")
	BlockChainStoreWithLRU blockChainStore;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "CacheBlock_HashMapDB", scope = "global")
	CacheBlockHashMapDB oCacheHashMapDB;
	@ActorRequire(name = "KeyStore_Helper", scope = "global")
	KeyStoreHelper keyStoreHelper;

	/**
	 * 获取节点最后一个区块的Hash
	 * 
	 * @return
	 * @throws Exception
	 */
	public byte[] GetStableBestBlockHash() throws Exception {
		OValue oOValue = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get();
		if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
			log.error("");
		}
		return oOValue.getExtdata().toByteArray();
		// return blockCache.last();
	}

	public BlockEntity GetStableBestBlock() throws Exception {
		byte[] hash = GetStableBestBlockHash();
		if (hash != null) {
			return getBlock(hash).build();
		} else {
			return null;
		}
		// return blockCache.last();
	}

	public byte[] GetUnStableBestBlockHash() throws Exception {
		BlockChainTempNode oNode = blockChainTempStore.getMaxBlock();
		if (oNode != null) {
			return encApi.hexDec(oNode.getHash());
		}
		return null;
		// return blockCache.last();
	}

	public BlockEntity GetUnStableBestBlock() throws Exception {
		byte[] hash = GetUnStableBestBlockHash();
		if (hash != null) {
			return getBlock(hash).build();
		} else {
			return null;
		}
		// return blockCache.last();
	}

	/**
	 * 获取节点区块个数
	 * 
	 * @return
	 */
	public int getBlockCount() {
		return blockChainStore.getBlockCount();
		// return blockCache.size();
	}

	/**
	 * 获取最后一个区块的索引
	 * 
	 * @return
	 * @throws Exception
	 */
	public int getLastStableBlockNumber() throws Exception {
		return blockChainStore.getBlockCount() - 1;
		// return blockCache.getLast() == null ? 0 : blockCache.getLast().num;
	}

	public int getLastBlockNumber() {
		return blockChainTempStore.getMaxStableNumber();
	}

	/**
	 * 获取创世块
	 * 
	 * @return
	 * @throws Exception
	 */
	public BlockEntity getGenesisBlock() throws Exception {
		// BlockEntity oBlockEntity = getBlock(blockCache.first()).build();
		BlockEntity oBlockEntity = getBlock(blockChainStore.getBlockHashByNumber(0)).build();
		if (oBlockEntity.getHeader().getNumber() == KeyConstant.GENESIS_NUMBER) {
			return oBlockEntity;
		}
		throw new Exception(String.format("缺失创世块，当前第一个块索引 %s", oBlockEntity.getHeader().getNumber()));
	}

	public boolean isExistsGenesisBlock() throws Exception {
		// if (blockCache.first() == null) {
		// return false;
		// }
		if (blockChainStore.getLastBlockNumber() == -1) {
			return false;
		}
		BlockEntity oBlockEntity = getBlock(blockChainStore.getBlockHashByNumber(0)).build();
		if (oBlockEntity.getHeader().getNumber() == KeyConstant.GENESIS_NUMBER) {
			return true;
		}
		return false;
	}

	/**
	 * 向区块链中加入新的区块
	 * 
	 * @param oBlock
	 * @return
	 */
	public boolean appendBlock(BlockEntity oBlock) {
		// if (blockCache.insertAfter(oBlock.getHeader().getBlockHash().toByteArray(),
		// oBlock.getHeader().getNumber(),
		// oBlock.getHeader().getParentHash().toByteArray())) {
		int lastNumber = 0;
		try {
			lastNumber = getLastStableBlockNumber();
		} catch (Exception e) {
			lastNumber = -1;
		}

		log.debug("last_number::" + lastNumber + " block::" + oBlock.getHeader().getNumber());

		BlockChainTempNode oStableNode = blockChainTempStore.tryAddAndPop(
				encApi.hexEnc(oBlock.getHeader().getBlockHash().toByteArray()),
				encApi.hexEnc(oBlock.getHeader().getParentHash().toByteArray()), oBlock.getHeader().getNumber());

		log.debug("dump cache::" + blockChainTempStore.dump());

		if (oStableNode == null) {
			log.debug("not found stable node");
			dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(oBlock.getHeader().getBlockHash()),
					OEntityBuilder.byteValue2OValue(oBlock.toByteArray()));
		} else {
			OKey[] keys = null;
			OValue[] values = null;

			log.info("stable block number:: " + oStableNode.getNumber() + " hash::" + oStableNode.getHash());
			keys = new OKey[] { OEntityBuilder.byteKey2OKey(oBlock.getHeader().getBlockHash()),
					OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK) };
			values = new OValue[] { OEntityBuilder.byteValue2OValue(oBlock.toByteArray()),
					OEntityBuilder.byteValue2OValue(encApi.hexDec(oStableNode.getHash())) };
			dao.getBlockDao().batchPuts(keys, values);

			BlockEntity stableBlock;
			try {
				stableBlock = getBlock(encApi.hexDec(oStableNode.getHash())).build();
				blockChainStore.add(stableBlock, encApi.hexEnc(stableBlock.getHeader().getBlockHash().toByteArray()));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		if (oBlock.getBody() != null) {
			LinkedList<OKey> txBlockKeyList = new LinkedList<OKey>();
			LinkedList<OValue> txBlockValueList = new LinkedList<OValue>();

			for (MultiTransaction oMultiTransaction : oBlock.getBody().getTxsList()) {
				txBlockKeyList.add(OEntityBuilder.byteKey2OKey(oMultiTransaction.getTxHash()));
				txBlockValueList.add(OEntityBuilder.byteValue2OValue(oBlock.getHeader().getBlockHash()));
			}
			dao.getTxblockDao().batchPuts(txBlockKeyList.toArray(new OKey[0]), txBlockValueList.toArray(new OValue[0]));
		}

		return true;
		// }
		// return false;
	}

	public void rollBackTo(BlockEntity oBlock) {
		blockChainStore.rollBackTo(oBlock.getHeader().getNumber());
		appendBlock(oBlock);
	}

	public boolean cacheBlock(BlockEntity oBlock) {
		OKey[] keys = new OKey[] { OEntityBuilder.byteKey2OKey(oBlock.getHeader().getBlockHash()) };
		OValue[] values = new OValue[] { OEntityBuilder.byteValue2OValue(oBlock.toByteArray()) };

		dao.getBlockDao().batchPuts(keys, values);
		log.debug("cache block, parent::" + encApi.hexEnc(oBlock.getHeader().getParentHash().toByteArray()));
		blockChainTempStore.tryAdd(encApi.hexEnc(oBlock.getHeader().getBlockHash().toByteArray()),
				encApi.hexEnc(oBlock.getHeader().getParentHash().toByteArray()), oBlock.getHeader().getNumber());
		return true;
	}

	public BlockEntity tryGetAndDeleteBlockFromTempStore(byte[] parentHash) throws Exception {
		BlockChainTempNode oBlockChainTempNode = blockChainTempStore.getAndDeleteByParent(encApi.hexEnc(parentHash));
		if (oBlockChainTempNode != null) {
			try {
				log.debug("get cache block, parent::" + encApi.hexEnc(parentHash) + " number::"
						+ oBlockChainTempNode.getNumber());
				return getBlock(encApi.hexDec(oBlockChainTempNode.getHash())).build();
			} catch (InvalidProtocolBufferException e) {
				log.error("error on init block from cache::" + e.getMessage());
			}
		} else {
			log.debug("cache block not found, parent::" + encApi.hexEnc(parentHash));
		}
		return null;
	}

	public BlockChainTempNode tryGetBlockTempNodeFromTempStore(byte[] hash) {
		BlockChainTempNode oBlockChainTempNode = blockChainTempStore.get(encApi.hexEnc(hash));
		return oBlockChainTempNode;
	}

	public BlockEntity tryGetBlockFromStore(byte[] parentHash) {
		return blockChainStore.get(encApi.hexEnc(parentHash));
	}

	public boolean isExistsBlockFromStore(byte[] parentHash) {
		return blockChainStore.isExists(encApi.hexEnc(parentHash));
	}

	/**
	 * 从一个块开始遍历整个区块链，返回该块的所有子孙
	 * 
	 * @param blockHash
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getBlocks(byte[] blockHash) throws Exception {
		return getBlocks(blockHash, null);
	}

	/**
	 * 从一个块开始遍历整个区块链,到一个块结束，返回区间内的区块。默认返回200块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @return 200块
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getBlocks(byte[] blockHash, byte[] endBlockHash) throws Exception {
		return getBlocks(blockHash, endBlockHash, 200);
	}

	/**
	 * 从一个块开始遍历整个区块链,到一个块结束，返回区间指定数量的区块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @param maxCount
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getBlocks(byte[] blockHash, byte[] endBlockHash, int maxCount) throws Exception {
		LinkedList<BlockEntity> list = new LinkedList<BlockEntity>();
		list.addAll(blockChainStore.getChildListBlocksEndWith(encApi.hexEnc(blockHash), encApi.hexEnc(endBlockHash),
				maxCount));

		// Iterator<Node> iterator = blockCache.iterator();
		// boolean st = false;
		// while (iterator.hasNext() && maxCount > 0) {
		// Node cur = iterator.next();
		// log.debug(String.format("%s %s ", encApi.hexEnc(cur.data),
		// encApi.hexEnc(endBlockHash)));
		// if (endBlockHash != null && FastByteComparisons.equal(endBlockHash,
		// cur.data)) {
		// st = false;
		// list.add(getBlock(cur.data).build());
		// break;
		// } else if (FastByteComparisons.equal(blockHash, cur.data) || st) {
		// st = true;
		//
		// list.add(getBlock(cur.data).build());
		// maxCount--;
		// }
		// }
		return list;
	}

	/**
	 * 从一个块开始，遍历整个整个区块，返回改块的所有父块
	 * 
	 * @param blockHash
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getParentsBlocks(byte[] blockHash) throws Exception {
		return getParentsBlocks(blockHash, null);
	}

	/**
	 * 从一个块开始，到一个块结束，遍历整个整个区块，返回改块的所有父块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @return 200块
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getParentsBlocks(byte[] blockHash, byte[] endBlockHash) throws Exception {
		return getParentsBlocks(blockHash, endBlockHash, 200);
	}

	/**
	 * 从一个块开始，到一个块结束，遍历整个整个区块，返回指定数量的该块的所有父块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @param maxCount
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getParentsBlocks(byte[] blockHash, byte[] endBlockHash, int maxCount)
			throws Exception {
		LinkedList<BlockEntity> list = new LinkedList<BlockEntity>();
		String beginHashStr = encApi.hexEnc(blockHash);
		String endHashStr = "";
		if (endBlockHash != null) {
			endHashStr = encApi.hexEnc(endBlockHash);
		}
		list.addAll(blockChainStore.getParentListBlocksEndWith(beginHashStr, endHashStr, maxCount));
		// Iterator<Node> iterator = blockCache.reverseIterator();
		// boolean st = false;
		// while (iterator.hasNext() && maxCount > 0) {
		// Node cur = iterator.next();
		// if (endBlockHash != null && FastByteComparisons.equal(endBlockHash,
		// cur.data)) {
		// st = false;
		// list.add(getBlock(cur.data).build());
		// break;
		// } else if (FastByteComparisons.equal(blockHash, cur.data) || st) {
		// st = true;
		// list.add(getBlock(cur.data).build());
		// maxCount--;
		// }
		// }
		return list;
	}

	/**
	 * 根据区块高度，获取区块信息
	 * 
	 * @param number
	 * @return
	 * @throws Exception
	 */
	public BlockEntity getBlockByNumber(int number) throws Exception {
		// 判断从前遍历还是从后遍历
		BlockEntity oBlockEntity = null;
		BlockChainTempNode oNode = blockChainTempStore.getByNumber(number);
		if (oNode == null) {
			oBlockEntity = blockChainStore.getBlockByNumber(number);
			if (oBlockEntity == null) {
				throw new Exception(String.format("缓存中没有找到高度为 %s 的区块", number));
			} else {
				return oBlockEntity;
			}
		} else {
			oBlockEntity = getBlock(encApi.hexDec(oNode.getHash())).build();
			return oBlockEntity;
		}
	}

	/**
	 * get node account info.
	 * 
	 * return null while not found.
	 * 
	 * @return
	 */
	public String getNodeAccount() {
		OValue oOValue;
		try {
			oOValue = dao.getAccountDao().get(OEntityBuilder.byteKey2OKey("org.bc.manage.node.account".getBytes()))
					.get();
			if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
				// try read .keystore file with pwd
				if (StringUtils.isNotBlank(blockChainConfig.getPwd())) {
					FileReader fr = null;
					BufferedReader br = null;
					try {
						// read file

						fr = new FileReader("keystore" + File.separator + "keystore"
								+ blockChainConfig.getKeystoreNumber() + ".json");
						br = new BufferedReader(fr);
						String keyStoreJsonStr = "";

						String line = br.readLine();
						while (line != null) {
							keyStoreJsonStr += line.trim().replace("\r", "").replace("\t", "");
							line = br.readLine();
						}
						br.close();
						fr.close();

						KeyStoreValue oKeyStoreValue = keyStoreHelper.getKeyStore(keyStoreJsonStr,
								blockChainConfig.getPwd());
						if (oKeyStoreValue == null) {
							return null;
						} else {
							dao.getAccountDao().put(
									OEntityBuilder.byteKey2OKey("org.bc.manage.node.account".getBytes()),
									OEntityBuilder.byteValue2OValue(encApi.hexDec(oKeyStoreValue.getAddress())));
							return oKeyStoreValue.getAddress();
						}
					} catch (Exception e) {
						if (br != null) {
							br.close();
						}
						if (fr != null) {
							fr.close();
						}
						throw e;
					}
				}
				return null;
			} else {
				return encApi.hexEnc(oOValue.getExtdata().toByteArray());
			}
		} catch (Exception e) {
			log.error("fail to read node account from db::" + e.getMessage());
		}
		return null;
	}

	public void onStart(String bcuid, String address, String name) {
		try {
			String coinAddressHex = getNodeAccount();
			if (coinAddressHex == null) {
				throw new Exception("node account not found");
			}
			byte[] coinAddress = encApi.hexDec(coinAddressHex);
			if (coinAddress == null) {
				throw new Exception("node account not found");
			}
			NodeDef oNodeDef = new NodeDef();
			oNodeDef.setBcuid(bcuid);
			oNodeDef.setAddress(address);
			oNodeDef.setNode(name);

			NodeAccount oNodeAccount = oNodeDef.new NodeAccount();
			oNodeAccount.setAddress(encApi.hexEnc(coinAddress));
			// oNodeAccount.setBcuid(oKeyStoreValue.getBcuid());
			oNodeDef.setoAccount(oNodeAccount);
			KeyConstant.node = oNodeDef;
			reloadBlockCache();
			reloadBlockCacheByNumber();
			log.debug("block load complete");
		} catch (Exception e) {
			blockChainStore.clear();
			oCacheHashMapDB.clear();
			log.error("error on start node account::", e);
		}
	}

	/**
	 * 该方法会移除本地缓存，然后取数据库中保存的最后一个块的信息，重新构造缓存。该方法可能会带来很大开销。在缓存加载后节点才启动完成。在启动完成之前
	 * ，所有接口均无法使用。
	 * 
	 * @throws Exception
	 */
	public void reloadBlockCacheByNumber() throws Exception {
		int bestHeight = blockChainStore.getLastBlockNumber();
		int blockNumber = 0;
		
		if (bestHeight <= 0){
			log.warn("load block empty");
			return;
		}
		
		//缓存定量的block
		if(bestHeight > KeyConstant.CACHE_SIZE){
			blockNumber = bestHeight - KeyConstant.CACHE_SIZE;
		}
		
		log.debug(String.format("load block into cache from number = %s to number = %s", blockNumber, bestHeight));
		
		BlockEntity oBlockEntity = blockChainStore.getBlockByNumber(blockNumber);
		
		if (oBlockEntity == null) {
			KeyConstant.isStart = true;
			log.debug(String.format("not found number = %s block, start empty node", blockNumber));
			return;
		}

		while (oBlockEntity != null && blockNumber <= bestHeight) {
			try {
				if (blockNumber != oBlockEntity.getHeader().getNumber()) {
					throw new Exception(String.format("respect block number %s ,get block number %s",blockNumber, oBlockEntity.getHeader().getNumber()));
				}
				blockChainStore.add(oBlockEntity,encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()));
				log.info(String.format("load block::%s number::%s from datasource",encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()),oBlockEntity.getHeader().getNumber()));

				if (blockNumber < bestHeight) {
					blockNumber += 1;
				} else {
					break;
				}
				oBlockEntity = getBlockByNumber(blockNumber);
				if(oBlockEntity == null){
					break;
				}
			} catch (Exception e) {
				log.error("reload block error : " + e.getMessage());
			}
		}

		if (blockNumber != bestHeight) {
			log.error("block chain data is wrong, number::" + blockNumber);
			log.error("block chain data is wrong, the best number::" + bestHeight);
		}

		KeyConstant.isStart = true;
	}
	public void reloadBlockCache() throws Exception {
		BlockEntity oBlockEntity;
		oBlockEntity = GetStableBestBlock();
		if (oBlockEntity == null) {
			KeyConstant.isStart = true;
			log.debug(String.format("not found last block, start empty node"));
			return;
		}
		int blockNumber = oBlockEntity.getHeader().getNumber();
		blockChainStore.add(oBlockEntity, encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()));
		// blockCache.insertFirst(oBlockEntity.getHeader().getBlockHash().toByteArray(),
		// blockNumber);
		
		byte[] parentHash = oBlockEntity.getHeader().getParentHash().toByteArray();
		
		while (parentHash != null) {
			BlockEntity.Builder loopBlockEntity = null;
			try {
				loopBlockEntity = getBlock(parentHash);
				if (loopBlockEntity == null) {
					// maybe wrong
					break;
				} else {
					if (blockNumber - 1 != loopBlockEntity.getHeader().getNumber()) {
						throw new Exception(String.format("respect block number %s ,get block number %s",
								blockNumber - 1, loopBlockEntity.getHeader().getNumber()));
					}
					blockNumber -= 1;
					blockChainStore.add(loopBlockEntity.build(),
							encApi.hexEnc(loopBlockEntity.getHeader().getBlockHash().toByteArray()));
					log.info(String.format("load block::%s number::%s from datasource",
							encApi.hexEnc(loopBlockEntity.getHeader().getBlockHash().toByteArray()),
							loopBlockEntity.getHeader().getNumber()));
					
					if (loopBlockEntity.getHeader().getParentHash() != null) {
						parentHash = loopBlockEntity.getHeader().getParentHash().toByteArray();
					} else {
						break;
					}
				}
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
		
		if (blockNumber != 0) {
			log.error("block chain data is wrong, parent number::" + blockNumber);
		}
		
		KeyConstant.isStart = true;
	}

	private BlockEntity.Builder getBlock(byte[] blockHash) throws Exception {
		OValue v = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(blockHash)).get();
		if (v == null || v.getExtdata() == null) {
			return null;
		} else {
			BlockEntity.Builder oBlockEntity = BlockEntity.parseFrom(v.getExtdata()).toBuilder();
			return oBlockEntity;
		}
	}

	public String getBlockCacheDump() {
		return blockChainTempStore.dump();
	}
}
