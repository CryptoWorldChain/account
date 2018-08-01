package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;

public class CryptoTokenDomain extends ODBDao{

	public CryptoTokenDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}

	@Override
	public String getDomainName() {
		return "cryptotoken.symbol";
	}

}
