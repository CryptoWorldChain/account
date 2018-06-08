package org.brewchain.account.core.store;

import org.brewchain.account.gens.Block.BlockEntity;

public class BlockUnStableStore implements IBlockStore {

	@Override
	public boolean containKey(String hash) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public BlockStoreNodeValue get(String hash) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BlockStoreSummary tryAdd(BlockEntity block) {
		// TODO Auto-generated method stub
		return null;
	}

}
