package org.brewchain.account.core;

import java.util.Date;
import java.util.LinkedList;
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
import org.brewchain.account.gens.Block.BlockHeader;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

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

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	@ActorRequire(name = "WaitBlock_HashMapDB", scope = "global")
	WaitBlockHashMapDB oPendingHashMapDB;

	// @ActorRequire(name = "Block_StorageDB", scope = "global")
	// BlockStorageDB oBlockStorageDB;

	@ActorRequire(name = "Block_Cache_DLL", scope = "global")
	DoubleLinkedList<byte[]> blockCache;

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

		// 获取本节点的最后一块Block
		BlockEntity.Builder oBestBlockEntity = blockChainHelper.GetBestBlock();
		BlockHeader.Builder oBestBlockHeader = oBestBlockEntity.getHeader().toBuilder();

		// 构造Block Header
		oBlockHeader.setCoinbase(ByteString.copyFrom(coinBase));
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
		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);

		return oBlockEntity;
	}

	/**
	 * 创建创世块
	 * 
	 * @param txs
	 * @param extraData
	 */
	public void CreateGenesisBlock(LinkedList<MultiTransaction> txs, byte[] extraData) {
		BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();
		BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
		BlockBody.Builder oBlockBody = BlockBody.newBuilder();

		// 构造Block Header
		oBlockHeader.setCoinbase(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
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
		blockChainHelper.appendBlock(oBlockEntity.build());
	}

	/**
	 * 执行区块。比较交易完整性，执行交易。
	 * 
	 * @param oBlockEntity
	 * @throws Exception
	 */
	public void ApplyBlock(BlockEntity oBlockEntity) throws Exception {
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();

		// 上一个区块是否存在
		BlockEntity oParentBlock = getBlock(oBlockHeader.getParentHash().toByteArray()).build();
		// 上一个区块的所以是否一致
		if (oParentBlock.getHeader().getNumber() + 1 != oBlockHeader.getNumber()) {
			throw new Exception(String.format("区块 %s 的索引 %s 与父区块的索引 %s 不一致",
					encApi.hexEnc(oBlockHeader.getBlockHash().toByteArray()), oBlockHeader.getNumber(),
					oParentBlock.getHeader().getNumber()));
		}

		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		TrieImpl oTrieImpl = new TrieImpl();
		// 校验交易完整性
		for (ByteString txHash : oBlockHeader.getTxHashsList()) {
			// 交易必须都存在
			MultiTransaction oMultiTransaction = transactionHelper.GetTransaction(txHash.toByteArray());
			txs.add(oMultiTransaction);
			// 验证交易是否被篡改
			// 1. 重新Hash，比对交易Hash

			MultiTransaction.Builder oReHashMultiTransaction = oMultiTransaction.toBuilder();
			byte[] newHash = encApi.sha256Encode(oReHashMultiTransaction.getTxBody().toByteArray());
			if (!encApi.hexEnc(newHash).equals(encApi.hexEnc(oMultiTransaction.getTxHash().toByteArray()))) {
				// encApi.sha256Encode(oMultiTransaction.setTxHash(ByteString.EMPTY).build().toByteArray());
				log.debug(String.format("--> %s %s", encApi.hexEnc(oMultiTransaction.getTxHash().toByteArray()),
						oMultiTransaction));

				throw new Exception(String.format("交易Hash %s 与 %s 不一致", encApi.hexEnc(txHash.toByteArray()),
						encApi.hexEnc(newHash)));
			}
			// 2. 重构MPT Trie，比对RootHash
			oTrieImpl.put(oMultiTransaction.getTxHash().toByteArray(), oMultiTransaction.toByteArray());
		}
		if (!FastByteComparisons.equal(oBlockEntity.getHeader().getTxTrieRoot().toByteArray(),
				oTrieImpl.getRootHash())) {
			throw new Exception(String.format("交易根 %s 与 %s 不一致",
					encApi.hexEnc(oBlockEntity.getHeader().getTxTrieRoot().toByteArray()),
					encApi.hexEnc(oTrieImpl.getRootHash())));
		}

		// 执行交易
		transactionHelper.ExecuteTransaction(txs);

		blockChainHelper.appendBlock(oBlockEntity);
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
}
