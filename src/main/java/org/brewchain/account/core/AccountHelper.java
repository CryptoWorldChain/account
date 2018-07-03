package org.brewchain.account.core;

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
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoValue;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.ICO;
import org.brewchain.evmapi.gens.Act.ICOValue;
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

	public AccountHelper() {
	}

	public Account CreateAccount(ByteString address) {
		return CreateAccount(address, 0, 0, 0, null, null, null);
	}

	public Account CreateUnionAccount(ByteString address, long max, long acceptMax, int acceptLimit,
			List<ByteString> addresses) {
		return CreateAccount(address, max, acceptMax, acceptLimit, addresses, null, null);
	}

	public Account CreateAccount(ByteString address, long max, long acceptMax, int acceptLimit,
			List<ByteString> addresses, ByteString code, ByteString exdata) {
		Account.Builder oUnionAccount = Account.newBuilder();
		AccountValue.Builder oUnionAccountValue = AccountValue.newBuilder();

		oUnionAccountValue.setAcceptLimit(acceptLimit);
		oUnionAccountValue.setAcceptMax(acceptMax);
		if (addresses != null)
			oUnionAccountValue.addAllAddress(addresses);

		oUnionAccountValue.setBalance(KeyConstant.EMPTY_BALANCE.intValue());
		oUnionAccountValue.setMax(max);
		oUnionAccountValue.setNonce(KeyConstant.EMPTY_NONCE.intValue());

		if (code != null) {
			oUnionAccountValue.setCode(code);
			oUnionAccountValue.setCodeHash(ByteString.copyFrom(encApi.sha3Encode(code.toByteArray())));
		}

		if (exdata != null) {
			oUnionAccountValue.setData(exdata);
		}
		oUnionAccountValue.setTimestamp(Integer.valueOf(String.valueOf((new Date()).getTime())));
		
		oUnionAccount.setAddress(address);
		oUnionAccount.setValue(oUnionAccountValue);
		return CreateUnionAccount(oUnionAccount.build());
	}

	public Account CreateUnionAccount(Account oAccount) {
		putAccountValue(oAccount.getAddress(), oAccount.getValue());
		return oAccount;
	}

	/**
	 * 移除账户。删除后不可恢复。
	 * 
	 * @param address
	 */

	public void DeleteAccount(byte[] address) {
		dao.getAccountDao().delete(OEntityBuilder.byteKey2OKey(address));
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
			}
			if (valueHash == null) {
				OValue oValue = dao.getAccountDao().get(OEntityBuilder.byteKey2OKey(addr)).get();
				if (oValue != null && oValue.getExtdata() != null) {
					valueHash = oValue.getExtdata().toByteArray();
				} else {
					return null;
				}
			}
			AccountValue.Builder oAccountValue = AccountValue.newBuilder();
			oAccountValue.mergeFrom(valueHash);
			oAccount.setValue(oAccountValue);
			return oAccount.build();
		} catch (Exception e) {
			log.error("account not found::" + addr);
		}
		return null;
	}

	public List<Account> getContractByCreator(ByteString addr) {
		List<Account> contracts = new ArrayList<>();
		try {
			List<OPair> oPairs = dao.getBlockDao().listBySecondKey(encApi.hexEnc(addr.toByteArray())).get();
			if (oPairs.size() > 0) {
				for (OPair oPair : oPairs) {
					contracts.add(Account.parseFrom(oPair.getValue().getExtdata()));
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
	public synchronized int IncreaseNonce(ByteString addr) throws Exception {
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
	public synchronized long addBalance(ByteString addr, long balance) throws Exception {
		Account.Builder oAccount = GetAccountOrCreate(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setBalance(oAccountValue.getBalance() + balance);
		putAccountValue(addr, oAccountValue.build());
		return oAccountValue.getBalance();
	}

	/**
	 * 增加用户代币账户余额
	 * 
	 * @param addr
	 * @param balance
	 * @return
	 * @throws Exception
	 */
	public synchronized long addTokenBalance(ByteString addr, String token, long balance) throws Exception {
		Account.Builder oAccount = GetAccountOrCreate(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.setTokens(i, oAccountValue.getTokens(i).toBuilder()
						.setBalance(oAccountValue.getTokens(i).getBalance() + balance));
				putAccountValue(addr, oAccountValue.build());
				return oAccountValue.getTokens(i).getBalance();
			}
		}
		// 如果token账户余额不存在，直接增加一条记录
		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setBalance(balance);
		oAccountTokenValue.setToken(token);
		oAccountValue.addTokens(oAccountTokenValue);
		putAccountValue(addr, oAccountValue.build());
		return oAccountTokenValue.getBalance();
	}

	public synchronized long addTokenLockBalance(ByteString addr, String token, long balance) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.setTokens(i, oAccountValue.getTokens(i).toBuilder()
						.setLocked(oAccountValue.getTokens(i).getLocked() + balance));
				putAccountValue(addr, oAccountValue.build());
				return oAccountValue.getTokens(i).getBalance();
			}
		}
		// 如果token账户余额不存在，直接增加一条记录
		AccountTokenValue.Builder oAccountTokenValue = AccountTokenValue.newBuilder();
		oAccountTokenValue.setLocked(balance);
		oAccountTokenValue.setToken(token);
		oAccountValue.addTokens(oAccountTokenValue);
		putAccountValue(addr, oAccountValue.build());
		return oAccountTokenValue.getBalance();
	}

	/**
	 * 增加加密Token账户余额
	 * 
	 * @param addr
	 * @param symbol
	 * @param token
	 * @return
	 * @throws Exception
	 */
	public synchronized long addCryptoBalance(ByteString addr, String symbol, AccountCryptoToken.Builder token)
			throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		if (oAccount == null) {
			throw new Exception("account not founded::" + addr);
		}
		token.setOwner(addr);
		token.setNonce(token.getNonce() + 1);
		token.setOwnertime(new Date().getTime());
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
			if (oAccountValue.getCryptosList().get(i).getSymbol().equals(symbol)) {
				AccountCryptoValue.Builder oAccountCryptoValue = oAccountValue.getCryptos(i).toBuilder();
				boolean isTokenExists = false;
				for (int k = 0; k < oAccountCryptoValue.getTokensCount(); k++) {
					if (oAccountCryptoValue.getTokens(k).getHash().equals(token.getHash())) {
						isTokenExists = true;
						break;
					}
				}
				if (!isTokenExists) {
					oAccountCryptoValue.addTokens(token);
					oAccountValue.setCryptos(i, oAccountCryptoValue);
				}
				tokenMappingAccount(token);
				putAccountValue(addr, oAccountValue.build());
				return oAccountValue.getCryptosList().get(i).getTokensCount();
			}
		}

		// 如果是第一个，直接增加一个
		AccountCryptoValue.Builder oAccountCryptoValue = AccountCryptoValue.newBuilder();
		oAccountCryptoValue.setSymbol(symbol);
		oAccountCryptoValue.addTokens(token);
		oAccountValue.addCryptos(oAccountCryptoValue.build());
		tokenMappingAccount(token);
		putAccountValue(addr, oAccountValue.build());
		return 1;
	}

	/**
	 * batch add balance。
	 * 
	 * @param addr
	 * @param symbol
	 * @param tokens
	 * @return
	 * @throws Exception
	 */
	public synchronized long newCryptoBalances(ByteString addr, String symbol,
			ArrayList<AccountCryptoToken.Builder> tokens) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		if (oAccount == null) {
			throw new Exception("account not founded::" + addr);
		}
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		int symbolIndex = 0;
		boolean isExistsSymbol = false;
		for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
			if (oAccountValue.getCryptosList().get(i).getSymbol().equals(symbol)) {
				isExistsSymbol = true;
				symbolIndex = i;
				break;
			}
		}

		AccountCryptoValue.Builder oAccountCryptoValue;
		if (isExistsSymbol) {
			oAccountCryptoValue = oAccountValue.getCryptos(symbolIndex).toBuilder();
		} else {
			oAccountCryptoValue = AccountCryptoValue.newBuilder();
			oAccountCryptoValue.setSymbol(symbol);
		}
		for (AccountCryptoToken.Builder token : tokens) {
			oAccountCryptoValue.addTokens(token);
		}
		oAccountValue.addCryptos(oAccountCryptoValue.build());
		putAccountValue(addr, oAccountValue.build());
		return tokens.size();
	}

	/**
	 * 移除加密Token
	 * 
	 * @param addr
	 * @param symbol
	 * @param hash
	 * @return
	 */
	public synchronized long removeCryptoBalance(ByteString addr, ByteString symbol, ByteString hash) {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		int retBalance = 0;
		for (int i = 0; i < oAccountValue.getCryptosList().size(); i++) {
			if (oAccountValue.getCryptosList().get(i).getSymbol().equals(symbol)) {
				AccountCryptoValue.Builder value = oAccountValue.getCryptosList().get(i).toBuilder();

				for (int j = 0; j < value.getTokensCount(); j++) {
					if (value.getTokensBuilderList().get(j).getHash().equals(hash)) {
						value.removeTokens(j);
						break;
					}
				}
				oAccountValue.setCryptos(i, value);
				retBalance = value.getTokensCount();
				break;
			}
		}
		putAccountValue(addr, oAccountValue.build());
		return retBalance;
	}

	/**
	 * 设置用户账户Nonce
	 * 
	 * @param addr
	 * @param nonce
	 * @return
	 * @throws Exception
	 */
	public synchronized int setNonce(ByteString addr, int nonce) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setNonce(oAccountValue.getNonce() + nonce);
		putAccountValue(addr, oAccountValue.build());
		return oAccountValue.getNonce();
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
	public long getBalance(ByteString addr) throws Exception {
		log.debug("find balance::" + addr);
		Account oAccount = GetAccount(addr);
		if (oAccount == null) {
			throw new Exception("account not found");
		}
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		return oAccountValue.getBalance();
	}

	/**
	 * 获取用户Token账户的Balance
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public long getTokenBalance(ByteString addr, ByteString token) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				return oAccountValue.getTokens(i).getBalance();
			}
		}
		return 0;
	}

	public long getTokenLockedBalance(ByteString addr, ByteString token) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				return oAccountValue.getTokens(i).getLocked();
			}
		}
		return 0;
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
			if (oAccountValue.getCryptos(i).getSymbol().equals(symbol)) {
				return oAccountValue.getCryptos(i).getTokensList();
			}
		}

		return new ArrayList<AccountCryptoToken>();
	}

	/**
	 * 生成加密Token方法。 调用时必须确保symbol不重复。
	 * 
	 * @param addr
	 * @param symbol
	 * @param name
	 * @param code
	 * @throws Exception
	 */
	public synchronized void generateCryptoToken(ByteString addr, String symbol, String[] name, String[] code)
			throws Exception {
		if (name.length != code.length || name.length == 0) {
			throw new Exception(String.format("crypto token name %s or code %s invalid", name.length, code.length));
		}

		int total = name.length;
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		AccountCryptoValue.Builder oAccountCryptoValue = AccountCryptoValue.newBuilder();
		oAccountCryptoValue.setSymbol(symbol);

		for (int i = 0; i < name.length; i++) {
			AccountCryptoToken.Builder oAccountCryptoToken = AccountCryptoToken.newBuilder();
			oAccountCryptoToken.setName(ByteString.copyFromUtf8(name[i]));
			oAccountCryptoToken.setCode(ByteString.copyFromUtf8(code[i]));
			oAccountCryptoToken.setIndex(i);
			oAccountCryptoToken.setTotal(total);
			oAccountCryptoToken.setTimestamp(new Date().getTime());
			oAccountCryptoToken
					.setHash(ByteString.copyFrom(encApi.sha256Encode(oAccountCryptoToken.build().toByteArray())));

			oAccountCryptoToken.setOwner(addr);
			oAccountCryptoToken.setNonce(0);
			oAccountCryptoToken.setOwnertime(new Date().getTime());
			oAccountCryptoValue.addTokens(oAccountCryptoToken);
			tokenMappingAccount(oAccountCryptoToken);
		}

		oAccountValue.addCryptos(oAccountCryptoValue);
		putAccountValue(addr, oAccountValue.build());
	}

	public void ICO(ByteString addr, String token, long total) throws Exception {
		OValue oValue = dao.getAccountDao().get(OEntityBuilder.byteKey2OKey(KeyConstant.DB_EXISTS_TOKEN)).get();
		ICO.Builder oICO;
		if (oValue == null) {
			oICO = ICO.newBuilder();
		} else {
			oICO = ICO.parseFrom(oValue.getExtdata().toByteArray()).toBuilder();
		}

		ICOValue.Builder oICOValue = ICOValue.newBuilder();
		oICOValue.setAddress(encApi.hexEnc(addr.toByteArray()));
		oICOValue.setTimestamp((new Date()).getTime());
		oICOValue.setToken(token);
		oICOValue.setTotal(total);
		oICO.addValue(oICOValue);

		dao.getAccountDao().put(OEntityBuilder.byteKey2OKey(KeyConstant.DB_EXISTS_TOKEN),
				OEntityBuilder.byteValue2OValue(oICO.build().toByteArray()));
	}

	/**
	 * 判断token是否已经发行
	 * 
	 * @param token
	 * @return
	 * @throws Exception
	 */
	public boolean isExistsToken(String token) throws Exception {
		OValue oValue = dao.getAccountDao().get(OEntityBuilder.byteKey2OKey(KeyConstant.DB_EXISTS_TOKEN)).get();
		if (oValue == null) {
			return false;
		}
		ICO oICO = ICO.parseFrom(oValue.getExtdata().toByteArray());

		for (ICOValue oICOValue : oICO.getValueList()) {
			if (oICOValue.getToken().equals(token)) {
				return true;
			}
		}
		return false;
	}

	public void putAccountValue(ByteString addr, AccountValue oAccountValue) {
		if (oAccountValue.getCreator() != null && oAccountValue.getCreator().equals(ByteString.EMPTY)) {
			dao.getAccountDao().put(OEntityBuilder.byteKey2OKey(addr), OEntityBuilder.byteValue2OValue(
					oAccountValue.toByteArray(), encApi.hexEnc(oAccountValue.getCreator().toByteArray())));
		} else {
			dao.getAccountDao().put(OEntityBuilder.byteKey2OKey(addr),
					OEntityBuilder.byteValue2OValue(oAccountValue.toByteArray()));
		}

		if (this.stateTrie != null) {
			this.stateTrie.put(addr.toByteArray(), oAccountValue.toByteArray());
			// this.stateTrie.flush();
		}
	}

	public void BatchPutAccounts(LinkedList<OKey> keys, LinkedList<AccountValue> values) {
		OKey[] keysArray = new OKey[keys.size()];
		OValue[] valuesArray = new OValue[values.size()];

		LinkedList<OValue> list = new LinkedList<>();
		for (int i = 0; i < values.size(); i++) {
			// log.debug("====put batch account A::" +
			// encApi.hexEnc(keys.get(i).getData().toByteArray()));

			list.add(OEntityBuilder.byteValue2OValue(values.get(i).toByteArray()));
			if (this.stateTrie != null) {
				this.stateTrie.put(keys.get(i).getData().toByteArray(), values.get(i).toByteArray());
			}
		}

		dao.getAccountDao().batchPuts(keys.toArray(keysArray), list.toArray(valuesArray));
	}

	public void BatchPutAccounts(Map<String, Account.Builder> accountValues) {
		OKey[] keysArray = new OKey[accountValues.size()];
		OValue[] valuesArray = new OValue[accountValues.size()];
		Set<String> keySets = accountValues.keySet();
		Iterator<String> iterator = keySets.iterator();
		int i = 0;
		while (iterator.hasNext()) {
			String key = iterator.next();
			AccountValue value = accountValues.get(key).getValue();
			keysArray[i] = OEntityBuilder.byteKey2OKey(encApi.hexDec(key));
			valuesArray[i] = OEntityBuilder.byteValue2OValue(value.toByteArray());

			// log.debug("====put batch account B::" + key);

			i = i + 1;
		}
		dao.getAccountDao().batchPuts(keysArray, valuesArray);
	}

	public void tokenMappingAccount(AccountCryptoToken.Builder acBuilder) {
		// log.debug("====put tokenMappingAccount::");

		dao.getAccountDao().put(OEntityBuilder.byteKey2OKey(acBuilder.getHash()),
				OEntityBuilder.byteValue2OValue(acBuilder.build().toByteArray()));
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
			OValue otValue = dao.getAccountDao().get(OEntityBuilder.byteKey2OKey(tokenHash)).get();
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

		Account contract = GetAccount(address);
		AccountValue.Builder oAccountValue = contract.getValue().toBuilder();
		oAccountValue.setStorage(ByteString.copyFrom(rootHash));
		putAccountValue(address, oAccountValue.build());
	}

	public StorageTrie getStorageTrie(ByteString address) {
		StorageTrie oStorage = storageTrieCache.get(encApi.hexEnc(address.toByteArray()));
		if (oStorage == null) {
			oStorage = new StorageTrie(this.dao, this.encApi);
			Account contract = GetAccount(address);
			log.debug("contract address::" + address);
			if (contract.getValue() == null || contract.getValue().getStorage() == null) {
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
}
