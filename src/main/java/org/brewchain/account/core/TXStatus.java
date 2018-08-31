package org.brewchain.account.core;

import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOrBuilder;

import com.google.protobuf.ByteString;

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
		setDone(mtx, ByteString.EMPTY);
	}

	public static void setDone(MultiTransaction.Builder mtx, ByteString result) {
		mtx.setStatus("D");
		mtx.setResult(result);
	}

	public static void setError(MultiTransaction.Builder mtx) {
		setError(mtx, ByteString.EMPTY);
	}

	public static void setError(MultiTransaction.Builder mtx, ByteString result) {
		mtx.setStatus("E");
		mtx.setResult(result);
	}
}
