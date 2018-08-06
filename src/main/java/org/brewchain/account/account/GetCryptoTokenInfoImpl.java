package org.brewchain.account.account;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.gens.Actimpl.MsgCryptoToken;
import org.brewchain.account.gens.Actimpl.PACTCommand;
import org.brewchain.account.gens.Actimpl.PACTModule;
import org.brewchain.account.gens.Actimpl.ReqQueryCryptoToken;
import org.brewchain.account.gens.Actimpl.ReqQueryToken;
import org.brewchain.account.gens.Actimpl.RespQueryCryptoToken;
import org.brewchain.account.gens.Actimpl.RespQueryToken;
import org.brewchain.evmapi.gens.Act.CryptoTokenValue;
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
public class GetCryptoTokenInfoImpl extends SessionModules<ReqQueryCryptoToken> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.QII.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqQueryCryptoToken pb, final CompleteHandler handler) {
		RespQueryCryptoToken.Builder oRespQueryCryptoToken = RespQueryCryptoToken.newBuilder();

		CryptoTokenValue oCryptoTokenValue = oAccountHelper.getCryptoTokenValue(pb.getSymbolBytes());
		if (oCryptoTokenValue == null) {
			oRespQueryCryptoToken.setRetCode(-1);
			oRespQueryCryptoToken.setRetMsg("cannot find crypto token");
		} else {
			MsgCryptoToken.Builder oMsgCryptoToken = MsgCryptoToken.newBuilder();
			oMsgCryptoToken.setAddress(encApi.hexEnc(oCryptoTokenValue.getOwner().toByteArray()));
			oMsgCryptoToken.setCurrent(oCryptoTokenValue.getCurrent());
			oMsgCryptoToken.setTimestamp(oCryptoTokenValue.getTimestamp());
			oMsgCryptoToken.setTotal(oCryptoTokenValue.getTotal());
			oRespQueryCryptoToken.setToken(oMsgCryptoToken);
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespQueryCryptoToken.build()));
	}
}
