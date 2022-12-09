package io.siggi.transformfile.io;

import java.io.IOException;
import java.io.InputStream;

public class CountingInputStream extends InputStream {
    private final InputStream in;
    private long count = 0L;

    public CountingInputStream(InputStream in) {
        if (in == null) throw new NullPointerException();
        this.in = in;
    }

    public long getCount() {
        return count;
    }

    @Override
    public int read() throws IOException {
        int value = in.read();
        if (value >= 0) {
            count += 1L;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int c = in.read(buffer);
        if (c >= 0) count += c;
        return c;
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        int c = in.read(buffer, offset, count);
        if (c >= 0) this.count += c;
        return c;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }
}
