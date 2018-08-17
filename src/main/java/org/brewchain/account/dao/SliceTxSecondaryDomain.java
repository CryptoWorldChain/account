package org.brewchain.account.dao;

import org.brewchain.bcapi.backend.ODBDao;

import onight.tfw.ojpa.api.ServiceSpec;
import onight.tfw.outils.conf.PropHelper;

public class SliceTxSecondaryDomain extends ODBDao {
	public SliceTxSecondaryDomain(ServiceSpec serviceSpec) {
		super(serviceSpec);
	}
	@Override
	public String getDomainName() {
		return "tx.sec."+new PropHelper(null).get("org.brewchain.txsec.slicecount", 16);
	}
}
