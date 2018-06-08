package org.brewchain.account.core.store;

import lombok.Data;

@Data
public class BlockStoreSummary {
	private BLOCK_BEHAVIOR behavior;
	
	public enum BLOCK_BEHAVIOR {
		EXISTS_DROP, EXISTS_PREV, NOT_EXISTS_CACHE, NOT_EXISTS_APPLY
	}
}
