package io.siggi.transformfile.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RandomAccessInputStream extends InputStream {
    private final RandomAccessData rad;
    private final boolean relayClose;
    private final byte[] one = new byte[1];
    private long markPos = -1L;
    private long filePointer;

    public RandomAccessInputStream(RandomAccessFile raf, boolean relayClose) {
        this(new RandomAccessDataFile(raf), relayClose);
    }
    public RandomAccessInputStream(RandomAccessData rad, boolean relayClose) {
        this(rad, -1L, relayClose);
    }
    public RandomAccessInputStream(RandomAccessData rad, long filePointer, boolean relayClose) {
        this.rad = rad;
        this.filePointer = filePointer;
        this.relayClose = relayClose;
    }

    private void seekToOffset() throws IOException {
        if (filePointer < 0L) return;
        if (rad.getFilePointer() != filePointer) {
            rad.seek(filePointer);
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
        int amount = rad.read(buffer, offset, length);
        if (filePointer >= 0L && amount >= 0) filePointer += amount;
        return amount;
    }

    @Override
    public long skip(long n) throws IOException {
        long oldPointer = filePointer >= 0L ? filePointer : rad.getFilePointer();
        long newPointer = Math.max(0L, Math.min(rad.length(), oldPointer + n));
        if (filePointer >= 0L) filePointer = newPointer;
        else rad.seek(newPointer);
        return newPointer - oldPointer;
    }

    public void seek(long offset) throws IOException {
        if (filePointer >= 0L) filePointer = offset;
        else rad.seek(offset);
    }

    @Override
    public void mark(int limit) {
        if (limit <= 0) {
            markPos = -1;
            return;
        }
        try {
            markPos = filePointer >= 0L ? filePointer : rad.getFilePointer();
        } catch (IOException e) {
            markPos = -1;
        }
    }

    @Override
    public void reset() throws IOException {
        if (markPos == -1L) {
            throw new IOException("Mark never set");
        }
        if (filePointer >= 0L) filePointer = markPos;
        else rad.seek(markPos);
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    public long getFilePointer() throws IOException {
        return filePointer >= 0L ? filePointer : rad.getFilePointer();
    }

    @Override
    public void close() throws IOException {
        if (relayClose) {
            rad.close();
        }
    }
}
