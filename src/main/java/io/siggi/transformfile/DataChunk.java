package io.siggi.transformfile;

public class DataChunk {
    final TransformFile tf;
    final long transformedOffset;
    final int file;
    final long offset;
    final long length;

    DataChunk(TransformFile f, long transformedOffset, int file, long offset, long length) {
        this.tf = f;
        this.transformedOffset = transformedOffset;
        this.file = file;
        this.offset = offset;
        this.length = length;
        if (length < 0L) {
            throw new IllegalArgumentException("negative length");
        }
    }
}
