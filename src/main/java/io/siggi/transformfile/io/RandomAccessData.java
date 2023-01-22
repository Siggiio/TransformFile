package io.siggi.transformfile.io;

import java.io.Closeable;
import java.io.IOException;

public interface RandomAccessData extends Closeable {
    public int read() throws IOException;
    public int read(byte[] buffer) throws IOException;
    public int read(byte[] buffer, int offset, int length) throws IOException;
    public void write(int value) throws IOException;
    public void write(byte[] buffer) throws IOException;
    public void write(byte[] buffer, int offset, int length) throws IOException;
    public long length() throws IOException;
    public void setLength(long length) throws IOException;
    public void seek(long offset) throws IOException;
    public long getFilePointer() throws IOException;
    public boolean isCloseable();
}
