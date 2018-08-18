package org.brewchain.account.core.store;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OPair;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "BlockStore_Helper")
@Slf4j
@Data
public class BlockStore implements ActorService {
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockStore_Stable", scope = "global")
	BlockStableStore stableStore;
	@ActorRequire(name = "BlockStore_UnStable", scope = "global")
	BlockUnStableStore unStableStore;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;

	private long maxReceiveNumber = -1;
	private long maxConnectNumber = -1;
	private long maxStableNumber = -1;
	private BlockEntity maxReceiveBlock = null;
	private BlockEntity maxConnectBlock = null;
	private BlockEntity maxStableBlock = null;

	public void init() throws Exception {
		String lastConnectBlockHash = null;
		String lastStableBlockHash = null;

		OValue oStableMax = dao.getBlockDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get();
		if (oStableMax == null || oStableMax.getExtdata() == null || oStableMax.getExtdata().equals(ByteString.EMPTY)) {
			log.warn(String.format("not found last stable block, start empty node"));
			return;
		}
		lastStableBlockHash = encApi.hexEnc(oStableMax.getExtdata().toByteArray());

		OValue oUnStableMax = dao.getBlockDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK)).get();
		if (oUnStableMax == null || oUnStableMax.getExtdata() == null
				|| oUnStableMax.getExtdata().equals(ByteString.EMPTY)) {
			log.warn(String.format("not found last connect block, use stable block"));
			lastConnectBlockHash = encApi.hexEnc(oStableMax.getExtdata().toByteArray());
		} else {
			lastConnectBlockHash = encApi.hexEnc(oUnStableMax.getExtdata().toByteArray());
		}

		BlockEntity oLastConnectBlockEntity = getBlockByHash(lastConnectBlockHash);
		if (oLastConnectBlockEntity == null) {
			log.error(String.format("exists last stable block hash, but last block not exists, start empty node"));
		} else {
			if (oLastConnectBlockEntity.getHeader().getNumber() == 0) {
				log.debug("load block into stable cache number::" + oLastConnectBlockEntity.getHeader().getNumber()
						+ " hash::" + oLastConnectBlockEntity.getHeader().getBlockHash() + " stateroot::"
						+ oLastConnectBlockEntity.getHeader().getStateRoot());
				stableStore.add(oLastConnectBlockEntity);
				if (maxStableNumber < oLastConnectBlockEntity.getHeader().getNumber()) {
					maxStableNumber = oLastConnectBlockEntity.getHeader().getNumber();
					maxStableBlock = oLastConnectBlockEntity;
				}
			} else {
				long blockNumber = oLastConnectBlockEntity.getHeader().getNumber();

				int c = 0;
				boolean isStable = false;
				while (blockNumber > 0 && c < (blockChainConfig.getStableBlocks() + KeyConstant.CACHE_SIZE)) {
					c += 1;
					List<BlockEntity> loopBlocks = getBlocksByNumber(blockNumber);
					for (BlockEntity loopBlockEntity : loopBlocks) {
						if (blockNumber != loopBlockEntity.getHeader().getNumber()) {
							throw new Exception(String.format("respect block number %s ,get block number %s",
									blockNumber, loopBlockEntity.getHeader().getNumber()));
						}
						if (loopBlockEntity.getHeader().getBlockHash().equals(lastStableBlockHash)) {
							isStable = true;
						}

						if (isStable) {
							log.debug("load block into stable cache number::" + loopBlockEntity.getHeader().getNumber()
									+ " hash::" + loopBlockEntity.getHeader().getBlockHash() + " stateroot::"
									+ loopBlockEntity.getHeader().getStateRoot());
							stableStore.add(loopBlockEntity);
							if (maxStableNumber < loopBlockEntity.getHeader().getNumber()) {
								maxStableNumber = loopBlockEntity.getHeader().getNumber();
								maxStableBlock = loopBlockEntity;
							}
						} else {
							log.debug(
									"load block into unstable cache number::" + loopBlockEntity.getHeader().getNumber()
											+ " hash::" + loopBlockEntity.getHeader().getBlockHash() + " stateroot::"
											+ loopBlockEntity.getHeader().getStateRoot());
							unStableStore.add(loopBlockEntity);
							unStableStore.append(loopBlockEntity.getHeader().getBlockHash(),
									loopBlockEntity.getHeader().getNumber());
							if (maxReceiveNumber < loopBlockEntity.getHeader().getNumber()) {
								maxReceiveNumber = loopBlockEntity.getHeader().getNumber();
								maxReceiveBlock = loopBlockEntity;
							}
							if (maxConnectNumber < loopBlockEntity.getHeader().getNumber()) {
								maxConnectNumber = loopBlockEntity.getHeader().getNumber();
								maxConnectBlock = loopBlockEntity;
							}
						}
					}

					blockNumber -= 1;
				}
			}
		}
		log.info("max connect::" + maxConnectNumber + " max stable::" + maxStableNumber);
	}

	public synchronized BlockStoreSummary addBlock(BlockEntity block) {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();
		String hash = block.getHeader().getBlockHash();
		long number = block.getHeader().getNumber();
		String parentHash = block.getHeader().getParentHash();

		if (maxReceiveNumber < block.getHeader().getNumber()) {
			maxReceiveNumber = block.getHeader().getNumber();
			maxReceiveBlock = block;
		}

		if (unStableStore.containConnectKey(hash)) {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
			return oBlockStoreSummary;
		} else {
			if (maxStableNumber >= block.getHeader().getNumber()) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
				return oBlockStoreSummary;
			}
		}
		if (unStableStore.add(block)) {
			BlockStoreNodeValue oParentNode = unStableStore.getNode(parentHash, number - 1);
			BlockEntity oParent = null;
			// if (oParentNode == null && block.getHeader().getNumber() != 1) {
			// log.debug("try to restore from db into unstable cache::" +
			// parentHash + " number::" + (number - 1));
			// oParentNode = unStableStore.tryToRestoreFromDb(parentHash, number
			// - 1);
			// }
			if (oParentNode == null && block.getHeader().getNumber() == 1) {
				oParent = stableStore.get(block.getHeader().getParentHash());
				if (oParent.getHeader().getNumber() == 0) {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
				} else {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
				}
			} else {
				if (oParentNode == null) {
					List<BlockEntity> existsParent = unStableStore.getConnectBlocksByNumber(number - 1);
					if (existsParent.size() > 0) {
						log.warn("forks, number::" + (block.getHeader().getNumber() - 1));
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_PREV);
					} else if (maxStableBlock.getHeader().getBlockHash().equals(block.getHeader().getParentHash())
							&& maxStableBlock.getHeader().getNumber() == block.getHeader().getNumber() - 1) {
						log.warn("try apply block");
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
					} else {
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.CACHE);
					}
				} else if (oParentNode != null && (oParentNode.getNumber() + 1) != block.getHeader().getNumber()) {
					log.warn("parent node number is wrong::" + oParentNode.getNumber());
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.CACHE);
				} else if (oParentNode != null && !oParentNode.isConnect()) {
					log.warn("parent node not connect hash::" + oParentNode.getBlockHash() + " number::"
							+ oParentNode.getNumber());
					List<BlockEntity> existsParent = unStableStore
							.getConnectBlocksByNumber(block.getHeader().getNumber() - 1);
					if (existsParent.size() > 0) {
						log.warn("forks, number::" + (block.getHeader().getNumber() - 1));
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_PREV);
					} else {
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.CACHE);
					}
				} else {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
				}
			}
		} else {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
		}
		return oBlockStoreSummary;
	}

	public synchronized BlockStoreSummary tryAddBlock(BlockEntity block) {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();
		BlockStoreNodeValue oParentNode = unStableStore.getNode(block.getHeader().getParentHash(),
				block.getHeader().getNumber() - 1);

		log.debug("try to find hash::" + block.getHeader().getParentHash() + " number::"
				+ (block.getHeader().getNumber() - 1));

		if (oParentNode == null) {
			List<BlockEntity> existsParent = unStableStore.getConnectBlocksByNumber(block.getHeader().getNumber() - 1);
			if (existsParent.size() > 0) {
				log.warn("forks, number::" + (block.getHeader().getNumber() - 1));
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_PREV);
			} else {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
			}
		} else if (oParentNode != null && (oParentNode.getNumber() + 1) != block.getHeader().getNumber()) {
			log.warn("parent node number is wrong::" + oParentNode.getNumber());
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
		} else if (oParentNode != null && !oParentNode.isConnect()) {
			log.warn("parent node not connect hash::" + oParentNode.getBlockHash() + " number::"
					+ oParentNode.getNumber() + ", may be forked");
			List<BlockEntity> existsParent = unStableStore.getConnectBlocksByNumber(block.getHeader().getNumber() - 1);
			if (existsParent.size() > 0) {
				log.warn("forks, number::" + (block.getHeader().getNumber() - 1));
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_PREV);
			} else {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
			}
		} else {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
		}
		return oBlockStoreSummary;
	}

	public synchronized BlockStoreSummary connectBlock(BlockEntity block) throws BlockNotFoundInStoreException {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();

		String hash = block.getHeader().getBlockHash();
		long number = block.getHeader().getNumber();
		log.debug("connect block number::" + block.getHeader().getNumber() + " hash::" + hash + " stateroot::"
				+ block.getHeader().getStateRoot());

		if (unStableStore.containKey(hash) || block.getHeader().getNumber() == 1) {
			unStableStore.put(hash, block);
			unStableStore.connect(hash, number);
			BlockStoreNodeValue oBlockStoreNodeValue = null;

			if (maxConnectNumber < block.getHeader().getNumber()) {
				maxConnectNumber = block.getHeader().getNumber();
				maxConnectBlock = block;
			}

			oBlockStoreNodeValue = unStableStore.tryPop(hash, number);
			if (oBlockStoreNodeValue != null) {
				stableBlock(oBlockStoreNodeValue.getBlockEntity());
			}
			if (unStableStore.containsUnConnectChild(hash, number + 1)) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY_CHILD);
				return oBlockStoreSummary;
			} else {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				return oBlockStoreSummary;
			}
		} else {
			throw new BlockNotFoundInStoreException(
					"not found the block from block store::" + hash + " number::" + block.getHeader().getNumber());
		}
	}

	public synchronized BlockStoreSummary stableBlock(BlockEntity block) {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();
		if (stableStore.add(block)) {
			if (maxStableNumber < block.getHeader().getNumber()) {
				maxStableNumber = block.getHeader().getNumber();
				maxStableBlock = block;
			}
			unStableStore.removeForkBlock(block.getHeader().getNumber());
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
			return oBlockStoreSummary;
		}
		oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
		return oBlockStoreSummary;
	}

	public synchronized BlockEntity getBlockByNumber(long number) {
		BlockEntity oBlockEntity = unStableStore.getBlockByNumber(number);
		if (oBlockEntity == null) {
			oBlockEntity = stableStore.getBlockByNumber(number);
		}
		return oBlockEntity;
	}

	public synchronized List<BlockEntity> getBlocksByNumber(long number) {
		List<BlockEntity> ret = new ArrayList<>();
		try {
			List<OPair> oPairs = dao.getBlockDao().listBySecondKey(String.valueOf(number)).get();
			if (oPairs.size() > 0) {
				for (int i = 0; i < oPairs.size(); i++) {
					BlockEntity block = BlockEntity.parseFrom(oPairs.get(i).getValue().getExtdata());
					ret.add(block);
				}
			}
		} catch (ODBException | InterruptedException | ExecutionException | InvalidProtocolBufferException e) {
			log.error("get block from db error :: " + e.getMessage());
		}

		return ret;
	}

	public synchronized BlockEntity getBlockByHash(String hash) {
		BlockEntity oBlockEntity = unStableStore.get(hash);
		if (oBlockEntity == null) {
			oBlockEntity = stableStore.get(hash);
		}
		return oBlockEntity;
	}

	public synchronized BlockEntity getBlockByHash(String hash, long number) {
		BlockEntity oBlockEntity = unStableStore.get(hash, number);
		if (oBlockEntity == null) {
			oBlockEntity = stableStore.get(hash);
		}
		return oBlockEntity;
	}

	public synchronized List<BlockEntity> getReadyConnectBlock(String hash, long number) {
		return unStableStore.getUnConnectChild(hash, number + 1);
	}

	public synchronized List<BlockEntity> getChildListBlocksEndWith(String blockHash, String endBlockHash,
			int maxCount) {
		BlockEntity firstBlock = getBlockByHash(blockHash);
		if (firstBlock == null) {
			return new ArrayList<>();
		}
		List<BlockEntity> blocks = new ArrayList<>();
		long number = firstBlock.getHeader().getNumber();
		blocks.add(firstBlock);

		for (int i = 1; i <= maxCount; ++i) {
			BlockEntity child = getBlockByNumber(number + i);
			if (child != null) {
				blocks.add(child);
				if (StringUtils.isNotBlank(endBlockHash) && child.getHeader().getBlockHash().equals(endBlockHash)) {
					return blocks;
				}
			} else {
				return blocks;
			}
		}
		return blocks;
	}

	public synchronized List<BlockEntity> getParentListBlocksEndWith(String blockHash, String endBlockHash,
			long maxCount) {
		BlockEntity firstBlock = getBlockByHash(blockHash);
		if (firstBlock == null) {
			return new ArrayList<>();
		}
		List<BlockEntity> blocks = new ArrayList<>();
		blocks.add(firstBlock);

		String currentBlock = firstBlock.getHeader().getParentHash();
		long currentNumber = firstBlock.getHeader().getNumber();
		for (int i = 1; i <= maxCount; ++i) {
			BlockEntity child = getBlockByHash(currentBlock, currentNumber);
			if (child != null) {
				currentBlock = child.getHeader().getParentHash();
				currentNumber = child.getHeader().getNumber();
				blocks.add(child);
				if (StringUtils.isNotBlank(endBlockHash) && child.getHeader().getBlockHash().equals(endBlockHash)) {
					return blocks;
				}
			} else {
				return blocks;
			}
		}
		return blocks;
	}

	public synchronized boolean isConnect(String hash, long number) {
		if (unStableStore.isConnect(hash, number) || stableStore.containKey(hash)) {
			return true;
		}
		return false;
	}

	public synchronized boolean isStable(String hash) {
		return stableStore.containKey(hash);
	}

	public synchronized BlockEntity rollBackTo(long number) {
		log.info("blockstore try to rollback to number::" + number + " maxconnect::" + this.getMaxConnectNumber()
				+ " maxstable::" + this.getMaxStableNumber());

		if (number == 0) {
			BlockEntity block = stableStore.getBlockByNumber(0);
			unStableStore.disconnectAll(block);
			stableStore.add(block);
			if (maxStableNumber < block.getHeader().getNumber()) {
				maxStableNumber = block.getHeader().getNumber();
				maxStableBlock = block;
			}
			maxConnectNumber = block.getHeader().getNumber();
			maxConnectBlock = block;
			return block;
		} else if (this.getMaxConnectNumber() > number) {
			BlockEntity oBlockEntity = unStableStore.rollBackTo(number, this.getMaxConnectNumber());
			if (oBlockEntity == null) {
				log.error("cannot roll back to stable block number::" + number + " max::" + this.getMaxStableNumber());
			} else {
				maxConnectNumber = oBlockEntity.getHeader().getNumber();
				maxConnectBlock = oBlockEntity;
			}
			return oBlockEntity;
		} else {
			return maxConnectBlock;
		}
	}

	public static class IllegalOperationException extends Exception {
		public IllegalOperationException(String message, Object... args) {
			super(format(message, args));
		}
	}

	public static class BlockNotFoundInStoreException extends Exception {
		public BlockNotFoundInStoreException(String message, Object... args) {
			super(format(message, args));
		}
	}
}
