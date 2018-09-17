package org.brewchain.account.ept;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.commons.codec.binary.Hex;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ETNode {
	// String hashs[] = new String[EHelper.radix];
	String childrenHashs[] = new String[EHelper.radix];
	private String key;
	private byte v[];
	private boolean dirty = true;
	private byte[] hash = null;
	private byte[] contentData = null;
	private ETNode children[] = new ETNode[EHelper.radix];

	public void toJsonString(StringBuffer sb) {
		sb.append("{");
		sb.append("\"key\":\"").append(key).append("\"");
		for (int i = 0; i < EHelper.radix; i++) {
			if (children[i] != null) {
				sb.append(",\"node_").append(i).append("\":");
				children[i].toJsonString(sb);
			}
		}
		if (v != null) {
			sb.append(",\"v\":\"").append(Hex.encodeHex(v)).append("\"");
		}
		sb.append("}");
	}
	
	public byte[] encode(){
		if(hash!=null&!dirty){
			return hash;
		}
		contentData = toBytes();
		hash = EHelper.encAPI.sha3Encode(contentData);
		dirty = false;
		return hash;
	}

	public String getHashs(int idx) {
		if (childrenHashs[idx] != null)
			return childrenHashs[idx];
		else
			return null;
	}

	public String getHashs(char ch) {
		return getHashs(EHelper.findIdx(ch));
	}

	public ETNode getChild(char ch) {
		return children[EHelper.findIdx(ch)];
	}

	public void appendChildNode(ETNode node, char ch) {
		int idx = EHelper.findIdx(ch);
		this.children[idx] = node;
		this.childrenHashs[idx] = node.getKey();
		this.dirty = true;
	}

	public byte[] toBytes() {
		
		try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
			StringBuffer sb = new StringBuffer(key);
			for (String childHash : childrenHashs) {
				sb.append(",");
				if (childHash != null) {
					sb.append(childHash);
				}
			}
			byte[] hashBB = sb.toString().getBytes();
			bout.write(hashBB.length & 0xFF);
			bout.write((hashBB.length >> 8) & 0xFF);
			bout.write(hashBB);
			if (v != null && v.length > 0) {
				bout.write(v);
			}
			return bout.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static ETNode fromBytes(byte[] bb) {
		try (ByteArrayInputStream bin = new ByteArrayInputStream(bb);) {
			int lenbb1 = bin.read();
			int lenbb2 = bin.read();
			int len = (lenbb1 & 0xff) | ((lenbb2 << 8) & 0xff00);
			byte hashBB[] = new byte[len];
			bin.read(hashBB);
			String[] strs = new String(hashBB).split(",");
			ETNode node = new ETNode();
			byte[] bbv;
			node.key = strs[0];
			for (int i = 1; i < strs.length; i++) {
				node.childrenHashs[i - 1] = strs[i];
			}
			if (bin.available() > 0) {
				bbv = new byte[bin.available()];
				bin.read(bbv);
				node.v = bbv;
			}
			
			return node;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public ETNode(String key) {
		this.key = key;
	}

	public ETNode(String key, byte[] v) {
		this.key = key;
		this.v = v;
	}

}
