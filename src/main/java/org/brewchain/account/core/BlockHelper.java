package org.brewchain.account.core;

import java.util.Date;
import java.util.LinkedList;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.store.BlockStoreSummary;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Blockimpl.AddBlockResponse;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Block.BlockBody;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Block.BlockHeader;
import org.brewchain.evmapi.gens.Block.BlockMiner;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

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
	@ActorRequire(name = "CacheBlock_HashMapDB", scope = "global")
	CacheBlockHashMapDB oCacheHashMapDB;
	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;

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
		BlockEntity oBestBlockEntity = blockChainHelper.GetConnectBestBlock();
		if (oBestBlockEntity == null) {
			oBestBlockEntity = blockChainHelper.GetStableBestBlock();
		}
		BlockHeader oBestBlockHeader = oBestBlockEntity.getHeader();

		// 构造Block Header
		// oBlockHeader.setCoinbase(ByteString.copyFrom(coinBase));
		oBlockHeader.setParentHash(oBestBlockHeader.getBlockHash());

		// 确保时间戳不重复
		long currentTimestamp = new Date().getTime();
		oBlockHeader.setTimestamp(
				new Date().getTime() == oBestBlockHeader.getTimestamp() ? oBestBlockHeader.getTimestamp() + 1
						: currentTimestamp);
		oBlockHeader.setNumber(oBestBlockHeader.getNumber() + 1);
		oBlockHeader.setReward(ByteString.copyFrom(ByteUtil.intToBytes(KeyConstant.BLOCK_REWARD)));
		oBlockHeader.setExtraData(ByteString.copyFrom(extraData));
		oBlockHeader.setNonce(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
		// 构造MPT Trie
		CacheTrie oTrieImpl = new CacheTrie();
		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
			oTrieImpl.put(txs.get(i).getTxHash().toByteArray(), txs.get(i).toByteArray());
		}
		oBlockMiner.setAddress(KeyConstant.node.getoAccount().getAddress());
		oBlockMiner.setNode(KeyConstant.node.getNode());
		oBlockMiner.setBcuid(KeyConstant.node.getBcuid());
		oBlockMiner.setReward(KeyConstant.BLOCK_REWARD);
		// oBlockMiner.setAddress(value);

		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);
		oBlockEntity.setMiner(oBlockMiner);

		BlockStoreSummary oSummary = blockChainHelper.addBlock(oBlockEntity.build());
		switch (oSummary.getBehavior()) {
		case APPLY:
			this.stateTrie.setRoot(oBestBlockHeader.getStateRoot().toByteArray());
			byte[] stateRoot = processBlock(oBlockEntity);
			oBlockEntity.setHeader(oBlockEntity.getHeaderBuilder().setStateRoot(ByteString.copyFrom(stateRoot)));
			blockChainHelper.connectBlock(oBlockEntity.build());

			log.info(String.format("LOGFILTER %s %s %s %s 执行区块[%s]", KeyConstant.node.getoAccount().getAddress(),
					"account", "apply", "block", encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray())));

			log.debug("new block, number::" + oBlockEntity.getHeader().getNumber() + " hash::"
					+ encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()) + " parent::"
					+ encApi.hexEnc(oBlockEntity.getHeader().getParentHash().toByteArray()) + " state::"
					+ encApi.hexEnc(oBlockEntity.getHeader().getStateRoot().toByteArray()));

			return oBlockEntity;
		default:
			return null;
		}
	}

	/**
	 * 创建创世块
	 * 
	 * @param txs
	 * @param extraData
	 * @throws Exception
	 */
	public void CreateGenesisBlock(LinkedList<Account> accounts, LinkedList<MultiTransaction> txs, byte[] extraData)
			throws Exception {
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
		CacheTrie oTrieImpl = new CacheTrie();
		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
			oTrieImpl.put(txs.get(i).getTxHash().toByteArray(), txs.get(i).toByteArray());
		}

		for (Account oAccount : accounts) {
			this.stateTrie.put(oAccount.getAddress().toByteArray(), oAccount.getValue().toByteArray());
		}
		oBlockHeader.setStateRoot(ByteString.copyFrom(this.stateTrie.getRootHash()));
		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);

		// oBlockStorageDB.setLastBlock(oBlockEntity.build());
		blockChainHelper.addBlock(oBlockEntity.build());
		blockChainHelper.stableBlock(oBlockEntity.build());
	}

	public synchronized AddBlockResponse ApplyBlock(ByteString bs) throws Exception {
		return ApplyBlock(BlockEntity.newBuilder().mergeFrom(bs).build());
	}

	public synchronized AddBlockResponse ApplyBlock(BlockEntity oBlockEntity) {
		AddBlockResponse.Builder oAddBlockResponse = AddBlockResponse.newBuilder();
		log.debug("receive block number::" + oBlockEntity.getHeader().getNumber() + " hash::"
				+ encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()) + " stateroot::"
				+ encApi.hexEnc(oBlockEntity.getHeader().getStateRoot().toByteArray()));
		BlockStoreSummary oBlockStoreSummary = blockChainHelper.addBlock(oBlockEntity);
		while (oBlockStoreSummary.getBehavior() != BLOCK_BEHAVIOR.DONE) {
			switch (oBlockStoreSummary.getBehavior()) {
			case DROP:
				log.info("drop block number::" + oBlockEntity.getHeader().getNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case EXISTS_DROP:
				log.info("already exists, drop block number::" + oBlockEntity.getHeader().getNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case EXISTS_PREV:
				log.info("need prev block number::" + (blockChainHelper.getLastBlockNumber() - 1));
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case CACHE:
				log.info("cache block number::" + oBlockEntity.getHeader().getNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case APPLY:
				log.info("begin to apply block number::" + oBlockEntity.getHeader().getNumber());

				for (ByteString txHash : oBlockEntity.getHeader().getTxHashsList()) {
					if (!transactionHelper.isExistsTransaction(txHash.toByteArray())) {
						oAddBlockResponse.addTxHashs(encApi.hexEnc(txHash.toByteArray()));
					}
				}
				if (oAddBlockResponse.getTxHashsCount() > 0) {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
					break;
				}

				BlockEntity parentBlock;
				try {
					parentBlock = blockChainHelper
							.getBlockByHash(oBlockEntity.getHeader().getParentHash().toByteArray());
					this.stateTrie.setRoot(parentBlock.getHeader().getStateRoot().toByteArray());
					byte[] stateRoot = processBlock(oBlockEntity.toBuilder());

					log.debug("=====sync-> " + oBlockEntity.getHeader().getNumber() + " parent::"
							+ encApi.hexEnc(parentBlock.getHeader().getStateRoot().toByteArray()) + " current::"
							+ encApi.hexEnc(oBlockEntity.getHeader().getStateRoot().toByteArray()) + " exec::"
							+ encApi.hexEnc(stateRoot));

					if (!FastByteComparisons.equal(stateRoot, oBlockEntity.getHeader().getStateRoot().toByteArray())) {
						log.error("begin to roll back, stateRoot::" + encApi.hexEnc(stateRoot) + " blockStateRoot::"
								+ encApi.hexEnc(oBlockEntity.getHeader().getStateRoot().toByteArray()));
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
					} else {
						oBlockStoreSummary = blockChainHelper.connectBlock(oBlockEntity);
					}
				} catch (Exception e) {
					log.error(e.getMessage());
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
				}
				break;
			case APPLY_CHILD:
				log.info("ready to apply child block");
				oBlockEntity = blockChainHelper.getChildBlock(oBlockEntity);
				oBlockStoreSummary = blockChainHelper.addBlock(oBlockEntity);
				break;
			case STORE:
			case DONE:
				log.info("apply done number::" + blockChainHelper.getLastBlockNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case ERROR:
				log.error("fail to apply block number::" + oBlockEntity.getHeader().getNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			}
		}

		oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
		log.debug("return apply current::" + oAddBlockResponse.getCurrentNumber());
		return oAddBlockResponse.build();
		//
		// BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		// int currentLastBlockNumber;
		// try {
		// currentLastBlockNumber = blockChainHelper.getLastBlockNumber();
		// } catch (Exception e1) {
		// oAddBlockResponse.setRetCode(-2);
		// return oAddBlockResponse.build();
		// }
		// log.debug(
		// "receive block number::" + oBlockEntity.getHeader().getNumber() + "
		// current::" + currentLastBlockNumber
		// + " hash::" +
		// encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray()) + "
		// parent::"
		// + encApi.hexEnc(oBlockEntity.getHeader().getParentHash().toByteArray()));
		//
		// if (oBlockEntity.getHeader().getNumber() < (currentLastBlockNumber -
		// KeyConstant.ROLLBACK_BLOCK)) {
		// oAddBlockResponse.setRetCode(-2);
		// oAddBlockResponse.setCurrentNumber(currentLastBlockNumber);
		// return oAddBlockResponse.build();
		// }
		// // 上一个区块是否存在
		// BlockEntity oParentBlock = null;
		// try {
		// oParentBlock = getBlock(oBlockHeader.getParentHash().toByteArray()).build();
		// } catch (Exception e) {
		// log.error("try get parent block error::" + e.getMessage());
		// }
		// if (oParentBlock == null || oParentBlock.getHeader().getNumber() + 1 !=
		// oBlockHeader.getNumber()) {
		// oAddBlockResponse.setRetCode(-1);
		// oAddBlockResponse.setCurrentNumber(currentLastBlockNumber);
		//
		// // 暂存
		// BlockChainTempNode oTempNode =
		// blockChainHelper.cacheBlock(oBlockEntity.build());
		// log.error("parent block not found:: parent::" + (oBlockHeader.getNumber() -
		// 1) + " block::"
		// + oBlockHeader.getNumber() + " current::" + currentLastBlockNumber + "
		// count::"
		// + oTempNode.getSyncCount());
		//
		// if (oTempNode.getSyncCount() > 2 && oTempNode.getNumber() ==
		// (currentLastBlockNumber - 1)) {
		// log.warn("begin to request parent parent block::" + (currentLastBlockNumber -
		// 1));
		// oAddBlockResponse.setRetCode(-1);
		// oAddBlockResponse.setCurrentNumber(currentLastBlockNumber - 1);
		// return oAddBlockResponse.build();
		// }
		// } else if
		// (blockChainHelper.isExistsBlockFromStore(oBlockHeader.getBlockHash().toByteArray()))
		// {
		// log.warn("exists, drop it, number::" + oBlockHeader.getNumber());
		// oAddBlockResponse.setRetCode(-1);
		// oAddBlockResponse.setCurrentNumber(currentLastBlockNumber);
		// } else {
		// BlockChainTempNode oTempNode = null;
		//
		// oTempNode =
		// blockChainHelper.tryGetBlockTempNodeFromTempStore(oBlockHeader.getBlockHash().toByteArray());
		// if (oTempNode != null) {
		// log.warn("exists, drop it, number::" + oBlockHeader.getNumber());
		// oAddBlockResponse.setRetCode(-1);
		// oAddBlockResponse.setCurrentNumber(currentLastBlockNumber);
		// } else {
		// BlockChainTempNode oParentTempNode = null;
		//
		// oParentTempNode = blockChainHelper
		// .tryGetBlockTempNodeFromTempStore(oBlockHeader.getParentHash().toByteArray());
		//
		// if (oBlockHeader.getNumber() != 1
		// && (oParentTempNode == null || (oParentTempNode != null &&
		// !oParentTempNode.isStable()))) {
		// // 暂存
		// blockChainHelper.cacheBlock(oBlockEntity.build());
		// log.error("parent block not exec:: parent::" + (oBlockHeader.getNumber() - 1)
		// + " block::"
		// + oBlockHeader.getNumber() + " current::" + currentLastBlockNumber);
		// } else {
		// log.debug("begin to exce and add block::" +
		// oBlockEntity.getHeader().getNumber());
		// try {
		// if (addBlock(oBlockEntity, oParentBlock)) {
		// log.debug("success add block::" + oBlockEntity.getHeader().getNumber()
		// + ", current number is::" + currentLastBlockNumber);
		//
		// // 检查
		// BlockEntity child = blockChainHelper.tryGetAndDeleteBlockFromTempStore(
		// oBlockEntity.getHeader().getBlockHash().toByteArray());
		// while (child != null) {
		// log.debug("get child block::" + child.getHeader().getNumber());
		// addBlock(child.toBuilder(), oBlockEntity.build());
		// oBlockEntity = child.toBuilder();
		// child = blockChainHelper.tryGetAndDeleteBlockFromTempStore(
		// oBlockEntity.getHeader().getBlockHash().toByteArray());
		// }
		// }
		//
		// oAddBlockResponse.setRetCode(1);
		// oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
		// } catch (Exception e) {
		// e.printStackTrace();
		// oAddBlockResponse.setRetCode(-2);
		// try {
		// oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
		// } catch (Exception e2) {
		//
		// }
		// if (e.getMessage() != null)
		// oAddBlockResponse.setRetMsg(e.getMessage());
		// if (e != null && e.getMessage() != null)
		// log.error("append block error::" + e.getMessage());
		// }
		// }
		// }
		//
		// }
		//
		// log.debug("return apply block::" + " block::" +
		// oBlockEntity.getHeader().getNumber() + " current::"
		// + oAddBlockResponse.getCurrentNumber());
		// return oAddBlockResponse.build();
	}

	private synchronized byte[] processBlock(BlockEntity.Builder oBlockEntity) throws Exception {
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		CacheTrie oTrieImpl = new CacheTrie();

		BlockBody.Builder bb = oBlockEntity.getBody().toBuilder();
		// 校验交易完整性
		for (ByteString txHash : oBlockHeader.getTxHashsList()) {
			// 从本地缓存中移除交易
			transactionHelper.removeWaitBlockTx(encApi.hexEnc(txHash.toByteArray()));

			// 交易必须都存在
			MultiTransaction oMultiTransaction = transactionHelper.GetTransaction(txHash.toByteArray());

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

			if (oMultiTransaction.getStatus() == null || oMultiTransaction.getStatus().isEmpty()) {
				txs.add(oMultiTransaction);
			}
		}
		if (!FastByteComparisons.equal(oBlockEntity.getHeader().getTxTrieRoot().toByteArray(),
				oTrieImpl.getRootHash())) {
			throw new Exception(String.format("交易根 %s 与 %s 不一致",
					encApi.hexEnc(oBlockEntity.getHeader().getTxTrieRoot().toByteArray()),
					encApi.hexEnc(oTrieImpl.getRootHash())));
		}

		oBlockEntity.setBody(bb);

		// TODO 事务

		// 执行交易
		transactionHelper.ExecuteTransaction(txs);

		// reward
		applyReward(oBlockEntity.build());

		byte[] stateRoot = this.stateTrie.getRootHash();
		return stateRoot;
	}

	// private synchronized boolean addBlock(BlockEntity oBlockEntity, BlockEntity
	// parentBlock) throws Exception {
	// this.stateTrie.setRoot(parentBlock.getHeader().getStateRoot().toByteArray());
	// byte[] stateRoot = processBlock(oBlockEntity);
	// log.debug("=====sync-> " + oBlockEntity.getHeader().getNumber() + " parent::"
	// + encApi.hexEnc(parentBlock.getHeader().getStateRoot().toByteArray()) + "
	// current::"
	// + encApi.hexEnc(oBlockEntity.getHeader().getStateRoot().toByteArray()) + "
	// exec::"
	// + encApi.hexEnc(stateRoot));
	// if (!FastByteComparisons.equal(stateRoot,
	// oBlockEntity.getHeader().getStateRoot().toByteArray())) {
	// log.error("begin to roll back, stateRoot::" + encApi.hexEnc(stateRoot) + "
	// blockStateRoot::"
	// + encApi.hexEnc(oBlockEntity.getHeader().getStateRoot().toByteArray()));
	// blockChainHelper.rollBackTo(parentBlock);
	// return false;
	// } else {
	// // 添加块
	// if (!blockChainHelper.appendBlock(oBlockEntity.build())) {
	// log.error("append block error");
	// throw new Exception("block executed, but fail to add to db.");
	// }
	// log.info(String.format("LOGFILTER %s %s %s %s 执行区块[%s]",
	// KeyConstant.node.getNode(), "account", "apply",
	// "block",
	// encApi.hexEnc(oBlockEntity.getHeader().getBlockHash().toByteArray())));
	// return true;
	// }
	// }

	/**
	 * 区块奖励
	 * 
	 * @param oBlock
	 * @throws Exception
	 */
	public void applyReward(BlockEntity oCurrentBlock) throws Exception {
		accountHelper.addTokenBalance(encApi.hexDec(oCurrentBlock.getMiner().getAddress()), "CWS",
				oCurrentBlock.getMiner().getReward());
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
		return blockChainHelper.GetConnectBestBlock().toBuilder();
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
	public LinkedList<MultiTransaction> getTransactionByAddress(byte[] address, int blockCount) throws Exception {
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		// 找到最佳块，遍历所有block
		for (BlockEntity oBlockEntity : blockChainHelper.getParentsBlocks(blockChainHelper.GetConnectBestBlockHash(),
				null, blockCount)) {
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
