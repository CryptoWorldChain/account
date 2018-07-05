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
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.rcvm.exec.VM;
import org.brewchain.rcvm.exec.invoke.ProgramInvokeImpl;
import org.brewchain.rcvm.program.Program;
import org.brewchain.rcvm.program.ProgramResult;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

public class ActuatorCreateContract extends AbstractTransactionActuator implements iTransactionActuator {

	public ActuatorCreateContract(AccountHelper oAccountHelper, TransactionHelper oTransactionHelper,
			BlockEntity oBlock, EncAPI encApi, DefDaos dao, StateTrie oStateTrie) {
		super(oAccountHelper, oTransactionHelper, oBlock, encApi, dao, oStateTrie);
	}

	@Override
	public void onPrepareExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		if (accounts.size() != 1) {
			throw new Exception("exists multi sender address");
		}
		super.onPrepareExecute(oMultiTransaction, accounts);
	}

	@Override
	public ByteString onExecute(MultiTransaction oMultiTransaction, Map<String, Account.Builder> accounts)
			throws Exception {
		ByteString newContractAddress = oTransactionHelper.getContractAddressByTransaction(oMultiTransaction);
		if (oAccountHelper.isExist(newContractAddress)) {
			throw new Exception("contract address already exists");
		} else {
			accounts.put(encApi.hexEnc(newContractAddress.toByteArray()),
					oAccountHelper.CreateAccount(newContractAddress).toBuilder());

			EvmApiImp evmApiImp = new EvmApiImp();
			evmApiImp.setAccountHelper(oAccountHelper);
			evmApiImp.setTransactionHelper(oTransactionHelper);
			evmApiImp.setEncApi(this.encApi);

			MultiTransactionInput oInput = oMultiTransaction.getTxBody().getInputs(0);

			ProgramInvokeImpl createProgramInvoke = new ProgramInvokeImpl(newContractAddress.toByteArray(),
					oInput.getAddress().toByteArray(), oInput.getAddress().toByteArray(),
					ByteUtil.bigIntegerToBytes(BigInteger.valueOf(oInput.getAmount())),
					ByteUtil.bigIntegerToBytes(BigInteger.ZERO), oMultiTransaction.getTxBody().getData().toByteArray(),
					encApi.hexDec(oBlock.getHeader().getParentHash()), encApi.hexDec(oBlock.getMiner().getAddress()),
					Long.parseLong(String.valueOf(oBlock.getHeader().getTimestamp())),
					Long.parseLong(String.valueOf(oBlock.getHeader().getNumber())), ByteString.EMPTY.toByteArray(),
					evmApiImp);

			Program createProgram = new Program(oMultiTransaction.getTxBody().getData().toByteArray(),
					createProgramInvoke, oMultiTransaction);
			VM createVM = new VM();
			createVM.play(createProgram);
			ProgramResult createResult = ProgramResult.createEmpty();
			if (createResult.getException() != null) {
				throw createResult.getException();
			} else {
				createResult = createProgram.getResult();

				Account.Builder oCreateAccount = accounts.get(encApi.hexEnc(oInput.getAddress().toByteArray()));
				AccountValue.Builder oValue = oCreateAccount.getValueBuilder();
				oValue.setNonce(oValue.getNonce() + 1);
				oCreateAccount.setValue(oValue.build());
				accounts.put(encApi.hexEnc(oCreateAccount.getAddress().toByteArray()), oCreateAccount);

				Account.Builder contract = accounts.get(encApi.hexEnc(newContractAddress.toByteArray()));
				AccountValue.Builder oContractValue = contract.getValueBuilder();
				oContractValue.setCode(ByteString.copyFrom(createResult.getHReturn()));
				oContractValue
						.setCodeHash(ByteString.copyFrom(encApi.sha256Encode(oContractValue.getCode().toByteArray())));
				oContractValue.setCreator(oCreateAccount.getAddress());
				oContractValue.setData(oMultiTransaction.getTxBody().getExdata());
				contract.setValue(oContractValue);

				accounts.put(encApi.hexEnc(contract.getAddress().toByteArray()), contract);

			}

			return ByteString.EMPTY;
		}
	}
}
