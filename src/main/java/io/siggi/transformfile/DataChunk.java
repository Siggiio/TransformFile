package io.siggi.transformfile;

public final class DataChunk {
    public final long transformedOffset;
    public final int file;
    public final long offset;
    public final long length;

    public DataChunk(long transformedOffset, int file, long offset, long length) {
        this.transformedOffset = transformedOffset;
        this.file = file;
        this.offset = offset;
        this.length = length;
        if (length < 0L) {
            throw new IllegalArgumentException("negative length");
        }
    }
}
