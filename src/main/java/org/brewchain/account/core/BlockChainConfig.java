package org.brewchain.account.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigInteger;

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
	private BigInteger minerReward = new BigInteger(props().get("block.miner.reward", "0"));
	private String pwd = props().get("org.bc.manage.node.dev.pwd", KeyConstant.PWD);
	private String keystoreNumber = props().get("org.bc.manage.node.keystore.num",
			String.valueOf(Math.abs(NodeHelper.getCurrNodeListenOutPort() - 5100 + 1)));
	private String net = readNet();
	private int stableBlocks = props().get("org.brewchain.stable.blocks", KeyConstant.STABLE_BLOCK);
	private BigInteger contract_lock_balance = new BigInteger(props().get("org.brewchain.contract.lock.balance", "0"));
	private String lock_account_address = props().get("org.brewchain.account.lock.address", null);
	private boolean isDev = props().get("org.brewchain.man.dev", "true").equals("true");
	private BigInteger token_lock_balance = new BigInteger(props().get("org.brewchain.token.lock.balance", "0"));
	private int defaultRollBackCount = props().get("org.brewchain.manage.rollback.count", 1);
	private int accountVersion = props().get("org.brewchain.account.version", 0);
	private BigInteger maxTokenTotal = new BigInteger(props().get("org.brewchain.token.max.total", "0"));
	private BigInteger minTokenTotal = new BigInteger(props().get("org.brewchain.token.min.total", "0"));
	private int blockEpochMSecond = Math.round(props().get("org.bc.dpos.blk.epoch.ms", 1000) / 1000);
	private int blockEpochSecond = props().get("org.bc.dpos.blk.epoch.sec", 1);

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
		log.info(String.format("configuration %s = %s", "block.miner.reward", minerReward));
		log.info(String.format("configuration %s = %s", "org.bc.manage.node.dev.pwd", pwd));
		log.info(String.format("configuration %s = %s", "org.bc.manage.node.keystore.num", keystoreNumber));
		log.info(String.format("configuration %s = %s", "org.bc.manage.node.keystore.num", keystoreNumber));
		log.info(String.format("configuration %s = %s", "org.brewchain.stable.blocks", stableBlocks));
		log.info(String.format("configuration %s = %s", "org.brewchain.contract.lock.balance", contract_lock_balance));
		log.info(String.format("configuration %s = %s", "org.brewchain.account.lock.address", lock_account_address));
		log.info(String.format("configuration %s = %s", "org.brewchain.man.dev", isDev));
		log.info(String.format("configuration %s = %s", "org.brewchain.token.lock.balance", token_lock_balance));
		log.info(String.format("configuration %s = %s", "org.brewchain.manage.rollback.count", defaultRollBackCount));
		log.info(String.format("configuration %s = %s", "org.brewchain.account.version", accountVersion));
		log.info(String.format("configuration %s = %s", "org.brewchain.token.max.total", maxTokenTotal));
		log.info(String.format("configuration %s = %s", "org.brewchain.token.min.total", minTokenTotal));
		log.info(String.format("configuration %s = %s", "org.bc.manage.node.net", net));
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
