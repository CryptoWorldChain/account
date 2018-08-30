package org.brewchain.account.core.actuator;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.evmapi.EvmApiImp;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Act.Account.Builder;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.rcvm.exec.VM;
import org.brewchain.rcvm.exec.invoke.ProgramInvokeImpl;
import org.brewchain.rcvm.program.Program;
import org.brewchain.rcvm.program.ProgramResult;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCallContract extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCallContract(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper, BlockEntity oBlock,
			EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Builder> accounts) throws Exception {
		if (oMultiTransaction.getTxBody().getInputsCount() != 1
				|| oMultiTransaction.getTxBody().getOutputsCount() != 1) {
			throw new TransactionExecuteException("parameter invalid, the inputs and outputs must be only one");
		}

		MultiTransactionInput input = oMultiTransaction.getTxBody().getInputs(0);
		if (StringUtils.isNotBlank(input.getToken())) {
			throw new TransactionExecuteException("parameter invalid, token must be null");
		}

		if (StringUtils.isNotBlank(input.getSymbol())
				|| (input.getCryptoToken() != null && !input.getCryptoToken().equals(ByteString.EMPTY))) {
			throw new TransactionExecuteException("parameter invalid, crypto token must be null");
		}

		MultiTransactionOutput output = oMultiTransaction.getTxBody().getOutputs(0);

		if (!oAccountHelper.isContract(output.getAddress())) {
			throw new TransactionExecuteException("parameter invalid, address "
					+ encApi.hexEnc(output.getAddress().toByteArray()) + " is not validate contract.");
		}

		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Builder> accounts) throws Exception {
		VM vm = new VM();
		Account.Builder existsContract = accounts
				.get(encApi.hexEnc(oMultiTransaction.getTxBody().getOutputs(0).getAddress().toByteArray()));
		Account.Builder callAccount = accounts
				.get(encApi.hexEnc(oMultiTransaction.getTxBody().getInputs(0).getAddress().toByteArray()));

		AccountValue.Builder senderAccountValue = callAccount.getValue().toBuilder();
		senderAccountValue.setNonce(senderAccountValue.getNonce() + 1);
		callAccount.setValue(senderAccountValue);
		accounts.put(encApi.hexEnc(callAccount.getAddress().toByteArray()), callAccount);

		// BlockEntity.Builder oBlock = oBlockHelper.GetBestBlock();

		EvmApiImp evmApiImp = new EvmApiImp();
		evmApiImp.setAccountHelper(oAccountHelper);
		evmApiImp.setTransactionHelper(oTransactionHelper);
		evmApiImp.setEncApi(this.encApi);

		MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);
		ProgramInvokeImpl programInvoke = new ProgramInvokeImpl(existsContract.getAddress().toByteArray(),
				callAccount.getAddress().toByteArray(), callAccount.getAddress().toByteArray(),
				oInput.getAmount().toByteArray(), ByteUtil.bigIntegerToBytes(BigInteger.ZERO),
				oMultiTransaction.getTxBody().getData().toByteArray(),
				encApi.hexDec(oBlock.getHeader().getParentHash()), // encApi.hexDec(oBlock.getHeader().getParentHash()),
				null, // encApi.hexDec(oBlock.getMiner().getAddress()),
				Long.parseLong(String.valueOf(oBlock.getHeader().getTimestamp())),
				Long.parseLong(String.valueOf(oBlock.getHeader().getNumber())), ByteString.EMPTY.toByteArray(),
				evmApiImp);

		Program program = new Program(existsContract.getValue().getCodeHash().toByteArray(),
				existsContract.getValue().getCode().toByteArray(), programInvoke, oMultiTransaction);
		vm.play(program);
		ProgramResult result = program.getResult();

		if (result.getException() != null || result.isRevert()) {
			if (result.getException() != null) {
				throw result.getException();
			} else {
				throw new TransactionExecuteException("REVERT opcode executed");
			}
		} else {
			Iterator iter = evmApiImp.getTouchAccount().entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String, Account> entry = (Entry<String, Account>) iter.next();
				if (entry.getKey().equals(encApi.hexEnc(callAccount.getAddress().toByteArray()))) {
					Account.Builder touchAccount = ((Account) entry.getValue()).toBuilder();
					touchAccount
							.setValue(touchAccount.getValueBuilder().setNonce(touchAccount.getValue().getNonce() + 1));
					accounts.put(encApi.hexEnc(callAccount.getAddress().toByteArray()), touchAccount);
				} else {
					accounts.put(entry.getKey().toString(), ((Account) entry.getValue()).toBuilder());
				}
			}
			return ByteString.copyFrom(result.getHReturn());
		}
	}
}
