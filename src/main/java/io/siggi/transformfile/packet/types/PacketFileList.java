package io.siggi.transformfile.packet.types;

import io.siggi.transformfile.io.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class PacketFileList implements Packet {
    private List<String> fileList = null;

    public PacketFileList() {
    }

    public PacketFileList(List<String> fileList) {
        setFileList(fileList);
    }

    public List<String> getFileList() {
        return fileList;
    }

    public void setFileList(List<String> fileList) {
        this.fileList = fileList;
    }

    @Override
    public void read(InputStream in, int protocolVersion) throws IOException {
        int fileCount = (int) Util.readVarInt(in);
        fileList = new ArrayList<>(fileCount + 1); // +1 because an extra element is usually added in other code
        fileList:
        for (int i = 0; i < fileCount; i++) {
            fileList.add(Util.readString(in, 4096));
        }
    }

    @Override
    public void write(OutputStream out, int protocolVersion) throws IOException {
        out.write(fileList.size());
        for (String file : fileList) {
            Util.writeString(out, file);
        }
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.FILE_LIST;
    }
}
