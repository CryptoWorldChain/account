package org.brewchain.account.test;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.brewchain.account.util.ByteUtil;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadSignature extends Thread {
	private byte[] hash;
	private EncAPI encApi;

	public ThreadSignature(byte[] hash, EncAPI encApi) {
		this.hash = hash;
		this.encApi = encApi;
	}

	@Override
	public void run() {
		for (int i = 0; i < 1000; i++) {
			log.debug(String.format("%s", encApi.hexEnc(hash)));
		}
//		final Timer timer = new Timer();
//		// 设定定时任务
//		timer.schedule(new TimerTask() {
//			// 定时任务执行方法
//			@Override
//			public void run() {
//				
//				
//			}
//		}, 0, 5);
	}
}
