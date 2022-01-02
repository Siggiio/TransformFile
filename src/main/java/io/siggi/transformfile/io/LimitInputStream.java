package io.siggi.transformfile.io;

import java.io.IOException;
import java.io.InputStream;

public class LimitInputStream extends InputStream {
    private final InputStream in;
    private final boolean relayClose;
    private final byte[] one = new byte[1];
    private long left;

    public LimitInputStream(InputStream in, long amount, boolean relayClose) {
        if (in == null)
            throw new NullPointerException("null InputStream");
        if (left < 0L)
            throw new IllegalArgumentException("Negative amount");
        this.in = in;
        this.left = amount;
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
        if (left <= 0L) {
            return -1;
        } else if (length > left) {
            length = (int) left;
        }
        int amount = in.read(buffer, offset, length);
        if (amount > 0)
            left -= amount;
        return amount;
    }

    public long skip(long n) throws IOException {
        if (n <= 0L) {
            return 0L;
        }
        long amount = in.skip(n);
        left -= amount;
        return amount;
    }

    @Override
    public void close() throws IOException {
        if (relayClose) {
            in.close();
        }
    }
}
