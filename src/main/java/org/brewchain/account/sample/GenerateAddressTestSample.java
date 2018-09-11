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
public class GenerateAddressTestSample extends SessionModules<ReqSignLoadTest> {

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.GLT.name() };
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
		long start = 0;
		long end = 0;
		if (pb.getTestCase().equals("sign")) {
			start = System.currentTimeMillis();

			for (int i = 0; i < pb.getRepeated(); i++) {
				encApi.ecSign(oKeyPairs.getPrikey(), bs1);
			}
			end = System.currentTimeMillis();
		} else if (pb.getTestCase().equals("verify")) {
			final byte[] sign = encApi.ecSign(oKeyPairs.getPrikey(), bs);
			start = System.currentTimeMillis();

			for (int i = 0; i < pb.getRepeated(); i++) {
				encApi.ecVerify(oKeyPairs.getPubkey(), bs, sign);
			}
			end = System.currentTimeMillis();
		}
		
		oRespSignLoadTest.setStart(start);
		oRespSignLoadTest.setEnd(end);
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespSignLoadTest.build()));
		return;
	}

	private void gen() {
		encApi.genKeys();
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
		log.debug("tps test :: gen address ::" + String.valueOf(size * 1000 / (end - start)));
	}
}
