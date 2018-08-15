package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;

public class SliceAccoutDomain<T extends Integer> extends ODBDao {

	public SliceAccoutDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}

	@Override
	public String getDomainName() {
		return "account" ;
	}

}
