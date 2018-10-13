package org.brewchain.account.core.mis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.codec.binary.Hex;
import org.brewchain.account.sample.TransactionLoadTestExecImpl;
import org.brewchain.evmapi.gens.Tx.MultiTransaction;
import org.brewchain.evmapi.gens.Tx.MultiTransactionInput;
import org.brewchain.evmapi.gens.Tx.MultiTransactionOutput;

import com.google.protobuf.ByteString;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutual Irrelevance Separator Filter
 * 
 * 互不相关性分离器
 * 
 * @author brew
 *
 */

@NoArgsConstructor
@AllArgsConstructor
@Data
public class MultiTransactionSeparator {

	int bucketSize = Runtime.getRuntime().availableProcessors();

	class RelationShip {
		Map<String, MultiTransaction> sequances = new HashMap<>();

		LinkedBlockingQueue<MultiTransaction> queue = new LinkedBlockingQueue<>();
	}

	public LinkedBlockingQueue<MultiTransaction> getTxnQueue(int index) {
		return buckets.get(index).queue;
	}

	public void reset() {
		if (buckets != null) {
			buckets.clear();
		} else {
			buckets = new ArrayList<>(bucketSize);
		}
		for (int i = 0; i < bucketSize; i++) {
			buckets.add(new RelationShip());
		}
	}

	List<RelationShip> buckets = new ArrayList<>();

	public String getBucketInfo() {
		StringBuffer sb = new StringBuffer("MIS.MTS,BucketSize=").append(buckets.size()).append(":[");
		for (int i = 0; i < bucketSize; i++) {
			RelationShip rs = buckets.get(i);
			if (i > 0) {
				sb.append(",");
			}
			sb.append(rs.sequances.size());
		}
		sb.append("]");
		return sb.toString();
	}

	public static int MAX_COMPARE_SIZE = 4;

	public String fastAddress(ByteString bstr) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < MAX_COMPARE_SIZE && i < bstr.size(); i++) {
			sb.append(bstr.byteAt(0));
		}
		return sb.toString();
	}

	public void doClearing(MultiTransaction[] oMultiTransactions,TransactionLoadTestExecImpl loadTester) {
		int offset = 0;
		for (MultiTransaction tx : oMultiTransactions) {
			int bucketIdx = -1;
			for (int i = 0; i < bucketSize && bucketIdx < 0; i++) {
				RelationShip rs = buckets.get(i);
				if (buckets.get(i).sequances.containsKey(tx.getTxHash())) {
					bucketIdx = i;
					break;
				}
				if (bucketIdx < 0) {
					for (MultiTransactionInput input : tx.getTxBody().getInputsList()) {
						if (rs.sequances.containsKey(fastAddress(input.getAddress()))) {
							bucketIdx = i;
							break;
						}
					}
				}
				if (bucketIdx < 0) {
					for (MultiTransactionOutput output : tx.getTxBody().getOutputsList()) {
						if (rs.sequances.containsKey(fastAddress(output.getAddress()))) {
							bucketIdx = i;
							break;
						}
					}
				}
			}
			if (bucketIdx < 0) {// not found
				bucketIdx = offset;
				offset++;
			}
			RelationShip rs = buckets.get((bucketIdx) % bucketSize);
			for (MultiTransactionOutput output : tx.getTxBody().getOutputsList()) {
				rs.sequances.put(fastAddress(output.getAddress()), tx);
			}
			for (MultiTransactionInput input : tx.getTxBody().getInputsList()) {
				rs.sequances.put(fastAddress(input.getAddress()), tx);
			}
			if(loadTester!=null){
				for (MultiTransactionInput input : tx.getTxBody().getInputsList()) {
					loadTester.offerNewAccount(input.getAddress(), input.getNonce());
				}
			}
			rs.queue.offer(tx);
		}
	}
}
