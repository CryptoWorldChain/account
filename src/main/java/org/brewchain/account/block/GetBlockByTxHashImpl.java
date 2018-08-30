package org.brewchain.account.block;

import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.gens.Blockimpl.BlockHeaderImpl;
import org.brewchain.account.gens.Blockimpl.BlockMinerImpl;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqGetBlockByHash;
import org.brewchain.account.gens.Blockimpl.RespGetBlock;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;

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
public class GetBlockByTxHashImpl extends SessionModules<ReqGetBlockByHash> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;

	@Override
	public String[] getCmds() {
		return new String[] { PBCTCommand.GBA.name() };
	}

	@Override
	public String getModule() {
		return PBCTModule.BCT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetBlockByHash pb, final CompleteHandler handler) {
		RespGetBlock.Builder oRespGetBlock = RespGetBlock.newBuilder();

		try {

			BlockEntity oBlockEntity = blockHelper.getBlockByTransaction(encApi.hexDec(pb.getHash()));
			if (oBlockEntity == null) {
				oRespGetBlock.setRetCode(1);
				oRespGetBlock.setRetMsg("not find block");
				handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetBlock.build()));
				return;
			}
			BlockHeaderImpl.Builder oBlockHeaderImpl = BlockHeaderImpl.newBuilder();
			oBlockHeaderImpl.setBlockHash(oBlockEntity.getHeader().getBlockHash());
			oBlockHeaderImpl.setParentHash(oBlockEntity.getHeader().getParentHash());
			oBlockHeaderImpl.setNumber(oBlockEntity.getHeader().getNumber());
			oBlockHeaderImpl.setState(oBlockEntity.getHeader().getStateRoot());
			oBlockHeaderImpl.setReceipt(oBlockEntity.getHeader().getReceiptTrieRoot());
			oBlockHeaderImpl.setTxTrieRoot(oBlockEntity.getHeader().getTxTrieRoot());
			oBlockHeaderImpl.setTimestamp(oBlockEntity.getHeader().getTimestamp());
			oBlockHeaderImpl.setExtraData(oBlockEntity.getHeader().getExtraData());
			oBlockHeaderImpl.setSliceId(oBlockEntity.getHeader().getSliceId());
			for (String oTxhash : oBlockEntity.getHeader().getTxHashsList()) {
				oBlockHeaderImpl.addTxHashs(oTxhash);
			}

			BlockMinerImpl.Builder oBlockMinerImpl = BlockMinerImpl.newBuilder();
			oBlockMinerImpl.setBcuid(oBlockEntity.getMiner().getBcuid());
			oBlockMinerImpl.setAddress(oBlockEntity.getMiner().getAddress());
			oBlockMinerImpl.setNode(oBlockEntity.getMiner().getNode());
			oBlockMinerImpl.setReward(
					String.valueOf(ByteUtil.bytesToBigInteger(oBlockEntity.getMiner().getReward().toByteArray())));

			oRespGetBlock.setVersion(String.valueOf(oBlockEntity.getVersion()));
			oRespGetBlock.setHeader(oBlockHeaderImpl);
			oRespGetBlock.setMiner(oBlockMinerImpl);
			oRespGetBlock.setRetCode(1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		oRespGetBlock.setRetCode(1);

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetBlock.build()));
	}
}
