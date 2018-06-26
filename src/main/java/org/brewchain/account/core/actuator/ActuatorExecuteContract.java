package org.brewchain.account.core.actuator;

import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.trie.DBTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorExecuteContract extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorExecuteContract(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account> accounts) throws Exception {
		if (accounts.size() != 1) {
			throw new Exception("不允许存在多个发送方地址");
		}
		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<String, Account> accounts) throws Exception {
		
		for (MultiTransactionInput oInput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取发送方账户
			Account sender = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
			AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();

			senderAccountValue.setBalance(senderAccountValue.getBalance() - oInput.getAmount() - oInput.getFee());

			senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (senderAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(senderAccountValue.getStorage().toByteArray());
			}
			oCacheTrie.put(sender.getAddress().toByteArray(), senderAccountValue.build().toByteArray());
			senderAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));
			this.accountValues.put(encApi.hexEnc(sender.getAddress().toByteArray()), senderAccountValue.build());
//			keys.add(OEntityBuilder.byteKey2OKey(sender.getAddress().toByteArray()));
//			values.add(senderAccountValue.build());
		}
		
		for (MultiTransactionInput oOutput : oMultiTransaction.getTxBody().getInputsList()) {
			// 取接收方账户
			Account receiver = accounts.get(encApi.hexEnc(oOutput.getAddress().toByteArray()));
			AccountValue.Builder receiverAccountValue = receiver.getValue().toBuilder();

			receiverAccountValue.setData(receiver.getValue().getCode());
			
			receiverAccountValue.setBalance(receiverAccountValue.getBalance() - oOutput.getAmount() - oOutput.getFee());

			receiverAccountValue.setNonce(receiverAccountValue.getNonce() + 1);
			DBTrie oCacheTrie = new DBTrie(this.dao);
			if (receiverAccountValue.getStorage() == null) {
				oCacheTrie.setRoot(null);
			} else {
				oCacheTrie.setRoot(receiverAccountValue.getStorage().toByteArray());
			}
			
			
			oCacheTrie.put(receiver.getAddress().toByteArray(), receiverAccountValue.build().toByteArray());
			receiverAccountValue.setStorage(ByteString.copyFrom(oCacheTrie.getRootHash()));
			this.accountValues.put(encApi.hexEnc(receiver.getAddress().toByteArray()), receiverAccountValue.build());
//			keys.add(OEntityBuilder.byteKey2OKey(receiver.getAddress().toByteArray()));
//			values.add(receiverAccountValue.build());
		}
		
	}
}
