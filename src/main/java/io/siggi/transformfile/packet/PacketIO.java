package io.siggi.transformfile.packet;

import io.siggi.transformfile.exception.IncompatibleFileException;
import io.siggi.transformfile.io.Util;
import io.siggi.transformfile.packet.types.Packet;
import io.siggi.transformfile.packet.types.PacketOffsets;
import io.siggi.transformfile.packet.types.PacketCloseFile;
import io.siggi.transformfile.packet.types.PacketDataChunk;
import io.siggi.transformfile.packet.types.PacketEnd;
import io.siggi.transformfile.packet.types.PacketFileList;
import io.siggi.transformfile.packet.types.PacketFileName;
import io.siggi.transformfile.packet.types.PacketParentDirectoryDistance;
import io.siggi.transformfile.packet.types.PacketType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class PacketIO {
    private static final int DEFAULT_VERSION = 0;
    private static final int HIGHEST_SUPPORTED_VERSION = 0;

    private final List<Class<? extends Packet>> packets = new ArrayList<>();
    private final List<Supplier<? extends Packet>> packetConstructors = new ArrayList<>();
    private final Map<Class<? extends Packet>, Integer> packetToPacketId = new HashMap<>();
    private final Map<PacketType, Integer> packetTypeToPacketId = new HashMap<>();
    private final int protocolVersion;

    private void register(Class<? extends Packet> packet) {
        Supplier<? extends Packet> constructor;
        try {
            Method constructorGetter = packet.getDeclaredMethod("constructor");
            constructor = (Supplier<? extends Packet>) constructorGetter.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        int packetId = packets.size();
        packets.add(packet);
        packetConstructors.add(constructor);
        packetToPacketId.put(packet, packetId);
        packetTypeToPacketId.put(constructor.get().getPacketType(),  packetId);
    }

    public static PacketIO getDefault() {
        try {
            return new PacketIO(DEFAULT_VERSION);
        } catch (IncompatibleFileException e) {
            throw new RuntimeException(e);
        }
    }

    public static PacketIO get(int protocolVersion) throws IncompatibleFileException {
        return new PacketIO(protocolVersion);
    }

    private PacketIO(int protocolVersion) throws IncompatibleFileException {
        if (protocolVersion < 0) throw new IllegalArgumentException("Negative protocol version");
        if (protocolVersion > HIGHEST_SUPPORTED_VERSION) throw new IncompatibleFileException(protocolVersion, HIGHEST_SUPPORTED_VERSION);
        this.protocolVersion = protocolVersion;
        register(PacketEnd.class);
        register(PacketFileList.class);
        register(PacketDataChunk.class);
        register(PacketFileName.class);
        register(PacketParentDirectoryDistance.class);
        register(PacketCloseFile.class);
        register(PacketOffsets.class);
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public Packet read(InputStream in) throws IOException {
        int packetId = (int) Util.readVarInt(in);
        if (packetId < 0 || packetId >= packetConstructors.size()) {
            throw new IOException("Invalid TransformFile - Unknown packet ID " + packetId);
        }
        Packet packet = packetConstructors.get(packetId).get();
        packet.read(in, protocolVersion);
        return packet;
    }

    public void write(OutputStream out, Packet packet) throws IOException {
        Util.writeVarInt(out, packetTypeToPacketId.get(packet.getPacketType()));
        packet.write(out, protocolVersion);
    }

    public void writeFileHeader(OutputStream out) throws IOException {
        Util.writeVarInt(out, protocolVersion);
    }
}
