package org.brewchain.account.core;

import java.util.Date;
import java.util.LinkedList;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.actuator.ActuatorCallContract;
import org.brewchain.account.core.actuator.ActuatorCallInternalFunction;
import org.brewchain.account.core.actuator.ActuatorCreateContract;
import org.brewchain.account.core.actuator.ActuatorCreateToken;
import org.brewchain.account.core.actuator.ActuatorCreateUnionAccount;
import org.brewchain.account.core.actuator.ActuatorCryptoTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorDefault;
import org.brewchain.account.core.actuator.ActuatorLockTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorTokenTransaction;
import org.brewchain.account.core.actuator.ActuatorUnionAccountTransaction;
import org.brewchain.account.core.actuator.iTransactionActuator;
import org.brewchain.account.core.store.BlockStoreSummary;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.Blockimpl.AddBlockResponse;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OValue;
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

	/**
	 * 创建新区块
	 * 
	 * @param extraData
	 * @param coinBase
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder CreateNewBlock(String extraData) throws Exception {
		return CreateNewBlock(KeyConstant.DEFAULT_BLOCK_TX_COUNT, extraData);
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
	public BlockEntity.Builder CreateNewBlock(int txCount, String extraData) throws Exception {
		return CreateNewBlock(transactionHelper.getWaitBlockTx(txCount), extraData);
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
	public BlockEntity.Builder CreateNewBlock(LinkedList<MultiTransaction> txs, String extraData) throws Exception {
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
		long currentTimestamp = System.currentTimeMillis();
		oBlockHeader.setTimestamp(
				System.currentTimeMillis() == oBestBlockHeader.getTimestamp() ? oBestBlockHeader.getTimestamp() + 1
						: currentTimestamp);
		oBlockHeader.setNumber(oBestBlockHeader.getNumber() + 1);
		oBlockHeader.setReward(KeyConstant.BLOCK_REWARD);
		oBlockHeader.setExtraData(extraData);
		// 构造MPT Trie
		CacheTrie oTrieImpl = new CacheTrie();
		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
			oTrieImpl.put(encApi.hexDec(txs.get(i).getTxHash()), transactionHelper.getTransactionContent(txs.get(i)));
		}
		oBlockMiner.setAddress(encApi.hexEnc(KeyConstant.node.getoAccount().getAddress().toByteArray()));
		oBlockMiner.setNode(KeyConstant.node.getNode());
		oBlockMiner.setBcuid(KeyConstant.node.getBcuid());
		oBlockMiner.setReward(KeyConstant.BLOCK_REWARD);
		// oBlockMiner.setAddress(value);

		oBlockHeader.setTxTrieRoot(encApi.hexEnc(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(encApi.hexEnc(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);
		oBlockEntity.setMiner(oBlockMiner);

		BlockStoreSummary oSummary = blockChainHelper.addBlock(oBlockEntity.build());
		switch (oSummary.getBehavior()) {
		case APPLY:
			this.stateTrie.setRoot(encApi.hexDec(oBestBlockHeader.getStateRoot()));
			byte[] stateRoot = processBlock(oBlockEntity);
			oBlockEntity.setHeader(oBlockEntity.getHeaderBuilder().setStateRoot(encApi.hexEnc(stateRoot)));
			blockChainHelper.connectBlock(oBlockEntity.build());

			log.info(String.format("LOGFILTER %s %s %s %s 执行区块[%s]", KeyConstant.node.getoAccount().getAddress(),
					"account", "apply", "block", oBlockEntity.getHeader().getBlockHash()));

			log.debug("new block, number::" + oBlockEntity.getHeader().getNumber() + " hash::"
					+ oBlockEntity.getHeader().getBlockHash() + " parent::" + oBlockEntity.getHeader().getParentHash()
					+ " state::" + oBlockEntity.getHeader().getStateRoot() + " bcuid::" + oBlockMiner.getBcuid()
					+ " address::" + oBlockMiner.getAddress());

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
	public void CreateGenesisBlock(LinkedList<Account> accounts, LinkedList<MultiTransaction> txs, String extraData)
			throws Exception {
		if (blockChainHelper.isExistsGenesisBlock()) {
			throw new Exception("the genesis block already exists");
		}

		BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();
		BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
		BlockBody.Builder oBlockBody = BlockBody.newBuilder();

		// 构造Block Header
		// oBlockHeader.setCoinbase(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
		oBlockHeader.setParentHash(encApi.hexEnc(ByteUtil.EMPTY_BYTE_ARRAY));

		long currentTimestamp = System.currentTimeMillis();
		oBlockHeader.setTimestamp(currentTimestamp);
		oBlockHeader.setNumber(KeyConstant.GENESIS_NUMBER);
		oBlockHeader.setReward(0);
		oBlockHeader.setExtraData(extraData);
		// 构造MPT Trie
		CacheTrie oTrieImpl = new CacheTrie();
		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
			oTrieImpl.put(encApi.hexDec(txs.get(i).getTxHash()), txs.get(i).toByteArray());
		}

		for (Account oAccount : accounts) {
			this.stateTrie.put(oAccount.getAddress().toByteArray(), oAccount.getValue().toByteArray());
		}
		oBlockHeader.setStateRoot(encApi.hexEnc(this.stateTrie.getRootHash()));
		oBlockHeader.setTxTrieRoot(encApi.hexEnc(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(encApi.hexEnc(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
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
				+ oBlockEntity.getHeader().getBlockHash() + " parent::" + oBlockEntity.getHeader().getParentHash()
				+ " stateroot::" + oBlockEntity.getHeader().getStateRoot());
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
				log.info("block exists, but cannot find parent block number::" + oBlockEntity.getHeader().getNumber());
				try {
					BlockEntity pBlockEntity = blockChainHelper
							.getBlockByHash(oBlockEntity.getHeader().getParentHash());
					if (pBlockEntity != null) {
						log.debug("find in local cache number::" + pBlockEntity.getHeader().getBlockHash());
						oBlockEntity = pBlockEntity;
						oBlockStoreSummary = blockChainHelper.addBlock(oBlockEntity);
					} else {
						log.debug("need prev block number::" + (oBlockEntity.getHeader().getNumber() - 2));
						oAddBlockResponse.setRetCode(-9);
						oAddBlockResponse.setCurrentNumber(oBlockEntity.getHeader().getNumber() - 2);
						oAddBlockResponse.setWantNumber(oBlockEntity.getHeader().getNumber() - 1);
						blockChainHelper.rollbackTo(oBlockEntity.getHeader().getNumber() - 2);
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
					}
				} catch (Exception e1) {
					log.error("exception ", e1);
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
				}
				break;
			case CACHE:
				log.info("cache block number::" + oBlockEntity.getHeader().getNumber());
				oAddBlockResponse.setWantNumber(oBlockEntity.getHeader().getNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case APPLY:
				log.info("begin to apply block number::" + oBlockEntity.getHeader().getNumber());

				for (String txHash : oBlockEntity.getHeader().getTxHashsList()) {
					if (!transactionHelper.isExistsTransaction(txHash)) {
						oAddBlockResponse.addTxHashs(txHash);
					}
				}
				if (oAddBlockResponse.getTxHashsCount() > 0) {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
					break;
				}

				BlockEntity parentBlock;
				try {
					parentBlock = blockChainHelper.getBlockByHash(oBlockEntity.getHeader().getParentHash());
					this.stateTrie.setRoot(encApi.hexDec(parentBlock.getHeader().getStateRoot()));
					byte[] stateRoot = processBlock(oBlockEntity.toBuilder());

					log.debug("=====sync-> " + oBlockEntity.getHeader().getNumber() + " parent::"
							+ parentBlock.getHeader().getStateRoot() + " current::"
							+ oBlockEntity.getHeader().getStateRoot() + " exec::" + encApi.hexEnc(stateRoot));

					if (!oBlockEntity.getHeader().getStateRoot().equals(encApi.hexEnc(stateRoot))) {
						log.error("begin to roll back, stateRoot::" + encApi.hexEnc(stateRoot) + " blockStateRoot::"
								+ oBlockEntity.getHeader().getStateRoot());
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
				// blockChainHelper.rollback();
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			}
		}

		if (oAddBlockResponse.getCurrentNumber() == 0) {
			oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
		}
		
		if (oAddBlockResponse.getWantNumber() == 0) {
			oAddBlockResponse.setWantNumber(oAddBlockResponse.getWantNumber());
		}
		
		log.debug("return apply current::" + oAddBlockResponse.getCurrentNumber() + " retcode::"
				+ oAddBlockResponse.getRetCode() + " want::" + oAddBlockResponse.getWantNumber());
		return oAddBlockResponse.build();
	}

	private synchronized byte[] processBlock(BlockEntity.Builder oBlockEntity) throws Exception {
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		CacheTrie oTrieImpl = new CacheTrie();

		BlockBody.Builder bb = oBlockEntity.getBody().toBuilder();
		// 校验交易完整性
		for (String txHash : oBlockHeader.getTxHashsList()) {
			// 从本地缓存中移除交易
			transactionHelper.removeWaitBlockTx(txHash);

			// 交易必须都存在
			MultiTransaction oMultiTransaction = transactionHelper.GetTransaction(txHash);

			// 验证交易是否被篡改
			// 1. 重新Hash，比对交易Hash
			// MultiTransaction.Builder oReHashMultiTransaction =
			// oMultiTransaction.toBuilder();
			// byte[] newHash =
			// encApi.sha256Encode(oReHashMultiTransaction.getTxBody().toByteArray());
			// if (!encApi.hexEnc(newHash).equals(oMultiTransaction.getTxHash())) {
			// throw new Exception(String.format("交易Hash %s 与 %s 不一致", txHash,
			// encApi.hexEnc(newHash)));
			// }
			// 2. 重构MPT Trie，比对RootHash
			oTrieImpl.put(encApi.hexDec(oMultiTransaction.getTxHash()),
					transactionHelper.getTransactionContent(oMultiTransaction));

			bb.addTxs(oMultiTransaction);

			if (oMultiTransaction.getStatus() == null || oMultiTransaction.getStatus().isEmpty()) {
				txs.add(oMultiTransaction);
			}
		}
		if (!oBlockEntity.getHeader().getTxTrieRoot().equals(encApi.hexEnc(oTrieImpl.getRootHash()))) {
			throw new Exception(String.format("transaction trie root hash %s not equal %s",
					oBlockEntity.getHeader().getTxTrieRoot(), encApi.hexEnc(oTrieImpl.getRootHash())));
		}

		oBlockEntity.setBody(bb);
		// 执行交易
		transactionHelper.ExecuteTransaction(txs, oBlockEntity.build());
		// reward
		applyReward(oBlockEntity.build());

		byte[] stateRoot = this.stateTrie.getRootHash();
		return stateRoot;
	}

	/**
	 * 区块奖励
	 * 
	 * @param oBlock
	 * @throws Exception
	 */
	public void applyReward(BlockEntity oCurrentBlock) throws Exception {
		accountHelper.addTokenBalance(ByteString.copyFrom(encApi.hexDec(oCurrentBlock.getMiner().getAddress())), "CWS",
				oCurrentBlock.getMiner().getReward());
	}

	/**
	 * 根据区块Hash获取区块信息
	 * 
	 * @param blockHash
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder getBlock(String blockHash) throws Exception {
		BlockEntity block = blockChainHelper.getBlockByHash(blockHash);
		if (block != null) {
			return block.toBuilder();
		}
		return null;
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
	public LinkedList<MultiTransaction> GetTransactionsByBlock(String blockHash) throws Exception {
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
		OValue oOValue = dao.getTxblockDao().get(OEntityBuilder.byteKey2OKey(txHash)).get();
		if (oOValue != null && oOValue.getExtdata() != null) {
			String blockHash = encApi.hexEnc(oOValue.getExtdata().toByteArray());
			return getBlock(blockHash).build();
		}
		return null;
	}

	/**
	 * 根据账户地址，读取账户关联的交易(可以增加遍历的区块的数量)
	 * 
	 * @param address
	 * @return
	 * @throws Exception
	 */
	public LinkedList<MultiTransaction> getTransactionByAddress(String address, int blockCount) throws Exception {
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		// 找到最佳块，遍历所有block
		for (BlockEntity oBlockEntity : blockChainHelper.getParentsBlocks(blockChainHelper.GetConnectBestBlockHash(),
				null, blockCount)) {
			for (MultiTransaction multiTransaction : oBlockEntity.getBody().getTxsList()) {
				// if
				// (multiTransaction.toBuilder().build().getTxBody().getInputs(index))
				boolean added = false;
				for (MultiTransactionInput oMultiTransactionInput : multiTransaction.getTxBody().getInputsList()) {
					if (address.equals(encApi.hexEnc(oMultiTransactionInput.getAddress().toByteArray()))) {
						txs.add(multiTransaction);
						added = true;
						break;
					}
				}
				if (!added) {
					for (MultiTransactionOutput oMultiTransactionOutput : multiTransaction.getTxBody()
							.getOutputsList()) {
						if (address.equals(encApi.hexEnc(oMultiTransactionOutput.getAddress().toByteArray()))) {
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
