package org.brewchain.account.account;

import org.apache.commons.lang3.StringUtils;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainConfig;
import org.brewchain.account.gens.Actimpl.PACTCommand;
import org.brewchain.account.gens.Actimpl.PACTModule;
import org.brewchain.account.gens.Actimpl.ReqGenerateAccount;
import org.brewchain.account.gens.Actimpl.ReqGetAccount;
import org.brewchain.account.gens.Actimpl.RespGenerateAccount;
import org.brewchain.account.gens.Actimpl.RespGetAccount;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class GenerateAccountImpl extends SessionModules<ReqGenerateAccount> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;

	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.GEA.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGenerateAccount pb, final CompleteHandler handler) {
		RespGenerateAccount.Builder oRespGenerateAccount = RespGenerateAccount.newBuilder();
		if (!blockChainConfig.isDev()) {
			oRespGenerateAccount.setRetCode(-1);
			handler.onFinished(PacketHelper.toPBReturn(pack, oRespGenerateAccount.build()));
			return;
		}
		try {
			KeyPairs oKeyPairs;
			if (StringUtils.isNotBlank(pb.getKey())) {
				oKeyPairs = encApi.genKeys(pb.getKey());
			} else {
				oKeyPairs = encApi.genKeys();
			}
			oRespGenerateAccount.setAddress(oKeyPairs.getAddress());
			oRespGenerateAccount.setPrivKey(oKeyPairs.getPrikey());
			oRespGenerateAccount.setPubKey(oKeyPairs.getPubkey());
			oRespGenerateAccount.setRetCode(1);
			oRespGenerateAccount.setRetMsg("success");
		} catch (Exception e) {
			oRespGenerateAccount.clear();
			oRespGenerateAccount.setRetCode(-1);
			oRespGenerateAccount.setRetMsg(e.getMessage());
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGenerateAccount.build()));
		return;
	}
}
