package org.brewchain.account.core.store;

import lombok.Data;

@Data
public class BlockStoreSummary {
	private BLOCK_BEHAVIOR behavior;

	public enum BLOCK_BEHAVIOR {
		DROP, EXISTS_DROP, EXISTS_PREV, CACHE, APPLY, APPLY_CHILD, STORE, DONE
	}
}
