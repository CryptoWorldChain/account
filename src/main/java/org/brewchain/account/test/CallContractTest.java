package org.brewchain.account.test;

import java.math.BigInteger;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.evmapi.EvmApiImp;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCreateContract;
import org.brewchain.account.gens.TxTest.RespTxTest;
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
import org.brewchain.rcvm.utils.HashUtil;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class CallContractTest extends SessionModules<ReqCreateContract> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.ECT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCreateContract pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();
		try {
			byte[] data = encApi.hexDec(
					"608060405234801561001057600080fd5b5061013f806100206000396000f300608060405260043610610041576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806355c2780f14610046575b600080fd5b34801561005257600080fd5b5061005b6100d6565b6040518080602001828103825283818151815260200191508051906020019080838360005b8381101561009b578082015181840152602081019050610080565b50505050905090810190601f1680156100c85780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b60606040805190810160405280600f81526020017f68656c6c6f20e682a8e5a5bdefbc8100000000000000000000000000000000008152509050905600a165627a7a72305820181f8bfbfa60590a1eebe87e774e0b01ab68dc9445b05d96efa66cdb0eeb6cdb0029");

			// 30073b02aa89956aadc1f4a5d03d42ee8c748a4ad5
			EvmApiImp evmApiImp = new EvmApiImp();
			evmApiImp.setAccountHelper(this.accountHelper);
			evmApiImp.setTransactionHelper(this.transactionHelper);
			evmApiImp.setEncApi(this.encApi);

			KeyPairs oKeyPairs = encApi.genKeys();
			log.debug("contract address::" + oKeyPairs.getAddress());
			Account contract = accountHelper.CreateAccount(encApi.hexDec(oKeyPairs.getAddress()), null);

			int nonce = accountHelper.getNonce(encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e"));
			MultiTransaction.Builder createContract = MultiTransaction.newBuilder();
			MultiTransactionBody.Builder createContractBody = MultiTransactionBody.newBuilder();
			MultiTransactionInput.Builder createContractInput = MultiTransactionInput.newBuilder();
			createContractInput.setNonce(nonce);
			createContractInput
					.setAddress(ByteString.copyFrom(encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e")));
			createContractBody.addInputs(createContractInput);
			createContract.setTxBody(createContractBody);

			ProgramInvokeImpl createProgramInvoke = new ProgramInvokeImpl(encApi.hexDec(oKeyPairs.getAddress()),
					encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e"),
					encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e"),
					ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10000)), ByteUtil.bigIntegerToBytes(BigInteger.ZERO),
					data, blockChainHelper.GetConnectBestBlock().getHeader().getParentHash().toByteArray(),
					encApi.hexDec(blockChainHelper.GetConnectBestBlock().getMiner().getAddress()),
					Long.parseLong(String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getTimestamp())),
					Long.parseLong(String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getNumber())),
					ByteString.EMPTY.toByteArray(), evmApiImp);

			// programInvokeFactory.createProgramInvoke(tx, currentBlock, cacheTrack,
			// blockStore);
			Program createProgram = new Program(data, createProgramInvoke, createContract.build());
			VM createVM = new VM();
			createVM.play(createProgram);
			ProgramResult createResult = ProgramResult.createEmpty();
			createResult = createProgram.getResult();
			accountHelper.saveCode(encApi.hexDec(oKeyPairs.getAddress()), createResult.getHReturn());
			log.debug(String.format("result.getHReturn():%s", createResult.getHReturn()));
			
			/////////////////////
			nonce = accountHelper.getNonce(encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e"));
			MultiTransaction.Builder callContract = MultiTransaction.newBuilder();
			MultiTransactionBody.Builder callContractBody = MultiTransactionBody.newBuilder();
			MultiTransactionInput.Builder callContractInput = MultiTransactionInput.newBuilder();
			callContractInput.setNonce(nonce);
			callContractInput
					.setAddress(ByteString.copyFrom(encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e")));
			callContractBody.addInputs(callContractInput);
			callContract.setTxBody(callContractBody);

			VM vm = new VM();
			Account existsContract = accountHelper.GetAccount(encApi.hexDec(oKeyPairs.getAddress()));
			ProgramInvokeImpl programInvoke = new ProgramInvokeImpl(
					encApi.hexDec(oKeyPairs.getAddress()),
					encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e"),
					encApi.hexDec("30ba538f6e50266c62537f3baffb8a3a28b01b499e"),
					ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10000)), ByteUtil.bigIntegerToBytes(BigInteger.ZERO),
					encApi.hexDec("55c2780f"),
					blockChainHelper.GetConnectBestBlock().getHeader().getParentHash().toByteArray(),
					encApi.hexDec(blockChainHelper.GetConnectBestBlock().getMiner().getAddress()),
					Long.parseLong(String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getTimestamp())),
					Long.parseLong(String.valueOf(blockChainHelper.GetConnectBestBlock().getHeader().getNumber())),
					ByteString.EMPTY.toByteArray(), evmApiImp);

			Program program = new Program(existsContract.getValue().getCodeHash().toByteArray(),
					existsContract.getValue().getCode().toByteArray(), programInvoke, callContract.build());
			vm.play(program);
			ProgramResult result = program.getResult();
			byte[] hreturn = result.getHReturn();
			log.debug(String.format("result.getHReturn():%s", hreturn));
			RuntimeException ex = result.getException();

			CallTransaction.Contract cc = new CallTransaction.Contract(
					"[{\"constant\":true,\"inputs\":[],\"name\":\"getCurrentTimes\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}]");
			Object[] ret = cc.getByName("getCurrentTimes").decodeResult(hreturn);
			log.info("Current contract data member value: " + ret[0]);
			// oRespTxTest.setRetCode(Integer.valueOf(ret[0].toString()));
			if (ex == null) {
				log.debug("call contract success");
			} else {
				log.debug("call contract error");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}
}
