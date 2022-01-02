package io.siggi.transformfile.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RafInputStream extends InputStream {
    private final RandomAccessFile raf;
    private final boolean relayClose;
    private final byte[] one = new byte[1];
    private long markPos = -1L;

    public RafInputStream(RandomAccessFile raf, boolean relayClose) {
        this.raf = raf;
        this.relayClose = relayClose;
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
        return raf.read(buffer, offset, length);
    }

    @Override
    public long skip(long n) throws IOException {
        long oldPointer = raf.getFilePointer();
        long newPointer = Math.max(0L, Math.min(raf.length(), oldPointer + n));
        raf.seek(newPointer);
        return newPointer - oldPointer;
    }

    @Override
    public void mark(int limit) {
        if (limit <= 0) {
            markPos = -1;
            return;
        }
        try {
            markPos = raf.getFilePointer();
        } catch (IOException e) {
            markPos = -1;
        }
    }

    @Override
    public void reset() throws IOException {
        if (markPos == -1L) {
            throw new IOException("Mark never set");
        }
        raf.seek(markPos);
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    public long getFilePointer() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public void close() throws IOException {
        if (relayClose) {
            raf.close();
        }
    }
}
