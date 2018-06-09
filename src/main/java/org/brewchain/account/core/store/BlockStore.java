package org.brewchain.account.core.store;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

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
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	private int maxReceiveNumber = -1;
	private int maxConnectNumber = -1;
	private int maxStableNumber = -1;
	private BlockEntity maxReceiveBlock = null;
	private BlockEntity maxConnectBlock = null;
	private BlockEntity maxStableBlock = null;
	private BlockStableStore stableStore = new BlockStableStore();
	private BlockUnStableStore unStableStore = new BlockUnStableStore();

	public BlockStoreSummary addBlock(BlockEntity block) {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();
		String hash = encApi.hexEnc(block.getHeader().getBlockHash().toByteArray());

		if (maxReceiveNumber < block.getHeader().getNumber()) {
			maxReceiveNumber = block.getHeader().getNumber();
			maxReceiveBlock = block;
		}

		if (unStableStore.containKey(hash)) {
			if (unStableStore.isConnect(hash)) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_DROP);
				return oBlockStoreSummary;
			} else if (unStableStore.increaseRetryTimes(hash) > 2) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_PREV);
				return oBlockStoreSummary;
			}
		} else {
			if (maxStableNumber >= (block.getHeader().getNumber() + KeyConstant.STABLE_BLOCK)) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
				return oBlockStoreSummary;
			}
		}

		if (unStableStore.add(block)) {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY);
			return oBlockStoreSummary;
		} else {
			oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DROP);
			return oBlockStoreSummary;
		}
	}

	public BlockStoreSummary connectBlock(BlockEntity block) throws BlockNotFoundInStoreException {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();
		String hash = encApi.hexEnc(block.getHeader().getBlockHash().toByteArray());
		if (unStableStore.containKey(hash)) {
			unStableStore.connect(hash);
			BlockStoreNodeValue oBlockStoreNodeValue = null;

			if (maxConnectNumber < block.getHeader().getNumber()) {
				maxConnectNumber = block.getHeader().getNumber();
				maxConnectBlock = block;
			}

			if (unStableStore.tryPop(hash, oBlockStoreNodeValue)) {
				if (stableStore.add(oBlockStoreNodeValue.getBlockEntity())) {
					if (maxStableNumber < oBlockStoreNodeValue.getBlockEntity().getHeader().getNumber()) {
						maxStableNumber = oBlockStoreNodeValue.getBlockEntity().getHeader().getNumber();
						maxStableBlock = oBlockStoreNodeValue.getBlockEntity();
					}
				}
			}
			if (unStableStore.containsUnConnectChild(hash)) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.APPLY_CHILD);
				return oBlockStoreSummary;
			} else {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.DONE);
				return oBlockStoreSummary;
			}
		} else {
			throw new BlockNotFoundInStoreException("not found the block from block store::" + hash);
		}
	}

	public BlockEntity getBlockByNumber(int number) {
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

	public List<BlockEntity> getReadyConnectBlock(String hash) {
		return unStableStore.getUnConnectChild(hash);
	}

	public List<BlockEntity> getChildListBlocksEndWith(String blockHash, String endBlockHash, int maxCount) {
		BlockEntity firstBlock = getBlockByHash(blockHash);
		if (firstBlock == null) {
			return new ArrayList<>();
		}
		List<BlockEntity> blocks = new ArrayList<>();
		int number = firstBlock.getHeader().getNumber();
		blocks.add(firstBlock);

		for (int i = 1; i <= maxCount; ++i) {
			BlockEntity child = getBlockByNumber(number + i);
			if (child != null) {
				blocks.add(child);
				if (StringUtils.isNotBlank(endBlockHash)
						&& encApi.hexEnc(child.getHeader().getBlockHash().toByteArray()).equals(endBlockHash)) {
					return blocks;
				}
			} else {
				return blocks;
			}
		}

		return blocks;
	}

	public List<BlockEntity> getParentListBlocksEndWith(String blockHash, String endBlockHash, int maxCount) {
		BlockEntity firstBlock = getBlockByHash(blockHash);
		if (firstBlock == null) {
			return new ArrayList<>();
		}
		List<BlockEntity> blocks = new ArrayList<>();
		int number = firstBlock.getHeader().getNumber();
		blocks.add(firstBlock);

		for (int i = 1; i <= maxCount; ++i) {
			BlockEntity child = getBlockByNumber(number - i);
			if (child != null) {
				blocks.add(child);

				if (StringUtils.isNotBlank(endBlockHash)
						&& encApi.hexEnc(child.getHeader().getBlockHash().toByteArray()).equals(endBlockHash)) {
					return blocks;
				}
			} else {
				return blocks;
			}
		}
		return blocks;
	}

	public boolean isConnect(String hash) {
		if (unStableStore.isConnect(hash) || stableStore.containKey(hash)) {
			return true;
		}
		return false;
	}

	public boolean isStable(String hash) {
		return stableStore.containKey(hash);
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
