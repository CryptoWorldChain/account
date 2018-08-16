package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;
import onight.tfw.outils.conf.PropHelper;

public class SliceBlockDomain extends ODBDao {
	public SliceBlockDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}

	@Override
	public String getDomainName() {
		return "block.number."+new PropHelper(null).get("org.brewchain.block.slicecount", 8);
//		return "block.number";
	}
}
