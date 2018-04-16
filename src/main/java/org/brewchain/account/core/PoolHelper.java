package org.brewchain.account.core;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.gens.Tx.SingleTransaction;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.iPojoBean;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@iPojoBean
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "pool_Helper")
@Slf4j
@Data
public class PoolHelper implements ActorService {

	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;

	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	public boolean tx(SingleTransaction.Builder tx) throws Exception {
//		MultiTransaction.Builder oMultiTransaction = transactionHelper.ParseSingleTransactionToMultiTransaction(newTx);
		
		String addr = "";
		String pubKey = "";
		
		// TODO 判断 币种
//		if("cws".equals(tx.get)) {
			addr = Pool.CWS.address;
			pubKey = Pool.CWS.pubKey;
//		}else if("cwb".equals(tx.get)) {
			addr = Pool.CWB.address;
			pubKey = Pool.CWB.pubKey;
//		}
		
		// 判断 发送方或者接收方为黑洞地址
		if(addr.equals(tx.getSenderAddress().toString())) {
			// TODO 奖
//			dao.getTxsDao().put(arg0, arg1)
		} else if(addr.equals(tx.getReceiveAddress().toString())) {
			// TODO 惩
		}

		// 持久化：修改余额、记录到交易表
		
		return false;
	}
	
	public static void main(String[] args) {
		
	}
	
	public enum Pool {
		CWS("30bd084040fffb37ee976b5a7b0fbda16b65b11bda","04428a4ac2a4cf2f6eb4dc02eaa9c2f83343fc1e82e058721d30d4293fa09b2c9dfd5f404db746a91f516a761c934a4c134d81fd611f5c14d3f12064b3141f9da9")
		,CWB("30dac7d8bb2d4962de12a82876dd04bb8d695356a7","043f16d93f0bad94c314752ddc54a24c458605085bd7fe091a81a657999c09464736e7742dce6c65f539987212fd11c620e6b707ca458c8db173c36c17f477a2cf")
		;
		
		//d04da746ea388df77fe55ed7a165e5efc8c4192ef582de0bd7ae8b967fb70e96
		//47162ead71d501aed1a6949b364732e45ea3137e1cf8daec519fd06f05c29d89
		public String address;
		public String pubKey;
		
		private Pool(String addr,String pub) {
			this.address=addr;
			this.pubKey=pub;
		}
		
	}
	
}
