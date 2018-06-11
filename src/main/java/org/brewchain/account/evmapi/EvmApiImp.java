package org.brewchain.account.evmapi;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.evm.api.EvmApi;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import onight.tfw.ntrans.api.annotation.ActorRequire;

public class EvmApiImp implements EvmApi {

	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public Account CreateAccount(byte[] arg0, byte[] arg1) {
		// TODO Auto-generated method stub
		return accountHelper.CreateAccount(arg0, arg1);
	}

	@Override
	public Account CreateAccount(byte[] address, byte[] pubKey, long max, long acceptMax, int acceptLimit,
			List<ByteString> addresses, byte[] code, byte[] exdata) {
		// TODO Auto-generated method stub
		return accountHelper.CreateAccount(address, pubKey, max, acceptMax, acceptLimit, addresses, code, exdata);
	}

	@Override
	public Account CreateContract(byte[] address, byte[] pubKey, byte[] code, byte[] exdata) {
		// TODO Auto-generated method stub
		return accountHelper.CreateContract(address, pubKey, code, exdata);
	}

	@Override
	public ByteString CreateGenesisMultiTransaction(MultiTransaction.Builder oMultiTransaction) {
		// TODO Auto-generated method stub
		try {
			return transactionHelper.CreateGenesisMultiTransaction(oMultiTransaction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public ByteString CreateMultiTransaction(MultiTransaction.Builder oMultiTransaction) {
		// TODO Auto-generated method stub
		try {
			return transactionHelper.CreateMultiTransaction(oMultiTransaction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Account CreateUnionAccount(Account oAccount) {
		// TODO Auto-generated method stub
		return accountHelper.CreateUnionAccount(oAccount);
	}

	@Override
	public void DeleteAccount(byte[] address) {
		// TODO Auto-generated method stub
		accountHelper.DeleteAccount(address);
	}

	@Override
	public void ExecuteTransaction(LinkedList<MultiTransaction> oMultiTransactions) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.ExecuteTransaction(oMultiTransactions);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Account GetAccount(byte[] addr) {
		// TODO Auto-generated method stub
		return accountHelper.GetAccount(addr);
	}

	@Override
	public Account GetAccountOrCreate(byte[] addr) {
		// TODO Auto-generated method stub
		return accountHelper.GetAccountOrCreate(addr);
	}

	@Override
	public MultiTransaction GetTransaction(byte[] txHash) {
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
	public void ICO(byte[] addr, String token) {
		// TODO Auto-generated method stub
		try {
			accountHelper.ICO(addr, token);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public int IncreaseNonce(byte[] addr) {
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
	public void Signature(List<String> privKeys, MultiTransaction.Builder oTransaction) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.Signature(privKeys, oTransaction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void SyncTransaction(MultiTransaction.Builder oMultiTransaction) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.syncTransaction(oMultiTransaction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public long addBalance(byte[] addr, long balance) {
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
	public long addCryptoBalance(byte[] addr, String symbol, AccountCryptoToken.Builder token) {
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
	public long addTokenBalance(byte[] addr, String token, long balance) {
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
	public long addTokenLockBalance(byte[] addr, String token, long balance) {
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
	public void generateCryptoToken(byte[] addr, String symbol, String[] name, String[] code) {
		// TODO Auto-generated method stub
		try {
			accountHelper.generateCryptoToken(addr, symbol, name, code);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public long getBalance(byte[] addr) {
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
			return transactionHelper.getContractAddressByTransaction(oMultiTransaction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public List<AccountCryptoToken> getCryptoTokenBalance(byte[] addr, String symbol) {
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
	public int getNonce(byte[] addr) {
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
	public long getTokenBalance(byte[] addr, String token) {
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
	public long getTokenLockedBalance(byte[] addr, String token) {
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
	public void getTransactionHash(MultiTransaction.Builder oTransaction) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.getTransactionHash(oTransaction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public LinkedList<MultiTransaction> getWaitBlockTx(int count) {
		// TODO Auto-generated method stub
		try {
			return transactionHelper.getWaitBlockTx(count);
		} catch (InvalidProtocolBufferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public List<MultiTransaction> getWaitSendTx(int count) {
		// TODO Auto-generated method stub
		try {
			return transactionHelper.getWaitSendTx(count);
		} catch (InvalidProtocolBufferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean isContract(byte[] addr) {
		// TODO Auto-generated method stub
		return accountHelper.isContract(addr);
	}

	@Override
	public boolean isExist(byte[] addr) {
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
	public long newCryptoBalances(byte[] addr, String symbol, ArrayList<AccountCryptoToken.Builder> tokens) {
		// TODO Auto-generated method stub
		try {
			return accountHelper.newCryptoBalances(addr, symbol, tokens);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public void putAccountValue(byte[] addr, AccountValue oAccountValue) {
		// TODO Auto-generated method stub
		accountHelper.putAccountValue(addr, oAccountValue);
	}

	@Override
	public long removeCryptoBalance(byte[] addr, String symbol, byte[] hash) {
		// TODO Auto-generated method stub
		return accountHelper.removeCryptoBalance(addr, symbol, hash);
	}

	@Override
	public void removeWaitBlockTx(byte[] txHash) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.removeWaitBlockTx(encApi.hexEnc(txHash));
		} catch (InvalidProtocolBufferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public int setNonce(byte[] addr, int nonce) {
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
	public void setTransactionDone(byte[] txHash) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.setTransactionDone(txHash);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void setTransactionError(byte[] txHash) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.setTransactionError(txHash);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public MultiTransaction verifyAndSaveMultiTransaction(MultiTransaction.Builder oMultiTransaction) {
		// TODO Auto-generated method stub
		try {
			return transactionHelper.verifyAndSaveMultiTransaction(oMultiTransaction);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void verifySignature(String pubKey, String signature, byte[] tx) {
		// TODO Auto-generated method stub
		try {
			transactionHelper.verifySignature(pubKey, signature, tx);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
