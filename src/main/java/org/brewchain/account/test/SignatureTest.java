package org.brewchain.account.test;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class SignatureTest extends SessionModules<ReqTxTest> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.SST.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		final RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();
		oRespTxTest.setRetCode(1234);

		// KeyPairs oKeyPairs1 = encApi.genKeys();
		// log.debug(String.format("import %s", oKeyPairs1.getPubkey()));
		//
		// String base64str =
		// encApi.base64Enc(encApi.ecSign(oKeyPairs1.getPrikey(),
		// oRespTxTest.build().toByteArray()));
		// log.debug(String.format("export %s",
		// encApi.hexEnc(encApi.ecToKeyBytes(oRespTxTest.build().toByteArray(),
		// base64str))));
		ThreadSignature oThreadSignature1 = new ThreadSignature(oRespTxTest.build().toByteArray(), encApi);
		oThreadSignature1.start();
		
		ThreadSignature oThreadSignature2 = new ThreadSignature(oRespTxTest.build().toByteArray(), encApi);
		oThreadSignature2.start();
		
		ThreadSignature oThreadSignature3 = new ThreadSignature(oRespTxTest.build().toByteArray(), encApi);
		oThreadSignature3.start();

		oRespTxTest.setRetCode(-1);
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}
}
