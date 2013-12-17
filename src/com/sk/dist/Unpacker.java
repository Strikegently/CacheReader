package com.sk.dist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public abstract class Unpacker<T extends Packed> {

	private final RandomAccessFile src;
	private final int metaStart;
	protected final Class<T> storage;

	public Unpacker(Class<T> storage, RandomAccessFile src) throws IOException {
		this.storage = storage;
		this.src = src;
		this.metaStart = (int) (this.src.length() - unpackIndex(this.src.length() - 4) - 4);
	}

	public Packed unpack(int id) {
		if (this.src == null)
			throw new RuntimeException("Must set packed source first");
		int metaLoc = this.metaStart + id * 4;
		try (RACInputStream indexStream = new RACInputStream(this.src, metaLoc)) {
			if (metaLoc >= this.src.length() - 8)
				return null;
			int[] indices = readDelimiters(indexStream);
			byte[] data = readPackedSource(indices);
			if (data == null)
				return null;
			Packed ret = unpack(data);
			ret.id = id;
			return ret;
		} catch (IOException ex) {
			return null;
		}
	}

	private int[] readDelimiters(InputStream stream) throws IOException {
		int startIndex = readIndex(stream);
		if (startIndex == -1)
			return null;
		int endIndex;
		while ((endIndex = readIndex(stream)) == -1)
			;
		return new int[] { startIndex, endIndex };
	}

	private byte[] readPackedSource(int[] indices) throws IOException {
		if (indices == null)
			return null;
		byte[] ret = new byte[indices[1] - indices[0]];
		this.src.seek(indices[0]);
		this.src.readFully(ret);
		return ret;
	}

	private int unpackIndex(long loc) {
		try {
			return readIndex(new RACInputStream(this.src, loc));
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
	}

	protected int readIndex(InputStream in) throws IOException {
		int ret = 0;
		for (int i = 0; i < 4; ++i) {
			ret <<= 8;
			ret |= in.read() & 0xff;
		}
		return ret;
	}

	protected long readValue(InputStream in) throws IOException {
		int first = in.read();
		if (first == 0xff)
			return -1;
		int type = (first >> 6) & 0x3;
		long ret = first & ~0xc0;
		int count = (int) Math.pow(2, type);
		for (int i = 1; i < count; ++i) {
			ret <<= 8;
			ret |= in.read() & 0xff;
		}
		return ret;
	}

	protected String readString(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int curByte;
		while ((curByte = in.read()) != 0 && curByte != -1) {
			baos.write(curByte);
		}
		return new String(baos.toByteArray(), "UTF-8");
	}

	public abstract Packed unpack(byte[] input) throws IOException;

}
