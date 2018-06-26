package org.brewchain.account.test;

import java.math.BigInteger;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.evmapi.EvmApiImp;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCommonTest;
import org.brewchain.account.gens.TxTest.RespCommonTest;
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
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class CallContractTest extends SessionModules<ReqCommonTest> implements ActorService {
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
	public void onPBPacket(final FramePacket pack, final ReqCommonTest pb, final CompleteHandler handler) {
		RespCommonTest.Builder oRespTxTest = RespCommonTest.newBuilder();
		try {
			String accountAddress = "502f600d0e813c51aafca905f73a44de748c4d65";
			String createAddress = "47dd7cf1ef6a0f7eb7500d5c73fd0730902b3a62";

			ByteString accountAddressBytes = ByteString.copyFrom(encApi.hexDec(accountAddress));
			ByteString createAddressByte = ByteString.copyFrom(encApi.hexDec(createAddress));
			byte[] data = encApi.hexDec(
					"608060405234801561001057600080fd5b5060405161023b38038061023b83398101806040528101908080518201929190505050336000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055508060019080519060200190610089929190610090565b5050610135565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f106100d157805160ff19168380011785556100ff565b828001600101855582156100ff579182015b828111156100fe5782518255916020019190600101906100e3565b5b50905061010c9190610110565b5090565b61013291905b8082111561012e576000816000905550600101610116565b5090565b90565b60f8806101436000396000f300608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063724a3e05146044575b600080fd5b6060600480360381019080803590602001909291905050506062565b005b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff166108fc829081150290604051600060405180830381858888f1935050505015801560c8573d6000803e3d6000fd5b50505600a165627a7a723058207c52912001e2dab5c01d1a87f7c2cd307010fdcfa4d40277f67d09a94054c15b0029000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000053232323232000000000000000000000000000000000000000000000000000000");

			// 30073b02aa89956aadc1f4a5d03d42ee8c748a4ad5
			EvmApiImp evmApiImp = new EvmApiImp();
			evmApiImp.setAccountHelper(this.accountHelper);
			evmApiImp.setTransactionHelper(this.transactionHelper);
			evmApiImp.setEncApi(this.encApi);

			accountHelper.IncreaseNonce(createAddressByte);
			int nonce = accountHelper.getNonce(createAddressByte);
			log.debug("nonce::" + nonce);
			MultiTransaction.Builder createContract = MultiTransaction.newBuilder();
			MultiTransactionBody.Builder createContractBody = MultiTransactionBody.newBuilder();
			MultiTransactionInput.Builder createContractInput = MultiTransactionInput.newBuilder();
			createContractInput.setNonce(nonce);
			createContractInput.setAddress(createAddressByte);
			createContractBody.addInputs(createContractInput);
			createContract.setTxBody(createContractBody);

			ByteString contractAddress = transactionHelper.getContractAddressByTransaction(createContract.build());
			log.debug("contract address::" + contractAddress);
			Account contract = accountHelper.CreateAccount(contractAddress);
			// accountHelper.addBalance(contractAddress, 88888);

			// ba538f6e50266c62537f3baffb8a3a28b01b499e
			// 30ba538f6e50266c62537f3baffb8a3a28b01b499e
			// 0000ba538f6e50266c62537f3baffb8a3a28b01b499e
			ProgramInvokeImpl createProgramInvoke = new ProgramInvokeImpl(contractAddress.toByteArray(),
					encApi.hexDec(createAddress), encApi.hexDec(createAddress),
					ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10000)), ByteUtil.bigIntegerToBytes(BigInteger.ZERO),
					data, encApi.hexDec(blockChainHelper.GetConnectBestBlock().getHeader().getParentHash()),
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
			accountHelper.saveCode(contractAddress, ByteString.copyFrom(createResult.getHReturn()));
			log.debug(String.format("result.getHReturn():%s", createResult.getHReturn()));

			accountHelper.IncreaseNonce(accountAddressBytes);

			/////////////////////
			nonce = accountHelper.getNonce(accountAddressBytes);
			MultiTransaction.Builder callContract = MultiTransaction.newBuilder();
			MultiTransactionBody.Builder callContractBody = MultiTransactionBody.newBuilder();
			MultiTransactionInput.Builder callContractInput = MultiTransactionInput.newBuilder();
			callContractInput.setNonce(nonce);
			callContractInput.setAddress(accountAddressBytes);
			callContractBody.addInputs(callContractInput);
			callContract.setTxBody(callContractBody);

			VM vm = new VM();
			Account existsContract = accountHelper.GetAccount(contractAddress);
			//
			ProgramInvokeImpl programInvoke = new ProgramInvokeImpl(contractAddress.toByteArray(),
					encApi.hexDec(accountAddress), encApi.hexDec(accountAddress),
					ByteUtil.bigIntegerToBytes(BigInteger.valueOf(10000)), ByteUtil.bigIntegerToBytes(BigInteger.ZERO),
					encApi.hexDec("724a3e05000000000000000000000000000000000000000000000000000000000000000a"),
					encApi.hexDec(blockChainHelper.GetConnectBestBlock().getHeader().getParentHash()),
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
					"[{\"constant\":false,\"inputs\":[{\"name\":\"pirce\",\"type\":\"uint256\"}],\"name\":\"testBid\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"},{\"inputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]");
			Object[] ret = cc.getByName("testBid").decodeResult(hreturn);
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
