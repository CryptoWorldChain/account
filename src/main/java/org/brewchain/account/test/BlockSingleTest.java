package org.brewchain.account.test;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Block.BlockHeader;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.Tx.SingleTransaction;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
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
public class BlockSingleTest extends SessionModules<ReqTxTest> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.BST.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();
		String coinBase = this.props().get("block.coinBase", "");
		if (coinBase == null) {
			coinBase = "1234";
		}

		BlockEntity.Builder oSyncBlock = BlockEntity.newBuilder();
		BlockEntity.Builder newBlock;
		try {
			newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY, ByteUtil.EMPTY_BYTE_ARRAY);
			log.debug("创建区块 " + newBlock.toString());
			BlockHeader.Builder oBlockHeader = newBlock.getHeader().toBuilder();
			oBlockHeader.setNumber(pb.getBlockNumber());

			BlockEntity.Builder oSyncBlock1 = BlockEntity.newBuilder();
			newBlock = blockHelper.CreateNewBlock(600, ByteUtil.EMPTY_BYTE_ARRAY, ByteUtil.EMPTY_BYTE_ARRAY);
			BlockHeader.Builder oBlockHeader1 = newBlock.getHeader().toBuilder();
			oBlockHeader1.setNumber(pb.getBlockNumber() - 1);
			oBlockHeader.setParentHash(oBlockHeader1.getBlockHash());

			oSyncBlock.setHeader(oBlockHeader);
			oSyncBlock1.setHeader(oBlockHeader1);

			log.debug(" after " + blockHelper.ApplyBlock(oSyncBlock.build()));
			log.debug(" before " + blockHelper.ApplyBlock(oSyncBlock1.build()));

		} catch (Exception e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		log.debug("block已同步");

		oRespTxTest.setRetCode(-1);
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}
}
