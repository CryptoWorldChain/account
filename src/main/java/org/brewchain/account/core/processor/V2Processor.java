package org.brewchain.account.core.processor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.bean.HashPair;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.actuator.iTransactionActuator;
import org.brewchain.account.core.mis.MultiTransactionSeparator;
import org.brewchain.account.core.store.BlockStoreSummary;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.exception.BlockStateTrieRuntimeException;
import org.brewchain.account.gens.Blockimpl.AddBlockResponse;
import org.brewchain.account.sample.TransactionLoadTestExecImpl;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.core.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Block.BlockBody;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Block.BlockHeader;
import org.brewchain.evmapi.gens.Block.BlockMiner;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.rcvm.utils.RLP;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.AllArgsConstructor;
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
	MultiTransactionSeparator mts = new MultiTransactionSeparator();

	// @ActorRequire(name = "LoadTester", scope = "global")
	// TransactionLoadTestExecImpl loadTester;

	public synchronized Map<String, ByteString> ExecuteTransaction(MultiTransaction[] oMultiTransactions,
			BlockEntity currentBlock, Map<String, Account.Builder> accounts) throws Exception {

		Map<String, ByteString> results = new ConcurrentHashMap<>();
		mts.reset();

		CountDownLatch cdl = new CountDownLatch(oMultiTransactions.length);

		// long start = System.currentTimeMillis();
		// String content = mts.getBucketInfo();

		for (int i = 0; i < mts.getBucketSize(); i++) {
			this.stateTrie.getExecutor().submit(new MisV2TransactionRunner(mts.getTxnQueue(i), transactionHelper,
					currentBlock, accounts, results, cdl));
		}
		mts.doClearing(oMultiTransactions, null);
		cdl.await();

		// log.error("====> ExecuteTransaction.clearing:" + content + ",cost=" +
		// (System.currentTimeMillis() - start));

		// log.debug("--:cdlwaitup" + cdl.getCount());
		oAccountHelper.BatchPutAccounts(accounts);
		return results;
	}

	@Override
	public synchronized Map<String, ByteString> ExecuteTransaction(List<MultiTransaction> oMultiTransactions,
			BlockEntity currentBlock) throws Exception {

		Map<String, ByteString> results = new HashMap<>();

		Map<Integer, iTransactionActuator> actorByType = new HashMap<>();
		// Map<String, Account.Builder> accounts = new HashMap<>();
		for (MultiTransaction oTransaction : oMultiTransactions) {
			iTransactionActuator oiTransactionActuator = actorByType.get(oTransaction.getTxBody().getType());
			if (oiTransactionActuator == null) {
				oiTransactionActuator = transactionHelper.getActuator(oTransaction.getTxBody().getType(), currentBlock);
				actorByType.put(oTransaction.getTxBody().getType(), oiTransactionActuator);
			} else {
				transactionHelper.resetActuator(oiTransactionActuator, currentBlock);
			}

			try {

				// Map<String, Account.Builder> accounts =
				// transactionHelper.getTransactionAccounts(oTransaction);
				Map<String, Account.Builder> accounts = new HashMap<>();
				transactionHelper.merageTransactionAccounts(oTransaction.toBuilder(), accounts);
				oiTransactionActuator.onPrepareExecute(oTransaction, accounts);
				ByteString result = oiTransactionActuator.onExecute(oTransaction, accounts);

				// Iterator<String> iterator = accounts.keySet().iterator();
				// while (iterator.hasNext()) {
				// String key = iterator.next();
				// AccountValue value = accounts.get(key).getValue();
				// this.stateTrie.put(encApi.hexDec(key), value.toByteArray());
				// }
				oiTransactionActuator.onExecuteDone(oTransaction, currentBlock, result);

				results.put(oTransaction.getTxHash(), result);
				oAccountHelper.BatchPutAccounts(accounts);
			} catch (Exception e) {
				log.error("block " + currentBlock.getHeader().getBlockHash() + " exec transaction hash::"
						+ oTransaction.getTxHash() + " error::" + e.getMessage());

				try {
					oiTransactionActuator.onExecuteError(oTransaction, currentBlock,
							ByteString.copyFromUtf8(e.getMessage() == null ? "unknown exception" : e.getMessage()));
					results.put(oTransaction.getTxHash(),
							ByteString.copyFromUtf8(e.getMessage() == null ? "unknown exception" : e.getMessage()));
				} catch (Exception e1) {
					log.error("onexec errro:" + e1.getMessage(), e1);
				}
			}
		}

		// oAccountHelper.BatchPutAccounts(accounts);
		return results;
	}

	@Override
	public void applyReward(BlockEntity.Builder oCurrentBlock) throws Exception {

		Account oAccount = accountHelper.addBalance(
				ByteString.copyFrom(encApi.hexDec(oCurrentBlock.getMiner().getAddress())),
				ByteUtil.bytesToBigInteger(oCurrentBlock.getMiner().getReward().toByteArray()));

		accountHelper.putAccountValue(oAccount.getAddress(), oAccount.getValue());
	}

	@Override
	public synchronized BlockEntity.Builder CreateNewBlock(List<MultiTransaction> txs, String extraData, String term)
			throws Exception {
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
		}
		oBlockMiner.setAddress(encApi.hexEnc(KeyConstant.node.getoAccount().getAddress().toByteArray()));
		oBlockMiner.setNode(KeyConstant.node.getNode());
		oBlockMiner.setBcuid(KeyConstant.node.getBcuid());
		oBlockMiner.setTermuid(term);

		oBlockMiner.setReward(ByteString.copyFrom(
				ByteUtil.bigIntegerToBytes(blockChainConfig.getMinerReward().multiply(new BigInteger(String.valueOf(
						Math.max(blockChainConfig.getBlockEpochSecond(), blockChainConfig.getBlockEpochMSecond())))))));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);
		oBlockEntity.setMiner(oBlockMiner);
		oBlockEntity.setVersion(blockChainConfig.getAccountVersion());

		try {
			AddBlockResponse.Builder oAddBlockResponse = AddBlockResponse.newBuilder();
			processBlock(oBlockEntity, oBestBlockEntity, oAddBlockResponse, txs);
			if (oAddBlockResponse.getTxHashsCount() > 0) {
				log.error("must sync tx first, need count::" + oAddBlockResponse.getTxHashsCount());
				return null;
			}
			byte[] blockContent = org.brewchain.account.util.ByteUtil.appendBytes(
					oBlockEntity.getHeaderBuilder().clearBlockHash().build().toByteArray(),
					oBlockMiner.build().toByteArray());
			oBlockEntity.setHeader(
					oBlockEntity.getHeaderBuilder().setBlockHash(encApi.hexEnc(encApi.sha256Encode(blockContent))));

			BlockStoreSummary oSummary = blockChainHelper.addBlock(oBlockEntity.build());
			switch (oSummary.getBehavior()) {
			case APPLY:
				long start = System.currentTimeMillis();
				blockChainHelper.connectBlock(oBlockEntity.build());

				log.info("new block, number::" + oBlockEntity.getHeader().getNumber() + " hash::"
						+ oBlockEntity.getHeader().getBlockHash() + " parent::"
						+ oBlockEntity.getHeader().getParentHash() + " tx::" + oBlockEntity.getHeader().getTxTrieRoot()
						+ " state::" + oBlockEntity.getHeader().getStateRoot() + " receipt::"
						+ oBlockEntity.getHeader().getReceiptTrieRoot() + " bcuid::" + oBlockMiner.getBcuid()
						+ " address::" + oBlockMiner.getAddress() + " headerTx::"
						+ oBlockEntity.getHeader().getTxHashsCount() + " bodyTx::"
						+ oBlockEntity.getBody().getTxsCount());

				return oBlockEntity;
			default:
				return null;
			}
		} catch (BlockStateTrieRuntimeException e) {
			log.error("block need to roll back::" + e.getMessage(), e);
			blockChainHelper.rollbackTo(oBestBlockHeader.getNumber() - 1);
		} catch (Exception e) {
			throw new Exception(e);
		}
		return null;
	}

	// public synchronized boolean preCheckBlockTx(BlockEntity.Builder
	// oBlockEntity) throws Exception {
	// for (String txHash : oBlockEntity.getHeader().getTxHashsList()) {
	// MultiTransaction oMultiTransaction =
	// transactionHelper.GetTransaction(txHash);
	// if (TXStatus.isProccessed(oMultiTransaction)) {
	// // 区块有些交易已经处理过的，要报错
	// return false;
	// }
	// }
	// return true;
	// }

	@AllArgsConstructor
	class ParalTxLoader implements Runnable {
		String txHash;
		int dstIndex;
		CountDownLatch cdl;
		MultiTransaction[] bb;
		byte[][] txTrieBB;
		Map<String, Account.Builder> accounts;
		ConcurrentLinkedQueue<String> missingHash;
		long blocknumber;
		AtomicBoolean justCheck;

		@Override
		public void run() {
			try {
				Thread.currentThread().setName("txloader-" + blocknumber);
				HashPair hp = transactionHelper.removeWaitingSendOrBlockTx(txHash);
				MultiTransaction oMultiTransaction = null;
				if (hp != null) {
					oMultiTransaction = hp.getTx();
				}
				if (oMultiTransaction == null) {
					oMultiTransaction = transactionHelper.GetTransaction(txHash);
				}
				if (oMultiTransaction == null || StringUtils.isBlank(oMultiTransaction.getTxHash())
						|| oMultiTransaction.getTxBody().getInputsCount() <= 0) {
					log.error("cannot load tx :txhash=" + txHash);
					// oAddBlockResponse.addTxHashs(txHash);
					if (StringUtils.isBlank(txHash)) {
						log.error("!!!Get Empty TXHash::" + txHash);
					} else {
						missingHash.add(txHash);
					}
					justCheck.set(true);
				} else {
					if (!justCheck.get()) {
						bb[dstIndex] = oMultiTransaction;
						txTrieBB[dstIndex] = transactionHelper.getTransactionContent(oMultiTransaction);
						transactionHelper.merageTransactionAccounts(oMultiTransaction, accounts);
					} else {
						log.error("cannot load tx accounts::txhash=" + txHash);
					}
				}

			} catch (Exception e) {
				log.error("error in loading tx:" + txHash + ",idx=" + dstIndex, e);
			} finally {
				cdl.countDown();
				Thread.currentThread().setName("statetrie-pool");
			}
		}

	}

	MultiTransaction emptytx = MultiTransaction.newBuilder().build();
	byte[] emptybb = new byte[1];

	public void waitUntilFlushFinished(long bh) {
		// int cc = 0;
		// while (this.stateTrie.getFlushexecutor().getActiveThreadCount() > 0)
		// {
		// if (cc++ % 100 == 0) {
		// // log.error("still waiting db flush... " +
		// // this.stateTrie.getFlushexecutor().getActiveThreadCount()
		// // + ",bh=" + bh);
		// }
		// try {
		// Thread.sleep(10);
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }
		// }
	}

	private boolean processBlock(BlockEntity.Builder oBlockEntity, BlockEntity oParentBlock,
			AddBlockResponse.Builder oAddBlockResponse, List<MultiTransaction> createdtxs) throws Exception {
		CacheTrie oTransactionTrie = new CacheTrie(this.encApi);
		CacheTrie oReceiptTrie = new CacheTrie(this.encApi);
		long start = System.currentTimeMillis();
		try {
			// if (oBlockEntity.getHeader().getNumber() ==
			// oParentBlock.getHeader().getNumber()) {
			// // log.error("dulipcate apply block parentHeight=currentHeight:"
			// // + oParentBlock.getHeader().getNumber()
			// // + ",txs=" + createdtxs);
			// return true;
			// }
			// if (StringUtils.equals(oParentBlock.getHeader().getBlockHash(),
			// oBlockEntity.getHeader().getBlockHash())) {
			// // log.error("dulipcate apply block parentHash=currentHash:" +
			// // oBlockEntity.getHeader().getBlockHash()
			// // + ",blocknumber=" + oParentBlock.getHeader().getNumber() +
			// // ",txs=" + createdtxs);
			// return true;
			// }
			BlockHeader.Builder oBlockHeader = oBlockEntity.getHeader().toBuilder();
			// LinkedList<MultiTransaction> txs = new LinkedList<>();

			// long start = System.currentTimeMillis();
			// this.stateTrie.getNodeCounter().set(0);

			if (oBlockEntity.getHeader().getNumber() >= 1 && !Arrays
					.equals(encApi.hexDec(oParentBlock.getHeader().getStateRoot()), this.stateTrie.getRootHash())) {
				// log.error("reset state root=stateTirRoothash=" +
				// encApi.hexEnc(this.stateTrie.getRootHash())
				// + ",parentHash=" + oParentBlock.getHeader().getStateRoot() +
				// ",applyheight="
				// + oBlockEntity.getHeader().getNumber());
				this.stateTrie.clear();
				this.stateTrie.setRoot(encApi.hexDec(oParentBlock.getHeader().getStateRoot()));
				// waitUntilFlushFinished(oBlockEntity.getHeader().getNumber());
			}

			// this.stateTrie.clear();
			// log.error(" put statetrie root::" +
			// oParentBlock.getHeader().getStateRoot());
			// this.stateTrie.setRoot(encApi.hexDec(oParentBlock.getHeader().getStateRoot()));
			long waitflushend = System.currentTimeMillis();

			BlockBody.Builder bb = oBlockEntity.getBody().toBuilder();

			byte[][] txTrieBB = new byte[oBlockHeader.getTxHashsCount()][];
			MultiTransaction[] txs = new MultiTransaction[oBlockHeader.getTxHashsCount()];
			int i = 0;
			Map<String, Account.Builder> accounts = new ConcurrentHashMap<>(oBlockHeader.getTxHashsCount());
			if (createdtxs != null) {
				for (int dstIndex = 0; dstIndex < createdtxs.size(); dstIndex++) {
					MultiTransaction oMultiTransaction = createdtxs.get(dstIndex);
					txs[dstIndex] = oMultiTransaction;
					txTrieBB[dstIndex] = transactionHelper.getTransactionContent(oMultiTransaction);
					transactionHelper.merageTransactionAccounts(oMultiTransaction, accounts);
				}
			} else {
				CountDownLatch cdl = new CountDownLatch(oBlockHeader.getTxHashsCount());
				AtomicBoolean justCheck = new AtomicBoolean(false);
				ConcurrentLinkedQueue<String> missingHash = new ConcurrentLinkedQueue<>();
				for (String txHash : oBlockHeader.getTxHashsList()) {
					this.stateTrie.getExecutor().submit(new ParalTxLoader(txHash, i, cdl, txs, txTrieBB, accounts,
							missingHash, oBlockHeader.getNumber(), justCheck));
					i++;
				}

				cdl.await();
				if (!missingHash.isEmpty()) {
					String hash = missingHash.poll();
					while (hash != null) {
						oAddBlockResponse.addTxHashs(hash);
						hash = missingHash.poll();
					}
					return false;
				}
			}

			for (i = 0; i < oBlockHeader.getTxHashsCount(); i++) {
				bb.addTxs(txs[i]);
				oTransactionTrie.put(RLP.encodeInt(i), txTrieBB[i]);
			}

			transactionHelper.getDao().getStats().signalBlockTx(oBlockHeader.getTxHashsCount());
			oBlockEntity.setBody(bb);

			long loadtxend = System.currentTimeMillis();

			Map<String, ByteString> results = ExecuteTransaction(txs, oBlockEntity.build(), accounts);
			long execend = System.currentTimeMillis();

			BlockHeader.Builder header = oBlockEntity.getHeaderBuilder();

			Iterator<String> iter = results.keySet().iterator();
			List<String> keys = new ArrayList<>();
			while (iter.hasNext()) {
				String key = iter.next();
				keys.add(key);
			}
			Collections.sort(keys);
			for (String key : keys) {
				oReceiptTrie.put(RLP.encodeInt(i), results.get(key).toByteArray());
			}

			applyReward(oBlockEntity);

			header.setReceiptTrieRoot(encApi.hexEnc(
					oReceiptTrie.getRootHash() == null ? ByteUtil.EMPTY_BYTE_ARRAY : oReceiptTrie.getRootHash()));
			header.setTxTrieRoot(encApi.hexEnc(oTransactionTrie.getRootHash() == null ? ByteUtil.EMPTY_BYTE_ARRAY
					: oTransactionTrie.getRootHash()));
			long starttriecode = System.currentTimeMillis();
			header.setStateRoot(encApi.hexEnc(this.stateTrie.getRootHash()));

			// this.stateTrie.getNodeCounter().get()
			log.error("calc trie total cost:" + (System.currentTimeMillis() - start) + ",blocknumber="
					+ header.getNumber() + ",txcount=" + oBlockHeader.getTxHashsCount() + ",encodecc=" + "" + ",cost[f="
					+ (waitflushend - start) + ",l=" + (loadtxend - waitflushend) + ",e=" + (execend - loadtxend)
					+ ",a=" + (starttriecode - execend) + ",t=" + (System.currentTimeMillis() - starttriecode) + "]");
			oBlockEntity.setHeader(header.build());
			if (StringUtils.isBlank(oBlockEntity.getHeader().getStateRoot())) {
				log.error("get empty stateroot==");
			}

			// this.stateTrie.clear();
		} finally {
			oTransactionTrie.clear();
			oTransactionTrie = null;
			oReceiptTrie.clear();
			oReceiptTrie = null;
		}

		return true;
	}

	@Override
	public synchronized AddBlockResponse ApplyBlock(BlockEntity.Builder oBlockEntity) {
		Thread.currentThread().setName("v2Apply-" + oBlockEntity.getHeader().getNumber());
		BlockEntity.Builder applyBlock = oBlockEntity.clone();
		long start = System.currentTimeMillis();
		AddBlockResponse.Builder oAddBlockResponse = AddBlockResponse.newBuilder();

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
				BlockStoreSummary oBlockStoreSummary = blockChainHelper.addBlock(oBlockEntity.build());
				while (oBlockStoreSummary.getBehavior() != BLOCK_BEHAVIOR.DONE) {
					switch (oBlockStoreSummary.getBehavior()) {
					case DROP:
						log.error("drop block number::" + applyBlock.getHeader().getNumber());
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					case EXISTS_DROP:
						log.error("already exists, try to apply::" + applyBlock.getHeader().getNumber());
						oBlockStoreSummary.setBehavior(blockChainHelper.tryAddBlock(applyBlock.build()).getBehavior());
						break;
					case EXISTS_PREV:
						log.error("block exists, but cannot find parent block number::"
								+ applyBlock.getHeader().getNumber());
						try {
							long rollBackNumber = applyBlock.getHeader().getNumber() - 2;
							// log.debug("need prev block number::" +
							// rollBackNumber);
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
						log.error("cache block number::" + applyBlock.getHeader().getNumber());
						oAddBlockResponse.setWantNumber(applyBlock.getHeader().getNumber());
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					case APPLY:
						BlockEntity parentBlock;
						parentBlock = blockChainHelper.getBlockByHash(applyBlock.getHeader().getParentHash());
						processBlock(applyBlock, parentBlock, oAddBlockResponse, null);
						if (oAddBlockResponse.getTxHashsCount() > 0) {
							oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
							oAddBlockResponse.setWantNumber(applyBlock.getHeader().getNumber());
							log.error("must sync tx first, need count::" + oAddBlockResponse.getTxHashsCount());
							break;
						}
						if (!oBlockEntity.getHeader().getStateRoot().equals(applyBlock.getHeader().getStateRoot())
								|| !oBlockEntity.getHeader().getTxTrieRoot()
										.equals(applyBlock.getHeader().getTxTrieRoot())
								|| !oBlockEntity.getHeader().getReceiptTrieRoot()
										.equals(applyBlock.getHeader().getReceiptTrieRoot())) {
							log.error("begin to roll back, stateRoot::" + oBlockEntity.getHeader().getStateRoot()
									+ " blockStateRoot::" + applyBlock.getHeader().getStateRoot());

							transactionHelper.getDao().getStats().getRollBackBlockCount().incrementAndGet();
							transactionHelper.getDao().getStats().getRollBackTxCount().incrementAndGet();
							transactionHelper.getDao().getStats()
									.signalBlockTx(-applyBlock.getHeader().getTxHashsCount());
							blockChainHelper.rollbackTo(applyBlock.getHeader().getNumber() - 1);

							final BlockHeader.Builder bbh = oBlockHeader;
							this.stateTrie.getExecutor().submit(new Runnable() {
								@Override
								public void run() {
									for (String txHash : bbh.getTxHashsList()) {
										transactionHelper.getOConfirmMapDB().revalidate(txHash);
									}
								}
							});
							oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);

							// re
						} else {
							oBlockStoreSummary = blockChainHelper.connectBlock(applyBlock.build());
							this.stateTrie.getExecutor().submit(new Runnable() {

								@Override
								public void run() {
									transactionHelper.getOConfirmMapDB().clear();
								}
							});
						}
						break;
					case APPLY_CHILD:

						List<BlockEntity> childs = blockChainHelper.getChildBlock(applyBlock.build());
						// log.debug("find childs count::" + childs.size());
						for (BlockEntity blockEntity : childs) {
							applyBlock = blockEntity.toBuilder();
							log.error("ready to apply child block::" + applyBlock.getHeader().getBlockHash()
									+ " number::" + applyBlock.getHeader().getNumber());
							ApplyBlock(applyBlock);
						}
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					case STORE:
						log.error("apply done number::" + blockChainHelper.getLastBlockNumber());
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					case ERROR:
						log.error("fail to apply block number::" + applyBlock.getHeader().getNumber() + ":want="
								+ oAddBlockResponse.getWantNumber() + ",needTxHash="
								+ oAddBlockResponse.getTxHashsCount() + ",ApplyHash="
								+ applyBlock.getHeader().getBlockHash());
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
						break;
					}
				}
			}
		} catch (BlockStateTrieRuntimeException e) {
			log.error("block need to roll back " + e.getMessage(), e);
			blockChainHelper.rollbackTo(applyBlock.getHeader().getNumber() - 2);
		} catch (Exception e2) {
			log.error("error on validate block header::" + e2, e2);
		}

		if (oAddBlockResponse.getCurrentNumber() == 0) {
			oAddBlockResponse.setCurrentNumber(blockChainHelper.getLastBlockNumber());
		}

		if (oAddBlockResponse.getWantNumber() == 0) {
			oAddBlockResponse.setWantNumber(oAddBlockResponse.getCurrentNumber());
		}

		log.error("====> end apply block number::" + oBlockEntity.getHeader().getNumber() + " cost::"
				+ (System.currentTimeMillis() - start) + " txs::" + oBlockEntity.getHeader().getTxHashsCount());
		blockChainHelper.getDao().getStats().setCurBlockID(oBlockEntity.getHeader().getNumber());

		return oAddBlockResponse.build();
	}
}
