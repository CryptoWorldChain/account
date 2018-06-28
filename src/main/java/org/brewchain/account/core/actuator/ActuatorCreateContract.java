package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.evmapi.EvmApiImp;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.rcvm.exec.VM;
import org.brewchain.rcvm.exec.invoke.ProgramInvokeImpl;
import org.brewchain.rcvm.program.Program;
import org.brewchain.rcvm.program.ProgramResult;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCreateContract extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateContract(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockHelper oBlockHelper, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlockHelper, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {
		if (accounts.size() != 1) {
			throw new Exception("不允许存在多个发送方地址");
		}
		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts) throws Exception {
		// 创建
		ByteString newContractAddress = oTransactionHelper.getContractAddressByTransaction(oMultiTransaction);
		if (oAccountHelper.isExist(newContractAddress)) {
			throw new Exception("contract address already exists");
		}
		
		EvmApiImp evmApiImp = new EvmApiImp();
		evmApiImp.setAccountHelper(oAccountHelper);
		evmApiImp.setTransactionHelper(oTransactionHelper);
		evmApiImp.setEncApi(this.encApi);
		
		ProgramInvokeImpl createProgramInvoke = new ProgramInvokeImpl(newContractAddress.toByteArray(),
				oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray(),
				oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray(),
				ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10000)), ByteUtil.bigIntegerToBytes(BigInteger.ZERO),
				oMultiTransaction.getTxBody().getData().toByteArray(),
				encApi.hexDec(oBlockHelper.GetBestBlock().getHeader().getParentHash()),
				encApi.hexDec(oBlockHelper.GetBestBlock().getMiner().getAddress()),
				Long.parseLong(String.valueOf(oBlockHelper.GetBestBlock().getHeader().getTimestamp())),
				Long.parseLong(String.valueOf(oBlockHelper.GetBestBlock().getHeader().getNumber())),
				ByteString.EMPTY.toByteArray(), evmApiImp);

		Program createProgram = new Program(oMultiTransaction.getTxBody().getData().toByteArray(), createProgramInvoke,
				oMultiTransaction);
		VM createVM = new VM();
		createVM.play(createProgram);
		ProgramResult createResult = ProgramResult.createEmpty();
		createResult = createProgram.getResult();

		oAccountHelper.IncreaseNonce(oMultiTransaction.getTxBody().getInputs(0).getAddress());
		oAccountHelper.saveCode(newContractAddress, ByteString.copyFrom(createResult.getHReturn()));

		// oAccountHelper.CreateContract(oTransactionHelper.getContractAddressByTransaction(oMultiTransaction),
		// ByteString.copyFrom(bytes)oMultiTransaction.getTxBody().getData(),
		// oMultiTransaction.getTxBody().getExdata());
		//
		// for (MultiTransactionInput oInput :
		// oMultiTransaction.getTxBody().getInputsList()) {
		// // 取发送方账户
		// Account sender = accounts.get(oInput.getAddress());
		// AccountValue.Builder senderAccountValue = sender.getValue().toBuilder();
		//
		// senderAccountValue.setBalance(senderAccountValue.getBalance() -
		// oInput.getAmount() - oInput.getFee());
		//
		// senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
		// DBTrie oCacheTrie = new DBTrie(this.dao);
		// if (senderAccountValue.getStorage() == null) {
		// oCacheTrie.setRoot(null);
		// } else {
		// oCacheTrie.setRoot(encApi.hexDec(senderAccountValue.getStorage()));
		// }
		// oCacheTrie.put(encApi.hexDec(sender.getAddress()),
		// senderAccountValue.build().toByteArray());
		// senderAccountValue.setStorage(encApi.hexEnc(oCacheTrie.getRootHash()));
		// this.accountValues.put(sender.getAddress(), senderAccountValue.build());
		// }
	}
}
