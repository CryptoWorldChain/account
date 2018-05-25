package org.brewchain.account.core;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.doublyll.DoubleLinkedList;
import org.brewchain.account.trie.TrieImpl;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.account.gens.Block.BlockBody;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Block.BlockEntityOrBuilder;
import org.brewchain.account.gens.Block.BlockHeader;
import org.brewchain.account.gens.Block.BlockMiner;
import org.brewchain.account.gens.Blockimpl.AddBlockResponse;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.inject.Key;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Instantiate(name = "Block_Helper")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class BlockHelper implements ActorService {
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	
	// @ActorRequire(name = "Block_StorageDB", scope = "global")
	// BlockStorageDB oBlockStorageDB;
	//
	// @ActorRequire(name = "Block_Cache_DLL", scope = "global")
	// DoubleLinkedList<byte[]> blockCache;

	/**
	 * 创建新区块
	 * 
	 * @param extraData
	 * @param coinBase
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder CreateNewBlock(byte[] extraData, byte[] coinBase) throws Exception {
		return CreateNewBlock(KeyConstant.DEFAULT_BLOCK_TX_COUNT, extraData, coinBase);
	}

	/**
	 * 创建新区块
	 * 
	 * @param txCount
	 * @param extraData
	 * @param coinBase
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder CreateNewBlock(int txCount, byte[] extraData, byte[] coinBase) throws Exception {
		return CreateNewBlock(transactionHelper.getWaitBlockTx(txCount), extraData, coinBase);
	}

	/**
	 * 创建新区块
	 * 
	 * @param txs
	 * @param extraData
	 * @param coinBase
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder CreateNewBlock(LinkedList<MultiTransaction> txs, byte[] extraData, byte[] coinBase)
			throws Exception {
		BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();
		BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
		BlockBody.Builder oBlockBody = BlockBody.newBuilder();
		BlockMiner.Builder oBlockMiner = BlockMiner.newBuilder();

		// 获取本节点的最后一块Block
		BlockEntity.Builder oBestBlockEntity = GetBestBlock();
		BlockHeader.Builder oBestBlockHeader = oBestBlockEntity.getHeader().toBuilder();

		// 构造Block Header
		// oBlockHeader.setCoinbase(ByteString.copyFrom(coinBase));
		oBlockHeader.setParentHash(oBestBlockHeader.getBlockHash());

		// 确保时间戳不重复
		long currentTimestamp = new Date().getTime();
		oBlockHeader.setTimestamp(new Date().getTime() == oBestBlockHeader.getTimestamp()
				? oBestBlockHeader.getTimestamp() + 1 : currentTimestamp);
		oBlockHeader.setNumber(oBestBlockHeader.getNumber() + 1);
		oBlockHeader.setReward(ByteString.copyFrom(ByteUtil.intToBytes(KeyConstant.BLOCK_REWARD)));
		oBlockHeader.setExtraData(ByteString.copyFrom(extraData));
		oBlockHeader.setNonce(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
		// 构造MPT Trie
		TrieImpl oTrieImpl = new TrieImpl();
		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
			oTrieImpl.put(txs.get(i).getTxHash().toByteArray(), txs.get(i).toByteArray());
		}
		oBlockMiner.setAddress(KeyConstant.node.getAddress());
		oBlockMiner.setNode(KeyConstant.node.getNode());
		oBlockMiner.setBcuid(KeyConstant.node.getBcuid());
		oBlockMiner.setReward(KeyConstant.BLOCK_REWARD);
		// oBlockMiner.setAddress(value);

		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		//accountHelper.getStateRoot(addr);
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);
		oBlockEntity.setMiner(oBlockMiner);

		log.info(String.format("LOGFILTER %s %s %s %s 创建区块[%s]", KeyConstant.node.getNode(), "account", "create",
				"block", encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray())));

		// ApplyBlock(oBlockEntity.build());

		return oBlockEntity;
	}

	/**
	 * 创建创世块
	 * 
	 * @param txs
	 * @param extraData
	 * @throws Exception
	 */
	public void CreateGenesisBlock(LinkedList<MultiTransaction> txs, byte[] extraData) throws Exception {
		if (blockChainHelper.isExistsGenesisBlock()) {
			throw new Exception("不允许重复创建创世块");
		}

		BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();
		BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
		BlockBody.Builder oBlockBody = BlockBody.newBuilder();

		// 构造Block Header
		// oBlockHeader.setCoinbase(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
		oBlockHeader.setParentHash(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));

		// 确保时间戳不重复
		long currentTimestamp = new Date().getTime();
		oBlockHeader.setTimestamp(currentTimestamp);
		oBlockHeader.setNumber(KeyConstant.GENESIS_NUMBER);
		oBlockHeader.setReward(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
		oBlockHeader.setExtraData(ByteString.copyFrom(extraData));
		oBlockHeader.setNonce(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
		// 构造MPT Trie
		TrieImpl oTrieImpl = new TrieImpl();
		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
			oTrieImpl.put(txs.get(i).getTxHash().toByteArray(), txs.get(i).toByteArray());
		}
		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);

		// oBlockStorageDB.setLastBlock(oBlockEntity.build());
		blockChainHelper.newBlock(oBlockEntity.build());
	}

	public AddBlockResponse ApplyBlock(ByteString bs) throws Exception {
		return ApplyBlock(BlockEntity.newBuilder().mergeFrom(bs).build());
	}

	public synchronized AddBlockResponse ApplyBlock(BlockEntity oBlockEntity) {
		AddBlockResponse.Builder oAddBlockResponse = AddBlockResponse.newBuilder();
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		int currentLastBlockNumber;
		try {
			currentLastBlockNumber = blockChainHelper.getLastBlockNumber();
		} catch (Exception e1) {
			oAddBlockResponse.setRetCode(-2);
			return oAddBlockResponse.build();
		}
		// 上一个区块是否存在
		BlockEntity oParentBlock = null;
		try {
			oParentBlock = getBlock(oBlockHeader.getParentHash().toByteArray()).build();
		} catch (Exception e) {
			log.error("parent block not found::" + (oBlockHeader.getNumber() - 1) + " current::" + currentLastBlockNumber);
		}
		if (oParentBlock == null) {
			oAddBlockResponse.setRetCode(-1);
			oAddBlockResponse.setCurrentNumber(currentLastBlockNumber);

			// 暂存
			blockChainHelper.cacheBlock(oBlockEntity);
		} else {
			if (oParentBlock.getHeader().getNumber() + 1 != oBlockHeader.getNumber()
					|| currentLastBlockNumber != oParentBlock.getHeader().getNumber()) {
				oAddBlockResponse.setRetCode(-1);
				oAddBlockResponse.setCurrentNumber(currentLastBlockNumber);

				// 暂存
				blockChainHelper.cacheBlock(oBlockEntity);
			} else {
				try {
					addBlock(oBlockEntity);

					// 检查
					List<BlockEntity> childs = blockChainHelper
							.tryGetChildBlock(oBlockEntity.getHeader().getBlockHash().toByteArray());
					if (childs.size() == 1) {
						oAddBlockResponse = ApplyBlock(childs.get(0)).toBuilder();
					} else if (childs.size() > 1) {

					}
					
					currentLastBlockNumber = blockChainHelper.getLastBlockNumber();
					log.debug("success add block, current number is::" + currentLastBlockNumber);
					oAddBlockResponse.setCurrentNumber(currentLastBlockNumber);
				} catch (Exception e) {
					oAddBlockResponse.setRetCode(-2);
					if (e.getMessage() != null)
						oAddBlockResponse.setRetMsg(e.getMessage());
				}
			}
		}

		return oAddBlockResponse.build();
	}

	/**
	 * 执行区块。比较交易完整性，执行交易。
	 * 
	 * @param oBlockEntity
	 * @throws Exception
	 */
	private synchronized void addBlock(BlockEntity oBlockEntity) throws Exception {
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		TrieImpl oTrieImpl = new TrieImpl();

		BlockBody.Builder bb = oBlockEntity.getBody().toBuilder();
		// 校验交易完整性
		for (ByteString txHash : oBlockHeader.getTxHashsList()) {
			// 从本地缓存中移除交易
			transactionHelper.removeWaitBlockTx(txHash.toByteArray());

			// 交易必须都存在
			MultiTransaction oMultiTransaction = transactionHelper.GetTransaction(txHash.toByteArray());
			txs.add(oMultiTransaction);
			// 验证交易是否被篡改
			// 1. 重新Hash，比对交易Hash

			MultiTransaction.Builder oReHashMultiTransaction = oMultiTransaction.toBuilder();
			byte[] newHash = encApi.sha256Encode(oReHashMultiTransaction.getTxBody().toByteArray());
			if (!encApi.hexEnc(newHash).equals(encApi.hexEnc(oMultiTransaction.getTxHash().toByteArray()))) {
				throw new Exception(String.format("交易Hash %s 与 %s 不一致", encApi.hexEnc(txHash.toByteArray()),
						encApi.hexEnc(newHash)));
			}
			// 2. 重构MPT Trie，比对RootHash
			oTrieImpl.put(oMultiTransaction.getTxHash().toByteArray(), oMultiTransaction.toByteArray());

			bb.addTxs(oMultiTransaction);
		}
		if (!FastByteComparisons.equal(oBlockEntity.getHeader().getTxTrieRoot().toByteArray(),
				oTrieImpl.getRootHash())) {
			throw new Exception(String.format("交易根 %s 与 %s 不一致",
					encApi.hexEnc(oBlockEntity.getHeader().getTxTrieRoot().toByteArray()),
					encApi.hexEnc(oTrieImpl.getRootHash())));
		}

		oBlockEntity = oBlockEntity.toBuilder().setBody(bb).build();

		// 执行交易
		transactionHelper.ExecuteTransaction(txs);

		// 添加块
		blockChainHelper.appendBlock(oBlockEntity);

		// 应用奖励
		// applyReward(oBlockEntity);
		log.info(String.format("LOGFILTER %s %s %s %s 执行区块[%s]", KeyConstant.node.getNode(), "account", "apply",
				"block", encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray())));
	}

	/**
	 * 区块奖励
	 * 
	 * @param oBlock
	 * @throws Exception
	 */
	public void applyReward(BlockEntity oBlock) throws Exception {
		// blockChainConfig.getMinerReward()
		// 取第n块block
		if (oBlock.getHeader().getNumber() - blockChainConfig.getMinerRewardWait() > 0) {
			BlockEntity rewardBlock = blockChainHelper
					.getBlockByNumber(oBlock.getHeader().getNumber() - blockChainConfig.getMinerRewardWait());
			// TODO 待实现
			// accountHelper.addBalance(rewardBlock.getHeader().getCoinbase().toByteArray(),
			// blockChainConfig.getMinerReward());
		}
	}

	/**
	 * 根据区块Hash获取区块信息
	 * 
	 * @param blockHash
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder getBlock(byte[] blockHash) throws Exception {
		BlockEntity.Builder oBlockEntity = BlockEntity
				.parseFrom(dao.getBlockDao().get(OEntityBuilder.byteKey2OKey(blockHash)).get().getExtdata())
				.toBuilder();
		return oBlockEntity;
	}

	/**
	 * 获取节点最后一个区块
	 * 
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder GetBestBlock() throws Exception {
		return getBlock(blockChainHelper.GetBestBlock());
	}

	/**
	 * 获取Block中的全部交易
	 * 
	 * @param blockHash
	 * @return
	 * @throws Exception
	 */
	public LinkedList<MultiTransaction> GetTransactionsByBlock(byte[] blockHash) throws Exception {
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		BlockEntity.Builder oBlockEntity = getBlock(blockHash);
		for (MultiTransaction tx : oBlockEntity.getBody().getTxsList()) {
			txs.add(tx);
		}
		return txs;
	}

	/**
	 * 根据交易，获取区块信息
	 * 
	 * @param txHash
	 * @return
	 * @throws Exception
	 */
	public BlockEntity getBlockByTransaction(byte[] txHash) throws Exception {
		ByteString blockHash = dao.getTxblockDao().get(OEntityBuilder.byteKey2OKey(txHash)).get().getExtdata();
		return getBlock(blockHash.toByteArray()).build();
	}

	/**
	 * 根据账户地址，读取账户关联的交易(可以增加遍历的区块的数量)
	 * 
	 * @param address
	 * @return
	 * @throws Exception
	 */
	public LinkedList<MultiTransaction> getTransactionByAddress(byte[] address) throws Exception {
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		// 找到最佳块，遍历所有block
		for (BlockEntity oBlockEntity : blockChainHelper.getParentsBlocks(blockChainHelper.GetBestBlock())) {
			for (MultiTransaction multiTransaction : oBlockEntity.getBody().getTxsList()) {
				// if
				// (multiTransaction.toBuilder().build().getTxBody().getInputs(index))
				boolean added = false;
				for (MultiTransactionInput oMultiTransactionInput : multiTransaction.getTxBody().getInputsList()) {
					if (FastByteComparisons.equal(oMultiTransactionInput.getAddress().toByteArray(), address)) {
						txs.add(multiTransaction);
						added = true;
						break;
					}
				}
				if (!added) {
					for (MultiTransactionOutput oMultiTransactionOutput : multiTransaction.getTxBody()
							.getOutputsList()) {
						if (FastByteComparisons.equal(oMultiTransactionOutput.getAddress().toByteArray(), address)) {
							txs.add(multiTransaction);
							added = true;
							break;
						}
					}
				}
			}
		}

		return txs;
	}
}
