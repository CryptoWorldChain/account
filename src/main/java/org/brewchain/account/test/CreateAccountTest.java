package org.brewchain.account.test;

import java.security.SecureRandom;
import java.util.Date;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCommonTest;
import org.brewchain.account.gens.TxTest.ReqCreateToken;
import org.brewchain.account.gens.TxTest.RespCommonTest;
import org.brewchain.account.sample.CreateTokenTransaction;
import org.brewchain.account.util.IDGenerator;
import org.fc.brewchain.bcapi.EncAPI;

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
public class CreateAccountTest extends SessionModules<ReqCommonTest> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	// @ActorRequire(name = "BlockStore_UnStable", scope = "global")
	// BlockUnStableStore unStableStore;
	@ActorRequire(name = "BlockStore_Helper", scope = "global")
	BlockStore blockStore;

	private static final String CHAR_LIST = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.CAT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCommonTest pb, final CompleteHandler handler) {
		RespCommonTest.Builder oRespCommonTest = RespCommonTest.newBuilder();
		IDGenerator id = IDGenerator.getInstance();
		SecureRandom secureRandom = new SecureRandom();
		accountHelper.GetAccountOrCreate(ByteString.copyFrom(
				(id.generate() + CHAR_LIST.charAt(secureRandom.nextInt(CHAR_LIST.length())) + new Date().getTime())
						.getBytes()));
		
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespCommonTest.build()));
		return;
	}
}
