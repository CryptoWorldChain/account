package org.brewchain.account.core;

import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOrBuilder;

public class TXStatus {

	public static boolean isDone(MultiTransactionOrBuilder mtx) {
		return "D".equals(mtx.getStatus());
	}

	public static boolean isError(MultiTransactionOrBuilder mtx) {
		return "E".equals(mtx.getStatus());
	}

	public static boolean isProccessed(MultiTransactionOrBuilder mtx) {
		return isDone(mtx) || isError(mtx);
	}

	public static void setDone(MultiTransaction.Builder mtx) {
		mtx.setStatus("D");
	}

	public static void setError(MultiTransaction.Builder mtx) {
		mtx.setStatus("E");
	}
}
