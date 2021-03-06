package org.brewchain.account.evmapi;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.trie.StorageTrie;
import org.brewchain.account.trie.StorageTrieCache;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evm.api.EvmApi;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;
import lombok.Data;

@Data
public class EvmApiImp implements EvmApi {
	AccountHelper accountHelper;
	BlockChainHelper blockChainHelper;
	TransactionHelper transactionHelper;
	EncAPI encApi;
	StorageTrieCache storageTrieCache;

	Map<String, Account> touchAccount = new HashMap<>();

	private Account.Builder getAccount(ByteString addr) {
		if (!touchAccount.containsKey(encApi.hexEnc(addr.toByteArray()))) {
			return accountHelper.GetAccount(addr);
		} else {
			return touchAccount.get(encApi.hexEnc(addr.toByteArray())).toBuilder();
		}
	}

	@Override
	public Account GetAccount(ByteString addr) {
		return getAccount(addr).build();
	}

	@Override
	public BigInteger addBalance(ByteString addr, BigInteger balance) {
		try {
			Account.Builder oAccount = getAccount(addr);
			if (oAccount == null) {

			} else {
				AccountValue.Builder value = oAccount.getValue().toBuilder();
				value.setBalance(
						ByteString.copyFrom(ByteUtil.bytesAddToBytes(balance, value.getBalance().toByteArray())));

				oAccount.setValue(value);
			}
			touchAccount.put(encApi.hexEnc(addr.toByteArray()), oAccount.build());
			return ByteUtil.bytesToBigInteger(oAccount.getValue().getBalance().toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return BigInteger.ZERO;
	}

	@Override
	public BigInteger getBalance(ByteString addr) {
		try {
			return accountHelper.getBalance(addr);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return BigInteger.ZERO;
	}

	@Override
	public boolean isExist(ByteString addr) {
		try {
			return accountHelper.isExist(addr);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public void saveStorage(ByteString address, byte[] key, byte[] value) {
		// accountHelper.saveStorage(address, key, value);
		
		try {
			Account.Builder oAccount = getAccount(address);
			if (oAccount != null) {
				AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
				StorageTrie oStorage = accountHelper.getStorageTrie(address);
				oStorage.put(key, value);
				byte[] rootHash = oStorage.getRootHash();
				oAccountValue.setStorage(ByteString.copyFrom(rootHash));
				oAccount.setValue(oAccountValue);
				touchAccount.put(encApi.hexEnc(address.toByteArray()), oAccount.build());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Map<String, byte[]> getStorage(ByteString address, List<byte[]> keys) {
		Map<String, byte[]> storage = new HashMap<>();
		StorageTrie oStorage = accountHelper.getStorageTrie(address);
		for (int i = 0; i < keys.size(); i++) {
			storage.put(encApi.hexEnc(keys.get(i)), oStorage.get(keys.get(i)));
		}
		return storage;
	}

	@Override
	public byte[] getStorage(ByteString address, byte[] key) {
		StorageTrie oStorage = accountHelper.getStorageTrie(address);
		return oStorage.get(key);
	}

	// @Override
	// public byte[] getBlockHashByNumber(int number) {
	// try {
	// BlockEntity block = blockChainHelper.getBlockByNumber(number);
	// if (block == null) {
	// return null;
	// } else {
	// return encApi.hexDec(block.getHeader().getBlockHash());
	// }
	// } catch (Exception e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// return null;
	// }
}
