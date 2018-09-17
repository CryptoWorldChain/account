package org.brewchain.account.ept;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.dao.DefDaos;
import org.brewchain.account.util.OEntityBuilder;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@AllArgsConstructor
@NoArgsConstructor
@Data
@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Block_BrewTrie")
public class EMTree implements ActorService {
	@ActorRequire(name = "Def_Daos", scope = "global")
	DefDaos dao;
	@ActorRequire(name = "OEntity_Helper", scope = "global")
	OEntityBuilder oEntityHelper;

	ETNode root;

	public ETNode delete(ETNode node, String key) {
		return root;
	}

	public byte[] getRootHash() throws Exception {
		return root.encode();
	}

	public void setRootHash(byte[] hash) {
		
	}

	public ETNode insert(ETNode node, String key, byte[] value, int deep) {
		char ch = key.charAt(deep);
		ETNode child = node.getChild(ch);
		if (child == null) {
			ETNode oETNode = new ETNode(key, value);
			node.appendChildNode(oETNode, ch);
		} else {
			child.setDirty(true);
			insert(child, key, value, deep + 1);
		}
		return node;
	}

	public byte[] get(byte[] key) {
		if (root == null) {
			return null;
		} else {
			return get(EHelper.bytesToMapping(key), 0, root);
		}
	}

	public byte[] get(String key, int deep, ETNode lastNode) {
		char ch = key.charAt(deep);
		ETNode oETNode = lastNode.getChild(ch);
		if (oETNode == null) {
			return lastNode.getContentData();
		} else if (oETNode.getKey().equals(key)) {
			return oETNode.getV();
		} else {
			return get(key, deep + 1, oETNode);
		}
	}

	public void put(byte[] key, byte[] value) {
		putM(EHelper.bytesToMapping(key), value);
	}

	public void put(String hexEnc, byte[] value) {
		putM(EHelper.bytesToMapping(hexEnc), value);
	}

	public void putM(String mapKey, byte[] value) {
		if (root == null) {
			root = new ETNode(mapKey, value);
		} else {
			if (value == null || value.length == 0) {
				root = delete(root, mapKey);
			} else {
				root = insert(root, mapKey, value, 0);
			}
		}
	}

}
