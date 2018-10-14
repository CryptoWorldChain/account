package org.brewchain.account.core;

import java.util.LinkedList;
import java.util.List;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.processor.ProcessorManager;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Blockimpl.AddBlockResponse;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Block.BlockBody;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Block.BlockHeader;
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
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
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
	@ActorRequire(name = "Processor_Manager", scope = "global")
	ProcessorManager oProcessorManager;

	/**
	 * 创建新区块
	 * 
	 * @param extraData
	 * @param coinBase
	 * @return
	 * @throws Exception
	 */
	public BlockEntity.Builder CreateNewBlock(String extraData, int confirmRecvCount, String term) throws Exception {
		return CreateNewBlock(KeyConstant.DEFAULT_BLOCK_TX_COUNT, confirmRecvCount, extraData, term);
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
	public BlockEntity.Builder CreateNewBlock(int txCount, int confirmRecvCount, String extraData, String term)
			throws Exception {
		log.error(
				"make new block::maxTxCount=" + txCount + ",confirmRecvCount::" + confirmRecvCount + " term::" + term);
		return CreateNewBlock(transactionHelper.getWaitBlockTx(txCount, confirmRecvCount), extraData, term);
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
	public BlockEntity.Builder CreateNewBlock(List<MultiTransaction> txs, String extraData, String term)
			throws Exception {
		return oProcessorManager.getProcessor(blockChainConfig.getAccountVersion()).CreateNewBlock(txs, extraData,
				term);
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
		oBlockHeader.setExtraData(extraData);

		CacheTrie oTransactionTrie = new CacheTrie(this.encApi);

		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
			oTransactionTrie.put(encApi.hexDec(txs.get(i).getTxHash()), txs.get(i).toByteArray());
		}

		for (Account oAccount : accounts) {
			this.stateTrie.put(oAccount.getAddress().toByteArray(), oAccount.getValue().toByteArray());
		}
		oBlockHeader.setStateRoot(encApi.hexEnc(this.stateTrie.getRootHash()));
		oBlockHeader.setTxTrieRoot(encApi.hexEnc(oTransactionTrie.getRootHash()));
		// oBlockHeader.setReceiptTrieRoot(value)
		oBlockHeader.setBlockHash(encApi.hexEnc(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);

		// oBlockStorageDB.setLastBlock(oBlockEntity.build());
		// blockChainHelper.addBlock(oBlockEntity.build());
		blockChainHelper.stableBlock(oBlockEntity.build());
	}

	public AddBlockResponse ApplyBlock(ByteString bs) throws Exception {
		BlockEntity.Builder block = BlockEntity.newBuilder().mergeFrom(bs);
		return ApplyBlock(block);
	}

	public AddBlockResponse ApplyBlock(BlockEntity.Builder block) throws Exception {
		BlockEntity dbblock = blockChainHelper.getBlockByHash(block.getHeader().getBlockHash());
		if (dbblock != null) {
			AddBlockResponse.Builder oAddBlockResponse = AddBlockResponse.newBuilder();
			oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
			oAddBlockResponse.setWantNumber(oAddBlockResponse.getCurrentNumber());
			return oAddBlockResponse.build();
		}
		return oProcessorManager.getProcessor(block.getVersion()).ApplyBlock(block);
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
		OValue oOValue = dao.getTxsDao().get(oEntityHelper.byteKey2OKey(txHash)).get();
		if (oOValue != null && oOValue.getExtdata() != null) {
			return blockChainHelper.getBlockByNumber(Long.parseLong(oOValue.getSecondKey()));
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
