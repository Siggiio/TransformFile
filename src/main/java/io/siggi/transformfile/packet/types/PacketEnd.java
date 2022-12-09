package io.siggi.transformfile.packet.types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public final class PacketEnd implements Packet {

    public static final PacketEnd instance = new PacketEnd();

    private PacketEnd() {
    }

    public static Supplier<PacketEnd> constructor() {
        return () -> instance;
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
    }

    @Override
    public void write(OutputStream out, int protocolVersion) throws IOException {
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.END;
    }
}
