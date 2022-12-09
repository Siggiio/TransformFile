package io.siggi.transformfile.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RafInputStream extends InputStream {
    private final RandomAccessFile raf;
    private final boolean relayClose;
    private final byte[] one = new byte[1];
    private long markPos = -1L;
    private long offset;

    public RafInputStream(RandomAccessFile raf, boolean relayClose) {
        this(raf, -1L, relayClose);
    }
    public RafInputStream(RandomAccessFile raf, long offset, boolean relayClose) {
        this.raf = raf;
        this.offset = offset;
        this.relayClose = relayClose;
    }

    private void seekToOffset() throws IOException {
        if (offset < 0L) return;
        if (raf.getFilePointer() != offset) {
            raf.seek(offset);
        }
    }

    @Override
    public int read() throws IOException {
        int amount = read(one, 0, 1);
        if (amount == -1) return -1;
        return one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        seekToOffset();
        int amount = raf.read(buffer, offset, length);
        if (this.offset >= 0L && amount >= 0) this.offset += amount;
        return amount;
    }

    @Override
    public long skip(long n) throws IOException {
        long oldPointer = offset >= 0L ? offset : raf.getFilePointer();
        long newPointer = Math.max(0L, Math.min(raf.length(), oldPointer + n));
        if (offset >= 0L) offset = newPointer;
        else raf.seek(newPointer);
        return newPointer - oldPointer;
    }

    public void seek(long offset) throws IOException {
        if (this.offset >= 0L) this.offset = offset;
        else raf.seek(offset);
    }

    @Override
    public void mark(int limit) {
        if (limit <= 0) {
            markPos = -1;
            return;
        }
        try {
            markPos = offset >= 0L ? offset : raf.getFilePointer();
        } catch (IOException e) {
            markPos = -1;
        }
    }

    @Override
    public void reset() throws IOException {
        if (markPos == -1L) {
            throw new IOException("Mark never set");
        }
        if (offset >= 0L) offset = markPos;
        else raf.seek(markPos);
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    public long getFilePointer() throws IOException {
        return offset >= 0L ? offset : raf.getFilePointer();
    }

    @Override
    public void close() throws IOException {
        if (relayClose) {
            raf.close();
        }
    }
}
