package io.siggi.transformfile.packet;

import io.siggi.transformfile.DataChunk;
import io.siggi.transformfile.packet.types.Packet;
import io.siggi.transformfile.packet.types.PacketEnd;

import java.io.IOException;

public class MemoryDataChunkPacketReader implements PacketReader {
    private final DataChunk[] chunks;
    private int next;

    public MemoryDataChunkPacketReader(DataChunk[] chunks) {
        this(chunks, 0);
    }

    public MemoryDataChunkPacketReader(DataChunk[] chunks, int startAt) {
        this.chunks = chunks;
        this.next = startAt;
    }

    @Override
    public Packet readPacket() throws IOException {
        int currentPointer = next++;
        if (currentPointer == chunks.length) {
            return PacketEnd.instance;
        } else if (currentPointer > chunks.length) {
            return null;
        }
        return chunks[currentPointer];
    }
}
