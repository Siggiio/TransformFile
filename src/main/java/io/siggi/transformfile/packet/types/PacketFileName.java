package io.siggi.transformfile.packet.types;

import io.siggi.transformfile.io.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public final class PacketFileName implements Packet {
    private String fileName;

    public PacketFileName() {
        this.fileName = "";
    }

    public PacketFileName(String fileName) {
        setFileName(fileName);
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        if (fileName == null) fileName = "";
        this.fileName = fileName;
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
        fileName = Util.readString(in, 4096);
    }

    @Override
    public void write(OutputStream out, int protocolVersion) throws IOException {
        Util.writeString(out, fileName);
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.FILE_NAME;
    }
}
