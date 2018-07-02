package org.brewchain.account.account;

import org.brewchain.account.core.AccountHelper;
import org.brewchain.account.gens.Actimpl.AccountCryptoTokenImpl;
import org.brewchain.account.gens.Actimpl.AccountCryptoValueImpl;
import org.brewchain.account.gens.Actimpl.AccountTokenValueImpl;
import org.brewchain.account.gens.Actimpl.AccountValueImpl;
import org.brewchain.account.gens.Actimpl.PACTCommand;
import org.brewchain.account.gens.Actimpl.PACTModule;
import org.brewchain.account.gens.Actimpl.ReqGetAccount;
import org.brewchain.account.gens.Actimpl.RespGetAccount;
import org.brewchain.account.util.ByteUtil;
import org.brewchain.evmapi.gens.Act.Account;
import org.brewchain.evmapi.gens.Act.AccountCryptoToken;
import org.brewchain.evmapi.gens.Act.AccountCryptoValue;
import org.brewchain.evmapi.gens.Act.AccountTokenValue;
import org.brewchain.evmapi.gens.Act.AccountValue;
import org.fc.brewchain.bcapi.EncAPI;
import org.fc.brewchain.bcapi.KeyPairs;

import com.google.protobuf.ByteString;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.oapi.scala.commons.SessionModules;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.async.CompleteHandler;
import onight.tfw.ntrans.api.annotation.ActorRequire;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;

@NActorProvider
@Slf4j
@Data
public class GetAccountImpl extends SessionModules<ReqGetAccount> {
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper oAccountHelper;
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	@Override
	public String[] getCmds() {
		return new String[] { PACTCommand.GAC.name() };
	}

	@Override
	public String getModule() {
		return PACTModule.ACT.name();
	}

	@Override
	public void onPBPacket(final FramePacket pack, final ReqGetAccount pb, final CompleteHandler handler) {
		RespGetAccount.Builder oRespGetAccount = RespGetAccount.newBuilder();
		KeyPairs oKeyPairs1 = encApi.genKeys("aaaa");
		KeyPairs oKeyPairs2 = encApi.genKeys("aaaa");

		log.debug("oKeyPairs1::" + oKeyPairs1.getAddress() + " oKeyPairs2::" + oKeyPairs2.getAddress());

		try {
			for (String address : pb.getAddressList()) {
				Account oAccount = oAccountHelper
						.GetAccount(ByteString.copyFrom(encApi.hexDec(ByteUtil.formatHexAddress(address))));
				AccountValueImpl.Builder oAccountValueImpl = AccountValueImpl.newBuilder();
				oAccountValueImpl.setAccountAddress(address);
				if (oAccount != null) {
					AccountValue oAccountValue = oAccount.getValue();

					oAccountValueImpl.setAcceptLimit(oAccountValue.getAcceptLimit());
					oAccountValueImpl.setAcceptMax(oAccountValue.getAcceptMax());
					for (ByteString relAddress : oAccountValue.getAddressList()) {
						oAccountValueImpl.addAddress(encApi.hexEnc(relAddress.toByteArray()));
					}

					oAccountValueImpl.setBalance(oAccountValue.getBalance());
					// oAccountValueImpl.setCryptos(index, value)
					for (AccountCryptoValue oAccountTokenValue : oAccountValue.getCryptosList()) {
						AccountCryptoValueImpl.Builder oAccountCryptoValueImpl = AccountCryptoValueImpl.newBuilder();
						oAccountCryptoValueImpl.setSymbol(oAccountTokenValue.getSymbol());

						for (AccountCryptoToken oAccountCryptoToken : oAccountTokenValue.getTokensList()) {
							AccountCryptoTokenImpl.Builder oAccountCryptoTokenImpl = AccountCryptoTokenImpl
									.newBuilder();
							oAccountCryptoTokenImpl.setCode(encApi.hexEnc(oAccountCryptoToken.getCode().toByteArray()));
							oAccountCryptoTokenImpl.setHash(encApi.hexEnc(oAccountCryptoToken.getHash().toByteArray()));
							oAccountCryptoTokenImpl.setIndex(oAccountCryptoToken.getIndex());
							oAccountCryptoTokenImpl.setName(encApi.hexEnc(oAccountCryptoToken.getName().toByteArray()));
							oAccountCryptoTokenImpl.setNonce(oAccountCryptoToken.getNonce());
							oAccountCryptoTokenImpl
									.setOwner(encApi.hexEnc(oAccountCryptoToken.getOwner().toByteArray()));
							oAccountCryptoTokenImpl.setOwnertime(oAccountCryptoToken.getOwnertime());
							oAccountCryptoTokenImpl.setTimestamp(oAccountCryptoToken.getTimestamp());
							oAccountCryptoTokenImpl.setTotal(oAccountCryptoToken.getTotal());

							oAccountCryptoValueImpl.addTokens(oAccountCryptoTokenImpl);
						}
						oAccountValueImpl.addCryptos(oAccountCryptoValueImpl);
					}
					oAccountValueImpl.setMax(oAccountValue.getMax());
					oAccountValueImpl.setNonce(oAccountValue.getNonce());
					for (AccountTokenValue oAccountTokenValue : oAccountValue.getTokensList()) {
						AccountTokenValueImpl.Builder oAccountTokenValueImpl = AccountTokenValueImpl.newBuilder();
						oAccountTokenValueImpl.setBalance(oAccountTokenValue.getBalance());
						oAccountTokenValueImpl.setToken(oAccountTokenValue.getToken());
						oAccountTokenValueImpl.setLocked(oAccountTokenValue.getLocked());
						oAccountValueImpl.addTokens(oAccountTokenValueImpl);
					}
					oAccountValueImpl.setStorage(encApi.hexEnc(oAccountValue.getStorage().toByteArray()));
					oAccountValueImpl.setCode(encApi.hexEnc(oAccountValue.getCode().toByteArray()));
					oAccountValueImpl.setCodeHash(encApi.hexEnc(oAccountValue.getCodeHash().toByteArray()));
				}
				oRespGetAccount.addAddress(address);
				oRespGetAccount.addAccount(oAccountValueImpl);
			}
			oRespGetAccount.setRetCode(1);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			log.error("GetAccountImpl error", e);
			oRespGetAccount.clear();
			oRespGetAccount.setRetCode(-1);
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetAccount.build()));
	}
}
