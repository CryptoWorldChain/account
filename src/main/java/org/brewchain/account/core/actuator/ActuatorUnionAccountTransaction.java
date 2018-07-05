package org.brewchain.account.core.actuator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.brewchain.account.core.AbstractLocalCache;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
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
		// 校验每日最大转账金额
		// 校验每笔最大转账金额
		// 校验超过单笔转账金额后的用户签名
		if (accounts.size() != 1) {
			throw new Exception(String.format("not allow multi sender address %s", accounts.size()));
		}

		AccountValue.Builder oSenderValue = accounts.get(accounts.keySet().toArray()[0]).getValue().toBuilder();

		long totalAmount = 0;
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			totalAmount += oInput.getAmount();
			// totalAmount += oInput.getFee();
		}
		String key = String.format("%s_%s", new SimpleDateFormat("yyyy-MM-dd").format(new Date()),
				oMultiTransaction.getTxBody().getInputs(0).getAddress());
		log.debug(String.format("%s 累计 %s", key, AbstractLocalCache.dayTotalAmount.get(key)));

		long dayTotal = totalAmount + AbstractLocalCache.dayTotalAmount.get(key);
		long dayMax = oSenderValue.getMax();
		if (dayTotal > dayMax) {
			throw new Exception(String.format("day accumulated amount %s more than %s", dayTotal, dayMax));
		}
		// 当单笔金额超过一个预设值后，则需要多方签名
		if (totalAmount > oSenderValue.getAcceptMax()) {
			if (oMultiTransaction.getTxBody().getSignaturesCount() != oSenderValue.getAcceptMax()) {
				throw new Exception(String.format("must have %s signature when transaction value %s more than %s",
						totalAmount, oSenderValue.getAcceptMax(), oSenderValue.getAcceptLimit()));
			} else {
				// TODO 如何判断交易的签名，是由多重签名账户的关联账户进行签名的
			}
		} else {
			// 需要至少有一个子账户签名
			if (oMultiTransaction.getTxBody().getSignaturesCount() == 0) {
				throw new Exception(String.format("the transaction requires at least one signature to be verified"));
			}
		}

		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public void onExecuteDone(MultiTransaction oMultiTransaction, ByteString result) throws Exception {
		// 缓存，累加当天转账金额
		long totalAmount = 0;
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			totalAmount += oInput.getAmount();
			// totalAmount += oInput.getFee();
		}
		String key = String.format("%s_%s", new SimpleDateFormat("yyyy-MM-dd").format(new Date()),
				oMultiTransaction.getTxBody().getInputs(0).getAddress());
		long v = AbstractLocalCache.dayTotalAmount.get(key);
		AbstractLocalCache.dayTotalAmount.put(key, v + totalAmount);
		super.onExecuteDone(oMultiTransaction, result);
	}

	@Override
	public void onVerifySignature(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {

		// 签名的账户是否是该多重签名账户的子账户，如果不是，抛出异常
		for (MultiTransactionSignature oSignature : oMultiTransaction.getTxBody().getSignaturesList()) {
			// TODO 需要能解出签名地址的方法，验证每个签名地址都是多重签名账户的关联自账户
		}

		super.onVerifySignature(oMultiTransaction, accounts);
	}
}
