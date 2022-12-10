package io.siggi.transformfile.packet;

import io.siggi.transformfile.packet.types.Packet;

import java.io.IOException;

public interface PacketReader {
    public Packet readPacket() throws IOException;
}
