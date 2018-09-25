package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.exception.TransactionParameterInvalidException;
import org.brewchain.account.exception.TransactionVerifyException;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.fc.brewchain.bcapi.EncAPI;
import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTransactionActuator implements iTransactionActuator {

	protected Map<String, MultiTransaction> txValues = new HashMap<>();

	@Override
	public Map<String, MultiTransaction> getTxValues() {
		return txValues;
	}

	@Override
	public void onVerifySignature(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		List<String> inputAddresses = new ArrayList<>();
		for (int i = 0; i < oMultiTransaction.getTxBody().getInputsCount(); i++) {
			String inputAddress = encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(i).getAddress().toByteArray());
			if (!inputAddresses.contains(inputAddress)) {
				inputAddresses.add(inputAddress);
			}
		}

		// 获取交易原始encode
		MultiTransaction.Builder signatureTx = oMultiTransaction.toBuilder();
		MultiTransactionBody.Builder txBody = signatureTx.getTxBodyBuilder();
		signatureTx.clearTxHash();

		txBody = txBody.clearSignatures();
		byte[] oMultiTransactionEncode = txBody.build().toByteArray();
		// 校验交易签名
		for (MultiTransactionSignature oMultiTransactionSignature : oMultiTransaction.getTxBody().getSignaturesList()) {
			String hexPubKey = encApi.hexEnc(encApi.ecToKeyBytes(oMultiTransactionEncode,
					encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray())));
			String hexAddress = encApi.hexEnc(encApi.ecToAddress(oMultiTransactionEncode,
					encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray())));

			if (inputAddresses.remove(hexAddress)) {
				if (!encApi.ecVerify(hexPubKey, oMultiTransactionEncode,
						oMultiTransactionSignature.getSignature().toByteArray())) {
					throw new TransactionVerifyException(String.format("signature %s verify fail with pubkey %s",
							encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray()), hexPubKey));
				}
			} else {
				throw new TransactionVerifyException(
						String.format("signature cannot find sign address %s", hexAddress));
			}
		}

		if (inputAddresses.size() > 0) {
			throw new TransactionVerifyException(
					String.format("signature cannot match address %s", inputAddresses.get(0)));
		}
	}

	protected AccountHelper oAccountHelper;
	protected TransactionHelper oTransactionHelper;
	protected BlockEntity oBlock;
	protected EncAPI encApi;
	protected DefDaos dao;
	protected StateTrie oStateTrie;

	public AbstractTransactionActuator(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity currentBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		this.oAccountHelper = oAccountHelper;
		this.oTransactionHelper = oTransactionHelper;
		this.oBlock = currentBlock;
		this.encApi = encApi;
		this.dao = dao;
		this.oStateTrie = oStateTrie;
	}

	public void reset(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper, BlockEntity currentBlock,
			EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		this.oAccountHelper = oAccountHelper;
		this.oTransactionHelper = oTransactionHelper;
		this.oBlock = currentBlock;
		this.encApi = encApi;
		this.dao = dao;
		this.oStateTrie = oStateTrie;
		this.txValues.clear();
	}

	@Override
	public boolean needSignature() {
		return true;
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		BigInteger inputsTotal = BigInteger.ZERO;
		BigInteger outputsTotal = BigInteger.ZERO;
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			BigInteger bi = ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray());
			if (bi.compareTo(BigInteger.ZERO) < 0) {
				throw new TransactionParameterInvalidException("parameter invalid, amount must large than 0");
			}
			inputsTotal = inputsTotal.add(bi);

			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			BigInteger balance = ByteUtil.bytesToBigInteger(senderAccountValue.getBalance().toByteArray());

			if (balance.compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionParameterInvalidException(
						String.format("parameter invalid, sender %s balance %s less than 0",
								encApi.hexEnc(sender.getAddress().toByteArray()), balance));
			}
			if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionParameterInvalidException(
						String.format("parameter invalid, transaction value %s less than 0",
								ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())));
			}

			if (ByteUtil.bytesToBigInteger(balance.toByteArray()).compareTo(inputsTotal) == -1) {
				throw new TransactionParameterInvalidException(
						String.format("parameter invalid, sender balance %s less than %s", balance,
								ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray())));
			}

			int nonce = senderAccountValue.getNonce();
			if (nonce != oInput.getNonce()) {
				throw new TransactionParameterInvalidException(
						String.format("parameter invalid, sender nonce %s is not equal with transaction nonce %s",
								nonce, oInput.getNonce()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			BigInteger bi = ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray());
			if (bi.compareTo(BigInteger.ZERO) < 0) {
				throw new TransactionParameterInvalidException("parameter invalid, amount must large than 0");
			}
			outputsTotal = outputsTotal.add(bi);

			BigInteger balance = ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray());
			if (balance.compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionParameterInvalidException(
						String.format("parameter invalid, receive balance %s less than 0", balance));
			}
		}

		if (inputsTotal.compareTo(outputsTotal) != 0) {
			throw new TransactionParameterInvalidException(String
					.format("parameter invalid, transaction value %s not equal with %s", inputsTotal, outputsTotal));
		}
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			senderAccountValue.setBalance(ByteString.copyFrom(ByteUtil
					.bytesSubToBytes(senderAccountValue.getBalance().toByteArray(), oInput.getAmount().toByteArray())));

			senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
			sender.setValue(senderAccountValue);
			accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			Account.Builder receiver = accounts.get(encApi.hexEnc(oOutput.getAddress().toByteArray()));
			if (receiver == null) {
				receiver = oAccountHelper.CreateAccount(oOutput.getAddress());
			}
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();
			receiverAccountValue.setBalance(ByteString.copyFrom(ByteUtil.bytesAddToBytes(
					receiverAccountValue.getBalance().toByteArray(), oOutput.getAmount().toByteArray())));

			receiver.setValue(receiverAccountValue);
			accounts.put(encApi.hexEnc(receiver.getAddress().toByteArray()), receiver);
		}

		return ByteString.EMPTY;
	}

	@Override
	public void onExecuteDone(MultiTransaction oMultiTransaction, BlockEntity be, ByteString result) throws Exception {
		oTransactionHelper.setTransactionDone(oMultiTransaction, be, result);
	}

	@Override
	public void onExecuteError(MultiTransaction oMultiTransaction, BlockEntity be, ByteString result) throws Exception {
		oTransactionHelper.setTransactionError(oMultiTransaction, be, result);
	}
}
