package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;

public class CommonDomain extends ODBDao{
	public CommonDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}

	@Override
	public String getDomainName() {
		return "comm";
	}
}
