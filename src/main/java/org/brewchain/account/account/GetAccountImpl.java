package org.brewchain.account.account;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Act.PACTCommand;
import org.brewchain.account.gens.Act.PACTModule;
import org.brewchain.account.gens.Act.ReqGetAccount;
import org.brewchain.account.gens.Act.RespGetAccount;
import org.fc.brewchain.bcapi.EncAPI;

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
public class GetAccountImpl extends SessionModules<ReqGetAccount> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.GAC.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetAccount pb, final CompleteHandler handler) {
		RespGetAccount.Builder oRespGetAccount = RespGetAccount.newBuilder();

		try {
			Account oAccount = oAccountHelper.GetAccount(encApi.hexDec(pb.getAddress()));
			oRespGetAccount.setAccount(oAccount);
			oRespGetAccount.setRetCode(1);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			oRespGetAccount.setRetCode(-1);
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetAccount.build()));
	}
}
