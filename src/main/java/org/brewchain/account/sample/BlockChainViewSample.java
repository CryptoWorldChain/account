package org.brewchain.account.sample;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.core.BlockChainHelper;
import org.brewchain.account.core.BlockHelper;
import org.brewchain.account.core.TransactionHelper;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.gens.TxTest.MsgBlockView;
import org.brewchain.account.gens.TxTest.PTSTCommand;
import org.brewchain.account.gens.TxTest.PTSTModule;
import org.brewchain.account.gens.TxTest.ReqCommonTest;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.codehaus.jackson.map.ObjectMapper;
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
public class BlockChainViewSample extends SessionModules<ReqCommonTest> {
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
		return new String[] { PTSTCommand.BCV.name() };
	}

	@Override
	public String getModule() {
		return PTSTModule.TST.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqCommonTest pb, final CompleteHandler handler) {
		MsgBlockView.Builder oMsgBlockView = MsgBlockView.newBuilder();

		try {
			// for (Iterator<Map.Entry<String, BlockStoreNodeValue>> it =
			// unStableStore.getStorage().entrySet().iterator(); it.hasNext();) {
			// Map.Entry<String, BlockStoreNodeValue> item = it.next();
			//
			// BlockView oChild = new BlockView();
			// DecimalFormat df = new DecimalFormat("0000");
			//
			// oChild.setHash(be.getHeader().getBlockHash());
			// oChild.setName(String.format("%s ~ %s...%s",
			// df.format(be.getHeader().getNumber()),
			// be.getHeader().getBlockHash().substring(0, 5),
			// be.getHeader().getBlockHash().substring(
			// be.getHeader().getBlockHash().length() - 5,
			// be.getHeader().getBlockHash().length())));
			// oChild.setNumber(be.getHeader().getNumber());
			// }

			long maxNumber = blockStore.getMaxReceiveNumber();
			long fixNumber = 0;
			if (maxNumber - Integer.valueOf(pb.getArg1()) < 1) {
				fixNumber = Integer.valueOf(pb.getArg1()) - maxNumber;
			}
			BlockView root = new BlockView();
			root.setName("Start");
			root.setHash(" null ");
			root.setNumber(0);
			BlockView currentNode = null;
			for (int i = 0; i < Integer.valueOf(pb.getArg1()); i++) {
				long number = maxNumber + fixNumber - Integer.valueOf(pb.getArg1()) + i;

				if (number < 0 || number > maxNumber) {
					break;
				}
				List<BlockEntity> be = blockStore.getUnStableStore().getBlocksByNumber(number);
				if (be.size() == 0) {
					be.add(blockStore.getStableStore().getBlockByNumber(number));
				}
				for (BlockEntity blockEntity : be) {
					BlockView oChild = new BlockView();
					DecimalFormat df = new DecimalFormat("0000");

					oChild.setHash(blockEntity.getHeader().getBlockHash());
					oChild.setName(String.format("%s ~ %s...%s", df.format(blockEntity.getHeader().getNumber()),
							blockEntity.getHeader().getBlockHash().substring(0, 5),
							blockEntity.getHeader().getBlockHash().substring(
									blockEntity.getHeader().getBlockHash().length() - 5,
									blockEntity.getHeader().getBlockHash().length())));
					oChild.setNumber(blockEntity.getHeader().getNumber());
					if (currentNode == null) {
						currentNode = root;
					}

					BlockView p = getNode(blockEntity.getHeader().getParentHash(), root);
					if (p == null) {
						root.children.add(oChild);
					} else {
						p.children.add(oChild);
					}
				}
			}

			ObjectMapper mapper = new ObjectMapper();
			try {
				oMsgBlockView.setJsonStr(mapper.writeValueAsString(root));
			} catch (Exception e) {
				log.error("format error");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oMsgBlockView.build()));
		return;
	}

	private BlockView getNode(String hash, BlockView node) {
		BlockView findNode = null;
		if (node == null) {
			return null;
		}
		if (node.hash.equals(hash)) {
			return node;
		} else {
			for (int i = 0; i < node.children.size(); i++) {
				findNode = getNode(hash, node.children.get(i));
				if (findNode != null) {
					return findNode;
				}
			}
		}
		return findNode;
	}

	private class BlockView {
		private String name;
		private String hash;
		private long number;
		private List<BlockView> children = new ArrayList<>();

		public String getHash() {
			return hash;
		}

		public void setHash(String hash) {
			this.hash = hash;
		}

		public long getNumber() {
			return number;
		}

		public void setNumber(long number) {
			this.number = number;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<BlockView> getChildren() {
			return children;
		}

		public void setChildren(List<BlockView> children) {
			this.children = children;
		}
	}
}
