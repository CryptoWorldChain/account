package org.brewchain.account.sample;

import java.math.BigInteger;

import org.apache.commons.lang3.StringUtils;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.enums.TransTypeEnum;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqUnionAccountTransaction;
import org.brewchain.account.gens.TxTest.ReqVoteStorage;
import org.brewchain.account.gens.TxTest.ReqVoteTransaction;
import org.brewchain.account.gens.TxTest.RespCreateUnionAccount;
import org.brewchain.account.gens.TxTest.RespVoteStorage;
import org.brewchain.account.gens.TxTest.RespVoteStorageItem;
import org.brewchain.account.gens.TxTest.RespVoteTransaction;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.SanctionStorage;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionBody;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionSignature;
import org.brewchain.evmapi.gens.Tx.SanctionData;
import org.brewchain.rcvm.utils.ByteUtil;
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
public class VoteStorageSample extends SessionModules<ReqVoteStorage> {
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
	// @ActorRequire(name = "BlockStore_UnStable", scope = "global")
	// BlockUnStableStore unStableStore;
	@ActorRequire(name = "BlockStore_Helper", scope = "global")
	BlockStore blockStore;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.VTT.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqVoteStorage pb, final CompleteHandler handler) {
		RespVoteStorage.Builder oRespVoteStorage = RespVoteStorage.newBuilder();

		try {
			Account.Builder oAccount = accountHelper.GetAccount(ByteString.copyFrom(encApi.hexDec(pb.getAddress())));

			byte[] v = accountHelper.getStorage(oAccount, encApi.hexDec(pb.getKey()));
			if (v != null) {
				SanctionStorage oSanctionStorage = SanctionStorage.parseFrom(v);
				for (int i = 0; i < oSanctionStorage.getAddressCount(); i++) {
					RespVoteStorageItem.Builder oItem = RespVoteStorageItem.newBuilder();
					String txHash = oSanctionStorage.getTxHash(i);
					MultiTransaction oMultiTransaction = transactionHelper.GetTransaction(txHash);

					oItem.setAddress(encApi.hexEnc(oSanctionStorage.getAddress(i).toByteArray()));

					BlockEntity oBlockEntity = blockHelper.getBlockByTransaction(encApi.hexDec(txHash));
					if (oBlockEntity != null) {
						oItem.setBlockHash(oBlockEntity.getHeader().getBlockHash());
						oItem.setBlockHeight(oBlockEntity.getHeader().getNumber());
					}
					oItem.setTxHash(txHash);
					oItem.setTimestamp(oMultiTransaction.getTxBody().getTimestamp());
					oItem.setCost(ByteUtil
							.bytesToBigInteger(oMultiTransaction.getTxBody().getInputs(0).getAmount().toByteArray())
							.toString());

					SanctionData oSanctionData = SanctionData.parseFrom(oMultiTransaction.getTxBody().getData());
					oItem.setVoteContent(oSanctionData.getContent().toStringUtf8());
					oItem.setVoteResult(oSanctionData.getResult().toStringUtf8());
					oItem.setEndBlockHeight(oSanctionData.getEndBlockHeight());
					oRespVoteStorage.setVoteTxHash(oSanctionStorage.getVoteTxHash());
					oRespVoteStorage.addItems(oItem);
				}
				oRespVoteStorage.setRetMsg(oSanctionStorage.toString());

			} else {
				oRespVoteStorage.setRetMsg("");
			}
		} catch (Exception e) {
			e.printStackTrace();
			oRespVoteStorage.setRetCode(-1);
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespVoteStorage.build()));
		return;
	}
}
