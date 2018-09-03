package org.brewchain.account.account;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.gens.Actimpl.AccountCryptoTokenImpl;
import org.brewchain.account.gens.Actimpl.AccountCryptoValueImpl;
import org.brewchain.account.gens.Actimpl.AccountTokenValueImpl;
import org.brewchain.account.gens.Actimpl.AccountValueImpl;
import org.brewchain.account.gens.Actimpl.PACTCommand;
import org.brewchain.account.gens.Actimpl.PACTModule;
import org.brewchain.account.gens.Actimpl.ReqGetAccount;
import org.brewchain.account.gens.Actimpl.ReqGetStorage;
import org.brewchain.account.gens.Actimpl.RespGetAccount;
import org.brewchain.account.gens.Actimpl.RespGetStorage;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoValue;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;

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
public class GetAccountStorageImpl extends SessionModules<ReqGetStorage> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.GAS.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetStorage pb, final CompleteHandler handler) {
		RespGetStorage.Builder oRespGetStorage = RespGetStorage.newBuilder();

		try {
			Account.Builder oAccount = oAccountHelper
					.GetAccount(ByteString.copyFrom(encApi.hexDec(ByteUtil.formatHexAddress(pb.getAddress()))));

			for (int i = 0; i < pb.getKeyCount(); i++) {
				oRespGetStorage.addContent(encApi.hexEnc(oAccountHelper.getStorage(oAccount, encApi.hexDec(pb.getKey(i)))));
			}
			oRespGetStorage.setRetMsg("success");
			oRespGetStorage.setRetCode(1);
		} catch (Exception e) {
			log.error("GetAccountImpl error::" + pb.getAddress(), e);
			oRespGetStorage.clear();
			oRespGetStorage.setRetCode(-1);
			oRespGetStorage.setRetMsg(e.getMessage());
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetStorage.build()));
	}
}
