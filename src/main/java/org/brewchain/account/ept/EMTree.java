package org.brewchain.account.ept;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EMTree {

	ETNode root;

	public ETNode delete(ETNode node, String key) {
		return root;
	}

	public ETNode insert(ETNode node, String key, byte[] value, int deep) {
		char ch = key.charAt(deep);
		ETNode child = node.getChild(ch);
		if (child == null) {
			node.appendChildNode(new ETNode(key, value), ch);
		} else {
			insert(child, key, value, deep + 1);
		}
		return node;
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
