package org.brewchain.frontend.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OPair;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.frontend.core.KeyConstant;
import org.brewchain.frontend.core.TransactionHelper;
import org.brewchain.frontend.dao.DefDaos;
import org.brewchain.frontend.gens.Tx.MultiTransaction;
import org.brewchain.frontend.gens.Tx.PTXTCommand;
import org.brewchain.frontend.gens.Tx.PTXTModule;
import org.brewchain.frontend.gens.Tx.ReqSyncTx;
import org.brewchain.frontend.gens.Tx.RespSyncTx;
import org.brewchain.frontend.util.OEntityBuilder;

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
public class SyncTransactionImpl extends SessionModules<ReqSyncTx> {
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@Override
	public String[] getCmds() {
		return new String[] { PTXTCommand.AYC.name() };
	}

	@Override
	public String getModule() {
		return PTXTModule.TXT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqSyncTx pb, final CompleteHandler handler) {
		RespSyncTx.Builder oRespSyncTx = RespSyncTx.newBuilder();
		oRespSyncTx.setRetCode(1);
		List<ByteString> list = new ArrayList<ByteString>();
		for (MultiTransaction oTransaction : pb.getTxsList()) {
			try {
				transactionHelper.SyncTransaction(oTransaction.toBuilder());
			} catch (Exception e) {
				list.add(oTransaction.getTxHash());
				oRespSyncTx.setRetCode(-1);
			}
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespSyncTx.build()));
	}
}
