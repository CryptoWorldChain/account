package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;
import onight.tfw.outils.conf.PropHelper;

public class SliceTxBlockDomain extends ODBDao{
	public SliceTxBlockDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}

	@Override
	public String getDomainName() {
//		return "txblock";
		return "txblock.."+new PropHelper(null).get("org.brewchain.txsec.slicecount", 16);

	}
}
