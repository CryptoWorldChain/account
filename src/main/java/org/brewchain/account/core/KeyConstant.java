package org.brewchain.account.core;

import java.math.BigInteger;

import org.brewchain.account.util.NodeDef;

public class KeyConstant {
	public static final int STABLE_BLOCK = 20;
	public static final int BLOCK_REWARD = 6;
	public static final int BLOCK_REWARD_DELAY = 5;
	public static final String BC_UNSEND_TX = "bc_unsend_tx";
	public static final byte[] BC_UNSEND_TX_BYTE = BC_UNSEND_TX.getBytes();
	public static final String BC_CONFIRM_TX = "bc_confirm_tx";
	public static final byte[] BC_CONFIRM_TX_BYTE = BC_CONFIRM_TX.getBytes();
	public static final BigInteger EMPTY_NONCE = BigInteger.ZERO;
	public static final BigInteger EMPTY_BALANCE = BigInteger.ZERO;
	public static final int GENESIS_NUMBER = 0;
	public static final byte[] GENESIS_HASH = String.valueOf(GENESIS_NUMBER).getBytes();
	public static final int DEFAULT_BLOCK_TX_COUNT = 10000;
	public static final byte[] DB_CURRENT_BLOCK = "DB_CURRENT_BLOCK_y0yXF4880c".getBytes();
	public static final byte[] DB_CURRENT_MAX_BLOCK = "DB_CURRENT_MAX_BLOCK_y0yXF4880c".getBytes();

	public static final byte[] DB_EXISTS_TOKEN = "DB_EXISTS_TOKEN_7513d2287ce94891ba227ba83aa6fe51".getBytes();
	public static final int CACHE_SIZE = 100;

	public static boolean isStart = false;
	public static NodeDef node;
	public static String PWD;
}
