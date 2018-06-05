package org.brewchain.account.core;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Actimpl.PACTModule;

import com.google.protobuf.Message;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;

@NActorProvider
@Data
@Slf4j
@Instantiate(name = "BlockChain_Config")
public class BlockChainConfig extends SessionModules<Message> {
	private int minerReward = props().get("block.miner.reward", 0);
	private String pwd = props().get("org.bc.manage.node.dev.pwd", null);
	private String keystoreNumber = props().get("org.bc.manage.node.keystore.num", "1");

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
}
