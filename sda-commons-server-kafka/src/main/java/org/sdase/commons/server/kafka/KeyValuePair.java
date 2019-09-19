package org.sdase.commons.server.kafka;

import org.apache.kafka.common.header.Header;

public class KeyValuePair implements Header {

	private String key;
	private byte[] value;

	public KeyValuePair(String key, byte[] value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public String key() {
		return key;
	}

	@Override
	public byte[] value() {
		return value;
	}

}
