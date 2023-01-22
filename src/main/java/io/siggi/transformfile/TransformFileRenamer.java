package io.siggi.transformfile;

import io.siggi.transformfile.exception.IncompatibleFileException;
import io.siggi.transformfile.io.Util;
import io.siggi.transformfile.packet.PacketIO;
import io.siggi.transformfile.packet.types.Packet;
import io.siggi.transformfile.packet.types.PacketFileList;
import io.siggi.transformfile.packet.types.PacketFileName;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class TransformFileRenamer {
    public static void main(String[] args) {

    }

    public static void rename(File original, File target, String newTargetName, String[] newSourceNames) throws IOException, IncompatibleFileException {
        try (FileInputStream in = new FileInputStream(original);
             FileOutputStream out = new FileOutputStream(target)) {
            int ver = (int) Util.readVarInt(in);
            PacketIO packetIO = PacketIO.get(ver);
            packetIO.writeFileHeader(out);
            boolean continueReading = true;
            while (continueReading) {
                Packet packet = packetIO.read(in);
                switch (packet.getPacketType()) {
                    case FILE_LIST:
                        if (newSourceNames != null) {
                            ((PacketFileList) packet).setFileList(Arrays.asList(newSourceNames));
                        }
                        break;
                    case FILE_NAME:
                        if (newTargetName != null) {
                            ((PacketFileName) packet).setFileName(newTargetName);
                        }
                        break;
                    case END:
                        continueReading = false;
                        break;
                }
                packetIO.write(out, packet);
            }
            Util.copy(in, out);
        }
    }
}
