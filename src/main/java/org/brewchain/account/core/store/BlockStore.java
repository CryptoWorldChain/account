package org.brewchain.account.core.store;

import static java.lang.String.format;

import org.brewchain.account.core.BlockChainStoreWithLRU;
import org.brewchain.account.core.store.BlockStoreSummary.BLOCK_BEHAVIOR;
import org.brewchain.account.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

import onight.tfw.ntrans.api.annotation.ActorRequire;

public class BlockStore {
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

	public BlockStoreSummary add(BlockEntity block) {
		BlockStoreSummary oBlockStoreSummary = new BlockStoreSummary();
		String hash = encApi.hexEnc(block.getHeader().getBlockHash().toByteArray());

		if (unStableStore.containKey(hash)) {
			BlockStoreNodeValue oNode = unStableStore.get(hash);
			oNode.increaseRetryTimes();
			if (oNode.getRetryTimes() < 2) {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_DROP);
			} else {
				oBlockStoreSummary.setBehavior(BLOCK_BEHAVIOR.EXISTS_PREV);
			}
		}
		return oBlockStoreSummary;
	}

	public static class IllegalOperationException extends Exception {
		public IllegalOperationException(String message, Object... args) {
			super(format(message, args));
		}
	}
}
