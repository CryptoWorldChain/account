package org.brewchain.account.core.processor;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.bean.HashPair;
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
import org.brewchain.rcvm.utils.RLP;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Instantiate(name = "V2_Processor")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class V2Processor implements IProcessor, ActorService {
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
				oiTransactionActuator.onExecuteError(oTransaction,
						ByteString.copyFromUtf8(e.getMessage() == null ? "unknown exception" : e.getMessage()));

				results.put(oTransaction.getTxHash(),
						ByteString.copyFromUtf8(e.getMessage() == null ? "unknown exception" : e.getMessage()));
				log.error("block " + currentBlock.getHeader().getBlockHash() + " exec transaction hash::"
						+ oTransaction.getTxHash() + " error::" + e.getMessage());
			}
		}
		return results;
	}

	@Override
	public void applyReward(BlockEntity oCurrentBlock) throws Exception {
		accountHelper.addBalance(ByteString.copyFrom(encApi.hexDec(oCurrentBlock.getMiner().getAddress())),
				ByteUtil.bytesToBigInteger(oCurrentBlock.getMiner().getReward().toByteArray()));
	}

	@Override
	public BlockEntity.Builder CreateNewBlock(LinkedList<MultiTransaction> txs, String extraData) throws Exception {
		log.debug("call create new block miner::" + KeyConstant.node.getAddress());
		BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();
		BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
		BlockBody.Builder oBlockBody = BlockBody.newBuilder();
		BlockMiner.Builder oBlockMiner = BlockMiner.newBuilder();
		BlockEntity oBestBlockEntity = blockChainHelper.GetConnectBestBlock();
		if (oBestBlockEntity == null) {
			oBestBlockEntity = blockChainHelper.GetStableBestBlock();
		}
		BlockHeader oBestBlockHeader = oBestBlockEntity.getHeader();
		oBlockHeader.setParentHash(oBestBlockHeader.getBlockHash());

		long currentTimestamp = System.currentTimeMillis();
		oBlockHeader.setTimestamp(System.currentTimeMillis() == oBestBlockHeader.getTimestamp()
				? oBestBlockHeader.getTimestamp() + 1 : currentTimestamp);
		oBlockHeader.setNumber(oBestBlockHeader.getNumber() + 1);
		oBlockHeader.setExtraData(extraData);
		for (int i = 0; i < txs.size(); i++) {
			oBlockHeader.addTxHashs(txs.get(i).getTxHash());
			oBlockBody.addTxs(txs.get(i));
		}
		oBlockMiner.setAddress(encApi.hexEnc(KeyConstant.node.getoAccount().getAddress().toByteArray()));
		oBlockMiner.setNode(KeyConstant.node.getNode());
		oBlockMiner.setBcuid(KeyConstant.node.getBcuid());

		// cal reward
		oBlockMiner.setReward(ByteString.copyFrom(
				ByteUtil.bigIntegerToBytes(blockChainConfig.getMinerReward().multiply(new BigInteger(String.valueOf(
						Math.max(blockChainConfig.getBlockEpochSecond(), blockChainConfig.getBlockEpochMSecond())))))));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);
		oBlockEntity.setMiner(oBlockMiner);
		oBlockEntity.setVersion(blockChainConfig.getAccountVersion());

		this.stateTrie.setRoot(encApi.hexDec(oBestBlockHeader.getStateRoot()));
		processBlock(oBlockEntity);

		byte[] blockContent = org.brewchain.account.util.ByteUtil.appendBytes(
				oBlockEntity.getHeaderBuilder().clearBlockHash().build().toByteArray(),
				oBlockMiner.build().toByteArray());
		oBlockEntity.setHeader(
				oBlockEntity.getHeaderBuilder().setBlockHash(encApi.hexEnc(encApi.sha256Encode(blockContent))));

		BlockStoreSummary oSummary = blockChainHelper.addBlock(oBlockEntity.build());
		switch (oSummary.getBehavior()) {
		case APPLY:
			blockChainHelper.connectBlock(oBlockEntity.build());

			log.info(String.format("LOGFILTER %s %s %s %s 执行区块[%s]",
					encApi.hexEnc(KeyConstant.node.getoAccount().getAddress().toByteArray()), "account", "apply",
					"block", oBlockEntity.getHeader().getBlockHash()));

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
		CacheTrie oTransactionTrie = new CacheTrie(this.encApi);
		CacheTrie oReceiptTrie = new CacheTrie(this.encApi);

		BlockBody.Builder bb = oBlockEntity.getBody().toBuilder();
		int i = 0;
		for (String txHash : oBlockHeader.getTxHashsList()) {
			HashPair hp = transactionHelper.removeWaitBlockTx(txHash);
			MultiTransaction oMultiTransaction = null;
			if (hp != null) {
				oMultiTransaction = hp.getTx();
			}
			if (oMultiTransaction == null) {
				oMultiTransaction = transactionHelper.GetTransaction(txHash);
			}
			oTransactionTrie.put(RLP.encodeInt(i), transactionHelper.getTransactionContent(oMultiTransaction));
			bb.addTxs(oMultiTransaction);
			txs.add(oMultiTransaction);

			i++;
		}
		oBlockEntity.setBody(bb);
		log.error("====>  start number::" + oBlockEntity.getHeader().getNumber() + " exec tx::"
				+ System.currentTimeMillis()+",count="+i);
		Map<String, ByteString> results = ExecuteTransaction(txs, oBlockEntity.build());
		log.error("====>  end number::" + oBlockEntity.getHeader().getNumber() + " exec tx::"
				+ System.currentTimeMillis()+",count="+i);
		BlockHeader.Builder header = oBlockEntity.getHeaderBuilder();

		Iterator<String> iter = results.keySet().iterator();
		i = 0;
		while (iter.hasNext()) {
			String key = iter.next();
			oReceiptTrie.put(RLP.encodeInt(i), results.get(key).toByteArray());
			i++;
		}

		applyReward(oBlockEntity.build());

		header.setReceiptTrieRoot(encApi
				.hexEnc(oReceiptTrie.getRootHash() == null ? ByteUtil.EMPTY_BYTE_ARRAY : oReceiptTrie.getRootHash()));
		header.setTxTrieRoot(encApi.hexEnc(
				oTransactionTrie.getRootHash() == null ? ByteUtil.EMPTY_BYTE_ARRAY : oTransactionTrie.getRootHash()));
		log.error("====>  start number::" + oBlockEntity.getHeader().getNumber() + "get root::"
				+ System.currentTimeMillis());
		header.setStateRoot(encApi.hexEnc(this.stateTrie.getRootHash()));
		log.error("====>  end number::" + oBlockEntity.getHeader().getNumber() + "get root::"
				+ System.currentTimeMillis());
		oBlockEntity.setHeader(header);
	}

	@Override
	public synchronized AddBlockResponse ApplyBlock(BlockEntity oBlockEntity) {
		BlockEntity.Builder applyBlock = oBlockEntity.toBuilder();
		log.error("====> start apply block number:: " + oBlockEntity.getHeader().getNumber() + " stamp::"
				+ System.currentTimeMillis());
		AddBlockResponse.Builder oAddBlockResponse = AddBlockResponse.newBuilder();
		log.debug("receive block number::" + applyBlock.getHeader().getNumber() + " hash::"
				+ oBlockEntity.getHeader().getBlockHash() + " parent::" + applyBlock.getHeader().getParentHash()
				+ " stateroot::" + applyBlock.getHeader().getStateRoot() + " miner::"
				+ applyBlock.getMiner().getAddress());

		try {
			BlockHeader.Builder oBlockHeader = BlockHeader.parseFrom(oBlockEntity.getHeader().toByteArray())
					.toBuilder();
			oBlockHeader.clearBlockHash();

			byte[] blockContent = org.brewchain.account.util.ByteUtil.appendBytes(oBlockHeader.build().toByteArray(),
					oBlockEntity.getMiner().toByteArray());

			if (!oBlockEntity.getHeader().getBlockHash().equals(encApi.hexEnc(encApi.sha256Encode(blockContent)))) {
				log.warn("wrong block hash::" + oBlockEntity.getHeader().getBlockHash() + " need::"
						+ encApi.hexEnc(encApi.sha256Encode(blockContent)));
			} else {
				BlockStoreSummary oBlockStoreSummary = blockChainHelper.addBlock(applyBlock.build());
				while (oBlockStoreSummary.getBehavior() != BLOCK_BEHAVIOR.DONE) {
					switch (oBlockStoreSummary.getBehavior()) {
					case DROP:
						log.info("drop block number::" + applyBlock.getHeader().getNumber());
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					case EXISTS_DROP:
						log.info("already exists, try to apply::" + applyBlock.getHeader().getNumber());
						oBlockStoreSummary.setBehavior(blockChainHelper.tryAddBlock(applyBlock.build()).getBehavior());
						break;
					case EXISTS_PREV:
						log.info("block exists, but cannot find parent block number::"
								+ applyBlock.getHeader().getNumber());
						try {
							long rollBackNumber = applyBlock.getHeader().getNumber() - 2;
							log.debug("need prev block number::" + rollBackNumber);
							oAddBlockResponse.setRetCode(-9);
							oAddBlockResponse.setCurrentNumber(rollBackNumber);
							oAddBlockResponse.setWantNumber(rollBackNumber + 1);
							blockChainHelper.rollbackTo(rollBackNumber);
							oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
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
						for (String txHash : applyBlock.getHeader().getTxHashsList()) {
							if (!transactionHelper.isExistsWaitBlockTx(txHash)
									&& !transactionHelper.isExistsTransaction(txHash)) {
								oAddBlockResponse.addTxHashs(txHash);
							}
						}
						if (oAddBlockResponse.getTxHashsCount() > 0) {
							oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
							oAddBlockResponse.setWantNumber(applyBlock.getHeader().getNumber());
							break;
						}

						BlockEntity parentBlock;
						parentBlock = blockChainHelper.getBlockByHash(applyBlock.getHeader().getParentHash());

						this.stateTrie.setRoot(encApi.hexDec(parentBlock.getHeader().getStateRoot()));
						processBlock(applyBlock);

						log.debug("=====sync-> " + applyBlock.getHeader().getNumber() + " parent::"
								+ parentBlock.getHeader().getStateRoot() + " current::"
								+ oBlockEntity.getHeader().getStateRoot() + " exec::"
								+ applyBlock.getHeader().getStateRoot());

						if (!oBlockEntity.getHeader().getStateRoot().equals(applyBlock.getHeader().getStateRoot())
								|| !oBlockEntity.getHeader().getTxTrieRoot()
										.equals(applyBlock.getHeader().getTxTrieRoot())
								|| !oBlockEntity.getHeader().getReceiptTrieRoot()
										.equals(applyBlock.getHeader().getReceiptTrieRoot())) {
							log.error("begin to roll back, stateRoot::" + oBlockEntity.getHeader().getStateRoot()
									+ " blockStateRoot::" + applyBlock.getHeader().getStateRoot());

							blockChainHelper.rollbackTo(applyBlock.getHeader().getNumber() - 1);
							oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
						} else {
							oBlockStoreSummary = blockChainHelper.connectBlock(applyBlock.build());
						}
						break;
					case APPLY_CHILD:
						List<BlockEntity> childs = blockChainHelper.getChildBlock(applyBlock.build());
						log.debug("find childs count::" + childs.size());
						for (BlockEntity blockEntity : childs) {
							applyBlock = blockEntity.toBuilder();
							log.info("ready to apply child block::" + applyBlock.getHeader().getBlockHash()
									+ " number::" + applyBlock.getHeader().getNumber());
							ApplyBlock(blockEntity);
						}
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					case STORE:
						log.info("apply done number::" + blockChainHelper.getLastBlockNumber());
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					case ERROR:
						log.error("fail to apply block number::" + applyBlock.getHeader().getNumber());
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					}
				}
			}
		} catch (Exception e2) {
			log.error("error on validate block header::" + e2, e2);

		}

		if (oAddBlockResponse.getCurrentNumber() == 0) {
			oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
		}

		if (oAddBlockResponse.getWantNumber() == 0) {
			oAddBlockResponse.setWantNumber(oAddBlockResponse.getCurrentNumber());
		}

		log.error("====> end apply block number::" + oBlockEntity.getHeader().getNumber() + "  stamp::"
				+ System.currentTimeMillis());
		return oAddBlockResponse.build();
	}
}
