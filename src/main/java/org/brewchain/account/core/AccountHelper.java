package org.brewchain.account.core;

import java.util.LinkedList;
import java.util.List;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OKey;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.account.gens.Act.Account;
import org.brewchain.account.gens.Act.AccountTokenValue;
import org.brewchain.account.gens.Act.AccountValue;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

/**
 * @author sean 用于账户的存储逻辑封装
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

	public AccountHelper() {
	}

	public synchronized Account CreateAccount(byte[] address, byte[] pubKey) {
		return CreateUnionAccount(address, pubKey, 0, 0, 0, null);
	}

	public synchronized Account CreateUnionAccount(byte[] address, byte[] pubKey, int max, int acceptMax,
			int acceptLimit, List<ByteString> addresses) {
		Account.Builder oUnionAccount = Account.newBuilder();
		AccountValue.Builder oUnionAccountValue = AccountValue.newBuilder();

		oUnionAccountValue.setAcceptLimit(acceptLimit);
		oUnionAccountValue.setAcceptMax(acceptMax);
		if (addresses != null)
			oUnionAccountValue.addAllAddress(addresses);

		oUnionAccountValue.setBalance(KeyConstant.EMPTY_BALANCE.intValue());
		oUnionAccountValue.setMax(max);
		oUnionAccountValue.setNonce(KeyConstant.EMPTY_NONCE.intValue());
		oUnionAccountValue.setPubKey(ByteString.copyFrom(pubKey));

		oUnionAccount.setAddress(ByteString.copyFrom(address));
		oUnionAccount.setValue(oUnionAccountValue);

		return CreateUnionAccount(oUnionAccount.build());
	}

	public synchronized Account CreateUnionAccount(Account oAccount) {
		putAccountValue(oAccount.getAddress().toByteArray(), oAccount.getValue());
		return oAccount;
	}
	
	public synchronized void DeleteAccount(byte[] address) {
		dao.getAccountDao().delete(OEntityBuilder.byteKey2OKey(address));
	}

	public synchronized boolean isExist(byte[] addr) throws Exception {
		return GetAccount(addr) != null;
	}

	/**
	 * 获取用户账户
	 * 
	 * @param addr
	 * @return
	 */
	public synchronized Account GetAccount(byte[] addr) {
		try {
			OValue oValue = dao.getAccountDao().get(OEntityBuilder.byteKey2OKey(addr)).get();
			AccountValue.Builder oAccountValue = AccountValue.newBuilder();
			oAccountValue.mergeFrom(oValue.getExtdata());

			Account.Builder oAccount = Account.newBuilder();
			oAccount.setAddress(ByteString.copyFrom(addr));
			oAccount.setValue(oAccountValue);
			return oAccount.build();
		} catch (Exception e) {
			// TODO: handle exception
		}
		return null;
	}

	public synchronized int increaseNonce(byte[] addr) throws Exception {
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
	public synchronized long addBalance(byte[] addr, int balance) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
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
	public synchronized long addTokenBalance(byte[] addr, String token, long balance) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();

		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				oAccountValue.getTokens(i).toBuilder().setBalance(oAccountValue.getTokens(i).getBalance() + balance);
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

	/**
	 * 设置用户账户Nonce
	 * 
	 * @param addr
	 * @param nonce
	 * @return
	 * @throws Exception
	 */
	public synchronized int setNonce(byte[] addr, int nonce) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		oAccountValue.setNonce(oAccountValue.getNonce() + nonce);
		putAccountValue(addr, oAccountValue.build());
		return oAccountValue.getNonce();
	}

	/**
	 * 获取用户账户Nonce
	 * 
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public synchronized int getNonce(byte[] addr) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
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
	public synchronized long getBalance(byte[] addr) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
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
	public synchronized long getTokenBalance(byte[] addr, String token) throws Exception {
		Account.Builder oAccount = GetAccount(addr).toBuilder();
		AccountValue.Builder oAccountValue = oAccount.getValue().toBuilder();
		for (int i = 0; i < oAccountValue.getTokensCount(); i++) {
			if (oAccountValue.getTokens(i).getToken().equals(token)) {
				return oAccountValue.getTokens(i).getBalance();
			}
		}
		return 0;
	}

	private void putAccountValue(byte[] addr, AccountValue oAccountValue) {
		dao.getAccountDao().put(OEntityBuilder.byteKey2OKey(addr),
				OEntityBuilder.byteValue2OValue(oAccountValue.toByteArray()));
	}

	public void BatchPutAccounts(LinkedList<OKey> keys, LinkedList<OValue> values) {
		OKey[] keysArray = new OKey[keys.size()];
		OValue[] valuesArray = new OValue[values.size()];

		dao.getAccountDao().batchPuts(keys.toArray(keysArray), values.toArray(valuesArray));
	}
}
