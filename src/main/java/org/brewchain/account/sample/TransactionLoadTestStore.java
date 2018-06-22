package org.brewchain.account.sample;

import java.util.ArrayList;
import java.util.List;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.trie.StorageTrieCache;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "TransactionLoadTest_Store")
@Slf4j
@Data
public class TransactionLoadTestStore implements ActorService {
	private List<MultiTransaction.Builder> loads = new ArrayList<>();
	private int loopCount = 0;

	public MultiTransaction.Builder getOne() {
		loopCount += 1;
		if (loopCount >= loads.size()) {
			loopCount = 0;
		}
		return loads.get(loopCount);
	}
}
