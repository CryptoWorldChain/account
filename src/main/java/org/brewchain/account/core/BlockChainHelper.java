package org.brewchain.account.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.store.BlockStore;
import org.brewchain.account.core.store.BlockStore.BlockNotFoundInStoreException;
import org.brewchain.account.core.store.BlockStoreSummary;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.NodeDef;
import org.brewchain.account.util.NodeDef.NodeAccount;
import org.brewchain.account.util.OEntityBuilder;
import org.brewchain.bcapi.gens.Oentity.KeyStoreValue;
import org.brewchain.bcapi.gens.Oentity.OValue;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyStoreHelper;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Instantiate(name = "BlockChain_Helper")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class BlockChainHelper implements ActorService {
	// @ActorRequire(name = "Block_Cache_DLL", scope = "global")
	// DoubleLinkedList blockCache;
	//
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;
	@ActorRequire(name = "BlockChain_Config", scope = "global")
	BlockChainConfig blockChainConfig;
	// @ActorRequire(name = "BlockChainTempStore_HashMapDB", scope = "global")
	// BlockChainTempStore blockChainTempStore;
	// @ActorRequire(name = "BlockChainStore_LRUHashMapDB", scope = "global")
	// BlockChainStoreWithLRU blockChainStore;
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;
	@ActorRequire(name = "CacheBlock_HashMapDB", scope = "global")
	CacheBlockHashMapDB oCacheHashMapDB;
	@ActorRequire(name = "KeyStore_Helper", scope = "global")
	KeyStoreHelper keyStoreHelper;
	@ActorRequire(name = "BlockStore_Helper", scope = "global")
	BlockStore blockStore;

	/**
	 * 获取节点最后一个区块的Hash
	 * 
	 * @return
	 * @throws Exception
	 */
	public String GetStableBestBlockHash() throws Exception {
		BlockEntity block = this.blockStore.getMaxStableBlock();
		if (block == null) {
			return null;
		}
		return block.getHeader().getBlockHash();
		// return blockCache.last();
	}

	public BlockEntity GetStableBestBlock() throws Exception {
		BlockEntity block = this.blockStore.getMaxStableBlock();
		return block;
		// return blockCache.last();
	}

	public String GetConnectBestBlockHash() throws Exception {
		BlockEntity oBlock = blockStore.getMaxConnectBlock();
		if (oBlock != null) {
			return oBlock.getHeader().getBlockHash();
		}
		return null;
		// return blockCache.last();
	}

	public BlockEntity GetConnectBestBlock() throws Exception {
		return blockStore.getMaxConnectBlock();
		// return blockCache.last();
	}

	/**
	 * 获取节点区块个数
	 * 
	 * @return
	 */
	public long getBlockCount() {
		return blockStore.getMaxConnectNumber();
		// return blockCache.size();
	}

	/**
	 * 获取最后一个区块的索引
	 * 
	 * @return
	 * @throws Exception
	 */
	public long getLastStableBlockNumber() throws Exception {
		return blockStore.getMaxStableNumber();
		// return blockCache.getLast() == null ? 0 : blockCache.getLast().num;
	}

	public long getLastBlockNumber() {
		return blockStore.getMaxConnectNumber() == -1 ? blockStore.getMaxStableNumber()
				: blockStore.getMaxConnectNumber();
	}

	/**
	 * 获取创世块
	 * 
	 * @return
	 * @throws Exception
	 */
	public BlockEntity getGenesisBlock() throws Exception {
		BlockEntity oBlockEntity = blockStore.getBlockByNumber(0);
		if (oBlockEntity.getHeader().getNumber() == KeyConstant.GENESIS_NUMBER) {
			return oBlockEntity;
		}
		throw new Exception(String.format("genesis block not exists, current block number is %s", oBlockEntity.getHeader().getNumber()));
	}

	public boolean isExistsGenesisBlock() throws Exception {
		if (blockStore.getMaxConnectNumber() == -1) {
			return false;
		}
		BlockEntity oBlockEntity = blockStore.getBlockByNumber(0);
		if (oBlockEntity.getHeader().getNumber() == KeyConstant.GENESIS_NUMBER) {
			return true;
		}
		return false;
	}

	/**
	 * 向区块链中加入新的区块
	 * 
	 * @param oBlock
	 * @return
	 * @throws BlockNotFoundInStoreException
	 */
	public BlockStoreSummary connectBlock(BlockEntity oBlock) throws BlockNotFoundInStoreException {
		return blockStore.connectBlock(oBlock);
	}

	public BlockStoreSummary stableBlock(BlockEntity oBlock) {
		return blockStore.stableBlock(oBlock);
	}
	
	public BlockEntity getChildBlock(BlockEntity oBlock) {
		List<BlockEntity> list = blockStore.getReadyConnectBlock(oBlock.getHeader().getBlockHash());
		if (list.size() > 0) {
			return list.get(0);
		}
		return null;
	}

	public BlockEntity rollbackTo(BlockEntity block) {
		return blockStore.rollBackTo(block.getHeader().getNumber());
	}
	
	public BlockEntity rollbackTo(long number) {
		return blockStore.rollBackTo(number);
	}

	public BlockStoreSummary addBlock(BlockEntity oBlock) {
		return blockStore.addBlock(oBlock);
	}
	
	/**
	 * 从一个块开始遍历整个区块链，返回该块的所有子孙
	 * 
	 * @param blockHash
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getBlocks(byte[] blockHash) throws Exception {
		return getBlocks(blockHash, null);
	}

	/**
	 * 从一个块开始遍历整个区块链,到一个块结束，返回区间内的区块。默认返回200块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @return 200块
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getBlocks(byte[] blockHash, byte[] endBlockHash) throws Exception {
		return getBlocks(blockHash, endBlockHash, 200);
	}

	/**
	 * 从一个块开始遍历整个区块链,到一个块结束，返回区间指定数量的区块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @param maxCount
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getBlocks(byte[] blockHash, byte[] endBlockHash, int maxCount) throws Exception {
		LinkedList<BlockEntity> list = new LinkedList<BlockEntity>();
		list.addAll(
				blockStore.getChildListBlocksEndWith(encApi.hexEnc(blockHash), encApi.hexEnc(endBlockHash), maxCount));
		return list;
	}

	/**
	 * 从一个块开始，遍历整个整个区块，返回改块的所有父块
	 * 
	 * @param blockHash
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getParentsBlocks(String blockHash) throws Exception {
		return getParentsBlocks(blockHash, null);
	}

	/**
	 * 从一个块开始，到一个块结束，遍历整个整个区块，返回改块的所有父块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @return 200块
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getParentsBlocks(String blockHash, String endBlockHash) throws Exception {
		return getParentsBlocks(blockHash, endBlockHash, 200);
	}

	/**
	 * 从一个块开始，到一个块结束，遍历整个整个区块，返回指定数量的该块的所有父块
	 * 
	 * @param blockHash
	 * @param endBlockHash
	 * @param maxCount
	 * @return
	 * @throws Exception
	 */
	public LinkedList<BlockEntity> getParentsBlocks(String blockHash, String endBlockHash, long maxCount)
			throws Exception {
		LinkedList<BlockEntity> list = new LinkedList<BlockEntity>();
		list.addAll(blockStore.getParentListBlocksEndWith(blockHash, endBlockHash, maxCount));
		return list;
	}

	/**
	 * 根据区块高度，获取区块信息
	 * 
	 * @param number
	 * @return
	 * @throws Exception
	 */
	public BlockEntity getBlockByNumber(long number) throws Exception {
		// 判断从前遍历还是从后遍历
		BlockEntity oBlockEntity = blockStore.getBlockByNumber(number);
		if (oBlockEntity == null) {
			throw new Exception(String.format("cannot find the block with number %s", number));
		} else {
			return oBlockEntity;
		}
	}

	/**
	 * get node account info.
	 * 
	 * return null while not found.
	 * 
	 * @return
	 */
	public String getNodeAccount() {
		OValue oOValue;
		try {
			oOValue = dao.getAccountDao().get(oEntityHelper.byteKey2OKey("org.bc.manage.node.account".getBytes()))
					.get();
			if (oOValue == null || oOValue.getExtdata() == null || oOValue.getExtdata().equals(ByteString.EMPTY)) {
				// get net config

				// for dev
				if (blockChainConfig.isDev()) {
					FileReader fr = null;
					BufferedReader br = null;
					try {
						// read file
						log.debug("keystore" + File.separator + blockChainConfig.getNet() + File.separator + "keystore"
								+ blockChainConfig.getKeystoreNumber() + ".json");

						fr = new FileReader("keystore" + File.separator + blockChainConfig.getNet() + File.separator
								+ "keystore" + blockChainConfig.getKeystoreNumber() + ".json");
						br = new BufferedReader(fr);
						String keyStoreJsonStr = "";

						String line = br.readLine();
						while (line != null) {
							keyStoreJsonStr += line.trim().replace("\r", "").replace("\t", "");
							line = br.readLine();
						}
						br.close();
						fr.close();

						KeyStoreValue oKeyStoreValue = keyStoreHelper.getKeyStore(keyStoreJsonStr,
								blockChainConfig.getPwd());
						if (oKeyStoreValue == null) {
							return null;
						} else {
							dao.getAccountDao().put(
									oEntityHelper.byteKey2OKey("org.bc.manage.node.account".getBytes()),
									oEntityHelper.byteValue2OValue(encApi.hexDec(oKeyStoreValue.getAddress())));
							return oKeyStoreValue.getAddress();
						}
					} catch (Exception e) {
						if (br != null) {
							br.close();
						}
						if (fr != null) {
							fr.close();
						}
						throw e;
					}
				} else {
					return null;
				}
			} else {
				return encApi.hexEnc(oOValue.getExtdata().toByteArray());
			}
		} catch (Exception e) {
			log.error("fail to read node account from db::" + e.getMessage());
		}
		return null;
	}

	public void onStart(String bcuid, String address, String name) {
		try {
			NodeDef oNodeDef = new NodeDef();

			oNodeDef.setBcuid(bcuid);
			oNodeDef.setAddress(address);
			oNodeDef.setNode(name);

			if (StringUtils.isNotBlank(bcuid)) {
				NodeAccount oNodeAccount = oNodeDef.new NodeAccount();
				String coinAddressHex = getNodeAccount();
				if (coinAddressHex == null) {
					throw new Exception("node account not found");
				}
				byte[] coinAddress = encApi.hexDec(coinAddressHex);
				if (coinAddress == null) {
					throw new Exception("node account not found");
				}
				log.info("start account with address::" + coinAddressHex);
				oNodeAccount.setAddress(ByteString.copyFrom(coinAddress));
				oNodeDef.setoAccount(oNodeAccount);
			}

			KeyConstant.node = oNodeDef;
			reloadBlockCache();
			// reloadBlockCacheByNumber();
			log.debug("block load complete");
		} catch (Exception e) {
			log.error("error on start node account::", e);
		}
	}

	public void reloadBlockCache() throws Exception {
		blockStore.init();
		KeyConstant.isStart = true;
	}

	public BlockEntity getBlockByHash(String blockHash) throws Exception {
		return blockStore.getBlockByHash(blockHash);
	}
}
