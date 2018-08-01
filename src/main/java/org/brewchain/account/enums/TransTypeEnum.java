package org.brewchain.account.enums;

public enum TransTypeEnum {
	TYPE_DEFAULT(0), TYPE_CreateUnionAccount(1), TYPE_TokenTransaction(2), TYPE_UnionAccountTransaction(
			3), TYPE_CallInternalFunction(4), TYPE_CryptoTokenTransaction(5), TYPE_LockTokenTransaction(
					6), TYPE_CreateContract(7), TYPE_CallContract(8), TYPE_CreateToken(9), TYPE_CreateCryptoToken(10);
	private int value = 0;

	private TransTypeEnum(int value) {
		this.value = value;
	}

	public int value() {
		return this.value;
	}

	public static TransTypeEnum transf(int t) {
		for (TransTypeEnum type : values()) {
			if (type.value == t) {
				return type;
			}
		}
		return TYPE_DEFAULT;
	}
}
