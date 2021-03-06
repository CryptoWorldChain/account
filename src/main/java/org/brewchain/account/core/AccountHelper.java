package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.AccountNotFoundException;
import org.brewchain.account.exception.AccountTokenNotFoundException;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.trie.StorageTrie;
import org.brewchain.account.trie.StorageTrieCache;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountContract;
import org.brewchain.evmapi.gens.Act.AccountContractValue;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountOrBuilder;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.CryptoTokenValue;
import org.brewchain.evmapi.gens.Act.ERC20Token;
import org.brewchain.evmapi.gens.Act.ERC20TokenValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

/**
 * @author
 * 
 */
@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Account_Helper")
@Slf4j
@Data
public class AccountHelper implements ActorService {
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;
	@ActorRequire(name = "Storage_TrieCache", scope = "global")
	StorageTrieCache storageTrieCache;
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;

	public AccountHelper() {
	}

	public Account.Builder CreateAccount(ByteString address) {
		return CreateAccount(address, ByteString.copyFrom(ByteUtil.ZERO_BYTE_ARRAY),
				ByteString.copyFrom(ByteUtil.ZERO_BYTE_ARRAY), 0, null, null, null);
	}

	public Account.Builder CreateUnionAccount(ByteString address, ByteString max, ByteString acceptMax, int acceptLimit,
			List<ByteString> addresses) {
		return CreateAccount(address, max, acceptMax, acceptLimit, addresses, null, null);
	}

	public Account.Builder CreateAccount(ByteString address, ByteString max, ByteString acceptMax, int acceptLimit,
			List<ByteString> addresses, ByteString code, ByteString exdata) {
		Account.Builder oUnionAccount = Account.newBuilder();
		AccountValue.Builder oUnionAccountValue = AccountValue.newBuilder();

		oUnionAccountValue.setAcceptLimit(acceptLimit);
		oUnionAccountValue.setAcceptMax(acceptMax);
		if (addresses != null) {
			for (int i = 0; i < addresses.size(); i++) {
				oUnionAccountValue.addAddress(addresses.get(i));
			}
		}

		oUnionAccountValue.setBalance(ByteString.copyFrom(ByteUtil.ZERO_BYTE_ARRAY));
		oUnionAccountValue.setMax(max);
		oUnionAccountValue.setNonce(KeyConstant.EMPTY_NONCE.intValue());

		if (code != null) {
			oUnionAccountValue.setCode(code);
			oUnionAccountValue.setCodeHash(ByteString.copyFrom(encApi.sha3Encode(code.toByteArray())));
		}

		if (exdata != null) {
			oUnionAccountValue.setData(exdata);
		}
		oUnionAccount.setAddress(address);
		oUnionAccount.setValue(oUnionAccountValue);
		return oUnionAccount;
	}

	/**
	 * 移除账户。删除后不可恢复。
	 * 
	 * @param address
	 */

	public void DeleteAccount(byte[] address) {
		dao.getAccountDao().delete(oEntityHelper.byteKey2OKey(address));
		if (this.stateTrie != null)
			this.stateTrie.delete(address);
	}

	public void saveCode(ByteString address, ByteString code) {
		Account.Builder oAccount = GetAccount(address);
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setCode(code);
		oAccountValue.setCodeHash(ByteString.copyFrom(encApi.sha3Encode(code.toByteArray())));
		putAccountValue(address, oAccountValue.build());
	}

	/**
	 * 账户是否存在
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */

	public boolean isExist(ByteString addr) throws Exception {
		return GetAccount(addr) != null;
	}

//	Cache<String, AccountValue.Builder> accountByHash = CacheBuilder.newBuilder().initialCapacity(10000)
//			.expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(300000)
//			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).build();

	/**
	 * 获取用户账户
	 * 
	 * @param addr
	 * @return
	 */
//	public Account.Builder GetAccount(ByteString addr) {
//		Account.Builder oAccount = null;
//		String addrHex = encApi.hexEnc(addr.toByteArray());
//		try {
//			oAccount = Account.newBuilder();
//			oAccount.setAddress(addr);
//			byte[] valueHash = null;
//			AccountValue.Builder cacheAcct = accountByHash.getIfPresent(addrHex);
//			if (cacheAcct != null) {
//				oAccount.setValue(cacheAcct);
//			} else if (this.stateTrie != null) {
//				valueHash = this.stateTrie.get(addr.toByteArray());
//				if (valueHash != null) {
//					AccountValue.Builder oAccountValue = AccountValue.newBuilder();
//					oAccountValue.mergeFrom(valueHash);
//					oAccount.setValue(oAccountValue);
//					accountByHash.put(addrHex, oAccountValue);
//				}
//			}
//		} catch (Exception e) {
//			log.error("account not found::" + encApi.hexEnc(addr.toByteArray()), e);
//			oAccount = GetAccountFromDB(addr);
//		}
//		return oAccount;
//	}
	
	public Account.Builder GetAccount(ByteString addr) {
		try {
			Account.Builder oAccount = Account.newBuilder();
			oAccount.setAddress(addr);
			byte[] valueHash = null;
			if (this.stateTrie != null) {
				valueHash = this.stateTrie.get(addr.toByteArray());
				if (valueHash != null) {
					AccountValue.Builder oAccountValue = AccountValue.newBuilder();
					oAccountValue.mergeFrom(valueHash);
					oAccount.setValue(oAccountValue);

					return oAccount;
				}
			}
		} catch (Exception e) {
			log.error("account not found::" + encApi.hexEnc(addr.toByteArray()), e);
		}
		return null;
	}


	private Account.Builder GetAccountFromDB(ByteString addr) {
		try {
			Account.Builder oAccount = Account.newBuilder();
			oAccount.setAddress(addr);
			OValue o = dao.getAccountDao().get(oEntityHelper.byteKey2OKey(addr.toByteArray())).get();
			AccountValue oAccountValue = AccountValue.parseFrom(o.getExtdata().toByteArray());
			oAccount.setValue(oAccountValue);
			return oAccount;
		} catch (Exception e) {
//			log.error("account not found::" + encApi.hexEnc(addr.toByteArray()));
		}
		return null;
	}

	public List<AccountContractValue> getContractByCreator(ByteString addr) {
		List<AccountContractValue> contracts = new ArrayList<>();
		try {
			AccountContract oAccountContract = null;
			OValue oOValue = dao.getCommonDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_EXISTS_CONTRACT)).get();
			if (oOValue != null && oOValue.getExtdata() != null) {
				oAccountContract = AccountContract.parseFrom(oOValue.getExtdata().toByteArray());
			}
			if (oAccountContract != null) {
				for (AccountContractValue oAccountContractValue : oAccountContract.getValueList()) {
					if (oAccountContractValue.getAddress().equals(encApi.hexEnc(addr.toByteArray()))) {
						contracts.add(oAccountContractValue);
					}
				}
			}
		} catch (Throwable e) {
			log.error("cannot find contract::" + e);
		}
		return contracts;
	}

	/**
	 * 获取用户账户，如果用户不存在，则创建账户
	 * 
	 * @param addr
	 * @return
	 */
	public Account.Builder GetAccountOrCreate(ByteString addr) {
		try {
			Account.Builder oAccount = GetAccount(addr);
			if (oAccount == null) {
				oAccount = CreateAccount(addr);
//				String addrHex = encApi.hexEnc(addr.toByteArray());
//				accountByHash.put(addrHex, oAccount.getValueBuilder());
			}
			return oAccount;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	

	/**
	 * Nonce自增1
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public int IncreaseNonce(ByteString addr) throws Exception {
		return setNonce(addr, 1);
	}

	public synchronized Account addBalance(ByteString addr, BigInteger balance) throws Exception {
		Account.Builder oAccount = GetAccount(addr);
		if (oAccount == null) {
			oAccount = CreateAccount(addr);
			// throw new AccountNotFoundException("account " +
			// encApi.hexEnc(addr.toByteArray()) + " not found");
		}
		addBalance(oAccount, balance);
		return oAccount.build();
	}

	public synchronized BigInteger addBalance(Account.Builder oAccount, BigInteger balance) throws Exception {
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setBalance(ByteString.copyFrom(ByteUtil
				.bigIntegerToBytes(balance.add(ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray())))));
		oAccount.setValue(oAccountValue);
		return ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray());
	}

	public synchronized BigInteger subBalance(Account.Builder oAccount, BigInteger balance) throws Exception {
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setBalance(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(
				ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray()).subtract(balance))));
		oAccount.setValue(oAccountValue);
		return ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray());
	}

	public synchronized BigInteger addTokenBalance(ByteString addr, String token, BigInteger balance) throws Exception {
		Account.Builder oAccount = GetAccount(addr);
		if (oAccount == null) {
			oAccount = CreateAccount(addr);
			// throw new AccountNotFoundException("account " +
			// encApi.hexEnc(addr.toByteArray()) + " not found");
		}
		return addTokenBalance(oAccount, token, balance);
	}

	public synchronized BigInteger addTokenBalance(Account.Builder oAccount, String token, BigInteger balance)
			throws Exception {
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.setTokens(i, oAccountValue.getTokens(i).toBuilder()
						.setBalance(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance.add(
								ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray()))))));
				oAccount.setValue(oAccountValue);
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray());
			}
		}
		// 如果token账户余额不存在，直接增加一条记录
		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setBalance(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance)));
		oAccountTokenValue.setToken(token);
		oAccountValue.addTokens(oAccountTokenValue);
		oAccount.setValue(oAccountValue);
		return ByteUtil.bytesToBigInteger(oAccountTokenValue.getBalance().toByteArray());
	}

	public synchronized BigInteger subTokenBalance(Account.Builder oAccount, String token, BigInteger balance)
			throws Exception {
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.setTokens(i,
						oAccountValue.getTokens(i).toBuilder()
								.setBalance(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(ByteUtil
										.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray())
										.subtract(balance)))));

				oAccount.setValue(oAccountValue);
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray());
			}
		}
		throw new AccountTokenNotFoundException("not found token" + token);
	}

	public synchronized BigInteger addTokenLockBalance(Account.Builder oAccount, String token, BigInteger balance)
			throws Exception {
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.setTokens(i, oAccountValue.getTokens(i).toBuilder()
						.setLocked(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance.add(
								ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getLocked().toByteArray()))))));
				// putAccountValue(oAccount.getAddress(),
				// oAccountValue.build());
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray());
			}
		}
		// 如果token账户余额不存在，直接增加一条记录
		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setLocked(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance)));
		oAccountTokenValue.setToken(token);
		oAccountValue.addTokens(oAccountTokenValue);
		oAccount.setValue(oAccountValue);
		// putAccountValue(oAccount.getAddress(), oAccountValue.build());
		return ByteUtil.bytesToBigInteger(oAccountTokenValue.getBalance().toByteArray());
	}

	//
	// /**
	// * 增加加密Token账户余额
	// *
	// * @param addr
	// * @param symbol
	// * @param token
	// * @return
	// * @throws Exception
	// */
	// public synchronized long addCryptoBalance(ByteString addr, String symbol,
	// AccountCryptoToken.Builder token)
	// throws Exception {
	//
	// OValue oValue =
	// dao.getAccountDao().get(oEntityHelper.byteKey2OKey(addr)).get();
	// if (oValue != null && oValue.getExtdata() != null) {
	// } else {
	// return 0;
	// }
	//
	// AccountValue.Builder oAccountValue = AccountValue.newBuilder();
	// oAccountValue.mergeFrom(oValue.getExtdata().toByteArray());
	//
	// token.setOwner(addr);
	// token.setNonce(token.getNonce() + 1);
	// token.setOwnertime(System.currentTimeMillis());
	//
	// for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
	// if (oAccountValue.getCryptosList().get(i).getSymbol().equals(symbol)) {
	// AccountCryptoValue.Builder oAccountCryptoValue =
	// oAccountValue.getCryptos(i).toBuilder();
	// oAccountCryptoValue.addTokens(token);
	// oAccountValue.setCryptos(i, oAccountCryptoValue);
	// // }
	// tokenMappingAccount(token);
	// putAccountValue(addr, oAccountValue.build(), false);
	// return oAccountValue.getCryptosList().get(i).getTokensCount();
	// }
	// }
	//
	// // 如果是第一个，直接增加一个
	// AccountCryptoValue.Builder oAccountCryptoValue =
	// AccountCryptoValue.newBuilder();
	// oAccountCryptoValue.setSymbol(symbol);
	// oAccountCryptoValue.addTokens(token);
	// oAccountValue.addCryptos(oAccountCryptoValue.build());
	// tokenMappingAccount(token);
	// putAccountValue(addr, oAccountValue.build(), false);
	// return 1;
	// }
	//
	// /**
	// * batch add balance。
	// *
	// * @param addr
	// * @param symbol
	// * @param tokens
	// * @return
	// * @throws Exception
	// */
	// public synchronized long newCryptoBalances(ByteString addr, String
	// symbol,
	// ArrayList<AccountCryptoToken.Builder> tokens) throws Exception {
	// Account.Builder oAccount = GetAccount(addr);
	// if (oAccount == null) {
	// throw new Exception("account not founded::" + addr);
	// }
	// AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
	//
	// int symbolIndex = 0;
	// boolean isExistsSymbol = false;
	// for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
	// if (oAccountValue.getCryptosList().get(i).getSymbol().equals(symbol)) {
	// isExistsSymbol = true;
	// symbolIndex = i;
	// break;
	// }
	// }
	//
	// AccountCryptoValue.Builder oAccountCryptoValue;
	// if (isExistsSymbol) {
	// oAccountCryptoValue = oAccountValue.getCryptos(symbolIndex).toBuilder();
	// } else {
	// oAccountCryptoValue = AccountCryptoValue.newBuilder();
	// oAccountCryptoValue.setSymbol(symbol);
	// }
	// for (AccountCryptoToken.Builder token : tokens) {
	// oAccountCryptoValue.addTokens(token);
	// }
	// oAccountValue.addCryptos(oAccountCryptoValue.build());
	// putAccountValue(addr, oAccountValue.build());
	// return tokens.size();
	// }
	//
	// /**
	// * 移除加密Token
	// *
	// * @param addr
	// * @param symbol
	// * @param hash
	// * @return
	// */
	// public synchronized long removeCryptoBalance(ByteString addr, ByteString
	// symbol, ByteString hash) {
	// Account.Builder oAccount = GetAccount(addr);
	// AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
	//
	// int retBalance = 0;
	// for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
	// if
	// (oAccountValue.getCryptosList().get(i).getSymbolBytes().equals(symbol)) {
	// AccountCryptoValue.Builder value =
	// oAccountValue.getCryptosList().get(i).toBuilder();
	//
	// for (int j = 0; j < value.getTokensCount(); j++) {
	// if (value.getTokensBuilderList().get(j).getHash().equals(hash)) {
	// value.removeTokens(j);
	// break;
	// }
	// }
	// oAccountValue.setCryptos(i, value);
	// retBalance = value.getTokensCount();
	// break;
	// }
	// }
	// putAccountValue(addr, oAccountValue.build());
	// return retBalance;
	// }

	public String getSyncStr(ByteString addr) {
		return ("" + addr.byteAt(2) + addr.byteAt(3));
	}

	/**
	 * 设置用户账户Nonce
	 * 
	 * @param addr
	 * @param nonce
	 * @return
	 * @throws Exception
	 */
	public int setNonce(ByteString addr, int nonce) throws Exception {
		// synchronized (getSyncStr(addr).intern()) {
		Account.Builder oAccount = GetAccount(addr);
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setNonce(oAccountValue.getNonce() + nonce);
		putAccountValue(addr, oAccountValue.build());
		return oAccountValue.getNonce();
		// }
	}

	public boolean isContract(ByteString addr) {
		Account.Builder oAccount = GetAccount(addr);
		if (oAccount == null) {
			log.error("account not found::" + addr);
			return false;
		}
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		if (oAccountValue.getCodeHash() == null || oAccountValue.getCode() == null) {
			return false;
		}
		return true;
	}

	/**
	 * 获取用户账户Nonce
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public int getNonce(ByteString addr) throws Exception {
		Account.Builder oAccount = GetAccountOrCreate(addr);
		AccountValue oAccountValue = oAccount.getValue();
		return oAccountValue.getNonce();
	}

	/**
	 * 获取用户账户的Balance
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public BigInteger getBalance(ByteString addr) throws Exception {
		Account.Builder oAccount = GetAccount(addr);
		if (oAccount == null) {
			throw new Exception("account not found");
		}
		AccountValue oAccountValue = oAccount.getValue();
		return ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray());
	}

	/**
	 * 获取用户Token账户的Balance
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public BigInteger getTokenBalance(ByteString addr, String token) throws Exception {
		Account.Builder oAccount = GetAccount(addr);
		return getTokenBalance(oAccount, token);
	}

	public BigInteger getTokenBalance(Account.Builder oAccount, String token) throws Exception {
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray());
			}
		}
		return BigInteger.ZERO;
	}

	public BigInteger getTokenLockedBalance(ByteString addr, String token) throws Exception {
		Account.Builder oAccount = GetAccount(addr);
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getLocked().toByteArray());
			}
		}
		return BigInteger.ZERO;
	}

	/**
	 * 获取加密Token账户的余额
	 * 
	 * @param addr
	 * @param symbol
	 * @return
	 * @throws Exception
	 */
	public List<AccountCryptoToken> getCryptoTokenBalance(ByteString addr, ByteString symbol) throws Exception {
		Account.Builder oAccount = GetAccount(addr);
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getCryptosCount(); i++) {
			if (oAccountValue.getCryptos(i).getSymbolBytes().equals(symbol)) {
				return oAccountValue.getCryptos(i).getTokensList();
			}
		}

		return new ArrayList<AccountCryptoToken>();
	}

	// /**
	// * 生成加密Token方法。 调用时必须确保symbol不重复。
	// *
	// * @param addr
	// * @param symbol
	// * @param name
	// * @param code
	// * @throws Exception
	// */
	// public synchronized void generateCryptoToken(ByteString addr, String
	// symbol, String[] name, String[] code)
	// throws Exception {
	// if (name.length != code.length || name.length == 0) {
	// throw new Exception(String.format("crypto token name %s or code %s
	// invalid", name.length, code.length));
	// }
	//
	// int total = name.length;
	// Account.Builder oAccount = GetAccount(addr);
	// AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
	// AccountCryptoValue.Builder oAccountCryptoValue =
	// AccountCryptoValue.newBuilder();
	// oAccountCryptoValue.setSymbol(symbol);
	//
	// for (int i = 0; i < name.length; i++) {
	// AccountCryptoToken.Builder oAccountCryptoToken =
	// AccountCryptoToken.newBuilder();
	// oAccountCryptoToken.setName(name[i]);
	// oAccountCryptoToken.setCode(code[i]);
	// oAccountCryptoToken.setIndex(i);
	// oAccountCryptoToken.setTotal(total);
	// oAccountCryptoToken.setTimestamp(System.currentTimeMillis());
	// oAccountCryptoToken
	// .setHash(ByteString.copyFrom(encApi.sha256Encode(oAccountCryptoToken.build().toByteArray())));
	//
	// oAccountCryptoToken.setOwner(addr);
	// oAccountCryptoToken.setNonce(0);
	// oAccountCryptoToken.setOwnertime(System.currentTimeMillis());
	// oAccountCryptoValue.addTokens(oAccountCryptoToken);
	// tokenMappingAccount(oAccountCryptoToken);
	// }
	//
	// oAccountValue.addCryptos(oAccountCryptoValue);
	// putAccountValue(addr, oAccountValue.build());
	// }

	public void createToken(ByteString addr, String token, BigInteger total) throws Exception {
		OValue oValue = dao.getCommonDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_EXISTS_TOKEN)).get();
		ERC20Token.Builder oERC20Token;
		if (oValue == null) {
			oERC20Token = ERC20Token.newBuilder();
		} else {
			oERC20Token = ERC20Token.parseFrom(oValue.getExtdata().toByteArray()).toBuilder();
		}

		ERC20TokenValue.Builder oICOValue = ERC20TokenValue.newBuilder();
		oICOValue.setAddress(encApi.hexEnc(addr.toByteArray()));
		oICOValue.setTimestamp(System.currentTimeMillis());
		oICOValue.setToken(token);
		oICOValue.setTotal(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(total)));
		oERC20Token.addValue(oICOValue);

		dao.getCommonDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_EXISTS_TOKEN),
				oEntityHelper.byteValue2OValue(oERC20Token.build().toByteArray()));
	}
	//
	// public void createCryptoToken(Builder oAccountCryptoToken, String symbol)
	// {
	// dao.getCryptoTokenDao().put(oEntityHelper.byteKey2OKey(oAccountCryptoToken.getHash()),
	// oEntityHelper.byteValue2OValue(oAccountCryptoToken.build().toByteArray(),
	// symbol));
	// }

	public void createContract(ByteString addr, ByteString contract) throws Exception {
		OValue oValue = dao.getCommonDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_EXISTS_CONTRACT)).get();
		AccountContract.Builder oAccountContract;
		if (oValue == null || oValue.getExtdata() == null) {
			oAccountContract = AccountContract.newBuilder();
		} else {
			oAccountContract = AccountContract.parseFrom(oValue.getExtdata().toByteArray()).toBuilder();
		}

		AccountContractValue.Builder oAccountContractValue = AccountContractValue.newBuilder();
		oAccountContractValue.setAddress(encApi.hexEnc(addr.toByteArray()));
		oAccountContractValue.setContractHash(encApi.hexEnc(contract.toByteArray()));
		oAccountContractValue.setTimestamp(System.currentTimeMillis());
		oAccountContract.addValue(oAccountContractValue);

		dao.getCommonDao().put(oEntityHelper.byteKey2OKey(KeyConstant.DB_EXISTS_CONTRACT),
				oEntityHelper.byteValue2OValue(oAccountContract.build().toByteArray()));
	}

	public List<ERC20TokenValue> getTokens(String address, String token) {
		List<ERC20TokenValue> list = new ArrayList<>();
		OValue oValue;
		try {
			oValue = dao.getCommonDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_EXISTS_TOKEN)).get();
			if (oValue == null) {
				return list;
			}
			ERC20Token oERC20Token = ERC20Token.parseFrom(oValue.getExtdata().toByteArray());
			for (ERC20TokenValue erc20TokenValue : oERC20Token.getValueList()) {
				if (StringUtils.isNotBlank(address) && address.equals(erc20TokenValue.getAddress())) {
					list.add(erc20TokenValue);
				} else if (StringUtils.isNotBlank(token) && token.equals(erc20TokenValue.getToken())) {
					list.add(erc20TokenValue);
				} else if (StringUtils.isBlank(address) && StringUtils.isBlank(token)) {
					list.add(erc20TokenValue);
				}
			}
			return list;
		} catch (Exception e) {
			log.error("error on query tokens::" + e);
		}
		return list;
	}

	/**
	 * 判断token是否已经发行
	 * 
	 * @param token
	 * @return
	 * @throws Exception
	 */
	public boolean isExistsToken(String token) throws Exception {
		OValue oValue = dao.getCommonDao().get(oEntityHelper.byteKey2OKey(KeyConstant.DB_EXISTS_TOKEN)).get();
		if (oValue == null) {
			return false;
		}
		ERC20Token oERC20Token = ERC20Token.parseFrom(oValue.getExtdata().toByteArray());

		for (ERC20TokenValue oICOValue : oERC20Token.getValueList()) {
			if (oICOValue.getToken().equals(token)) {
				return true;
			}
		}
		return false;
	}

	public boolean isExistsCryptoToken(byte[] hash) throws Exception {
		OValue oValue = dao.getCommonDao().get(oEntityHelper.byteKey2OKey(hash)).get();
		if (oValue == null) {
			return false;
		}
		return true;
	}

	public void putAccountValue(final ByteString addr, final AccountValue oAccountValue, boolean stateable) {
		dao.getAccountDao().put(oEntityHelper.byteKey2OKey(addr),
				oEntityHelper.byteValue2OValue(oAccountValue.toByteArray()));
		if (this.stateTrie != null && stateable) {
			this.stateTrie.put(addr.toByteArray(), oAccountValue.toByteArray());
		}
		String addrHex = encApi.hexEnc(addr.toByteArray());
//		accountByHash.put(addrHex, oAccountValue.toBuilder());
	}

	public void putAccountValue(ByteString addr, AccountValue oAccountValue) {
		putAccountValue(addr, oAccountValue, true);
	}

	public void BatchPutAccounts(Map<String, Account.Builder> accountValues) {
		long start = System.currentTimeMillis();
		Set<String> keySets = accountValues.keySet();
		Iterator<String> iterator = keySets.iterator();
		// sort by keys
		List<String> keys = new ArrayList<>();
		while (iterator.hasNext()) {
			String key = iterator.next();
			keys.add(key);
		}
		Collections.sort(keys);
		
		long sortend = System.currentTimeMillis();
		for (String key : keys) {
			AccountValue value = accountValues.get(key).getValue();
//			if (this.stateTrie != null) {
				this.stateTrie.put(encApi.hexDec(key), value.toByteArray());
//			}
		}
		log.error("batchputs.put.cost="+(System.currentTimeMillis()-sortend)+",sortcost="+(sortend-start)+",count="+keys.size());
		keys.clear();
		// no need, because all account already on the mpt
		// doPutAccounts(accountValues);
	}

	public void doPutAccounts(Map<String, Account.Builder> accountValues) {
		try {
			OKey[] keysArray = new OKey[accountValues.size()];
			OValue[] valuesArray = new OValue[accountValues.size()];
			Set<String> keySets = accountValues.keySet();
			Iterator<String> iterator = keySets.iterator();
			int i = 0;
			while (iterator.hasNext()) {
				String key = iterator.next();
				keysArray[i] = oEntityHelper.byteKey2OKey(accountValues.get(key).getAddress());
				valuesArray[i] = oEntityHelper.byteValue2OValue(accountValues.get(key).getValue().toByteArray());
				i = i + 1;
			}
			dao.getAccountDao().batchPuts(keysArray, valuesArray);
		} finally {
		}
	}

	public void tokenMappingAccount(AccountCryptoToken.Builder acBuilder) {
		dao.getAccountDao().put(oEntityHelper.byteKey2OKey(acBuilder.getHash()),
				oEntityHelper.byteValue2OValue(acBuilder.build().toByteArray()));
	}

	/**
	 * 根据token的hash获取此token的信息
	 * 
	 * @param tokenHash
	 * @return
	 */
	public AccountCryptoToken.Builder getCryptoTokenByTokenHash(ByteString tokenHash) {
		AccountCryptoToken.Builder cryptoTokenBuild = null;
		try {
			OValue otValue = dao.getAccountDao().get(oEntityHelper.byteKey2OKey(tokenHash)).get();
			cryptoTokenBuild = AccountCryptoToken.parseFrom(otValue.getExtdata()).toBuilder();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cryptoTokenBuild;
	}

	public void putStorage(Account.Builder oAccount, byte[] key, byte[] value) {
		StorageTrie oStorage = getStorageTrie(oAccount.getAddress());
		oStorage.put(key, value);
		byte[] rootHash = oStorage.getRootHash();

		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setStorage(ByteString.copyFrom(rootHash));
		oAccount.setValue(oAccountValue);
	}

	public void saveStorage(ByteString address, byte[] key, byte[] value) {
		Account.Builder oAccount = GetAccount(address);
		putStorage(oAccount, key, value);
		// putAccountValue(address, oAccount.getValue());
	}

	public StorageTrie getStorageTrie(Account.Builder oAccount) {
		StorageTrie oStorage = storageTrieCache.get(encApi.hexEnc(oAccount.getAddress().toByteArray()));
		if (oStorage == null) {
			oStorage = new StorageTrie(this.dao, this.encApi, this.oEntityHelper);
			if (oAccount == null || oAccount.getValue() == null || oAccount.getValue().getStorage() == null) {
				oStorage.setRoot(ByteUtil.EMPTY_BYTE_ARRAY);
			} else {
				oStorage.setRoot(oAccount.getValue().getStorage().toByteArray());
			}
			storageTrieCache.put(encApi.hexEnc(oAccount.getAddress().toByteArray()), oStorage);
		}
		return oStorage;
	}

	public StorageTrie getStorageTrie(ByteString address) {
		Account.Builder oAccount = GetAccount(address);
		return getStorageTrie(oAccount);
	}

	public Map<String, byte[]> getStorage(Account.Builder oAccount, List<byte[]> keys) {
		Map<String, byte[]> storage = new HashMap<>();
		StorageTrie oStorage = getStorageTrie(oAccount);
		for (int i = 0; i < keys.size(); i++) {
			storage.put(encApi.hexEnc(keys.get(i)), oStorage.get(keys.get(i)));
		}
		return storage;
	}

	public Map<String, byte[]> getStorage(ByteString address, List<byte[]> keys) {
		Account.Builder oAccount = GetAccount(address);
		return getStorage(oAccount, keys);
	}

	public byte[] getStorage(Account.Builder oAccount, byte[] key) {
		StorageTrie oStorage = getStorageTrie(oAccount);
		return oStorage.get(key);
	}

	public byte[] getStorage(ByteString address, byte[] key) {
		Account.Builder oAccount = GetAccount(address);
		return getStorage(oAccount, key);
	}

	public boolean canCreateCryptoToken(ByteString symbol, ByteString address, long total, int codeCount) {
		try {
			OValue cryptoTokenValue = dao.getCryptoTokenDao().get(oEntityHelper.byteKey2OKey(symbol)).get();
			if (cryptoTokenValue == null || cryptoTokenValue.getExtdata() == null) {
				return true;
			} else {
				CryptoTokenValue oCryptoTokenValue = CryptoTokenValue
						.parseFrom(cryptoTokenValue.getExtdata().toByteArray());
				if (oCryptoTokenValue.getTotal() < oCryptoTokenValue.getCurrent() + codeCount) {
					return false;
				} else if (!oCryptoTokenValue.getOwner().equals(address)) {
					return false;
				}
				return true;
			}
		} catch (Exception e) {
			log.error("error on canCreateCryptoToken", e);
		}
		return false;
	}

	public CryptoTokenValue getCryptoTokenValue(ByteString symbolBytes) {
		try {
			OValue o = dao.getCryptoTokenDao().get(oEntityHelper.byteKey2OKey(symbolBytes)).get();
			if (o != null && o.getExtdata() != null) {
				CryptoTokenValue oCryptoTokenValue = CryptoTokenValue.parseFrom(o.getExtdata().toByteArray());
				return oCryptoTokenValue;
			}
			return null;
		} catch (Exception e) {
			log.error("error on getCryptoTokenValue", e);
		}
		return null;
	}

	public void updateCryptoTokenValue(ByteString symbol, CryptoTokenValue newCryptoTokenValue) {
		dao.getCryptoTokenDao().put(oEntityHelper.byteKey2OKey(symbol), oEntityHelper
				.byteValue2OValue(newCryptoTokenValue.toByteArray(), KeyConstant.DB_EXISTS_CRYPTO_TOKEN_STR));
	}
}
