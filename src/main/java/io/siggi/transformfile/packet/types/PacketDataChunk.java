package io.siggi.transformfile.packet.types;

import io.siggi.transformfile.DataChunk;
import io.siggi.transformfile.io.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public class PacketDataChunk implements Packet {
    private DataChunk dataChunk = null;

    public PacketDataChunk() {
    }

    public PacketDataChunk(DataChunk dataChunk) {
        setDataChunk(dataChunk);
    }

    public DataChunk getDataChunk() {
        return dataChunk;
    }

    public void setDataChunk(DataChunk dataChunk) {
        this.dataChunk = dataChunk;
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
        long transformedOffset = Util.readVarInt(in);
        int fileIndex = (int) Util.readVarInt(in);
        long offset = Util.readVarInt(in);
        long length = Util.readVarInt(in);
        dataChunk = new DataChunk(transformedOffset, fileIndex, offset, length);
    }

    @Override
    public void write(OutputStream out, int protocolVersion) throws IOException {
        Util.writeVarInt(out, dataChunk.transformedOffset);
        Util.writeVarInt(out, dataChunk.file);
        Util.writeVarInt(out, dataChunk.offset);
        Util.writeVarInt(out, dataChunk.length);
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.DATA_CHUNK;
    }
}
