package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.TransactionParameterInvalidException;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.SanctionStorage;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.SanctionData;
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

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		if (oMultiTransaction.getTxBody().getInputsCount() != 1
				|| oMultiTransaction.getTxBody().getOutputsCount() != 1) {
			throw new TransactionParameterInvalidException("parameter invalid, inputs or outputs must be only one");
		}

		SanctionData.Builder oSanctionData = SanctionData
				.parseFrom(oMultiTransaction.getTxBody().getData().toByteArray()).toBuilder();
		if (oBlock != null && oBlock.getHeader().getNumber() > oSanctionData.getEndBlockHeight()) {
			throw new TransactionParameterInvalidException("parameter invalid, this vote has ended");
		}

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder senderAccount = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));

		ByteString contractAddr = oMultiTransaction.getTxBody().getOutputs(0).getAddress();
		Account.Builder contractAccount = accounts.get(encApi.hexEnc(contractAddr.toByteArray()));
		AccountValue.Builder oContractAccountValue = contractAccount.getValue().toBuilder();

		byte[] voteKey = ByteUtil.intToBytes(oContractAccountValue.getNonce());

		byte votehashBB[] = oAccountHelper.getStorage(contractAccount, voteKey);
		if (votehashBB == null) {
			if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())
					.compareTo(oTransactionHelper.getBlockChainConfig().getMinSanctionCost()) < 0) {
				throw new TransactionParameterInvalidException("parameter invalid, not enouth CWS token cost");
			}

			if (oAccountHelper.getTokenBalance(senderAccount, "CWS")
					.compareTo(ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())) < 0) {
				throw new TransactionParameterInvalidException(
						"parameter invalid, not enouth CWS token to initiate vote");
			}
		} else {
			if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())
					.compareTo(oTransactionHelper.getBlockChainConfig().getMinVoteCost()) < 0) {
				throw new TransactionParameterInvalidException("parameter invalid, not enouth CWS token cost");
			}

			SanctionStorage oSanctionStorage = SanctionStorage.parseFrom(votehashBB);

			for (ByteString b : oSanctionStorage.getAddressList()) {
				if (FastByteComparisons.equal(b.toByteArray(), oInput.getAddress().toByteArray())) {
					throw new TransactionParameterInvalidException(
							"parameter invalid, Duplicate join vote is not allowed");
				}
			}

			if (oAccountHelper.getTokenBalance(senderAccount, "CWS")
					.compareTo(ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())) < 0) {
				throw new TransactionParameterInvalidException("parameter invalid, not enouth CWS token to join vote");
			}

			MultiTransaction oVoteTx = oTransactionHelper.GetTransaction(oSanctionStorage.getVoteTxHash());
			if (oVoteTx == null) {
				throw new TransactionParameterInvalidException("parameter invalid, not found vote transaction");
			}

			SanctionData.Builder oVoteSanctionData = SanctionData.parseFrom(oVoteTx.getTxBody().getData().toByteArray())
					.toBuilder();

			if (oSanctionData.getEndBlockHeight() != oVoteSanctionData.getEndBlockHeight() || !FastByteComparisons
					.equal(oVoteSanctionData.getContent().toByteArray(), oSanctionData.getContent().toByteArray())) {
				throw new TransactionParameterInvalidException("parameter invalid, vote content invalidate");
			}
		}
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
			oAccountHelper.subTokenBalance(senderAccount, "CWS",
					ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()));
			oAccountHelper.addTokenBalance(contractAccount, "CWS",
					ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()));
			oSanctionStorage.setVoteTxHash(oMultiTransaction.getTxHash());
		} else {
			oAccountHelper.subTokenBalance(senderAccount, "CWS",
					ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()));
			oAccountHelper.addTokenBalance(contractAccount, "CWS",
					ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()));
			oSanctionStorage = SanctionStorage.parseFrom(votehashBB).toBuilder();
		}
		oSanctionStorage.addAddress(oInput.getAddress());
		oSanctionStorage.addTxHash(oMultiTransaction.getTxHash());
		oAccountHelper.putStorage(contractAccount, voteKey, oSanctionStorage.build().toByteArray());
		accounts.put(encApi.hexEnc(contractAddr.toByteArray()), contractAccount);

		senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
		senderAccount.setValue(senderAccountValue);
		accounts.put(encApi.hexEnc(oInput.getAddress().toByteArray()), senderAccount);

		return ByteString.EMPTY;
	}
}
