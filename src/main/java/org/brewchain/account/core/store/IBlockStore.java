package org.brewchain.account.core.store;

import org.brewchain.evmapi.gens.Block.BlockEntity;

public interface IBlockStore {
	boolean containKey(String hash);

	BlockEntity get(String hash);

	boolean add(BlockEntity block);
	
	BlockEntity getBlockByNumber(long number);
	
	BlockEntity rollBackTo(long number);
	
	void clear();
}
