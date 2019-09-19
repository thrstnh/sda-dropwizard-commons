package org.sdase.commons.server.kafka;

import java.util.Iterator;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class HeaderList implements Headers {

	@Override
	public Iterator<Header> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Headers add(Header header) throws IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Headers add(String key, byte[] value) throws IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Headers remove(String key) throws IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Header lastHeader(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Header> headers(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Header[] toArray() {
		// TODO Auto-generated method stub
		return null;
	}

}
