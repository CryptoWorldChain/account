package org.brewchain.frontend.account;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.frontend.core.AccountHelper;
import org.brewchain.frontend.dao.DefDaos;
import org.brewchain.frontend.gens.Act.PACTCommand;
import org.brewchain.frontend.gens.Act.PACTModule;
import org.brewchain.frontend.gens.Act.ReqCreateAccount;
import org.brewchain.frontend.gens.Act.RespCreateAccount;
import org.brewchain.frontend.util.OEntityBuilder;

import com.google.protobuf.ByteString;
import com.google.protobuf.Value;

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
public class CreateAccountImpl extends SessionModules<ReqCreateAccount> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;

	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.CAC.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateAccount pb, final CompleteHandler handler) {
		RespCreateAccount.Builder oRespCreateAccount = RespCreateAccount.newBuilder();
		// ExAccountState state = new ExAccountState(BigInteger.ZERO,
		// BigInteger.ZERO);
		try {
			oAccountHelper.CreateAccount(pb.getAddress().toByteArray(), pb.getPubKey().toByteArray());
			oRespCreateAccount.setRetCode(1);
		} catch (Exception e) {
			e.printStackTrace();
			oRespCreateAccount.setRetCode(-1);
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateAccount.build()));
	}
}
