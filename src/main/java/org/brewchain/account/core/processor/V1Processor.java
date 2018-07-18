package org.brewchain.account.core.processor;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.actuator.iTransactionActuator;
import org.brewchain.account.core.store.BlockStoreSummary;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.gens.Blockimpl.AddBlockResponse;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.core.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockBody;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Block.BlockHeader;
import org.brewchain.evmapi.gens.Block.BlockMiner;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Instantiate(name = "V1_Processor")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class V1Processor implements IProcessor, ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;

	@Override
	public Map<String, ByteString> ExecuteTransaction(LinkedList<MultiTransaction> oMultiTransactions,
			BlockEntity currentBlock) throws Exception {

		Map<String, ByteString> results = new LinkedHashMap<>();
		for (MultiTransaction oTransaction : oMultiTransactions) {
			log.debug("exec transaction hash::" + oTransaction.getTxHash());
			iTransactionActuator oiTransactionActuator = transactionHelper
					.getActuator(oTransaction.getTxBody().getType(), currentBlock);

			try {
				Map<String, Account.Builder> accounts = transactionHelper
						.getTransactionAccounts(oTransaction.toBuilder());
				oiTransactionActuator.onPrepareExecute(oTransaction, accounts);
				ByteString result = oiTransactionActuator.onExecute(oTransaction, accounts);

				Iterator<String> iterator = accounts.keySet().iterator();
				while (iterator.hasNext()) {
					String key = iterator.next();
					AccountValue value = accounts.get(key).getValue();
					this.stateTrie.put(encApi.hexDec(key), value.toByteArray());
				}
				oAccountHelper.BatchPutAccounts(accounts);
				oiTransactionActuator.onExecuteDone(oTransaction, result);
				results.put(oTransaction.getTxHash(), result);
			} catch (Exception e) {
				e.printStackTrace();
				oiTransactionActuator.onExecuteError(oTransaction, ByteString.copyFromUtf8(e.getMessage()));
				results.put(oTransaction.getTxHash(), ByteString.copyFromUtf8(e.getMessage()));
				// throw e;
				log.error("error on exec tx::" + e.getMessage(), e);
			}
		}
		return results;
		// oStateTrie.flush();
		// return oStateTrie.getRootHash();
	}

	@Override
	public void applyReward(BlockEntity oCurrentBlock) throws Exception {
		accountHelper.addTokenBalance(ByteString.copyFrom(encApi.hexDec(oCurrentBlock.getMiner().getAddress())), "CWS",
				new BigInteger(String.valueOf(oCurrentBlock.getMiner().getReward())));
	}

	@Override
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
		// oBlockHeader.setReward(bloc);
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
		oBlockMiner.setReward(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(blockChainConfig.getMinerReward())));
		// oBlockMiner.setAddress(value);

		oBlockHeader.setTxTrieRoot(encApi.hexEnc(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(encApi.hexEnc(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);
		oBlockEntity.setMiner(oBlockMiner);
		oBlockEntity.setVersion(blockChainConfig.getAccountVersion());

		BlockStoreSummary oSummary = blockChainHelper.addBlock(oBlockEntity.build());
		switch (oSummary.getBehavior()) {
		case APPLY:
			this.stateTrie.setRoot(encApi.hexDec(oBestBlockHeader.getStateRoot()));
			// processBlock(oBlockEntity);
			processBlock(oBlockEntity);
			// oBlockEntity
			// .setHeader(oBlockEntity.getHeaderBuilder().setStateRoot(oBlockEntity.getHeader().getStateRoot()));
			blockChainHelper.connectBlock(oBlockEntity.build());

			log.info(String.format("LOGFILTER %s %s %s %s 执行区块[%s]", KeyConstant.node.getoAccount().getAddress(),
					"account", "apply", "block", oBlockEntity.getHeader().getBlockHash()));

			log.debug("new block, number::" + oBlockEntity.getHeader().getNumber() + " hash::"
					+ oBlockEntity.getHeader().getBlockHash() + " parent::" + oBlockEntity.getHeader().getParentHash()
					+ " tx::" + oBlockEntity.getHeader().getTxTrieRoot() + " state::"
					+ oBlockEntity.getHeader().getStateRoot() + " receipt::"
					+ oBlockEntity.getHeader().getReceiptTrieRoot() + " bcuid::" + oBlockMiner.getBcuid() + " address::"
					+ oBlockMiner.getAddress());

			return oBlockEntity;
		default:
			return null;
		}
	}

	private synchronized void processBlock(BlockEntity.Builder oBlockEntity) throws Exception {
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		CacheTrie oTrieImpl = new CacheTrie();

		BlockBody.Builder bb = oBlockEntity.getBody().toBuilder();
		for (String txHash : oBlockHeader.getTxHashsList()) {
			transactionHelper.removeWaitBlockTx(txHash);
			MultiTransaction oMultiTransaction = transactionHelper.GetTransaction(txHash);

			log.debug("Thread Transaction Test ==> exec hash::" + txHash + " from::"
					+ encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray()) + " nonce::"
					+ oMultiTransaction.getTxBody().getInputs(0).getNonce() + " to::"
					+ encApi.hexEnc(oMultiTransaction.getTxBody().getOutputs(0).getAddress().toByteArray()));

			oTrieImpl.put(encApi.hexDec(oMultiTransaction.getTxHash()),
					transactionHelper.getTransactionContent(oMultiTransaction));
			bb.addTxs(oMultiTransaction);
			if (oMultiTransaction.getStatus() == null || oMultiTransaction.getStatus().isEmpty()) {
				txs.add(oMultiTransaction);
			}
			oMultiTransaction = null;
		}
		if (!oBlockEntity.getHeader().getTxTrieRoot().equals(encApi.hexEnc(oTrieImpl.getRootHash()))) {
			throw new Exception(String.format("transaction trie root hash %s not equal %s",
					oBlockEntity.getHeader().getTxTrieRoot(), encApi.hexEnc(oTrieImpl.getRootHash())));
		}
		oBlockEntity.setBody(bb);
		Map<String, ByteString> results = ExecuteTransaction(txs, oBlockEntity.build());
		BlockHeader.Builder header = oBlockEntity.getHeaderBuilder();

		CacheTrie receiptTrie = new CacheTrie();
		Iterator<String> iter = results.keySet().iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			receiptTrie.put(encApi.hexDec(key), results.get(key).toByteArray());
		}
		if (results.size() > 0) {
			header.setReceiptTrieRoot(encApi.hexEnc(receiptTrie.getRootHash()));
		}

		// reward
		accountHelper.addTokenBalance(ByteString.copyFrom(encApi.hexDec(oBlockEntity.getMiner().getAddress())), "CWS",
				ByteUtil.bytesToBigInteger(oBlockEntity.getMiner().getReward().toByteArray()));

		header.setStateRoot(encApi.hexEnc(this.stateTrie.getRootHash()));
		oBlockEntity.setHeader(header);
	}

	@Override
	public synchronized AddBlockResponse ApplyBlock(BlockEntity oBlockEntity) {
		BlockEntity.Builder applyBlock = oBlockEntity.toBuilder();

		AddBlockResponse.Builder oAddBlockResponse = AddBlockResponse.newBuilder();
		log.debug("receive block number::" + applyBlock.getHeader().getNumber() + " hash::"
				+ oBlockEntity.getHeader().getBlockHash() + " parent::" + applyBlock.getHeader().getParentHash()
				+ " stateroot::" + applyBlock.getHeader().getStateRoot());

		BlockStoreSummary oBlockStoreSummary = blockChainHelper.addBlock(applyBlock.build());
		while (oBlockStoreSummary.getBehavior() != BLOCK_BEHAVIOR.DONE) {
			switch (oBlockStoreSummary.getBehavior()) {
			case DROP:
				log.info("drop block number::" + applyBlock.getHeader().getNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case EXISTS_DROP:
				if (blockChainHelper.getLastBlockNumber() == applyBlock.getHeader().getNumber() - 1) {
					log.info("already exists, try to apply::" + applyBlock.getHeader().getNumber());
					blockChainHelper.reAddBlock(applyBlock.build());
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
				} else {
					log.info("already exists, drop it::" + applyBlock.getHeader().getNumber() + " last::"
							+ blockChainHelper.getLastBlockNumber());
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				}
				break;
			case EXISTS_PREV:
				log.info("block exists, but cannot find parent block number::" + applyBlock.getHeader().getNumber());
				try {
					BlockEntity pBlockEntity = blockChainHelper.getBlockByHash(applyBlock.getHeader().getParentHash());
					if (pBlockEntity != null) {
						log.debug("find in local cache number::" + pBlockEntity.getHeader().getBlockHash());
						applyBlock = pBlockEntity.toBuilder();
						oBlockStoreSummary = blockChainHelper.addBlock(applyBlock.build());
					} else {
						long rollBackNumber = applyBlock.getHeader().getNumber() > blockChainConfig
								.getDefaultRollBackCount()
										? applyBlock.getHeader().getNumber()
												- (blockChainConfig.getDefaultRollBackCount() + 1)
										: applyBlock.getHeader().getNumber() - 2;
						log.debug("need prev block number::" + rollBackNumber);
						oAddBlockResponse.setRetCode(-9);
						oAddBlockResponse.setCurrentNumber(rollBackNumber);
						oAddBlockResponse.setWantNumber(rollBackNumber + 1);
						blockChainHelper.rollbackTo(rollBackNumber);
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
					}
				} catch (Exception e1) {
					log.error("exception ", e1);
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
				}
				break;
			case CACHE:
				log.info("cache block number::" + applyBlock.getHeader().getNumber());
				oAddBlockResponse.setWantNumber(applyBlock.getHeader().getNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case APPLY:
				log.info("begin to apply block number::" + applyBlock.getHeader().getNumber());

				for (String txHash : applyBlock.getHeader().getTxHashsList()) {
					if (!transactionHelper.isExistsTransaction(txHash)) {
						oAddBlockResponse.addTxHashs(txHash);
					}
				}
				if (oAddBlockResponse.getTxHashsCount() > 0) {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
					oAddBlockResponse.setWantNumber(applyBlock.getHeader().getNumber());
					break;
				}

				BlockEntity parentBlock;
				try {
					parentBlock = blockChainHelper.getBlockByHash(applyBlock.getHeader().getParentHash());
					this.stateTrie.setRoot(encApi.hexDec(parentBlock.getHeader().getStateRoot()));
					// processBlock(applyBlock);
					processBlock(applyBlock);

					log.debug("=====sync-> " + applyBlock.getHeader().getNumber() + " parent::"
							+ parentBlock.getHeader().getStateRoot() + " current::"
							+ oBlockEntity.getHeader().getStateRoot() + " exec::"
							+ applyBlock.getHeader().getStateRoot());

					if (!oBlockEntity.getHeader().getStateRoot().equals(applyBlock.getHeader().getStateRoot())
							|| !oBlockEntity.getHeader().getTxTrieRoot().equals(applyBlock.getHeader().getTxTrieRoot())
							|| !oBlockEntity.getHeader().getReceiptTrieRoot()
									.equals(applyBlock.getHeader().getReceiptTrieRoot())) {
						log.error("begin to roll back, stateRoot::" + oBlockEntity.getHeader().getStateRoot()
								+ " blockStateRoot::" + applyBlock.getHeader().getStateRoot());

						blockChainHelper.rollbackTo(applyBlock.getHeader().getNumber() - 1);
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
					} else {
						oBlockStoreSummary = blockChainHelper.connectBlock(applyBlock.build());
					}
				} catch (Exception e) {
					log.error(e.getMessage());
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
				}
				break;
			case APPLY_CHILD:
				applyBlock = blockChainHelper.getChildBlock(applyBlock.build()).toBuilder();
				log.info("ready to apply child block::" + applyBlock.getHeader().getBlockHash());
				oBlockStoreSummary = blockChainHelper.addBlock(applyBlock.build());
				break;
			case STORE:
			case DONE:
				log.info("apply done number::" + blockChainHelper.getLastBlockNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case ERROR:
				log.error("fail to apply block number::" + applyBlock.getHeader().getNumber());
				//
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			}
		}

		if (oAddBlockResponse.getCurrentNumber() == 0) {
			oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
		}

		if (oAddBlockResponse.getWantNumber() == 0) {
			oAddBlockResponse.setWantNumber(oAddBlockResponse.getCurrentNumber());
		}

		log.debug("return apply current::" + oAddBlockResponse.getCurrentNumber() + " retcode::"
				+ oAddBlockResponse.getRetCode() + " want::" + oAddBlockResponse.getWantNumber() + " summary::"
				+ oBlockStoreSummary.getBehavior().name());
		return oAddBlockResponse.build();
	}
}