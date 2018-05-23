package org.brewchain.account;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.collections.functors.WhileClosure;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Validate;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Actimpl.PACTCommand;
import org.brewchain.account.gens.Actimpl.PACTModule;
import org.brewchain.account.gens.Actimpl.ReqCreateAccount;
import org.brewchain.account.util.NodeDef;
import org.fc.brewchain.p22p.node.Network;

import com.google.protobuf.Message;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class ApplicationStart extends SessionModules<Message> {

	@Override
	public String[] getCmds() {
		return new String[] { "___" };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	@Validate
	public void startup() {
		try {
			new Thread(new AccountStartThread()).start();
			// final Timer timer = new Timer();
			// timer.schedule(new TimerTask() {
			// @Override
			// public void run() {
			// // copy db is db is not exists
			//
			// // load block
			// blockChainHelper.onStart();
			//
			// // get node
			// // Network oNetwork = dao.getPzp().networkByID("raft");
			// // KeyConstant.nodeName = oNetwork.root().name();
			// KeyConstant.nodeName = "测试节点01";
			// }
			// }, 1000 * 20);
		} catch (Exception e) {
			// e.printStackTrace();
			log.error("dao注入异常", e);
		}
	}

	class AccountStartThread extends Thread {
		@Override
		public void run() {

			String consensus = props().get("org.brewchain.consensus", "");
			if (StringUtils.isNotBlank(consensus) && !StringUtils.equalsIgnoreCase(consensus, "single")) {
				// run account in dpos layer
				log.debug("Account Start Running In " + consensus + " layer");
			} else {
				try {
					while (dao == null || !dao.isReady()) {
						log.debug("等待dao注入完成...");
						Thread.sleep(1000);
					}

					log.debug("dao注入完成");

					Network oNetwork = dao.getPzp().networkByID("dpos");
					NodeDef oNodeDef = new NodeDef();
					if (oNetwork.root().node_idx() > 0) {
						// waiting for dpos
					}
					oNodeDef.setBcuid(oNetwork.root().bcuid());
					oNodeDef.setAddress(oNetwork.root().v_address());
					oNodeDef.setNode(oNetwork.root().name());
					KeyConstant.node = oNodeDef;

					log.debug("节点信息::" + KeyConstant.node);

					log.debug("开始初始化数据库");
					blockChainHelper.onStart();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
					log.error("dao注入异常", e);
				}
			}
		}

	}
}
