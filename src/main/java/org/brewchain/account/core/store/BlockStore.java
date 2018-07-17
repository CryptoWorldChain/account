package org.brewchain.account.core.store;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

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
		String lastBlockHash = null;
		OValue oOValue = dao.getBlockDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_MAX_BLOCK)).get();
		if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
			oOValue = dao.getBlockDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_CURRENT_BLOCK)).get();
			if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
				log.warn(String.format("not found last block, start empty node"));
				return;
			}
		}
		lastBlockHash = encApi.hexEnc(oOValue.getExtdata().toByteArray());

		BlockEntity oBlockEntity = getBlockByHash(lastBlockHash);
		if (oBlockEntity == null) {
			log.error(String.format("exists last block hash, but last block not exists, start empty node"));
		} else {
			if (oBlockEntity.getHeader().getNumber() == 0) {
				log.debug("load block into stable cache number::" + oBlockEntity.getHeader().getNumber() + " hash::"
						+ oBlockEntity.getHeader().getBlockHash() + " stateroot::"
						+ oBlockEntity.getHeader().getStateRoot());
				stableStore.add(oBlockEntity);
				if (maxStableNumber < oBlockEntity.getHeader().getNumber()) {
					maxStableNumber = oBlockEntity.getHeader().getNumber();
					maxStableBlock = oBlockEntity;
				}
			} else {
				log.debug("load block into unstable cache number::" + oBlockEntity.getHeader().getNumber() + " hash::"
						+ oBlockEntity.getHeader().getBlockHash() + " stateroot::"
						+ oBlockEntity.getHeader().getStateRoot());
				unStableStore.add(oBlockEntity);
				unStableStore.connect(oBlockEntity.getHeader().getBlockHash(), oBlockEntity.getHeader().getNumber());
				if (maxReceiveNumber < oBlockEntity.getHeader().getNumber()) {
					maxReceiveNumber = oBlockEntity.getHeader().getNumber();
					maxReceiveBlock = oBlockEntity;
				}
				if (maxConnectNumber < oBlockEntity.getHeader().getNumber()) {
					maxConnectNumber = oBlockEntity.getHeader().getNumber();
					maxConnectBlock = oBlockEntity;
				}
			}

			long blockNumber = oBlockEntity.getHeader().getNumber();
			long maxBlockNumber = blockNumber;
			int c = 0;
			String parentHash = oBlockEntity.getHeader().getParentHash();
			long parentNumber = oBlockEntity.getHeader().getNumber() - 1;
			while (StringUtils.isNotBlank(parentHash) && c < KeyConstant.CACHE_SIZE) {
				c += 1;
				BlockEntity loopBlockEntity = null;
				loopBlockEntity = getBlockByHash(parentHash, parentNumber);
				if (loopBlockEntity == null) {
					break;
				} else {
					if (blockNumber - 1 != loopBlockEntity.getHeader().getNumber()) {
						throw new Exception(String.format("respect block number %s ,get block number %s",
								blockNumber - 1, loopBlockEntity.getHeader().getNumber()));
					}
					blockNumber -= 1;
					if (maxBlockNumber > (blockNumber + blockChainConfig.getStableBlocks())) {
						log.debug("load block into stable cache number::" + loopBlockEntity.getHeader().getNumber()
								+ " hash::" + loopBlockEntity.getHeader().getBlockHash() + " stateroot::"
								+ loopBlockEntity.getHeader().getStateRoot());
						stableStore.add(loopBlockEntity);
						if (maxStableNumber < loopBlockEntity.getHeader().getNumber()) {
							maxStableNumber = loopBlockEntity.getHeader().getNumber();
							maxStableBlock = loopBlockEntity;
						}
					} else {
						log.debug("load block into unstable cache number::" + loopBlockEntity.getHeader().getNumber()
								+ " hash::" + loopBlockEntity.getHeader().getBlockHash() + " stateroot::"
								+ loopBlockEntity.getHeader().getStateRoot());
						unStableStore.add(loopBlockEntity);
						unStableStore.append(parentHash, parentNumber);
						if (maxReceiveNumber < loopBlockEntity.getHeader().getNumber()) {
							maxReceiveNumber = loopBlockEntity.getHeader().getNumber();
							maxReceiveBlock = loopBlockEntity;
						}
						if (maxConnectNumber < loopBlockEntity.getHeader().getNumber()) {
							maxConnectNumber = loopBlockEntity.getHeader().getNumber();
							maxConnectBlock = loopBlockEntity;
						}
					}
					if (loopBlockEntity.getHeader().getParentHash() != null) {
						parentHash = loopBlockEntity.getHeader().getParentHash();
						parentNumber = loopBlockEntity.getHeader().getNumber() - 1;
					} else {
						break;
					}
				}
			}
		}
	}

	public BlockStoreSummary addBlock(BlockEntity block) {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();
		String hash = block.getHeader().getBlockHash();
		long number = block.getHeader().getNumber();
		String parentHash = block.getHeader().getParentHash();

		if (maxReceiveNumber < block.getHeader().getNumber()) {
			maxReceiveNumber = block.getHeader().getNumber();
			maxReceiveBlock = block;
		}

		if (unStableStore.containKey(hash) && unStableStore.isConnect(hash, number)) {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_DROP);
			return oBlockStoreSummary;
		} else if (stableStore.containKey(hash)) {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_DROP);
			return oBlockStoreSummary;
		} else {
			if (maxStableNumber >= (block.getHeader().getNumber() + blockChainConfig.getStableBlocks())) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
				return oBlockStoreSummary;
			}
		}
		if (unStableStore.add(block)) {
			BlockStoreNodeValue oParentNode = unStableStore.getNode(parentHash, number - 1);
			BlockEntity oParent = null;
			if (oParentNode == null) {
				log.debug("try to restore from db into unstable cache::" + parentHash + " number::" + (number - 1));
				oParentNode = unStableStore.tryToRestoreFromDb(parentHash, number - 1);
			}
			if (oParentNode == null && block.getHeader().getNumber() == 1) {
				oParent = stableStore.get(block.getHeader().getParentHash());
				if (oParent.getHeader().getNumber() == 0) {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
				} else {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.ERROR);
				}
			} else {
				if (oParentNode == null) {
					BlockEntity existsParent = unStableStore.getBlockByNumber(number - 1);
					if (existsParent != null) {
						log.warn("forks, number::" + (block.getHeader().getNumber() - 1));
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_PREV);
					} else {
						oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.CACHE);
					}
				} else if (oParentNode != null && (oParentNode.getNumber() + 1) != block.getHeader().getNumber()) {
					log.warn("parent node number is wrong::" + oParentNode.getNumber());
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.CACHE);
				} else if (oParentNode != null && !oParentNode.isConnect()) {
					log.warn("parent node not connect");
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.CACHE);
				} else {
					oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
				}
			}
		} else {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
		}
		return oBlockStoreSummary;
	}

	public BlockStoreSummary connectBlock(BlockEntity block) throws BlockNotFoundInStoreException {
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
				// ?
				// unStableStore.removeForkBlock(oBlockStoreNodeValue.getNumber());
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

	public BlockStoreSummary stableBlock(BlockEntity block) {
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

	public BlockEntity getBlockByNumber(long number) {
		BlockEntity oBlockEntity = unStableStore.getBlockByNumber(number);
		if (oBlockEntity == null) {
			oBlockEntity = stableStore.getBlockByNumber(number);
		}
		return oBlockEntity;
	}

	public BlockEntity getBlockByHash(String hash) {
		BlockEntity oBlockEntity = unStableStore.get(hash);
		if (oBlockEntity == null) {
			oBlockEntity = stableStore.get(hash);
		}
		return oBlockEntity;
	}

	public BlockEntity getBlockByHash(String hash, long number) {
		BlockEntity oBlockEntity = unStableStore.get(hash, number);
		if (oBlockEntity == null) {
			oBlockEntity = stableStore.get(hash);
		}
		return oBlockEntity;
	}

	public BlockEntity getReadyConnectBlock(String hash, long number) {
		List<BlockEntity> lists = unStableStore.getBlocksByNumber(number + 1);
		for (Iterator iterator = lists.iterator(); iterator.hasNext();) {
			BlockEntity blockEntity = (BlockEntity) iterator.next();
			if (blockEntity.getHeader().getParentHash().equals(hash)) {
				return blockEntity;
			}
		}
		return null;
	}

	public List<BlockEntity> getChildListBlocksEndWith(String blockHash, String endBlockHash, int maxCount) {
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

	public List<BlockEntity> getParentListBlocksEndWith(String blockHash, String endBlockHash, long maxCount) {
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

	public boolean isConnect(String hash, long number) {
		if (unStableStore.isConnect(hash, number) || stableStore.containKey(hash)) {
			return true;
		}
		return false;
	}

	public boolean isStable(String hash) {
		return stableStore.containKey(hash);
	}

	public BlockEntity rollBackTo(long number) {
		log.info("blockstore try to rollback to number::" + number + " maxconnect::" + this.getMaxConnectNumber()
				+ " maxstable::" + this.getMaxStableNumber());

		BlockEntity oBlockEntity = unStableStore.rollBackTo(number, maxConnectBlock);
		if (oBlockEntity == null) {
			oBlockEntity = stableStore.rollBackTo(number);
			unStableStore.clear();
			unStableStore.disconnectAll(oBlockEntity);

			maxConnectNumber = oBlockEntity.getHeader().getNumber();
			maxConnectBlock = oBlockEntity;
			maxStableNumber = oBlockEntity.getHeader().getNumber();
			maxStableBlock = oBlockEntity;
		} else {
			maxConnectNumber = oBlockEntity.getHeader().getNumber();
			maxConnectBlock = oBlockEntity;
		}
		return oBlockEntity;
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
