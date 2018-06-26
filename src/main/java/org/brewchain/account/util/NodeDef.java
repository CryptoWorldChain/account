package org.brewchain.account.util;

import com.google.protobuf.ByteString;

public class NodeDef {
	private String node;
	private String bcuid;
	private String address;
	private NodeAccount oAccount;

	public String getNode() {
		return node;
	}

	public void setNode(String node) {
		this.node = node;
	}

	public String getBcuid() {
		return bcuid;
	}

	public void setBcuid(String bcuid) {
		this.bcuid = bcuid;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public NodeAccount getoAccount() {
		return oAccount;
	}

	public void setoAccount(NodeAccount oAccount) {
		this.oAccount = oAccount;
	}

	public class NodeAccount {
		private ByteString address;
		private String bcuid;

		public ByteString getAddress() {
			return address;
		}

		public void setAddress(ByteString address) {
			this.address = address;
		}

		public String getBcuid() {
			return bcuid;
		}

		public void setBcuid(String bcuid) {
			this.bcuid = bcuid;
		}

	}
}
