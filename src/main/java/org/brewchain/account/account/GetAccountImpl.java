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

		try {
			Account oAccount = oAccountHelper
					.GetAccount(ByteString.copyFrom(encApi.hexDec(ByteUtil.formatHexAddress(pb.getAddress()))));
			AccountValueImpl.Builder oAccountValueImpl = AccountValueImpl.newBuilder();
			oAccountValueImpl.setAccountAddress(pb.getAddress());
			if (oAccount != null) {
				AccountValue oAccountValue = oAccount.getValue();

				oAccountValueImpl.setAcceptLimit(oAccountValue.getAcceptLimit());
				oAccountValueImpl.setAcceptMax(String.valueOf(ByteUtil.bytesToBigInteger(oAccountValue.getAcceptMax().toByteArray())));
				for (ByteString relAddress : oAccountValue.getAddressList()) {
					oAccountValueImpl.addAddress(encApi.hexEnc(relAddress.toByteArray()));
				}

				oAccountValueImpl.setBalance(String.valueOf(ByteUtil.bytesToBigInteger(oAccountValue.getBalance().toByteArray())));
				// oAccountValueImpl.setCryptos(index, value)
				for (AccountCryptoValue oAccountTokenValue : oAccountValue.getCryptosList()) {
					AccountCryptoValueImpl.Builder oAccountCryptoValueImpl = AccountCryptoValueImpl.newBuilder();
					oAccountCryptoValueImpl.setSymbol(oAccountTokenValue.getSymbol());

					for (AccountCryptoToken oAccountCryptoToken : oAccountTokenValue.getTokensList()) {
						AccountCryptoTokenImpl.Builder oAccountCryptoTokenImpl = AccountCryptoTokenImpl.newBuilder();
						oAccountCryptoTokenImpl.setCode(encApi.hexEnc(oAccountCryptoToken.getCode().toByteArray()));
						oAccountCryptoTokenImpl.setHash(encApi.hexEnc(oAccountCryptoToken.getHash().toByteArray()));
						oAccountCryptoTokenImpl.setIndex(oAccountCryptoToken.getIndex());
						oAccountCryptoTokenImpl.setName(encApi.hexEnc(oAccountCryptoToken.getName().toByteArray()));
						oAccountCryptoTokenImpl.setNonce(oAccountCryptoToken.getNonce());
						oAccountCryptoTokenImpl.setOwner(encApi.hexEnc(oAccountCryptoToken.getOwner().toByteArray()));
						oAccountCryptoTokenImpl.setOwnertime(oAccountCryptoToken.getOwnertime());
						oAccountCryptoTokenImpl.setTimestamp(oAccountCryptoToken.getTimestamp());
						oAccountCryptoTokenImpl.setTotal(oAccountCryptoToken.getTotal());

						oAccountCryptoValueImpl.addTokens(oAccountCryptoTokenImpl);
					}
					oAccountValueImpl.addCryptos(oAccountCryptoValueImpl);
				}
				oAccountValueImpl.setMax(String.valueOf(ByteUtil.bytesToBigInteger(oAccountValue.getMax().toByteArray())));
				oAccountValueImpl.setNonce(oAccountValue.getNonce());
				for (AccountTokenValue oAccountTokenValue : oAccountValue.getTokensList()) {
					AccountTokenValueImpl.Builder oAccountTokenValueImpl = AccountTokenValueImpl.newBuilder();
					oAccountTokenValueImpl.setBalance(String.valueOf(ByteUtil.bytesToBigInteger(oAccountTokenValue.getBalance().toByteArray())));
					oAccountTokenValueImpl.setToken(oAccountTokenValue.getToken());
					oAccountTokenValueImpl.setLocked(String.valueOf(ByteUtil.bytesToBigInteger(oAccountTokenValue.getLocked().toByteArray())));
					oAccountValueImpl.addTokens(oAccountTokenValueImpl);
				}
				oAccountValueImpl.setStorage(encApi.hexEnc(oAccountValue.getStorage().toByteArray()));
				oAccountValueImpl.setCode(encApi.hexEnc(oAccountValue.getCode().toByteArray()));
				oAccountValueImpl.setCodeHash(encApi.hexEnc(oAccountValue.getCodeHash().toByteArray()));
			}
			oRespGetAccount.setAddress(pb.getAddress());
			oRespGetAccount.setAccount(oAccountValueImpl);
			oRespGetAccount.setRetCode(1);
		} catch (Exception e) {
			log.error("GetAccountImpl error", e);
			oRespGetAccount.clear();
			oRespGetAccount.setRetCode(-1);
		}

		handler.onFinished(PacketHelper.toPBReturn(pack, oRespGetAccount.build()));
	}
}
