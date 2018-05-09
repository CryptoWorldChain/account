package org.brewchain.account;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Validate;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.dao.DefDaos;
import org.fc.brewchain.p22p.node.Network;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class ApplicationStart implements ActorService {
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	@Validate
	public void startup() {
		try {
			final Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					// copy db is db is not exists

					// load block
					blockChainHelper.onStart();

					// get node
					// Network oNetwork = dao.getPzp().networkByID("raft");
					// KeyConstant.nodeName = oNetwork.root().name();
					KeyConstant.nodeName = "测试节点01";
				}
			}, 1000 * 20);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
