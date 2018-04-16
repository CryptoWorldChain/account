package org.brewchain.frontend.core;

import java.math.BigInteger;

public class KeyConstant {

	public static final int BLOCK_REWARD = 6;
	public static final int BLOCK_REWARD_DELAY = 5;
	public static String BC_UNSEND_TX = "bc_unsend_tx";
	public static byte[] BC_UNSEND_TX_BYTE = BC_UNSEND_TX.getBytes();

	public static String BC_CONFIRM_TX = "bc_confirm_tx";
	public static byte[] BC_CONFIRM_TX_BYTE = BC_CONFIRM_TX.getBytes();

	public static BigInteger EMPTY_NONCE = BigInteger.ZERO;
	public static BigInteger EMPTY_BALANCE = BigInteger.ZERO;

	public static String tag = "1.0";
	public static String type = "eth";
	public static int chainId = 1;

	public static String COIN_BASE = "address";
	public static byte[] COIN_BASE_BYTE = COIN_BASE.getBytes();

	public static int GENESIS_NUMBER = 0;
	public static byte[] GENESIS_HASH = String.valueOf(GENESIS_NUMBER).getBytes();
	
	public static int DEFAULT_BLOCK_TX_COUNT = 1000;
}
