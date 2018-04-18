package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.BlockException;
import org.brewchain.account.trie.TrieImpl;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OValue;
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
import onight.osgi.annotation.iPojoBean;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
//
//@iPojoBean
//@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
//

@NActorProvider
@Instantiate(name = "Block_Helper")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class BlockHelper implements ActorService {
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	@ActorRequire(name = "WaitBlock_HashMapDB", scope = "global")
	WaitBlockHashMapDB oPendingHashMapDB;

	@ActorRequire(name = "Block_StorageDB", scope = "global")
	BlockStorageDB oBlockStorageDB;

	public BlockEntity.Builder CreateNewBlock(byte[] extraData, byte[] coinBase) throws InvalidProtocolBufferException {
		return CreateNewBlock(KeyConstant.DEFAULT_BLOCK_TX_COUNT, extraData, coinBase);
	}

	public BlockEntity.Builder CreateNewBlock(int txCount, byte[] extraData, byte[] coinBase) throws InvalidProtocolBufferException {
		return CreateNewBlock(transactionHelper.getWaitBlockTx(txCount), extraData, coinBase);
	}

	public BlockEntity.Builder CreateNewBlock(LinkedList<MultiTransaction> txs, byte[] extraData, byte[] coinBase) {
		BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();
		BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
		BlockBody.Builder oBlockBody = BlockBody.newBuilder();

		// 获取本节点的最后一块Block
		BlockEntity.Builder oBestBlockEntity = GetBestBlock();
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
			oBlockBody.addTxs(i, txs.get(i));
			oTrieImpl.put(txs.get(i).getTxHash().toByteArray(), txs.get(i).toByteArray());
		}
		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);

		return oBlockEntity;
	}

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
			oBlockHeader.setTxHashs(i, txs.get(i).getTxHash());
			oBlockBody.setTxs(i, txs.get(i));
			oTrieImpl.put(txs.get(i).getTxHash().toByteArray(), txs.get(i).toByteArray());
		}
		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);

		oBlockStorageDB.setLastBlock(oBlockEntity.build());
		dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(oBlockEntity.getHeader().getBlockHash()),
				OEntityBuilder.byteValue2OValue(oBlockEntity.build().toByteArray()));
	}

	public void ApplyBlock(BlockEntity oBlockEntity) throws Exception {
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		LinkedList<MultiTransaction.Builder> txs = new LinkedList<MultiTransaction.Builder>();
		TrieImpl oTrieImpl = new TrieImpl();
		// 校验交易完整性
		for (ByteString txHash : oBlockHeader.getTxHashsList()) {
			// 交易必须都存在
			MultiTransaction.Builder oMultiTransaction = transactionHelper.GetTransaction(txHash.toByteArray())
					.toBuilder();
			txs.add(oMultiTransaction);
			// 验证交易是否被篡改
			// 1. 重新Hash，比对交易Hash
			byte[] newHash = transactionHelper.ReHashTransaction(oMultiTransaction);
			if (!FastByteComparisons.equal(newHash, txHash.toByteArray())) {
				throw new Exception(String.format("交易Hash %s 与 %s 不一致", Hex.toHexString(txHash.toByteArray()),
						Hex.toHexString(newHash)));
			}
			// 2. 重构MPT Trie，比对RootHash
			oTrieImpl.put(oMultiTransaction.getTxHash().toByteArray(), oMultiTransaction.build().toByteArray());
		}
		if (!FastByteComparisons.equal(oBlockEntity.getHeader().getTxTrieRoot().toByteArray(),
				oTrieImpl.getRootHash())) {
			throw new Exception(String.format("交易根 %s 与 %s 不一致",
					Hex.toHexString(oBlockEntity.getHeader().getTxTrieRoot().toByteArray()),
					Hex.toHexString(oTrieImpl.getRootHash())));
		}

		// 执行交易
		transactionHelper.ExecuteTransaction(txs);

		// 缓存
		oBlockStorageDB.setLastBlock(oBlockEntity);

		// 持久化
		dao.getBlockDao().put(OEntityBuilder.byteKey2OKey(oBlockHeader.getBlockHash()),
				OEntityBuilder.byteValue2OValue(oBlockEntity.toByteArray()));

		// 发送奖励

	}

	public BlockEntity.Builder GetBestBlock() {
		return oBlockStorageDB.getLastBlock().toBuilder();
	}
}
