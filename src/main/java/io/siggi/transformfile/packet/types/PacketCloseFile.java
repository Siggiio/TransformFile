package io.siggi.transformfile.packet.types;

import io.siggi.transformfile.io.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public class PacketCloseFile implements Packet {

    private int fileIndex;

    public PacketCloseFile() {
    }

    public PacketCloseFile(int fileIndex) {
        setFileIndex(fileIndex);
    }

    public int getFileIndex() {
        return fileIndex;
    }

    public void setFileIndex(int fileIndex) {
        this.fileIndex = fileIndex;
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
        fileIndex = (int) Util.readVarInt(in);
    }

    @Override
    public void write(OutputStream out, int protocolVersion) throws IOException {
        Util.writeVarInt(out, fileIndex);
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.CLOSE_FILE;
    }
}
