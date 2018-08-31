package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TXStatus;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.trie.StorageTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;

/**
 * 仲裁
 * 
 * @author brew
 *
 */
@Slf4j
public class ActuatorSanctionTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorSanctionTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	/*
	 * @see org.brewchain.account.core.transaction.AbstractTransactionActuator#
	 * onVerify(org.brewchain.account.gens.Tx.MultiTransaction, java.util.Map,
	 * java.util.Map)
	 */
	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		if (TXStatus.isDone(oMultiTransaction) || TXStatus.isError(oMultiTransaction)) {
			return;
		}

		if (oMultiTransaction.getTxBody().getOutputsCount() != 1) {
			// 地址0 ： 仲裁的合约地址
			// 地址1 ： 本轮投票的地址
			throw new TransactionExecuteException("parameter invalid, outputs must be one.");
		}
		if (oMultiTransaction.getTxBody().getInputsCount() <= 0) {
			throw new TransactionExecuteException("parameter invalid, inputs must large than one.");
		}
		encApi.hexEnc(oMultiTransaction.getTxBody().getOutputs(0).getAddress().toByteArray());

		BigInteger inputsTotal = BigInteger.ZERO;
		BigInteger outputsTotal = BigInteger.ZERO;

		MultiTransactionOutput output = oMultiTransaction.getTxBody().getOutputs(0);
		Account.Builder contractAccount = accounts.get(encApi.hexEnc(output.getAddress().toByteArray()));
		// if(contractAccount.getValue().getCodeHash()){

		// }
		// 获取cws
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			if (oInput.getToken().isEmpty() || oInput.getToken() == "") {
				throw new Exception(String.format("token must not be empty"));
			}
			// TODO sean
			// 1. 检查cws够不够
			// 2. oinput的签名：ecverhash256.txbody
			// 3. 取出account的上次签名结果，要求是一致的才行，如果为空，则不判断

			// TODO
			String token = oInput.getToken();
			// 获取到input的cws，扣除到
			// verify..
			// val pubKeyBytes = Hex.decode(pubKey);
			// val x = Arrays.copyOfRange(pubKeyBytes, 0, 32);
			// val y = Arrays.copyOfRange(pubKeyBytes, 32, 64);

			// encApi.ecVerify(arg0, arg1, arg2)

		}
	}

	/**
	 * 对节点的cws账户进行扣除，锁定到仲裁地址，output的第一个地址
	 * 
	 * @param oMultiTransaction
	 * @param accounts
	 */
	public void cwsLock(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) {
		// TODO: up to sean
	}

	public boolean contains(ByteString bs, ByteString sub) {
		boolean founded = true;
		for (int i = 0; i < bs.size(); i += 64) {
			founded = true;
			for (int j = 0; j < sub.size() && j + i < bs.size(); j++) {
				if (bs.byteAt(i + j) != sub.byteAt(j)) {
					founded = false;
					break;
				}
			}
			if (founded) {
				return true;
			}
		}
		return false;
	}

	public ByteString setResult(ByteString bs, ByteString address, byte[] resultHash) {
		boolean founded = true;
		ByteBuffer buffer = ByteBuffer.allocate(bs.size());
		for (int i = 64; i < bs.size(); i += 52) {
			founded = true;
			for (int j = 0; j < address.size() && j + i < bs.size(); j++) {
				if (bs.byteAt(i + j) != address.byteAt(j)) {
					founded = false;
					break;
				}
			}
			if (!founded) {
				buffer.put(bs.substring(i, i + 52).toByteArray());
			} else {
				buffer.put(bs.substring(i, i + 20).toByteArray());
				buffer.put(resultHash);
			}
		}
		return ByteString.copyFrom(buffer);
	}

	ByteString zeroBS = ByteString.copyFrom(Hex.decode(StringUtils.rightPad("", 64, '0')));

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		if (TXStatus.isDone(oMultiTransaction) || TXStatus.isError(oMultiTransaction)) {
			return ByteString.EMPTY;
		}
		cwsLock(oMultiTransaction, accounts);
		MultiTransactionOutput output_ContractID = oMultiTransaction.getTxBody().getOutputs(0);
		StorageTrie storageTrie = oAccountHelper.getStorageTrie(output_ContractID.getAddress());
		Account.Builder contractAccount = oAccountHelper.GetAccount(output_ContractID.getAddress());
		AccountValue.Builder oAccountValue = contractAccount.getValue().toBuilder();
		byte[] nonceBB = ByteUtil.intToBytes(oAccountValue.getNonce() + 1);
		byte votehashBB[] = storageTrie.get(nonceBB);
		String txhash;
		byte voteDatas[];
		if (votehashBB == null) {
			// create new one
			// already have one
			votehashBB = encApi.hexDec(oMultiTransaction.getTxHash());
			txhash = oMultiTransaction.getTxHash();
			voteDatas = oMultiTransaction.getTxBody().toByteArray();

			ByteBuffer buffer = ByteBuffer.allocate(voteDatas.length);
			buffer.put(voteDatas, 0, 64);
			for (int i = 64; i + 20 < voteDatas.length; i++) {
				buffer.put(voteDatas, i, 20);
				buffer.put(zeroBS.toByteArray(), i + 20, 32);
			}
			voteDatas = ByteString.copyFrom(buffer).toByteArray();
		} else {
			txhash = encApi.hexEnc(votehashBB);
			voteDatas = storageTrie.get(votehashBB);
		}
		if (voteDatas == null || !oMultiTransaction.getTxBody().equals(voteDatas)) {
			log.debug("txbody not equal:" + txhash);
			return ByteString.EMPTY;
		}
		ByteString bs = ByteString.copyFrom(voteDatas);// .concat(other)
		// 投票截止结算
		// 和前面64个字节完全一样
		long matchNeed = new BigInteger(1, bs.substring(0, 8).toByteArray()).longValue();
		long endBlock = new BigInteger(1, bs.substring(8, 16).toByteArray()).longValue();
		byte votestatus = bs.byteAt(16);
		if (votestatus > 0) {// already finished!
			log.debug("tx vote finished:status=" + votestatus + ",txid=" + txhash);
			return ByteString.EMPTY;
		}

		byte[] resultHex = encApi.sha256Encode(oMultiTransaction.getResult().toByteArray());
		// 32+32=64,each
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			bs = setResult(bs, oInput.getAddress(), resultHex);
		}
		ByteString matchBS = bs.substring(32, 64);
		int zeroCount = 0;
		int matchCount = 0;
		int unmatchCount = 0;
		int totalCount = bs.size() / 64 - 1;
		for (int i = 64 + 20; i + 32 < bs.size(); i += 54) {
			if (bs.substring(i, i + 32).equals(matchBS)) {
				matchCount++;
			} else if (bs.substring(i, i + 32).equals(zeroBS)) {
				zeroCount++;
			} else {
				unmatchCount++;
			}
		}
		log.debug("try to check vote:matchCount={},totalCount={},zeroCount={},tx={},mathbs={}", matchCount, totalCount,
				zeroCount, txhash, encApi.hexEnc(matchBS.toByteArray()));
		if (matchCount >= matchNeed) {
			log.debug("Converge,fro tx = " + txhash + ",resulthash=" + encApi.hexEnc(matchBS.toByteArray())
					+ ",endblock=" + endBlock + ",curblock=" + oBlock.getHeader().getNumber());
			votestatus = 'd';// done
		} else if (unmatchCount >= matchNeed) {
			log.debug("reject,fro tx = " + txhash + ",resulthash=" + encApi.hexEnc(matchBS.toByteArray()) + ",endblock="
					+ endBlock + ",curblock=" + oBlock.getHeader().getNumber());
			votestatus = 'e';// reject
		} else if (zeroCount <= 0) {
			log.debug("Not Converge,fro tx = " + txhash + ",resulthash=" + encApi.hexEnc(matchBS.toByteArray())
					+ ",endblock=" + endBlock + ",curblock=" + oBlock.getHeader().getNumber());
			votestatus = 'u';// undeciside
		} else if (oBlock.getHeader().getNumber() >= endBlock) {
			log.debug("cannot wait for other choice . block range out ." + txhash + ",resulthash="
					+ encApi.hexEnc(matchBS.toByteArray()) + ",endblock=" + endBlock + ",curblock="
					+ oBlock.getHeader().getNumber());
			votestatus = 't';// timeout
		}

		byte newbb[] = bs.toByteArray();
		newbb[16] = votestatus;
		if (votestatus > 0) {// already finished!
			log.debug("tx vote finished:status=" + votestatus + ",txid=" + txhash + ",save to account");
			// increase
			oAccountValue.setNonce(oAccountValue.getNonce() + 1);
			contractAccount.setValue(oAccountValue);
			accounts.put(encApi.hexEnc(contractAccount.getAddress().toByteArray()), contractAccount);
		}

		oAccountHelper.saveStorage(output_ContractID.getAddress(), nonceBB, votehashBB);
		oAccountHelper.saveStorage(output_ContractID.getAddress(), votehashBB, newbb);

		return ByteString.EMPTY;
	}
}
