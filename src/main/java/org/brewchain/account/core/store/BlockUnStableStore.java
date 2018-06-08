package org.brewchain.account.core.store;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.gens.Block.BlockEntity;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "BlockStore_UnStable")
@Slf4j
@Data
public class BlockUnStableStore implements IBlockStore, ActorService {

	@Override
	public boolean containKey(String hash) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public BlockEntity get(String hash) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean add(BlockEntity block) {
		// TODO Auto-generated method stub
		return false;
	}

	public int increaseRetryTimes(String hash) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean isConnect(String hash) {
		// TODO Auto-generated method stub
		return false;
	}

	public void connect(String hash) {
		// TODO Auto-generated method stub

	}

	public boolean isExistsUnConnectChild(String hash) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean tryPop(BlockStoreNodeValue oBlockStoreNodeValue) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public BlockEntity getBlockByNumber(int number) {
		// TODO Auto-generated method stub
		return null;
	}

}
