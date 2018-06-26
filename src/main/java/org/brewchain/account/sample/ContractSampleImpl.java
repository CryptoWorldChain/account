package org.brewchain.account.sample;

import java.math.BigInteger;

import org.brewchain.account.block.GetBlockByNumberImpl;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.evmapi.EvmApiImp;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqGetBlockByNumber;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqContract;
import org.brewchain.account.gens.TxTest.RespContract;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.rcvm.call.CallTransaction;
import org.brewchain.rcvm.exec.VM;
import org.brewchain.rcvm.exec.invoke.ProgramInvokeImpl;
import org.brewchain.rcvm.program.Program;
import org.brewchain.rcvm.program.ProgramResult;
import org.fc.brewchain.bcapi.EncAPI;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class ContractSampleImpl extends SessionModules<ReqContract> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.CSI.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqContract pb, final CompleteHandler handler) {
		RespContract.Builder oRespContract = RespContract.newBuilder();

		ByteString newContractAddress = ByteString.EMPTY;
		try {
			EvmApiImp evmApiImp = new EvmApiImp();
			evmApiImp.setAccountHelper(this.accountHelper);
			evmApiImp.setTransactionHelper(this.transactionHelper);
			evmApiImp.setEncApi(this.encApi);

			if (pb.getCreate() != null) {
				if (!accountHelper.isExist(ByteString.copyFrom(encApi.hexDec(pb.getCreate().getAddress())))) {
					oRespContract.setRetcode(-1);
					oRespContract.setRetmsg("account " + pb.getCreate().getAddress() + " not found");
					handler.onFinished(PacketHelper.toPBReturn(pack, oRespContract.build()));
					return;
				}
				oRespContract.addTrace("create account exists.");
				oRespContract.addTrace("create account nonce::"
						+ accountHelper.IncreaseNonce(ByteString.copyFrom(encApi.hexDec(pb.getCreate().getAddress()))));
				oRespContract.addTrace("create account balance::"
						+ accountHelper.getBalance(ByteString.copyFrom(encApi.hexDec(pb.getCreate().getAddress()))));

				int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(pb.getCreate().getAddress())));

				MultiTransaction.Builder createContract = MultiTransaction.newBuilder();
				MultiTransactionBody.Builder createContractBody = MultiTransactionBody.newBuilder();
				MultiTransactionInput.Builder createContractInput = MultiTransactionInput.newBuilder();
				createContractInput.setNonce(nonce);
				createContractInput.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getCreate().getAddress())));
				createContractBody.addInputs(createContractInput);
				createContract.setTxBody(createContractBody);

				newContractAddress = transactionHelper.getContractAddressByTransaction(createContract.build());
				oRespContract.addTrace("new contract address::" + newContractAddress);
				ProgramInvokeImpl createProgramInvoke = new ProgramInvokeImpl(newContractAddress.toByteArray(),
						encApi.hexDec(pb.getCreate().getAddress()), encApi.hexDec(pb.getCreate().getAddress()),
						ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10000)),
						ByteUtil.bigIntegerToBytes(BigInteger.ZERO), encApi.hexDec(pb.getCreate().getData()),
						encApi.hexDec(blockChainHelper.GetConnectBestBlock().getHeader().getParentHash()),
						encApi.hexDec(blockChainHelper.GetConnectBestBlock().getMiner().getAddress()),
						Long.parseLong(
								String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getTimestamp())),
						Long.parseLong(String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getNumber())),
						ByteString.EMPTY.toByteArray(), evmApiImp);

				Program createProgram = new Program(encApi.hexDec(pb.getCreate().getData()), createProgramInvoke,
						createContract.build());
				VM createVM = new VM();
				createVM.play(createProgram);
				ProgramResult createResult = ProgramResult.createEmpty();
				createResult = createProgram.getResult();

				oRespContract.addTrace("new contract code::" + encApi.hexEnc(createResult.getHReturn()));

				accountHelper.IncreaseNonce(ByteString.copyFrom(encApi.hexDec(pb.getCreate().getAddress())));
				accountHelper.saveCode(newContractAddress, ByteString.copyFrom(createResult.getHReturn()));
				handler.onFinished(PacketHelper.toPBReturn(pack, oRespContract.build()));
				return;
			}

			if (pb.getCall() != null) {
				ByteString callContractAddress;
				int nonce = accountHelper.getNonce(ByteString.copyFrom(encApi.hexDec(pb.getCall().getAddress())));
				MultiTransaction.Builder callContract = MultiTransaction.newBuilder();
				MultiTransactionBody.Builder callContractBody = MultiTransactionBody.newBuilder();
				MultiTransactionInput.Builder callContractInput = MultiTransactionInput.newBuilder();
				callContractInput.setNonce(nonce);
				if (pb.getCreate() != null) {
					oRespContract.addTrace("call new contract address::" + newContractAddress);
					callContractInput.setAddress(ByteString.copyFrom(encApi.hexDec(encApi.hexEnc(newContractAddress.toByteArray()))));
					callContractAddress = newContractAddress;
				} else {
					oRespContract.addTrace("call exists contract address::" + pb.getCall().getContract());
					callContractInput.setAddress(ByteString.copyFrom(encApi.hexDec(pb.getCall().getContract())));
					callContractAddress = ByteString.copyFrom(encApi.hexDec(pb.getCall().getContract()));
				}
				callContractBody.addInputs(callContractInput);
				callContract.setTxBody(callContractBody);

				VM vm = new VM();
				Account existsContract = accountHelper.GetAccount(callContractAddress);
				if (existsContract == null) {
					oRespContract.addTrace("call contract not exists");
					oRespContract.setRetcode(-1);
					oRespContract.setRetmsg("call contract not exists");
					handler.onFinished(PacketHelper.toPBReturn(pack, oRespContract.build()));
					return;
				}
				//
				ProgramInvokeImpl programInvoke = new ProgramInvokeImpl(callContractAddress.toByteArray(),
						encApi.hexDec(pb.getCall().getAddress()), encApi.hexDec(pb.getCall().getAddress()),
						ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10000)),
						ByteUtil.bigIntegerToBytes(BigInteger.ZERO), encApi.hexDec(pb.getCall().getData()),
						encApi.hexDec(blockChainHelper.GetConnectBestBlock().getHeader().getParentHash()),
						encApi.hexDec(blockChainHelper.GetConnectBestBlock().getMiner().getAddress()),
						Long.parseLong(
								String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getTimestamp())),
						Long.parseLong(String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getNumber())),
						ByteString.EMPTY.toByteArray(), evmApiImp);

				Program program = new Program(existsContract.getValue().getCodeHash().toByteArray(),
						existsContract.getValue().getCode().toByteArray(), programInvoke, callContract.build());
				vm.play(program);
				ProgramResult result = program.getResult();
				byte[] hreturn = result.getHReturn();
				RuntimeException ex = result.getException();

				oRespContract.addTrace("call contract hreturn::" + encApi.hexEnc(hreturn));

				oRespContract.addTrace("call contract exception::" + ex.getMessage());

				// new CallTransaction.Contract(arg0)
				CallTransaction.Contract cc = new CallTransaction.Contract(pb.getCall().getFunctionjson());
				Object[] ret = cc.getByName(pb.getCall().getFunction()).decodeResult(hreturn);
				if (ret == null || ret.length == 0) {
					oRespContract.addTrace("no data return");
				} else {
					for (int i = 0; i < ret.length; i++) {
						oRespContract.addTrace(i + " data return::" + ret[0]);
					}
				}
			}
		} catch (Exception e) {
			oRespContract.setRetcode(-1);
			oRespContract.setRetmsg(e.getMessage());
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespContract.build()));
		return;
	}
}
