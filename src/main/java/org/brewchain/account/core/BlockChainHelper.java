package org.brewchain.account.core;

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.doublyll.DoubleLinkedList;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.fc.brewchain.bcapi.EncAPI;

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
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "Block_Cache_DLL", scope = "global")
	DoubleLinkedList<byte[]> blockCache;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	/**
	 * 获取节点最后一个区块
	 * 
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder GetBestBlock() throws Exception {
		return blockHelper.getBlock(blockCache.last());
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
		return GetBestBlock().build().getHeader().getNumber();
	}

	/**
	 * 获取创世块
	 * 
	 * @return
	 * @throws Exception
	 */
	public BlockEntity getGenesisBlock() throws Exception {
		BlockEntity oBlockEntity = blockHelper.getBlock(blockCache.first()).build();
		if (oBlockEntity.getHeader().getNumber() == KeyConstant.GENESIS_NUMBER) {
			return oBlockEntity;
		}
		throw new Exception(String.format("缺失创世块，当前第一个块索引 %s", oBlockEntity.getHeader().getNumber()));
	}

	/**
	 * 向区块链中加入新的区块
	 * 
	 * @param oBlock
	 * @return
	 */
	public boolean appendBlock(BlockEntity oBlock) {

		OKey[] keys = new OKey[] { OEntityBuilder.byteKey2OKey(oBlock.getHeader().getBlockHash()),
				OEntityBuilder.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK) };
		OValue[] values = new OValue[] { OEntityBuilder.byteValue2OValue(oBlock.toByteArray()),
				OEntityBuilder.byteValue2OValue(oBlock.toByteArray()) };

		dao.getBlockDao().batchPuts(keys, values);
		return blockCache.insertAfter(oBlock.getHeader().getBlockHash().toByteArray(), oBlock.getHeader().getNumber(),
				oBlock.getHeader().getParentHash().toByteArray());
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
	 * @return
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
		Iterator<byte[]> iterator = blockCache.iterator();

		while (iterator.hasNext() || maxCount > 0) {
			byte[] cur = iterator.next();
			if (FastByteComparisons.equal(blockHash, cur)) {
				list.add(blockHelper.getBlock(cur).build());
				maxCount--;
			} else if (endBlockHash != null && FastByteComparisons.equal(endBlockHash, cur)) {
				break;
			}
		}
		return list;
	}

	/**
	 * 该方法会移除本地缓存，然后取数据库中保存的最后一个块的信息，重新构造缓存
	 * 
	 * @throws Exception
	 */
	public void reloadBlockCache() throws Exception {
		// 数据库中的最后一个块
		BlockEntity oBlockEntity = blockHelper.getBlock(KeyConstant.DB_CURRENT_BLOCK).build();
		int blockNumber = oBlockEntity.getHeader().getNumber();
		blockCache.insertFirst(oBlockEntity.getHeader().getBlockHash().toByteArray(), blockNumber);
		// 开始遍历区块
		while (blockNumber >= 0) {
			BlockEntity loopBlockEntity = blockHelper.getBlock(oBlockEntity.getHeader().getParentHash().toByteArray())
					.build();
			blockNumber = loopBlockEntity.getHeader().getNumber();
			blockCache.insertFirst(loopBlockEntity.getHeader().getBlockHash().toByteArray(), blockNumber);
			if (blockNumber == 0) {
				break;
			}
		}
	}
}
