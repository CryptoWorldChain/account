package org.brewchain.account.transaction;

import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.Tximpl.PTXTCommand;
import org.brewchain.account.gens.Tximpl.PTXTModule;
import org.brewchain.account.gens.Tximpl.SendLockTokenTransaction;
import org.brewchain.account.gens.Tximpl.SendNormalTransferTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class SendNormalTransferTransactionImpl  extends SessionModules<SendNormalTransferTransaction>{
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	
	@Override
	public String[] getCmds() {
		return new String[] { PTXTCommand.NOT.name() };
	}

	@Override
	public String getModule() {
		return PTXTModule.TXT.name();
	}
	
	@Override
	public void onPBPacket(final FramePacket pack, final SendNormalTransferTransaction pb, final CompleteHandler handler) {
		
	}
}
