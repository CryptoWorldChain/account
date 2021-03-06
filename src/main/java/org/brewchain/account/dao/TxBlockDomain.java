package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;
import onight.tfw.outils.conf.PropHelper;

public class TxBlockDomain extends ODBDao{
	public TxBlockDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}

	@Override
	public String getDomainName() {
		return "txblock.."+new PropHelper(null).get("org.brewchain.txblock.slicecount", 16);
	}
}
