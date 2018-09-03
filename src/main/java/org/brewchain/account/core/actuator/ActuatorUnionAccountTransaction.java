package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.TransactionExecuteException;
import org.brewchain.account.exception.TransactionParameterInvalidException;
import org.brewchain.account.exception.TransactionVerifyException;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.DateTimeUtil;
import org.brewchain.account.util.FastByteComparisons;
import org.brewchain.core.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.Account.Builder;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.UnionAccountStorage;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ActuatorUnionAccountTransaction extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorUnionAccountTransaction(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
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

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);

		Account.Builder unionAccount = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder unionAccountValue = unionAccount.getValue().toBuilder();
		int txNonce = oInput.getNonce();
		int nonce = unionAccountValue.getNonce();
		if (nonce != txNonce) {
			throw new TransactionParameterInvalidException(
					String.format("sender nonce %s is not equal with transaction nonce %s", nonce, nonce));
		}

		BigInteger amount = ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray());
		BigInteger unionAccountBalance = ByteUtil.bytesToBigInteger(unionAccountValue.getBalance().toByteArray());
		BigInteger acceptMax = ByteUtil.bytesToBigInteger(unionAccountValue.getAcceptMax().toByteArray());
		BigInteger max = ByteUtil.bytesToBigInteger(unionAccountValue.getMax().toByteArray());

		if (amount.compareTo(BigInteger.ZERO) <= 0) {
			throw new TransactionParameterInvalidException("parameter invalid, amount invalidate");
		}

		if (amount.compareTo(unionAccountBalance) > 0) {
			throw new TransactionParameterInvalidException(
					String.format("sender balance %s less than %s", unionAccountBalance, amount));
		}

		if (amount.compareTo(max) > 0) {
			throw new TransactionParameterInvalidException("parameter invalid, amount must small than " + max);
		}

		if (!FastByteComparisons.equal(oInput.getAmount().toByteArray(),
				oMultiTransaction.getTxBody().getOutputs(0).getAmount().toByteArray())) {
			throw new TransactionParameterInvalidException("parameter invalid, transaction value not equal");
		}

		if (amount.compareTo(acceptMax) >= 0) {
			if (oMultiTransaction.getTxBody().getData() != null && !oMultiTransaction.getTxBody().getData().isEmpty()) {

				MultiTransaction originalTx = oTransactionHelper
						.GetTransaction(encApi.hexEnc(oMultiTransaction.getTxBody().getData().toByteArray()));
				if (originalTx == null) {
					throw new TransactionParameterInvalidException("parameter invalid, not found original transaction");
				}

				byte[] confirmTxBytes = oAccountHelper.getStorage(unionAccount,
						oMultiTransaction.getTxBody().getData().toByteArray());
				UnionAccountStorage oUnionAccountStorage = UnionAccountStorage.parseFrom(confirmTxBytes);

				boolean isAlreadyConfirm = false;
				boolean isExistsConfirmTx = false;
				for (int i = 0; i < oUnionAccountStorage.getAddressCount(); i++) {
					if (FastByteComparisons.equal(oUnionAccountStorage.getAddress(i).toByteArray(),
							oMultiTransaction.getTxBody().getExdata().toByteArray())) {
						isAlreadyConfirm = true;
						break;
					}
					if (oUnionAccountStorage.getTxHash(i)
							.equals(encApi.hexEnc(oMultiTransaction.getTxBody().getData().toByteArray()))) {
						isExistsConfirmTx = true;
					}
				}
				if (isAlreadyConfirm) {
					throw new TransactionParameterInvalidException(
							"parameter invalid, transaction already confirmed by address "
									+ encApi.hexEnc(oMultiTransaction.getTxBody().getExdata().toByteArray()));
				}
				if (!isExistsConfirmTx) {
					throw new TransactionParameterInvalidException(
							"parameter invalid, not found transaction need to be confirmed");
				}
				if (!FastByteComparisons.equal(originalTx.getTxBody().getInputs(0).getAmount().toByteArray(),
						oInput.getAmount().toByteArray())) {
					throw new TransactionParameterInvalidException(
							"parameter invalid, transaction amount not equal with original transaction");
				}

				if (oUnionAccountStorage != null) {
					if (oUnionAccountStorage.getAddressCount() >= unionAccountValue.getAcceptLimit()) {
						throw new TransactionParameterInvalidException(
								"parameter invalid, transaction already confirmed");
					}
				}

				if (oUnionAccountStorage.getAddressCount() + 1 == unionAccountValue.getAcceptLimit()) {
					if (DateTimeUtil.isToday(unionAccountValue.getAccumulatedTimestamp())) {
						BigInteger totalMax = ByteUtil
								.bytesToBigInteger(unionAccountValue.getAccumulated().toByteArray());
						if (amount.add(totalMax).compareTo(max) > 0) {
							throw new TransactionParameterInvalidException(
									"parameter invalid, already more than the maximum transfer amount of the day");
						}
					}
				}
			} else {
				BigInteger totalMax = ByteUtil.bytesToBigInteger(unionAccountValue.getAccumulated().toByteArray());
				if (amount.add(totalMax).compareTo(max) > 0) {
					throw new TransactionParameterInvalidException(
							"parameter invalid, already more than the maximum transfer amount of the day");
				}
			}
		} else {
			BigInteger totalMax = ByteUtil.bytesToBigInteger(unionAccountValue.getAccumulated().toByteArray());
			if (amount.add(totalMax).compareTo(max) > 0) {
				throw new TransactionParameterInvalidException(
						"parameter invalid, already more than the maximum transfer amount of the day");
			}
		}
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Builder> accounts) throws Exception {

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder unionAccount = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder unionAccountValue = unionAccount.getValue().toBuilder();

		BigInteger amount = ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray());
		BigInteger acceptMax = ByteUtil.bytesToBigInteger(unionAccountValue.getAcceptMax().toByteArray());

		if (amount.compareTo(acceptMax) >= 0) {
			if (oMultiTransaction.getTxBody().getData() == null || oMultiTransaction.getTxBody().getData().isEmpty()) {
				unionAccountValue.setNonce(unionAccountValue.getNonce() + 1);
				unionAccount.setValue(unionAccountValue);

				UnionAccountStorage.Builder oUnionAccountStorage = UnionAccountStorage.newBuilder();
				oUnionAccountStorage.addAddress(oMultiTransaction.getTxBody().getExdata());
				oUnionAccountStorage.addTxHash(oMultiTransaction.getTxHash());

				oAccountHelper.putStorage(unionAccount, encApi.hexDec(oMultiTransaction.getTxHash()),
						oUnionAccountStorage.build().toByteArray());
				accounts.put(encApi.hexEnc(oInput.getAddress().toByteArray()), unionAccount);
				return ByteString.EMPTY;
			} else {
				byte[] confirmTxBytes = oAccountHelper.getStorage(unionAccount,
						oMultiTransaction.getTxBody().getData().toByteArray());
				UnionAccountStorage.Builder oUnionAccountStorage = UnionAccountStorage.parseFrom(confirmTxBytes)
						.toBuilder();
				oUnionAccountStorage.addAddress(oMultiTransaction.getTxBody().getExdata());
				oUnionAccountStorage.addTxHash(oMultiTransaction.getTxHash());

				oAccountHelper.putStorage(unionAccount, oMultiTransaction.getTxBody().getData().toByteArray(),
						oUnionAccountStorage.build().toByteArray());

				if (oUnionAccountStorage.getAddressCount() != unionAccountValue.getAcceptLimit()) {
					unionAccountValue.setNonce(unionAccountValue.getNonce() + 1);
					unionAccount.setValue(unionAccountValue);
					accounts.put(encApi.hexEnc(oInput.getAddress().toByteArray()), unionAccount);
					return ByteString.EMPTY;
				}
			}
		}
		if (DateTimeUtil.isToday(unionAccountValue.getAccumulatedTimestamp())) {
			BigInteger accumulated = ByteUtil.bytesToBigInteger(unionAccountValue.getAccumulated().toByteArray());
			unionAccountValue.setAccumulated(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(accumulated.add(amount))));
		} else {
			unionAccountValue.setAccumulated(ByteString.copyFrom(ByteUtil.bigIntegerToBytes(amount)));
		}
		unionAccountValue.setAccumulatedTimestamp(oMultiTransaction.getTxBody().getTimestamp());
		unionAccount.setValue(unionAccountValue);
		accounts.put(encApi.hexEnc(unionAccount.getAddress().toByteArray()), unionAccount);
		return super.onExecute(oMultiTransaction, accounts);
	}

	@Override
	public void onVerifySignature(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
		AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

		MultiTransaction.Builder signatureTx = oMultiTransaction.toBuilder();
		MultiTransactionBody.Builder txBody = signatureTx.getTxBodyBuilder();
		signatureTx.clearTxHash();
		txBody = txBody.clearSignatures();
		byte[] oMultiTransactionEncode = txBody.build().toByteArray();
		MultiTransactionSignature oMultiTransactionSignature = oMultiTransaction.getTxBody().getSignatures(0);
		String hexPubKey = encApi.hexEnc(encApi.ecToKeyBytes(oMultiTransactionEncode,
				encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray())));
		String hexAddress = encApi.hexEnc(encApi.ecToAddress(oMultiTransactionEncode,
				encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray())));

		boolean isRelAddress = false;
		for (ByteString relAddress : senderAccountValue.getAddressList()) {
			if (hexAddress.equals(encApi.hexEnc(relAddress.toByteArray()))) {
				isRelAddress = true;
				break;
			}
		}
		if (isRelAddress) {
			if (!encApi.ecVerify(hexPubKey, oMultiTransactionEncode,
					oMultiTransactionSignature.getSignature().toByteArray())) {
				throw new TransactionVerifyException(String.format("signature %s verify fail with pubkey %s",
						encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray()), hexPubKey));
			}
		} else {
			throw new TransactionVerifyException(
					"signature verify fail, current account are not allowed to initiate transactions");
		}

		if (!encApi.hexEnc(oMultiTransaction.getTxBody().getExdata().toByteArray()).equals(hexAddress)) {
			throw new TransactionVerifyException("signature verify fail, transaction data not equal with Signer");
		}
	}
}
