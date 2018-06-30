package org.brewchain.account.evmapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.trie.StorageTrie;
import org.brewchain.account.trie.StorageTrieCache;
import org.brewchain.evm.api.EvmApi;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken.Builder;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.Data;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@Data
public class EvmApiImp implements EvmApi {
	AccountHelper accountHelper;
	BlockChainHelper blockChainHelper;
	TransactionHelper transactionHelper;
	EncAPI encApi;
	StorageTrieCache storageTrieCache;

	@Override
	public void saveCode(ByteString code, ByteString address) {
		accountHelper.saveCode(address, code);
	}

	@Override
	public Account CreateAccount(ByteString arg0) {
		// TODO Auto-generated method stub
		return accountHelper.CreateAccount(arg0);
	}

	@Override
	public Account GetAccount(ByteString addr) {
		// TODO Auto-generated method stub
		return accountHelper.GetAccount(addr);
	}

	@Override
	public Account GetAccountOrCreate(ByteString addr) {
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
	public void ICO(ByteString addr, String token) {
		// TODO Auto-generated method stub
		try {
			accountHelper.ICO(addr, token, 0);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public int IncreaseNonce(ByteString addr) {
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
	public long addBalance(ByteString addr, long balance) {
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
	public long addCryptoBalance(ByteString addr, String symbol, AccountCryptoToken.Builder token) {
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
	public long addTokenBalance(ByteString addr, String token, long balance) {
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
	public long addTokenLockBalance(ByteString addr, String token, long balance) {
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
	public long getBalance(ByteString addr) {
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
			ByteString address = transactionHelper.getContractAddressByTransaction(oMultiTransaction);
			return address.toByteArray();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public List<AccountCryptoToken> getCryptoTokenBalance(ByteString addr, ByteString symbol) {
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
	public int getNonce(ByteString addr) {
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
	public long getTokenBalance(ByteString addr, ByteString token) {
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
	public long getTokenLockedBalance(ByteString addr, ByteString token) {
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
	public boolean isContract(ByteString addr) {
		// TODO Auto-generated method stub
		return accountHelper.isContract(addr);
	}

	@Override
	public boolean isExist(ByteString addr) {
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
	public int setNonce(ByteString addr, int nonce) {
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
	public void saveStorage(ByteString address, byte[] key, byte[] value) {
		accountHelper.saveStorage(address, key, value);
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

	@Override
	public byte[] getBlockHashByNumber(int number) {
		try {
			BlockEntity block = blockChainHelper.getBlockByNumber(number);
			if (block == null) {
				return null;
			} else {
				return encApi.hexDec(block.getHeader().getBlockHash());
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
