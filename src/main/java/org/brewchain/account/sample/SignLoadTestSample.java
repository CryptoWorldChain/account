package org.brewchain.account.sample;

import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqSignLoadTest;
import org.brewchain.account.gens.TxTest.ReqStartNewFork;
import org.brewchain.account.gens.TxTest.RespSignLoadTest;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

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
public class SignLoadTestSample extends SessionModules<ReqSignLoadTest> {

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.SLT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqSignLoadTest pb, final CompleteHandler handler) {
		RespSignLoadTest.Builder oRespSignLoadTest = RespSignLoadTest.newBuilder();
		final SecureRandom secureRandom = new SecureRandom();
		final KeyPairs oKeyPairs = encApi.genKeys();
		final byte[] bs = new byte[500];
		secureRandom.nextBytes(bs);

		parallRun(pb.getRepeated(), new Runnable() {
			@Override
			public void run() {
				sign(bs, oKeyPairs);
			}
		});

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespSignLoadTest.build()));
		return;
	}

	private void sign(byte[] bs, KeyPairs oKeyPairs) {
		byte[] sign = encApi.ecSign(oKeyPairs.getPrikey(), bs);
		encApi.ecVerify(oKeyPairs.getPubkey(), bs, sign);
	}

	final ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);

	public void parallRun(int size, final Runnable runer) {
		final CountDownLatch cdl = new CountDownLatch(size);
		long start = System.currentTimeMillis();
		for (int i = 0; i < size; i++) {
			pool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						runer.run();
					} catch (Throwable e) {
						e.printStackTrace();
					}
					cdl.countDown();
				}
			});
		}
		try {
			cdl.await(24, TimeUnit.HOURS);
		} catch (InterruptedException e) {
		}
		long end = System.currentTimeMillis();
		log.debug("tps test :: sign ::" + String.valueOf(size * 1000 / (end - start)));
	}

}
