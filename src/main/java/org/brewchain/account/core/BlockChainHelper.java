package org.brewchain.account.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Validate;
import org.apache.felix.ipojo.util.Log;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.doublyll.DoubleLinkedList;
import org.brewchain.account.doublyll.Node;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.NodeDef;
import org.brewchain.account.util.NodeDef.NodeAccount;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.KeyStoreValue;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Instantiate(name = "BlockChain_Helper")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class BlockChainHelper implements ActorService {
	@ActorRequire(name = "Block_Cache_DLL", scope = "global")
	DoubleLinkedList blockCache;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "CacheBlock_HashMapDB", scope = "global")
	CacheBlockHashMapDB oCacheHashMapDB;

	/**
	 * 获取节点最后一个区块的Hash
	 * 
	 * @return
	 * @throws Exception
	 */
	public byte[] GetBestBlock() throws Exception {
		OValue oOValue = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get();
		if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
			log.error("");
		}
		return dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get().getExtdata()
				.toByteArray();
		// return blockCache.last();
	}

	/**
	 * 获取节点区块个数
	 * 
	 * @return
	 */
	public int getBlockCount() {
		return blockCache.size();
	}

	/**
	 * 获取最后一个区块的索引
	 * 
	 * @return
	 * @throws Exception
	 */
	public int getLastBlockNumber() throws Exception {
		return getBlock(GetBestBlock()).getHeader().getNumber();
		// return blockCache.getLast() == null ? 0 : blockCache.getLast().num;
	}

	/**
	 * 获取创世块
	 * 
	 * @return
	 * @throws Exception
	 */
	public BlockEntity getGenesisBlock() throws Exception {
		BlockEntity oBlockEntity = getBlock(blockCache.first()).build();
		if (oBlockEntity.getHeader().getNumber() == KeyConstant.GENESIS_NUMBER) {
			return oBlockEntity;
		}
		throw new Exception(String.format("缺失创世块，当前第一个块索引 %s", oBlockEntity.getHeader().getNumber()));
	}

	public boolean isExistsGenesisBlock() throws Exception {
		if (blockCache.first() == null) {
			return false;
		}
		BlockEntity oBlockEntity = getBlock(blockCache.first()).build();
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
		if (blockCache.insertAfter(oBlock.getHeader().getBlockHash().toByteArray(), oBlock.getHeader().getNumber(),
				oBlock.getHeader().getParentHash().toByteArray())) {
			OKey[] keys = new OKey[] { OEntityBuilder.byteKey2OKey(oBlock.getHeader().getBlockHash()),
					OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK) };
			OValue[] values = new OValue[] { OEntityBuilder.byteValue2OValue(oBlock.toByteArray()),
					OEntityBuilder.byteValue2OValue(oBlock.getHeader().getBlockHash()) };
			dao.getBlockDao().batchPuts(keys, values);

			if (oBlock.getBody() != null) {
				LinkedList<OKey> txBlockKeyList = new LinkedList<OKey>();
				LinkedList<OValue> txBlockValueList = new LinkedList<OValue>();

				for (MultiTransaction oMultiTransaction : oBlock.getBody().getTxsList()) {
					txBlockKeyList.add(OEntityBuilder.byteKey2OKey(oMultiTransaction.getTxHash()));
					txBlockValueList.add(OEntityBuilder.byteValue2OValue(oBlock.getHeader().getBlockHash()));
				}
				dao.getTxblockDao().batchPuts(txBlockKeyList.toArray(new OKey[0]),
						txBlockValueList.toArray(new OValue[0]));
			}
			return true;
		}
		return false;
	}

	public boolean cacheBlock(BlockEntity oBlock) {
		OKey[] keys = new OKey[] { OEntityBuilder.byteKey2OKey(oBlock.getHeader().getBlockHash()) };
		OValue[] values = new OValue[] { OEntityBuilder.byteValue2OValue(oBlock.toByteArray()) };

		dao.getBlockDao().batchPuts(keys, values);
		log.debug("cache block, parent::" + encApi.hexEnc(oBlock.getHeader().getParentHash().toByteArray()));
		oCacheHashMapDB.put(encApi.hexEnc(oBlock.getHeader().getParentHash().toByteArray()), oBlock.toByteArray());
		return true;
	}

	public List<BlockEntity> tryGetChildBlock(byte[] parentHash) {
		List<BlockEntity> list = new ArrayList<BlockEntity>();

		byte[] blockEntityHash = oCacheHashMapDB.getAndDelete(encApi.hexEnc(parentHash));
		if (blockEntityHash != null) {
			BlockEntity.Builder oBlock;
			try {
				log.debug("get cache block, parent::" + encApi.hexEnc(parentHash));
				oBlock = BlockEntity.newBuilder().mergeFrom(blockEntityHash);
				list.add(oBlock.build());
			} catch (InvalidProtocolBufferException e) {
				log.error("error on init block from cache::" + e.getMessage());
			}
		} else {
			log.debug("cache block not found, parent::" + encApi.hexEnc(parentHash));
		}
		return list;
	}

	public boolean newBlock(BlockEntity oBlock) {

		OKey[] keys = new OKey[] { OEntityBuilder.byteKey2OKey(oBlock.getHeader().getBlockHash()),
				OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK) };
		OValue[] values = new OValue[] { OEntityBuilder.byteValue2OValue(oBlock.toByteArray()),
				OEntityBuilder.byteValue2OValue(oBlock.getHeader().getBlockHash()) };

		dao.getBlockDao().batchPuts(keys, values);
		blockCache.clear();
		blockCache.insertFirst(oBlock.getHeader().getBlockHash().toByteArray(), 0);
		return true;
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
		Iterator<Node> iterator = blockCache.iterator();
		boolean st = false;
		while (iterator.hasNext() && maxCount > 0) {
			Node cur = iterator.next();
			log.debug(String.format("%s %s ", encApi.hexEnc(cur.data), encApi.hexEnc(endBlockHash)));
			if (endBlockHash != null && FastByteComparisons.equal(endBlockHash, cur.data)) {
				st = false;
				list.add(getBlock(cur.data).build());
				break;
			} else if (FastByteComparisons.equal(blockHash, cur.data) || st) {
				st = true;

				list.add(getBlock(cur.data).build());
				maxCount--;
			}
		}
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
		Iterator<Node> iterator = blockCache.reverseIterator();
		boolean st = false;
		while (iterator.hasNext() && maxCount > 0) {
			Node cur = iterator.next();
			if (endBlockHash != null && FastByteComparisons.equal(endBlockHash, cur.data)) {
				st = false;
				list.add(getBlock(cur.data).build());
				break;
			} else if (FastByteComparisons.equal(blockHash, cur.data) || st) {
				st = true;
				list.add(getBlock(cur.data).build());
				maxCount--;
			}
		}
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
		Iterator<Node> iterator;
		if (getBlockCount() / 2 > number) {
			iterator = blockCache.iterator();
		} else {
			iterator = blockCache.reverseIterator();
		}
		while (iterator.hasNext()) {
			Node cur = iterator.next();
			if (cur.num == number) {
				return getBlock(cur.data).build();
			}
		}
		throw new Exception(String.format("没有找到高度为 %s 的区块", number));
	}

	public void onStart(String bcuid, String address, String name) {
		try {
			NodeDef oNodeDef = new NodeDef();
			oNodeDef.setBcuid(bcuid);
			oNodeDef.setAddress(address);
			oNodeDef.setNode(name);
//			
//			OValue oOValue = dao.getAccountDao()
//					.get(OEntityBuilder.byteKey2OKey("org.bc.manage.node.account".getBytes())).get();
//			if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
//
//			} else {
//				KeyStoreValue oKeyStoreValue = KeyStoreValue.parseFrom(oOValue.getExtdata().toByteArray());
//				NodeAccount oNodeAccount = oNodeDef.new NodeAccount();
//				oNodeAccount.setAddress(oKeyStoreValue.getAddress());
//				oNodeAccount.setBcuid(oKeyStoreValue.getBcuid());
//				oNodeAccount.setPriKey(oKeyStoreValue.getPrivKey());
//				oNodeAccount.setPubKey(oKeyStoreValue.getPubKey());
//				oNodeDef.setoAccount(oNodeAccount);
//			}

			KeyConstant.node = oNodeDef;
			reloadBlockCache();
			log.debug("block load complete");
		} catch (Exception e) {
			blockCache.clear();
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
	public void reloadBlockCache() throws Exception {
		BlockEntity.Builder oBlockEntity;
		OValue oOValue = dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get();
		if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
			log.debug("last block not found");
			return;
		}
		oBlockEntity = getBlock(oOValue.getExtdata().toByteArray());
		if (oBlockEntity == null) {
			KeyConstant.isStart = true;
			log.debug(String.format("not found last block, start empty node"));
			return;
		}
		int blockNumber = oBlockEntity.getHeader().getNumber();
		blockCache.insertFirst(oBlockEntity.getHeader().getBlockHash().toByteArray(), blockNumber);

		// 开始遍历区块
		while (blockNumber >= 0) {
			blockNumber--;
			if (oBlockEntity.getHeader().getParentHash() == null
					|| oBlockEntity.getHeader().getParentHash().equals(ByteString.EMPTY)) {
				// 只有创世块？
				if (oBlockEntity.getHeader().getNumber() == KeyConstant.GENESIS_NUMBER) {
					break;
				} else {
					throw new Exception(String.format("block db error，block %s can not find parent block, hash::%s",
							oBlockEntity.getHeader().getNumber(),
							encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray())));
				}
			}
			BlockEntity loopBlockEntity = null;

			try {
				loopBlockEntity = getBlock(oBlockEntity.getHeader().getParentHash().toByteArray()).build();
			} catch (Exception e) {
				log.error("block not found:: hash::"
						+ encApi.hexEnc(oBlockEntity.getHeader().getParentHash().toByteArray()) + " current::"
						+ oBlockEntity.getHeader().getNumber());
				throw e;
			}

			if (blockNumber != loopBlockEntity.getHeader().getNumber()) {
				throw new Exception(String.format("respect block number %s ,get block number %s", blockNumber,
						loopBlockEntity.getHeader().getNumber()));
			}
			blockCache.insertFirst(loopBlockEntity.getHeader().getBlockHash().toByteArray(), blockNumber);
			log.info(String.format("load block %s from datasource", blockNumber));
			if (blockNumber == 0) {
				break;
			}

			oBlockEntity = loopBlockEntity.toBuilder();
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

	public String getBlockCacheFormatString() {
		return blockCache.formatString();
	}
}
