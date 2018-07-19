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
@Instantiate(name = "Default_Processor")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class DefaultProcessor implements IProcessor, ActorService {
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
		oBlockMiner.setReward(ByteString
				.copyFrom(ByteUtil.bigIntegerToBytes(blockChainConfig.getMinerReward())));
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

	private synchronized byte[] processBlock(BlockEntity.Builder oBlockEntity) throws Exception {
		BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
		LinkedList<MultiTransaction> txs = new LinkedList<MultiTransaction>();
		CacheTrie oTrieImpl = new CacheTrie();

		BlockBody.Builder bb = oBlockEntity.getBody().toBuilder();
		// 校验交易完整性
		for (String txHash : oBlockHeader.getTxHashsList()) {
			transactionHelper.removeWaitBlockTx(txHash);

			MultiTransaction oMultiTransaction = transactionHelper.GetTransaction(txHash);

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
		// 执行交易
		ExecuteTransaction(txs, oBlockEntity.build());
		// reward
		applyReward(oBlockEntity.build());

		byte[] stateRoot = this.stateTrie.getRootHash();
		return stateRoot;
	}

	@Override
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
				// oAddBlockResponse.setWantNumber(oBlockEntity.getHeader().getNumber());
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
						blockChainHelper.rollbackTo(oBlockEntity.getHeader().getNumber() - 2);
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
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
				oBlockEntity = blockChainHelper.getChildBlock(oBlockEntity).get(0);
				oBlockStoreSummary = blockChainHelper.addBlock(oBlockEntity);
				break;
			case STORE:
			case DONE:
				log.info("apply done number::" + blockChainHelper.getLastBlockNumber());
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				break;
			case ERROR:
				log.error("fail to apply block number::" + oBlockEntity.getHeader().getNumber());
				blockChainHelper.rollbackTo(oBlockEntity.getHeader().getNumber() - 1);
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
