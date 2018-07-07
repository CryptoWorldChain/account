package org.brewchain.account.core.actuator;

import static java.lang.String.format;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.DBTrie;
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

public abstract class AbstractTransactionActuator implements iTransactionActuator {

	// protected Map<String, AccountValue> accountValues = new HashMap<>();
	protected Map<String, MultiTransaction> txValues = new HashMap<>();

	// protected LinkedList<OKey> keys = new LinkedList<>();
	// protected LinkedList<AccountValue> values = new LinkedList<>();
	// protected LinkedList<OKey> txKeys = new LinkedList<>();
	// protected LinkedList<MultiTransaction> txValues = new LinkedList<>();

	// @Override
	// public Map<String, AccountValue> getAccountValues() {
	// return accountValues;
	// }

	@Override
	public Map<String, MultiTransaction> getTxValues() {
		return txValues;
	}

	// @Override
	// public LinkedList<OKey> getKeys() {
	// return keys;
	// }
	//
	// @Override
	// public LinkedList<AccountValue> getValues() {
	// return values;
	// }

	// @Override
	// public LinkedList<OKey> getTxKeys() {
	// return txKeys;
	// }
	//
	// @Override
	// public LinkedList<MultiTransaction> getTxValues() {
	// return txValues;
	// }

	@Override
	public void onVerifySignature(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		// 获取交易原始encode
		MultiTransaction.Builder signatureTx = oMultiTransaction.toBuilder();
		MultiTransactionBody.Builder txBody = signatureTx.getTxBodyBuilder();
		signatureTx.clearTxHash();
		txBody = txBody.clearSignatures();
		byte[] oMultiTransactionEncode = txBody.build().toByteArray();
		// 校验交易签名
		int i = 0;
		for (MultiTransactionSignature oMultiTransactionSignature : oMultiTransaction.getTxBody().getSignaturesList()) {
			// byte[] address = encApi.ecToAddress(oMultiTransactionEncode,
			// encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray()));
			// if (!encApi.hexEnc(address)
			// .equals(encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(i).getAddress().toByteArray())))
			// {
			// throw new TransactionExecuteException(
			// String.format("signature address %s not equal with sender address %s ",
			// encApi.hexEnc(address),
			// encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(i).getAddress().toByteArray())));
			// }
			//
			// byte[] pubKey = encApi.ecToKeyBytes(oMultiTransactionEncode,
			// encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray()));
			if (!encApi.ecVerify(encApi.hexEnc(oMultiTransactionSignature.getPubKey().toByteArray()),
					oMultiTransactionEncode, oMultiTransactionSignature.getSignature().toByteArray())) {
				throw new TransactionExecuteException(String.format("signature %s verify fail with pubkey %s",
						encApi.hexEnc(oMultiTransactionSignature.getSignature().toByteArray()),
						encApi.hexEnc(oMultiTransactionSignature.getPubKey().toByteArray())));
			}

			i += 1;
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

	@Override
	public boolean needSignature() {
		return true;
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		BigInteger inputsTotal = BigInteger.ZERO;
		BigInteger outputsTotal = BigInteger.ZERO;

		if (oMultiTransaction.getTxBody().getInputsList().size() > 1
				&& oMultiTransaction.getTxBody().getOutputsList().size() > 1) {
			throw new TransactionExecuteException(
					String.format("some error in transaction parameters, sender %s，receiver %s",
							oMultiTransaction.getTxBody().getInputsList().size(),
							oMultiTransaction.getTxBody().getOutputsList().size()));
		}

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			inputsTotal = ByteUtil.bytesAdd(inputsTotal, oInput.getAmount().toByteArray());

			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			// 判断发送方余额是否足够
			BigInteger balance = ByteUtil.bytesToBigInteger(senderAccountValue.getBalance().toByteArray());

			if (balance.compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionExecuteException(String.format("sender %s balance %s less than 0",
						encApi.hexEnc(sender.getAddress().toByteArray()), balance));
			}
			if (ByteUtil.bytesToBigInteger(oInput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionExecuteException(
						String.format("transaction value %s less than 0", oInput.getAmount()));
			}

			if (ByteUtil.bytesSub(balance, oInput.getAmount().toByteArray()).compareTo(BigInteger.ZERO) == -1) {// -
																												// oInput.getFeeLimit()
				// 余额不够
				throw new TransactionExecuteException(
						String.format("sender balance %s less than %s", balance, oInput.getAmount()));// +
				// oInput.getFeeLimit()
			}

			// 判断nonce是否一致
			int nonce = senderAccountValue.getNonce();
			if (nonce != oInput.getNonce()) {
				throw new TransactionExecuteException(String
						.format("sender nonce %s is not equal with transaction nonce %s", nonce, oInput.getNonce()));
			}
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			outputsTotal = ByteUtil.bytesAdd(outputsTotal, oOutput.getAmount().toByteArray());

			BigInteger balance = ByteUtil.bytesToBigInteger(oOutput.getAmount().toByteArray());
			if (balance.compareTo(BigInteger.ZERO) == -1) {
				throw new TransactionExecuteException(String.format("receive balance %s less than 0", balance));
			}
		}

		if (inputsTotal.compareTo(outputsTotal) == -1) {
			throw new TransactionExecuteException(
					String.format("transaction value %s less than %s", inputsTotal, outputsTotal));
		}
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		// LinkedList<OKey> keys = new LinkedList<>();
		// LinkedList<AccountValue> values = new LinkedList<>();

		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取发送方账户
			Account.Builder sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			senderAccountValue.setBalance(ByteString.copyFrom(ByteUtil
					.bytesSubToBytes(senderAccountValue.getBalance().toByteArray(), oInput.getAmount().toByteArray())));

			senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (senderAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(senderAccountValue.getStorage().toByteArray());
			}
			oCacheTrie.put(sender.getAddress().toByteArray(), senderAccountValue.build().toByteArray());
			senderAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));
			sender.setValue(senderAccountValue);
			accounts.put(encApi.hexEnc(sender.getAddress().toByteArray()), sender);
		}

		for (MultiTransactionOutput oOutput : oMultiTransaction.getTxBody().getOutputsList()) {
			Account.Builder receiver = accounts.get(encApi.hexEnc(oOutput.getAddress().toByteArray()));
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();
			receiverAccountValue.setBalance(ByteString.copyFrom(ByteUtil.bytesAddToBytes(
					receiverAccountValue.getBalance().toByteArray(), oOutput.getAmount().toByteArray())));

			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (receiverAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(receiverAccountValue.getStorage().toByteArray());
			}
			oCacheTrie.put(receiver.getAddress().toByteArray(), receiverAccountValue.build().toByteArray());
			receiverAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));
			receiver.setValue(receiverAccountValue);
			accounts.put(encApi.hexEnc(receiver.getAddress().toByteArray()), receiver);
		}

		return ByteString.EMPTY;
	}

	@Override
	public void onExecuteDone(MultiTransaction oMultiTransaction, ByteString result) throws Exception {
		oTransactionHelper.setTransactionDone(oMultiTransaction.getTxHash(), result);
	}

	@Override
	public void onExecuteError(MultiTransaction oMultiTransaction, ByteString result) throws Exception {
		oTransactionHelper.setTransactionError(oMultiTransaction.getTxHash(), result);
	}

	public static class TransactionExecuteException extends Exception {
		public TransactionExecuteException(String message, Object... args) {
			super(format(message, args));
		}
	}
}
