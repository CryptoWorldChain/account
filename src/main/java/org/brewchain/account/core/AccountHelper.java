package org.brewchain.account.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.trie.StorageTrie;
import org.brewchain.account.trie.StorageTrieCache;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.backend.ODBException;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OPair;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountContract;
import org.brewchain.evmapi.gens.Act.AccountContractValue;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken.Builder;
import org.brewchain.evmapi.gens.Act.AccountCryptoValue;
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

	public Account CreateAccount(ByteString address) {
		return CreateAccount(address, BigInteger.ZERO, BigInteger.ZERO, 0, null, null, null);
	}

	public Account CreateUnionAccount(ByteString address, BigInteger max, BigInteger acceptMax, int acceptLimit,
			List<ByteString> addresses) {
		return CreateAccount(address, max, acceptMax, acceptLimit, addresses, null, null);
	}

	public Account CreateAccount(ByteString address, BigInteger max, BigInteger acceptMax, int acceptLimit,
			List<ByteString> addresses, ByteString code, ByteString exdata) {
		Account.Builder oUnionAccount = Account.newBuilder();
		AccountValue.Builder oUnionAccountValue = AccountValue.newBuilder();

		oUnionAccountValue.setAcceptLimit(acceptLimit);
		oUnionAccountValue.setAcceptMax(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(acceptMax)));
		if (addresses != null)
			oUnionAccountValue.addAllAddress(addresses);

		oUnionAccountValue.setBalance(ByteString.copyFrom(ByteUtil.ZERO_BYTE_ARRAY));
		oUnionAccountValue.setMax(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(max)));
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
		return CreateUnionAccount(oUnionAccount.build());
	}

	public Account CreateUnionAccount(Account oAccount) {
		// putAccountValue(oAccount.getAddress(), oAccount.getValue());
		return oAccount;
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
		Account oAccount = GetAccount(address);
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

	/**
	 * 获取用户账户
	 * 
	 * @param addr
	 * @return
	 */
	public Account GetAccount(ByteString addr) {
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

					return oAccount.build();
				}
			}
		} catch (Exception e) {
			log.error("account not found::" + encApi.hexEnc(addr.toByteArray()));
		}
		return null;
	}

	public Account GetAccountFromDB(ByteString addr) {
		try {
			Account.Builder oAccount = Account.newBuilder();
			oAccount.setAddress(addr);
			OValue o = dao.getAccountDao().get(oEntityHelper.byteKey2OKey(addr.toByteArray())).get();
			AccountValue oAccountValue = AccountValue.parseFrom(o.getExtdata().toByteArray());
			oAccount.setValue(oAccountValue);
			return oAccount.build();
		} catch (Exception e) {
			log.error("account not found::" + encApi.hexEnc(addr.toByteArray()));
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
	public Account GetAccountOrCreate(ByteString addr) {
		try {
			Account oAccount = GetAccount(addr);
			if (oAccount == null) {
				oAccount = CreateAccount(addr);
			}
			return oAccount;
		} catch (Exception e) {
			// TODO: handle exception
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

	/**
	 * 增加用户账户余额
	 * 
	 * @param addr
	 * @param balance
	 * @return
	 * @throws Exception
	 */
	public synchronized BigInteger addBalance(ByteString addr, BigInteger balance) throws Exception {
		Account.Builder oAccount = GetAccountOrCreate(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setBalance(ByteString.copyFrom(ByteUtil
				.bigIntegerToBytes(balance.add(ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray())))));
		putAccountValue(addr, oAccountValue.build());
		return ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray());
	}

	/**
	 * 增加用户代币账户余额
	 * 
	 * @param addr
	 * @param balance
	 * @return
	 * @throws Exception
	 */
	public synchronized BigInteger addTokenBalance(ByteString addr, String token, BigInteger balance) throws Exception {
		Account.Builder oAccount = GetAccountOrCreate(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.setTokens(i, oAccountValue.getTokens(i).toBuilder()
						.setBalance(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance.add(
								ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray()))))));

				putAccountValue(addr, oAccountValue.build());
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray());
			}
		}
		// 如果token账户余额不存在，直接增加一条记录
		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setBalance(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance)));
		oAccountTokenValue.setToken(token);
		oAccountValue.addTokens(oAccountTokenValue);
		putAccountValue(addr, oAccountValue.build());
		return ByteUtil.bytesToBigInteger(oAccountTokenValue.getBalance().toByteArray());
	}

	public synchronized BigInteger addTokenLockBalance(ByteString addr, String token, BigInteger balance)
			throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.setTokens(i, oAccountValue.getTokens(i).toBuilder()
						.setLocked(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance.add(
								ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getLocked().toByteArray()))))));
				putAccountValue(addr, oAccountValue.build());
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray());
			}
		}
		// 如果token账户余额不存在，直接增加一条记录
		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setLocked(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(balance)));
		oAccountTokenValue.setToken(token);
		oAccountValue.addTokens(oAccountTokenValue);
		putAccountValue(addr, oAccountValue.build());
		return ByteUtil.bytesToBigInteger(oAccountTokenValue.getBalance().toByteArray());
	}
//
//	/**
//	 * 增加加密Token账户余额
//	 * 
//	 * @param addr
//	 * @param symbol
//	 * @param token
//	 * @return
//	 * @throws Exception
//	 */
//	public synchronized long addCryptoBalance(ByteString addr, String symbol, AccountCryptoToken.Builder token)
//			throws Exception {
//
//		OValue oValue = dao.getAccountDao().get(oEntityHelper.byteKey2OKey(addr)).get();
//		if (oValue != null && oValue.getExtdata() != null) {
//		} else {
//			return 0;
//		}
//
//		AccountValue.Builder oAccountValue = AccountValue.newBuilder();
//		oAccountValue.mergeFrom(oValue.getExtdata().toByteArray());
//
//		token.setOwner(addr);
//		token.setNonce(token.getNonce() + 1);
//		token.setOwnertime(System.currentTimeMillis());
//
//		for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
//			if (oAccountValue.getCryptosList().get(i).getSymbol().equals(symbol)) {
//				AccountCryptoValue.Builder oAccountCryptoValue = oAccountValue.getCryptos(i).toBuilder();
//				oAccountCryptoValue.addTokens(token);
//				oAccountValue.setCryptos(i, oAccountCryptoValue);
//				// }
//				tokenMappingAccount(token);
//				putAccountValue(addr, oAccountValue.build(), false);
//				return oAccountValue.getCryptosList().get(i).getTokensCount();
//			}
//		}
//
//		// 如果是第一个，直接增加一个
//		AccountCryptoValue.Builder oAccountCryptoValue = AccountCryptoValue.newBuilder();
//		oAccountCryptoValue.setSymbol(symbol);
//		oAccountCryptoValue.addTokens(token);
//		oAccountValue.addCryptos(oAccountCryptoValue.build());
//		tokenMappingAccount(token);
//		putAccountValue(addr, oAccountValue.build(), false);
//		return 1;
//	}
//
//	/**
//	 * batch add balance。
//	 * 
//	 * @param addr
//	 * @param symbol
//	 * @param tokens
//	 * @return
//	 * @throws Exception
//	 */
//	public synchronized long newCryptoBalances(ByteString addr, String symbol,
//			ArrayList<AccountCryptoToken.Builder> tokens) throws Exception {
//		Account.Builder oAccount = GetAccount(addr).toBuilder();
//		if (oAccount == null) {
//			throw new Exception("account not founded::" + addr);
//		}
//		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
//
//		int symbolIndex = 0;
//		boolean isExistsSymbol = false;
//		for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
//			if (oAccountValue.getCryptosList().get(i).getSymbol().equals(symbol)) {
//				isExistsSymbol = true;
//				symbolIndex = i;
//				break;
//			}
//		}
//
//		AccountCryptoValue.Builder oAccountCryptoValue;
//		if (isExistsSymbol) {
//			oAccountCryptoValue = oAccountValue.getCryptos(symbolIndex).toBuilder();
//		} else {
//			oAccountCryptoValue = AccountCryptoValue.newBuilder();
//			oAccountCryptoValue.setSymbol(symbol);
//		}
//		for (AccountCryptoToken.Builder token : tokens) {
//			oAccountCryptoValue.addTokens(token);
//		}
//		oAccountValue.addCryptos(oAccountCryptoValue.build());
//		putAccountValue(addr, oAccountValue.build());
//		return tokens.size();
//	}
//
//	/**
//	 * 移除加密Token
//	 * 
//	 * @param addr
//	 * @param symbol
//	 * @param hash
//	 * @return
//	 */
//	public synchronized long removeCryptoBalance(ByteString addr, ByteString symbol, ByteString hash) {
//		Account.Builder oAccount = GetAccount(addr).toBuilder();
//		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
//
//		int retBalance = 0;
//		for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
//			if (oAccountValue.getCryptosList().get(i).getSymbolBytes().equals(symbol)) {
//				AccountCryptoValue.Builder value = oAccountValue.getCryptosList().get(i).toBuilder();
//
//				for (int j = 0; j < value.getTokensCount(); j++) {
//					if (value.getTokensBuilderList().get(j).getHash().equals(hash)) {
//						value.removeTokens(j);
//						break;
//					}
//				}
//				oAccountValue.setCryptos(i, value);
//				retBalance = value.getTokensCount();
//				break;
//			}
//		}
//		putAccountValue(addr, oAccountValue.build());
//		return retBalance;
//	}

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
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setNonce(oAccountValue.getNonce() + nonce);
		putAccountValue(addr, oAccountValue.build());
		return oAccountValue.getNonce();
		// }
	}

	public boolean isContract(ByteString addr) {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
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
		Account.Builder oAccount = GetAccountOrCreate(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
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
		Account oAccount = GetAccount(addr);
		if (oAccount == null) {
			throw new Exception("account not found");
		}
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		return ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray());
	}

	/**
	 * 获取用户Token账户的Balance
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public BigInteger getTokenBalance(ByteString addr, ByteString token) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getTokenBytes().equals(token)) {
				return ByteUtil.bytesToBigInteger(oAccountValue.getTokens(i).getBalance().toByteArray());
			}
		}
		return BigInteger.ZERO;
	}

	public BigInteger getTokenLockedBalance(ByteString addr, ByteString token) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getTokenBytes().equals(token)) {
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
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getCryptosCount(); i++) {
			if (oAccountValue.getCryptos(i).getSymbolBytes().equals(symbol)) {
				return oAccountValue.getCryptos(i).getTokensList();
			}
		}

		return new ArrayList<AccountCryptoToken>();
	}

//	/**
//	 * 生成加密Token方法。 调用时必须确保symbol不重复。
//	 * 
//	 * @param addr
//	 * @param symbol
//	 * @param name
//	 * @param code
//	 * @throws Exception
//	 */
//	public synchronized void generateCryptoToken(ByteString addr, String symbol, String[] name, String[] code)
//			throws Exception {
//		if (name.length != code.length || name.length == 0) {
//			throw new Exception(String.format("crypto token name %s or code %s invalid", name.length, code.length));
//		}
//
//		int total = name.length;
//		Account.Builder oAccount = GetAccount(addr).toBuilder();
//		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
//		AccountCryptoValue.Builder oAccountCryptoValue = AccountCryptoValue.newBuilder();
//		oAccountCryptoValue.setSymbol(symbol);
//
//		for (int i = 0; i < name.length; i++) {
//			AccountCryptoToken.Builder oAccountCryptoToken = AccountCryptoToken.newBuilder();
//			oAccountCryptoToken.setName(name[i]);
//			oAccountCryptoToken.setCode(code[i]);
//			oAccountCryptoToken.setIndex(i);
//			oAccountCryptoToken.setTotal(total);
//			oAccountCryptoToken.setTimestamp(System.currentTimeMillis());
//			oAccountCryptoToken
//					.setHash(ByteString.copyFrom(encApi.sha256Encode(oAccountCryptoToken.build().toByteArray())));
//
//			oAccountCryptoToken.setOwner(addr);
//			oAccountCryptoToken.setNonce(0);
//			oAccountCryptoToken.setOwnertime(System.currentTimeMillis());
//			oAccountCryptoValue.addTokens(oAccountCryptoToken);
//			tokenMappingAccount(oAccountCryptoToken);
//		}
//
//		oAccountValue.addCryptos(oAccountCryptoValue);
//		putAccountValue(addr, oAccountValue.build());
//	}

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
	}

	public void putAccountValue(ByteString addr, AccountValue oAccountValue) {
		putAccountValue(addr, oAccountValue, true);
	}

	public void BatchPutAccounts(Map<String, Account.Builder> accountValues) {
		Set<String> keySets = accountValues.keySet();
		Iterator<String> iterator = keySets.iterator();
		while (iterator.hasNext()) {
			String key = iterator.next();
			AccountValue value = accountValues.get(key).getValue();
			if (this.stateTrie != null) {
				this.stateTrie.put(encApi.hexDec(key), value.toByteArray());
			}
		}
		KeyConstant.QUEUE.add(accountValues);
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

	public void saveStorage(ByteString address, byte[] key, byte[] value) {
		StorageTrie oStorage = getStorageTrie(address);
		oStorage.put(key, value);
		byte[] rootHash = oStorage.getRootHash();

		Account.Builder contract = GetAccount(address).toBuilder();
		AccountValue.Builder oAccountValue = contract.getValue().toBuilder();
		oAccountValue.setStorage(ByteString.copyFrom(rootHash));
		putAccountValue(address, oAccountValue.build());
	}

	public StorageTrie getStorageTrie(ByteString address) {
		StorageTrie oStorage = storageTrieCache.get(encApi.hexEnc(address.toByteArray()));
		if (oStorage == null) {
			oStorage = new StorageTrie(this.dao, this.encApi, this.oEntityHelper);
			Account contract = GetAccount(address);
			log.debug("contract address::" + encApi.hexEnc(address.toByteArray()));
			if (contract == null || contract.getValue() == null || contract.getValue().getStorage() == null) {
				oStorage.setRoot(ByteUtil.EMPTY_BYTE_ARRAY);
			} else {
				oStorage.setRoot(contract.getValue().getStorage().toByteArray());
			}
			storageTrieCache.put(encApi.hexEnc(address.toByteArray()), oStorage);
		}
		return oStorage;
	}

	public Map<String, byte[]> getStorage(ByteString address, List<byte[]> keys) {
		Map<String, byte[]> storage = new HashMap<>();
		StorageTrie oStorage = getStorageTrie(address);
		for (int i = 0; i < keys.size(); i++) {
			storage.put(encApi.hexEnc(keys.get(i)), oStorage.get(keys.get(i)));
		}
		return storage;
	}

	public byte[] getStorage(ByteString address, byte[] key) {
		StorageTrie oStorage = getStorageTrie(address);
		return oStorage.get(key);
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
