package io.siggi.transformfile.io;

import java.io.IOException;
import java.io.RandomAccessFile;

public class RandomAccessDataFile implements RandomAccessData {
    private final RandomAccessFile raf;
    private final boolean relayClose;

    public RandomAccessDataFile(RandomAccessFile raf) {
        this(raf, true);
    }

    public RandomAccessDataFile(RandomAccessFile raf, boolean relayClose) {
        this.raf = raf;
        this.relayClose = relayClose;
    }

    @Override
    public int read() throws IOException {
        return raf.read();
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return raf.read(buffer);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return raf.read(buffer, offset, length);
    }

    @Override
    public void write(int value) throws IOException {
        raf.write(value);
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        raf.write(buffer);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        raf.write(buffer, offset, length);
    }

    @Override
    public long length() throws IOException {
        return raf.length();
    }

    @Override
    public void setLength(long length) throws IOException {
        raf.setLength(length);
    }

    @Override
    public void seek(long offset) throws IOException {
        raf.seek(offset);
    }

    @Override
    public long getFilePointer() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public boolean isCloseable() {
        return relayClose;
    }

    @Override
    public void close() throws IOException {
        if (relayClose) raf.close();
    }
}
