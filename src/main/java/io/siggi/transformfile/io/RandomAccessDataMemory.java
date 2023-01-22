package io.siggi.transformfile.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RandomAccessDataMemory implements RandomAccessData {
    private final byte[] buffer;
    private int filePointer;

    public RandomAccessDataMemory(byte[] buffer) {
        this.buffer = buffer;
    }

    public static RandomAccessDataMemory create(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Util.copy(in, out);
        return new RandomAccessDataMemory(out.toByteArray());
    }

    @Override
    public int read() throws IOException {
        if (filePointer >= buffer.length) return -1;
        return ((int) buffer[filePointer++]) & 0xff;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int remaining = this.buffer.length - filePointer;
        if (remaining <= 0) return -1;
        length = Math.min(length, remaining);
        System.arraycopy(this.buffer, filePointer, buffer, offset, length);
        filePointer += length;
        return length;
    }

    @Override
    public void write(int value) throws IOException {
        throw new IOException("Read-only memory");
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        throw new IOException("Read-only memory");
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("Read-only memory");
    }

    @Override
    public long length() throws IOException {
        return buffer.length;
    }

    @Override
    public void setLength(long length) throws IOException {
        throw new IOException("Read-only memory");
    }

    @Override
    public void seek(long offset) throws IOException {
        if (offset < 0) {
            throw new IOException("Seek to negative offset");
        }
        this.filePointer = (int) Math.min(buffer.length, offset);
    }

    @Override
    public long getFilePointer() throws IOException {
        return filePointer;
    }

    @Override
    public boolean isCloseable() {
        return false;
    }

    @Override
    public void close() throws IOException {
    }
}
