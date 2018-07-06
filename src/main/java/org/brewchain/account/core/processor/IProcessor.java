package org.brewchain.account.core.processor;

import java.util.LinkedList;
import java.util.Map;

import org.brewchain.account.gens.Blockimpl.AddBlockResponse;
import org.brewchain.evmapi.gens.Block.BlockEntity;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;

import com.google.protobuf.ByteString;

public interface IProcessor {
	void applyReward(BlockEntity oCurrentBlock) throws Exception;

	AddBlockResponse ApplyBlock(BlockEntity oBlockEntity);

	BlockEntity.Builder CreateNewBlock(LinkedList<MultiTransaction> txs, String extraData) throws Exception;

	Map<String, ByteString> ExecuteTransaction(LinkedList<MultiTransaction> oMultiTransactions,
			BlockEntity currentBlock) throws Exception;
}
