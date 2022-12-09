package io.siggi.transformfile.packet.types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Packet {
    public void read(InputStream in, int protocolVersion) throws IOException;
    public void write(OutputStream out, int protocolVersion) throws IOException;
    public PacketType getPacketType();
}
