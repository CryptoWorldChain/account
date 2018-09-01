package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TXStatus;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.actuator.AbstractTransactionActuator.TransactionExecuteException;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.trie.StorageTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.SanctionStorage;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.SanctionData;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.UnitUtil;

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
		if (oMultiTransaction.getTxBody().getInputsCount() != 1
				|| oMultiTransaction.getTxBody().getOutputsCount() != 1) {
			throw new TransactionExecuteException("parameter invalid, inputs or outputs must be only one");
		}

		SanctionData.Builder oSanctionData = SanctionData
				.parseFrom(oMultiTransaction.getTxBody().getData().toByteArray()).toBuilder();
		if (oBlock != null && oBlock.getHeader().getNumber() > oSanctionData.getEndBlockHeight()) {
			throw new TransactionExecuteException("parameter invalid, this vote has ended");
		}

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder senderAccount = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));

		ByteString contractAddr = oMultiTransaction.getTxBody().getOutputs(0).getAddress();
		Account.Builder contractAccount = accounts.get(encApi.hexEnc(contractAddr.toByteArray()));
		AccountValue.Builder oContractAccountValue = contractAccount.getValue().toBuilder();

		byte[] voteKey = ByteUtil.intToBytes(oContractAccountValue.getNonce());

		byte votehashBB[] = oAccountHelper.getStorage(contractAccount, voteKey);
		if (votehashBB == null) {
			if (oAccountHelper.getTokenBalance(senderAccount, "CWS").compareTo(UnitUtil.toWei("1100")) < 0) {
				throw new TransactionExecuteException("parameter invalid, not enouth CWS token to initiate vote");
			}
		} else {
			SanctionStorage oSanctionStorage = SanctionStorage.parseFrom(votehashBB);

			for (ByteString b : oSanctionStorage.getAddressList()) {
				if (FastByteComparisons.equal(b.toByteArray(), oInput.getAddress().toByteArray())) {
					throw new TransactionExecuteException("parameter invalid, Duplicate join vote is not allowed");
				}
			}

			if (oAccountHelper.getTokenBalance(senderAccount, "CWS").compareTo(UnitUtil.toWei("100")) < 0) {
				throw new TransactionExecuteException("parameter invalid, not enouth CWS token to join vote");
			}

			MultiTransaction oVoteTx = oTransactionHelper.GetTransaction(oSanctionStorage.getVoteTxHash());
			if (oVoteTx == null) {
				throw new TransactionExecuteException("parameter invalid, not found vote transaction");
			}

			SanctionData.Builder oVoteSanctionData = SanctionData.parseFrom(oVoteTx.getTxBody().getData().toByteArray())
					.toBuilder();

			if (!FastByteComparisons.equal(oVoteSanctionData.getContent().toByteArray(),
					oSanctionData.getContent().toByteArray())) {
				throw new TransactionExecuteException("parameter invalid, vote content invalidate");
			}
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
		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder senderAccount = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = senderAccount.getValue().toBuilder();

		ByteString contractAddr = oMultiTransaction.getTxBody().getOutputs(0).getAddress();
		Account.Builder contractAccount = accounts.get(encApi.hexEnc(contractAddr.toByteArray()));
		AccountValue.Builder oContractAccountValue = contractAccount.getValue().toBuilder();

		byte[] voteKey = ByteUtil.intToBytes(oContractAccountValue.getNonce());

		byte votehashBB[] = oAccountHelper.getStorage(contractAccount, voteKey);
		SanctionStorage.Builder oSanctionStorage = SanctionStorage.newBuilder();
		if (votehashBB == null) {
			oAccountHelper.subTokenBalance(senderAccount, "CWS", UnitUtil.toWei("1100"));
			oAccountHelper.addTokenBalance(contractAccount, "CWS", UnitUtil.toWei("1100"));
			oSanctionStorage.setVoteTxHash(oMultiTransaction.getTxHash());
		} else {
			oAccountHelper.subTokenBalance(senderAccount, "CWS", UnitUtil.toWei("100"));
			oAccountHelper.addTokenBalance(contractAccount, "CWS", UnitUtil.toWei("100"));
			oSanctionStorage = SanctionStorage.parseFrom(votehashBB).toBuilder();
		}
		oSanctionStorage.addAddress(oInput.getAddress());
		oSanctionStorage.addTxHash(oMultiTransaction.getTxHash());
		oAccountHelper.putStorage(contractAccount, voteKey, oSanctionStorage.build().toByteArray());
		accounts.put(encApi.hexEnc(contractAddr.toByteArray()), contractAccount);

		senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
		senderAccount.setValue(senderAccountValue);
		accounts.put(encApi.hexEnc(oInput.getAddress().toByteArray()), senderAccount);

		//
		// String txhash;
		// byte voteDatas[];
		// if (votehashBB == null) {
		// // create new one
		// // already have one
		// voteDatas = initNewSanction(oMultiTransaction);
		// txhash = oMultiTransaction.getTxHash();
		// votehashBB = encApi.hexDec(txhash);
		// } else {
		// txhash = encApi.hexEnc(votehashBB);
		// voteDatas = oAccountHelper.getStorage(contractAccount, votehashBB);
		// }
		// if (voteDatas == null) {
		// log.debug("txbody not equal:" + txhash);
		// return ByteString.EMPTY;
		// }
		// ByteString bs = ByteString.copyFrom(voteDatas);// .concat(other)
		// // 投票截止结算
		// // 和前面64个字节完全一样
		// long matchNeed = new BigInteger(1, bs.substring(0,
		// 8).toByteArray()).longValue();
		// long endBlock = new BigInteger(1, bs.substring(8,
		// 16).toByteArray()).longValue();
		// if (oBlock.getHeader().getNumber() >= endBlock) {
		// voteDatas = initNewSanction(oMultiTransaction);
		// txhash = oMultiTransaction.getTxHash();
		// votehashBB = encApi.hexDec(txhash);
		// matchNeed = new BigInteger(1, bs.substring(0, 8).toByteArray()).longValue();
		// endBlock = new BigInteger(1, bs.substring(8, 16).toByteArray()).longValue();
		// bs = ByteString.copyFrom(voteDatas);// .concat(other)
		// }
		//
		// byte votestatus = bs.byteAt(16);
		// if (votestatus > 0) {// already finished!
		// log.debug("tx vote finished:status=" + votestatus + ",txid=" + txhash);
		// return ByteString.EMPTY;
		// }
		//
		// byte[] resultHash =
		// encApi.sha256Encode(oMultiTransaction.getTxBody().getExdata().toByteArray());
		// // 32+32=64,each
		// ByteString matchBS = bs.substring(32, 64);
		// for (MultiTransactionInput oInput :
		// oMultiTransaction.getTxBody().getInputsList()) {
		// bs = setResult(bs, oInput.getAddress(), resultHash);
		// }
		// int zeroCount = 0;
		// int matchCount = 0;
		// int unmatchCount = 0;
		// int totalCount = (bs.size() - 64) / 52;
		// for (int i = 64; i + 32 <= bs.size(); i += 52) {
		// if (bs.substring(i + 20, i + 52).equals(matchBS)) {
		// matchCount++;
		// } else if (bs.substring(i + 20, i + 52).equals(zeroBS)) {
		// zeroCount++;
		// } else {
		// unmatchCount++;
		// }
		// }
		// log.debug("sanction
		// check:matchCount={},totalCount={},zeroCount={},tx={},mathbs={}", matchCount,
		// totalCount,
		// zeroCount, txhash, encApi.hexEnc(matchBS.toByteArray()));
		// if (matchCount >= matchNeed) {
		// log.debug("Converge,fro tx = " + txhash + ",resulthash=" +
		// encApi.hexEnc(matchBS.toByteArray())
		// + ",endblock=" + endBlock + ",curblock=" + oBlock.getHeader().getNumber());
		// votestatus = 'd';// done
		// } else if (unmatchCount >= matchNeed) {
		// log.debug("reject,fro tx = " + txhash + ",resulthash=" +
		// encApi.hexEnc(matchBS.toByteArray()) + ",endblock="
		// + endBlock + ",curblock=" + oBlock.getHeader().getNumber());
		// votestatus = 'e';// reject
		// } else if (zeroCount <= 0) {
		// log.debug("Not Converge,fro tx = " + txhash + ",resulthash=" +
		// encApi.hexEnc(matchBS.toByteArray())
		// + ",endblock=" + endBlock + ",curblock=" + oBlock.getHeader().getNumber());
		// votestatus = 'u';// undeciside
		// } else if (oBlock.getHeader().getNumber() >= endBlock) {
		// log.debug("cannot wait for other choice . block range out ." + txhash +
		// ",resulthash="
		// + encApi.hexEnc(matchBS.toByteArray()) + ",endblock=" + endBlock +
		// ",curblock="
		// + oBlock.getHeader().getNumber());
		// votestatus = 't';// timeout
		// }
		//
		// byte newbb[] = bs.toByteArray();
		// newbb[16] = votestatus;
		// if (votestatus > 0) {// already finished!
		// log.debug("tx vote finished:status=" + votestatus + ",txid=" + txhash +
		// ",save to account");
		// // increase
		// oAccountValue.setNonce(oAccountValue.getNonce() + 1);
		// contractAccount.setValue(oAccountValue);
		// accounts.put(encApi.hexEnc(contractAccount.getAddress().toByteArray()),
		// contractAccount);
		// }
		// accounts.put(encApi.hexEnc(contractAccount.getAddress().toByteArray()),
		// contractAccount);
		// oAccountHelper.putAccountValue(contractAccount.getAddress(),
		// contractAccount.getValue());
		// oAccountHelper.saveStorage(output_ContractID.getAddress(), nonceBB,
		// votehashBB);
		// oAccountHelper.saveStorage(output_ContractID.getAddress(), votehashBB,
		// newbb);

		return ByteString.EMPTY;
	}

	@Override
	public boolean needSignature() {
		return true;
	}
}
