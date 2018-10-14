package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;
import onight.tfw.outils.conf.PropHelper;

public class SliceAccoutDomain<T extends Integer> extends ODBDao {

	public SliceAccoutDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}

	@Override
	public String getDomainName() {
		return "account.." + new PropHelper(null).get("org.brewchain.account.slicecount", 8);
	}

}
