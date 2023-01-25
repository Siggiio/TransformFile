package io.siggi.transformfile.packet.types;

import io.siggi.transformfile.io.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public final class PacketParentDirectoryDistance implements Packet {
    private int distance;

    public PacketParentDirectoryDistance() {
    }

    public PacketParentDirectoryDistance(int distance) {
        setDistance(distance);
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
        distance = (int) Util.readVarInt(in);
    }

    @Override
    public void write(OutputStream out, int protocolVersion) throws IOException {
        Util.writeVarInt(out, distance);
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.PARENT_DIRECTORY_DISTANCE;
    }
}
