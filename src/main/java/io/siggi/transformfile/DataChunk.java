package io.siggi.transformfile;

import io.siggi.transformfile.packet.types.PacketDataChunk;
import java.io.IOException;
import java.io.InputStream;

public final class DataChunk extends PacketDataChunk {
    public final long transformedOffset;
    public final int file;
    public final long offset;
    public final long length;

    public DataChunk(long transformedOffset, int file, long offset, long length) {
        super.setDataChunk(this);
        this.transformedOffset = transformedOffset;
        this.file = file;
        this.offset = offset;
        this.length = length;
        if (length < 0L) {
            throw new IllegalArgumentException("negative length");
        }
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDataChunk(DataChunk dataChunk) {
        throw new UnsupportedOperationException();
    }
}
