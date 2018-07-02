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
import org.brewchain.evmapi.gens.Act.Account.Builder;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.rcvm.exec.VM;
import org.brewchain.rcvm.exec.invoke.ProgramInvokeImpl;
import org.brewchain.rcvm.program.Program;
import org.brewchain.rcvm.program.ProgramResult;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCallContract extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCallContract(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Builder> accounts) throws Exception {
		// TODO Auto-generated method stub
		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public void onExecute(MultiTransaction oMultiTransaction, Map<String, Builder> accounts) throws Exception {
		VM vm = new VM();
		Account.Builder existsContract = accounts
				.get(encApi.hexEnc(oMultiTransaction.getTxBody().getOutputs(0).getAddress().toByteArray()));
		Account.Builder callAccount = accounts
				.get(encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray()));

		// BlockEntity.Builder oBlock = oBlockHelper.GetBestBlock();

		EvmApiImp evmApiImp = new EvmApiImp();
		evmApiImp.setAccountHelper(oAccountHelper);
		evmApiImp.setTransactionHelper(oTransactionHelper);
		evmApiImp.setEncApi(this.encApi);

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		ProgramInvokeImpl programInvoke = new ProgramInvokeImpl(existsContract.getAddress().toByteArray(),
				callAccount.getAddress().toByteArray(), callAccount.getAddress().toByteArray(),
				ByteUtil.bigIntegerToBytes(BigInteger.valueOf(oInput.getAmount())),
				ByteUtil.bigIntegerToBytes(BigInteger.ZERO), oMultiTransaction.getTxBody().getData().toByteArray(),
				null, //encApi.hexDec(oBlock.getHeader().getParentHash()), 
				null, //encApi.hexDec(oBlock.getMiner().getAddress()),
				Long.parseLong(String.valueOf(oBlock.getHeader().getTimestamp())),
				Long.parseLong(String.valueOf(oBlock.getHeader().getNumber())), ByteString.EMPTY.toByteArray(),
				evmApiImp);

		Program program = new Program(existsContract.getValue().getCodeHash().toByteArray(),
				existsContract.getValue().getCode().toByteArray(), programInvoke, oMultiTransaction);
		vm.play(program);
		ProgramResult result = program.getResult();

		if (result.getException() != null || result.isRevert()) {
			throw result.getException();
		} else {
			Account.Builder touchAccount = evmApiImp.GetAccount(callAccount.getAddress()).toBuilder();
			touchAccount.setValue(touchAccount.getValueBuilder().setNonce(touchAccount.getValue().getNonce() + 1));
			accounts.put(encApi.hexEnc(callAccount.getAddress().toByteArray()), touchAccount);
		}
	}
}
