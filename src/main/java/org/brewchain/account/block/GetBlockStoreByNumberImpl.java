package org.brewchain.account.block;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.CacheBlockHashMapDB;
import org.brewchain.account.core.WaitBlockHashMapDB;
import org.brewchain.account.core.WaitSendHashMapDB;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.core.store.BlockStoreNodeValue;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Blockimpl.BlockHeaderImpl;
import org.brewchain.account.gens.Blockimpl.BlockMinerImpl;
import org.brewchain.account.gens.Blockimpl.BlocksStore;
import org.brewchain.account.gens.Blockimpl.PBCTCommand;
import org.brewchain.account.gens.Blockimpl.PBCTModule;
import org.brewchain.account.gens.Blockimpl.ReqGetBlockByNumber;
import org.brewchain.account.gens.Blockimpl.RespGetBlock;
import org.brewchain.account.gens.Blockimpl.RespGetBlocksByNumber;
import org.brewchain.account.gens.Blockimpl.RespGetBlocksStoreByNumber;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.UnitUtil;

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
public class GetBlockStoreByNumberImpl extends SessionModules<ReqGetBlockByNumber> {
	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;
	@ActorRequire(name = "BlockStore_Helper", scope = "global")
	BlockStore blockStore;
	
	@Override
	public String[] getCmds() {
		return new String[] { PBCTCommand.GBT.name() };
	}

	@Override
	public String getModule() {
		return PBCTModule.BCT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetBlockByNumber pb, final CompleteHandler handler) {
		RespGetBlocksStoreByNumber.Builder oRespGetBlock = RespGetBlocksStoreByNumber.newBuilder();

		try {
			for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = blockStore.getUnStableStore().getStorage().column(pb.getNumber()).entrySet().iterator(); it
					.hasNext();) {
				Map.Entry<String, BlockStoreNodeValue> item = it.next();
				BlocksStore.Builder oBlocksStore = BlocksStore.newBuilder();
				oBlocksStore.setConnect(String.valueOf(item.getValue().isConnect()));
				oBlocksStore.setHash(item.getKey());
				oBlocksStore.setParentHash(item.getValue().getParentHash());
				oBlocksStore.setNumber(String.valueOf(item.getValue().getNumber()));
				oBlocksStore.setMiner(item.getValue().getBlockEntity().getMiner().getAddress());

				oRespGetBlock.addBlocks(oBlocksStore);
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetBlock.build()));
	}
}

// getBlocksByNumber