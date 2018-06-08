package org.brewchain.account.core.store;

import org.brewchain.account.gens.Block.BlockEntity;

public interface IBlockStore {
	boolean containKey(String hash);

	BlockStoreNodeValue get(String hash);

	BlockStoreSummary tryAdd(BlockEntity block);
}
