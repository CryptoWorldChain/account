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

	public ByteString setResult(ByteString bs, ByteString address, byte[] resultHash) {
		boolean founded = true;
		ByteBuffer buffer = ByteBuffer.allocate(bs.size());
		buffer.put(bs.substring(0, 64).toByteArray());
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
		buffer.flip();
		return ByteString.copyFrom(buffer);
	}

	ByteString zeroBS = ByteString.copyFrom(Hex.decode(StringUtils.rightPad("", 64, '0')));

	public byte[] initNewSanction(MultiTransaction oMultiTransaction) {
		byte[] voteDatas = oMultiTransaction.getTxBody().getData().toByteArray();

		ByteBuffer buffer = ByteBuffer.allocate(voteDatas.length + (voteDatas.length - 64) / 20 * 32);
		buffer.put(voteDatas, 0, 32);
		byte[] resultHash = encApi.sha256Encode(oMultiTransaction.getTxBody().getExdata().toByteArray());
		buffer.put(resultHash, 0, 32);
		for (int i = 64; i + 20 <= voteDatas.length; i += 20) {
			buffer.put(voteDatas, i, 20);
			buffer.put(zeroBS.toByteArray(), 0, 32);
		}
		buffer.flip();
		return ByteString.copyFrom(buffer).toByteArray();
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		if (TXStatus.isDone(oMultiTransaction) || TXStatus.isError(oMultiTransaction)) {
			return ByteString.EMPTY;
		}
		cwsLock(oMultiTransaction, accounts);
		MultiTransactionOutput output_ContractID = oMultiTransaction.getTxBody().getOutputs(0);
		ByteString contractAddr = oMultiTransaction.getTxBody().getOutputs(0).getAddress();
		Account.Builder contractAccount = oAccountHelper.GetAccount(contractAddr);
		if (contractAccount == null) {
			contractAccount = oAccountHelper.GetAccountOrCreate(contractAddr);
			accounts.put(encApi.hexEnc(contractAddr.toByteArray()), contractAccount);
		}
		StorageTrie storageTrie = oAccountHelper.getStorageTrie(contractAccount);
		AccountValue.Builder oAccountValue = contractAccount.getValue().toBuilder();
		byte[] nonceBB = ByteUtil.intToBytes(oAccountValue.getNonce() + 1);
		byte votehashBB[] = storageTrie.get(nonceBB);
		String txhash;
		byte voteDatas[];
		if (votehashBB == null) {
			// create new one
			// already have one
			voteDatas = initNewSanction(oMultiTransaction);
			txhash = oMultiTransaction.getTxHash();
			votehashBB = encApi.hexDec(txhash);
		} else {
			txhash = encApi.hexEnc(votehashBB);
			voteDatas = storageTrie.get(votehashBB);
		}
		if (voteDatas == null) {
			log.debug("txbody not equal:" + txhash);
			return ByteString.EMPTY;
		}
		ByteString bs = ByteString.copyFrom(voteDatas);// .concat(other)
		// 投票截止结算
		// 和前面64个字节完全一样
		long matchNeed = new BigInteger(1, bs.substring(0, 8).toByteArray()).longValue();
		long endBlock = new BigInteger(1, bs.substring(8, 16).toByteArray()).longValue();
		if (oBlock.getHeader().getNumber() >= endBlock) {
			voteDatas = initNewSanction(oMultiTransaction);
			txhash = oMultiTransaction.getTxHash();
			votehashBB = encApi.hexDec(txhash);
			matchNeed = new BigInteger(1, bs.substring(0, 8).toByteArray()).longValue();
			endBlock = new BigInteger(1, bs.substring(8, 16).toByteArray()).longValue();
			bs = ByteString.copyFrom(voteDatas);// .concat(other)
		}

		byte votestatus = bs.byteAt(16);
		if (votestatus > 0) {// already finished!
			log.debug("tx vote finished:status=" + votestatus + ",txid=" + txhash);
			return ByteString.EMPTY;
		}

		byte[] resultHash = encApi.sha256Encode(oMultiTransaction.getTxBody().getExdata().toByteArray());
		// 32+32=64,each
		ByteString matchBS = bs.substring(32, 64);
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			bs = setResult(bs, oInput.getAddress(), resultHash);
		}
		int zeroCount = 0;
		int matchCount = 0;
		int unmatchCount = 0;
		int totalCount = (bs.size() - 64) / 52;
		for (int i = 64; i + 32 <= bs.size(); i += 52) {
			if (bs.substring(i + 20, i + 52).equals(matchBS)) {
				matchCount++;
			} else if (bs.substring(i + 20, i + 52).equals(zeroBS)) {
				zeroCount++;
			} else {
				unmatchCount++;
			}
		}
		log.debug("sanction check:matchCount={},totalCount={},zeroCount={},tx={},mathbs={}", matchCount, totalCount,
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
		accounts.put(encApi.hexEnc(contractAccount.getAddress().toByteArray()), contractAccount);
		oAccountHelper.putAccountValue(contractAccount.getAddress(), contractAccount.getValue());
		oAccountHelper.saveStorage(output_ContractID.getAddress(), nonceBB, votehashBB);
		oAccountHelper.saveStorage(output_ContractID.getAddress(), votehashBB, newbb);

		return ByteString.EMPTY;
	}

	@Override
	public boolean needSignature() {
		return false;// for test
	}

}
