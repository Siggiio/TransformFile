package io.siggi.transformfile;

public class DataChunk {
    final long transformedOffset;
    final int file;
    final long offset;
    final long length;

    DataChunk(long transformedOffset, int file, long offset, long length) {
        this.transformedOffset = transformedOffset;
        this.file = file;
        this.offset = offset;
        this.length = length;
        if (length < 0L) {
            throw new IllegalArgumentException("negative length");
        }
    }
}
