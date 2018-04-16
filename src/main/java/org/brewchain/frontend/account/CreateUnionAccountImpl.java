package org.brewchain.frontend.account;

import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.frontend.core.AccountHelper;
import org.brewchain.frontend.core.TransactionHelper;
import org.brewchain.frontend.dao.DefDaos;
import org.brewchain.frontend.gens.Act.PACTCommand;
import org.brewchain.frontend.gens.Act.PACTModule;
import org.brewchain.frontend.gens.Act.ReqCreateAccount;
import org.brewchain.frontend.gens.Act.ReqCreateUnionAccount;
import org.brewchain.frontend.gens.Act.RespCreateAccount;
import org.brewchain.frontend.gens.Act.RespCreateUnionAccount;
import org.brewchain.frontend.gens.Tx.SingleTransaction;
import org.brewchain.frontend.util.ByteUtil;
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
public class CreateUnionAccountImpl extends SessionModules<ReqCreateUnionAccount> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	
	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.UAC.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateUnionAccount pb, final CompleteHandler handler) {
		RespCreateUnionAccount.Builder oRespCreateUnionAccount = RespCreateUnionAccount.newBuilder();
		try {
			// 创建多重签名账户
			accountHelper.CreateUnionAccount(pb.getAddress().toByteArray(), ByteUtil.EMPTY_BYTE_ARRAY, pb.getMax(),
					pb.getAcceptMax(), pb.getAcceptLimit(), pb.getRelAddressList());
			
			oRespCreateUnionAccount.setRetCode(1);
		} catch (Exception e) {
			e.printStackTrace();
			oRespCreateUnionAccount.setRetCode(-1);
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCreateUnionAccount.build()));
	}
}
