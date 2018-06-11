package org.brewchain.account.test;

import org.fc.brewchain.bcapi.EncAPI;

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
