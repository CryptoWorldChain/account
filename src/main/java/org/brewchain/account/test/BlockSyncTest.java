package org.brewchain.account.test;

import java.util.Date;

import org.bouncycastle.util.encoders.Hex;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.KeyConstant;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Block.BlockBody;
import org.brewchain.account.gens.Block.BlockEntity;
import org.brewchain.account.gens.Block.BlockHeader;
import org.brewchain.account.gens.Block.BlockMiner;
import org.brewchain.account.gens.Tx.MultiTransaction;
import org.brewchain.account.gens.Tx.MultiTransactionBody;
import org.brewchain.account.gens.Tx.MultiTransactionInput;
import org.brewchain.account.gens.Tx.MultiTransactionOutput;
import org.brewchain.account.gens.Tx.MultiTransactionSignature;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqTxTest;
import org.brewchain.account.gens.TxTest.RespTxTest;
import org.brewchain.account.trie.CacheTrie;
import org.brewchain.account.trie.StateTrie;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.OValue;
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
public class BlockSyncTest extends SessionModules<ReqTxTest> implements ActorService {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "Block_StateTrie", scope = "global")
	StateTrie stateTrie;
	
	@Override
	public String[] getCmds() {
		return new String[] { PTSTCommand.SYC.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqTxTest pb, final CompleteHandler handler) {
		RespTxTest.Builder oRespTxTest = RespTxTest.newBuilder();
		try {
			BlockEntity blockInit1 = makeBlock();
			BlockEntity blockInit2 = makeBlock(blockInit1.toBuilder());
			BlockEntity blockInit3 = makeBlock(blockInit2.toBuilder());
			BlockEntity blockInit4 = makeBlock(blockInit3.toBuilder());
			BlockEntity blockInit5 = makeBlock(blockInit4.toBuilder());

			sendBlock(blockInit5.toBuilder(), blockInit4);
			sendBlock(blockInit4.toBuilder(), blockInit3);
			sendBlock(blockInit3.toBuilder(), blockInit2);
			sendBlock(blockInit2.toBuilder(), blockInit1);
			sendBlock(blockInit1.toBuilder(), blockHelper.GetBestBlock().build());

		} catch (Exception e) {
			e.printStackTrace();
		}
		

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespTxTest.build()));
	}

	private BlockEntity makeBlock() throws Exception {
		BlockEntity.Builder oBlockEntity = blockHelper.GetBestBlock();
		return makeBlock(oBlockEntity);
	}

	private BlockEntity makeBlock(BlockEntity.Builder oBestBlockEntity) throws Exception {

		BlockEntity.Builder oBlockEntity = BlockEntity.newBuilder();
		BlockHeader.Builder oBlockHeader = BlockHeader.newBuilder();
		BlockBody.Builder oBlockBody = BlockBody.newBuilder();
		BlockMiner.Builder oBlockMiner = BlockMiner.newBuilder();

		// 获取本节点的最后一块Block
		BlockHeader.Builder oBestBlockHeader = oBestBlockEntity.getHeader().toBuilder();

		// 构造Block Header
		// oBlockHeader.setCoinbase(ByteString.copyFrom(coinBase));
		oBlockHeader.setParentHash(oBestBlockHeader.getBlockHash());

		// 确保时间戳不重复
		long currentTimestamp = new Date().getTime();
		oBlockHeader.setTimestamp(
				new Date().getTime() == oBestBlockHeader.getTimestamp() ? oBestBlockHeader.getTimestamp() + 1
						: currentTimestamp);
		oBlockHeader.setNumber(oBestBlockHeader.getNumber() + 1);
		oBlockHeader.setReward(ByteString.copyFrom(ByteUtil.intToBytes(KeyConstant.BLOCK_REWARD)));
		oBlockHeader.setExtraData(ByteString.EMPTY);
		oBlockHeader.setNonce(ByteString.copyFrom(ByteUtil.EMPTY_BYTE_ARRAY));
		// 构造MPT Trie
		CacheTrie oTrieImpl = new CacheTrie();

		oBlockMiner.setAddress(KeyConstant.node.getoAccount().getAddress());
		oBlockMiner.setNode(KeyConstant.node.getNode());
		oBlockMiner.setBcuid(KeyConstant.node.getBcuid());
		oBlockMiner.setReward(KeyConstant.BLOCK_REWARD);
		// oBlockMiner.setAddress(value);

		oBlockHeader.setTxTrieRoot(ByteString.copyFrom(oTrieImpl.getRootHash()));
		oBlockHeader.setBlockHash(ByteString.copyFrom(encApi.sha256Encode(oBlockHeader.build().toByteArray())));
		oBlockEntity.setHeader(oBlockHeader);
		oBlockEntity.setBody(oBlockBody);
		oBlockEntity.setMiner(oBlockMiner);

		return oBlockEntity.build();
	}

	private void sendBlock(BlockEntity.Builder oBlockEntity, BlockEntity oBestBlock) {
		try {
			BlockHeader oBestBlockHeader = oBestBlock.getHeader();
			this.stateTrie.setRoot(oBestBlockHeader.getStateRoot().toByteArray());
			blockHelper.ApplyBlock(oBlockEntity);// ApplyBlock(oBlockEntity);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
