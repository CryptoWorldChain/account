package org.brewchain.account.evmapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.trie.StorageTrie;
import org.brewchain.account.trie.StorageTrieCache;
import org.brewchain.evm.api.EvmApi;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken.Builder;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@Data
public class EvmApiImp implements EvmApi {

	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@ActorRequire(name = "Storage_TrieCache", scope = "global")
	StorageTrieCache storageTrieCache;

	@Override
	public void saveCode(String code, String address) {
		accountHelper.saveCode(address, code);
	}

	@Override
	public Account CreateAccount(String arg0) {
		// TODO Auto-generated method stub
		return accountHelper.CreateAccount(arg0);
	}

	@Override
	public Account GetAccount(String addr) {
		// TODO Auto-generated method stub
		return accountHelper.GetAccount(addr);
	}

	@Override
	public Account GetAccountOrCreate(String addr) {
		// TODO Auto-generated method stub
		return accountHelper.GetAccountOrCreate(addr);
	}

	@Override
	public MultiTransaction GetTransaction(String txHash) {
		// TODO Auto-generated method stub
		try {
			return transactionHelper.GetTransaction(txHash);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void ICO(String addr, String token) {
		// TODO Auto-generated method stub
		try {
			accountHelper.ICO(addr, token);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public int IncreaseNonce(String addr) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.IncreaseNonce(addr);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public long addBalance(String addr, long balance) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.addBalance(addr, balance);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public long addCryptoBalance(String addr, String symbol, AccountCryptoToken.Builder token) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.addCryptoBalance(addr, symbol, token);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public long addTokenBalance(String addr, String token, long balance) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.addTokenBalance(addr, token, balance);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public long addTokenLockBalance(String addr, String token, long balance) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.addTokenLockBalance(addr, token, balance);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public long getBalance(String addr) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.getBalance(addr);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public byte[] getContractAddressByTransaction(MultiTransaction oMultiTransaction) {
		// TODO Auto-generated method stub
		try {
			String address = transactionHelper.getContractAddressByTransaction(oMultiTransaction);
			return encApi.hexDec(address);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public List<AccountCryptoToken> getCryptoTokenBalance(String addr, String symbol) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.getCryptoTokenBalance(addr, symbol);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public int getNonce(String addr) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.getNonce(addr);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public long getTokenBalance(String addr, String token) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.getTokenBalance(addr, token);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public long getTokenLockedBalance(String addr, String token) {
		// TODO Auto-generated method stub
		try {
			accountHelper.getTokenLockedBalance(addr, token);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public boolean isContract(String addr) {
		// TODO Auto-generated method stub
		return accountHelper.isContract(addr);
	}

	@Override
	public boolean isExist(String addr) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.isExist(addr);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean isExistsToken(String token) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.isExistsToken(token);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public int setNonce(String addr, int nonce) {
		// TODO Auto-generated method stub
		try {
			accountHelper.setNonce(addr, nonce);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public void saveStorage(String address, byte[] key, byte[] value) {
		accountHelper.saveStorage(address, key, value);
	}

	@Override
	public Map<String, byte[]> getStorage(String address, List<byte[]> keys) {
		Map<String, byte[]> storage = new HashMap<>();
		StorageTrie oStorage = accountHelper.getStorageTrie(address);
		for (int i = 0; i < keys.size(); i++) {
			storage.put(encApi.hexEnc(keys.get(i)), oStorage.get(keys.get(i)));
		}
		return storage;
	}

	@Override
	public byte[] getStorage(String address, byte[] key) {
		StorageTrie oStorage = accountHelper.getStorageTrie(address);
		return oStorage.get(key);
	}

	@Override
	public Account CreateContract(String arg0, byte[] arg1, byte[] arg2) {
		return null;
	}

	@Override
	public Account CreateUnionAccount(Account arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void generateCryptoToken(String arg0, String arg1, String[] arg2, String[] arg3) {
		// TODO Auto-generated method stub

	}

	@Override
	public long newCryptoBalances(String arg0, String arg1, ArrayList<Builder> arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long removeCryptoBalance(String arg0, String arg1, byte[] arg2) {
		// TODO Auto-generated method stub
		return 0;
	}
}
