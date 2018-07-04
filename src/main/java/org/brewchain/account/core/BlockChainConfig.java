package org.brewchain.account.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.brewchain.account.gens.Actimpl.PACTModule;

import com.google.protobuf.Message;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.mservice.NodeHelper;

@NActorProvider
@Data
@Slf4j
@Instantiate(name = "BlockChain_Config")
public class BlockChainConfig extends SessionModules<Message> {
	private int minerReward = props().get("block.miner.reward", 0);
	private String pwd = props().get("org.bc.manage.node.dev.pwd", KeyConstant.PWD);
	private String keystoreNumber = props().get("org.bc.manage.node.keystore.num",
			String.valueOf(Math.abs(NodeHelper.getCurrNodeListenOutPort() - 5100 + 1)));
	private String net = readNet();
	private int stableBlocks = props().get("org.brewchain.stable.blocks", KeyConstant.STABLE_BLOCK);
	private long contract_lock_balance = props().get("org.brewchain.contract.lock.balance", 30000);
	private String lock_account_address = props().get("org.brewchain.account.lock.address", "");
	private boolean isDev = props().get("org.brewchain.man.dev", "true").equals("true");

	@Override
	public String[] getCmds() {
		return new String[] { "BlockChainConfig" };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onDaoServiceAllReady() {
		// 校验配置是否有效
		log.info(String.format("配置 %s = %s", "block.miner.reward", minerReward));
		log.info(String.format("配置 %s = %s", "org.bc.manage.node.dev.pwd", pwd));
		log.info(String.format("配置 %s = %s", "org.bc.manage.node.keystore.num", keystoreNumber));
	}

	private String readNet() {
		String network = "";
		try {
			File networkFile = new File(".chainnet");
			if (!networkFile.exists() || !networkFile.canRead()) {
				// read default config
				network = props().get("org.bc.manage.node.net", null);
			}
			if (network == null || network.isEmpty()) {
				while (!networkFile.exists() || !networkFile.canRead()) {
					log.debug("waiting chain_net config...");
					Thread.sleep(1000);
				}

				FileReader fr = new FileReader(networkFile.getPath());
				BufferedReader br = new BufferedReader(fr);
				network = br.readLine().trim().replace("\r", "").replace("\t", "");
				br.close();
				fr.close();
			}
		} catch (Exception e) {
			log.error("fail to read net config");
		}
		return network;
	}
}
