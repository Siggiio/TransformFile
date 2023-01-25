package io.siggi.transformfile.packet.types;

import io.siggi.transformfile.io.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public class PacketOffsets implements Packet {
    private long nonRedundantOffset;
    private long addressIndexOffset;
    private long resultSize;

    public PacketOffsets() {
    }

    public PacketOffsets(long nonRedundantOffset, long addressIndexOffset, long resultSize) {
        setNonRedundantOffset(nonRedundantOffset);
        setAddressIndexOffset(addressIndexOffset);
        setResultSize(resultSize);
    }

    public long getNonRedundantOffset() {
        return nonRedundantOffset;
    }

    public void setNonRedundantOffset(long nonRedundantOffset) {
        this.nonRedundantOffset = nonRedundantOffset;
    }

    public long getAddressIndexOffset() {
        return addressIndexOffset;
    }

    public void setAddressIndexOffset(long addressIndexOffset) {
        this.addressIndexOffset = addressIndexOffset;
    }

    public long getResultSize() {
        return resultSize;
    }

    public void setResultSize(long resultSize) {
        this.resultSize = resultSize;
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
        nonRedundantOffset = Util.readVarInt(in);
        addressIndexOffset = Util.readVarInt(in);
        resultSize = Util.readVarInt(in);
    }

    @Override
    public void write(OutputStream out, int protocolVersion) throws IOException {
        Util.writeVarInt(out, nonRedundantOffset);
        Util.writeVarInt(out, addressIndexOffset);
        Util.writeVarInt(out, resultSize);
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.OFFSETS;
    }
}
