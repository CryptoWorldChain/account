package org.brewchain.account.sys;

import java.util.Iterator;
import java.util.Map;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.WaitBlockHashMapDB;
import org.brewchain.account.core.WaitSendHashMapDB;
import org.brewchain.account.core.store.BlockStoreNodeValue;
import org.brewchain.account.gens.Sys.PSYSCommand;
import org.brewchain.account.gens.Sys.PSYSModule;
import org.brewchain.account.gens.Sys.ReqAddCryptoToken;
import org.brewchain.account.gens.Sys.ReqGetSummary;
import org.brewchain.account.gens.Sys.RespAddCryptoToken;
import org.brewchain.account.gens.Sys.RespGetSummary;
import org.brewchain.account.gens.Sys.UnStableItems;
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
public class GetSummaryImpl extends SessionModules<ReqGetSummary>  {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "BlockChain_Helper", scope = "global")
	BlockChainHelper blockChainHelper;

	@ActorRequire(name = "WaitSend_HashMapDB", scope = "global")
	WaitSendHashMapDB oSendingHashMapDB; // 保存待广播交易
	@ActorRequire(name = "WaitBlock_HashMapDB", scope = "global")
	WaitBlockHashMapDB oPendingHashMapDB; // 保存待打包block的交易

	
	@Override
	public String[] getCmds() {
		return new String[] { PSYSCommand.SUM.name() };
	}

	@Override
	public String getModule() {
		return PSYSModule.SYS.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetSummary pb, final CompleteHandler handler) {
		RespGetSummary.Builder oRespGetSummary = RespGetSummary.newBuilder();
		oRespGetSummary.setMaxConnection(String.valueOf(blockChainHelper.getBlockStore().getMaxConnectNumber()));
		oRespGetSummary.setMaxStable(String.valueOf(blockChainHelper.getBlockStore().getMaxStableNumber()));
		oRespGetSummary.setStable(String.valueOf(blockChainHelper.getBlockStore().getStableStore().getBlocks().size()));
		oRespGetSummary.setUnStable(String.valueOf(blockChainHelper.getBlockStore().getUnStableStore().getStorage().size()));
		oRespGetSummary.setWaitBlock(String.valueOf(oPendingHashMapDB.getStorage().size()));
		oRespGetSummary.setWaitSync(String.valueOf(oSendingHashMapDB.getStorage().size()));
		
		for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it = blockChainHelper.getBlockStore().getUnStableStore().getStorage().entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, BlockStoreNodeValue> item = it.next();
			UnStableItems.Builder oUnStableItems = UnStableItems.newBuilder();
			oUnStableItems.setNumber(String.valueOf(item.getValue().getNumber()));
			oUnStableItems.setHash(item.getValue().getBlockHash());
			oRespGetSummary.addItems(oUnStableItems.build());
		}
		
		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetSummary.build()));
	}
}
