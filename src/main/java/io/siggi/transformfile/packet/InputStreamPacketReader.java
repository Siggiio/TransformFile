package io.siggi.transformfile.packet;

import io.siggi.transformfile.packet.types.Packet;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamPacketReader implements PacketReader {
    private final InputStream in;
    private final PacketIO packetIO;

    public InputStreamPacketReader(InputStream in, PacketIO packetIO) {
        this.in = in;
        this.packetIO = packetIO;
    }

    public Packet readPacket() throws IOException {
        return packetIO.read(in);
    }
}
