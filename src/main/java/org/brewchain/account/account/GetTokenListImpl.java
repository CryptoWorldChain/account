package org.brewchain.account.account;

import java.util.List;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.gens.Actimpl.*;
import org.brewchain.evmapi.gens.Act.ERC20TokenValue;
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
public class GetTokenListImpl extends SessionModules<ReqQueryIC> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.QIC.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqQueryIC pb, final CompleteHandler handler) {
		RespQueryIC.Builder oRespQueryIC = RespQueryIC.newBuilder();

		try {
			List<ERC20TokenValue> tokens = oAccountHelper.getTokens(pb.getAddress(), pb.getToken());
			for (ERC20TokenValue erc20TokenValue : tokens) {
				MsgToken.Builder oMsgToken = MsgToken.newBuilder();
				oMsgToken.setAmount(String.valueOf(erc20TokenValue.getTotal()));
				oMsgToken.setCreator(erc20TokenValue.getAddress());
				oMsgToken.setTimestamp(String.valueOf(erc20TokenValue.getTimestamp()));
				oMsgToken.setToken(erc20TokenValue.getToken());
				oRespQueryIC.addTokens(oMsgToken.build());
			}
			oRespQueryIC.setRetCode(1);
		} catch (Exception e) {
			oRespQueryIC.clear();
			oRespQueryIC.setRetCode(-1);
			oRespQueryIC.setRetMsg("error on query token::" + e);
		}
		
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespQueryIC.build()));
	}
}
