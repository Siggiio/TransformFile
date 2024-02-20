package io.siggi.transformfile;

import io.siggi.transformfile.exception.IncompatibleFileException;
import io.siggi.transformfile.io.Util;
import io.siggi.transformfile.packet.PacketIO;
import io.siggi.transformfile.packet.types.Packet;
import io.siggi.transformfile.packet.types.PacketFileList;
import io.siggi.transformfile.packet.types.PacketFileName;
import io.siggi.transformfile.packet.types.PacketParentDirectoryDistance;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TransformFilePrefixAndScan {
    public static void run(String prefix, int directoryScan, File file) throws IOException, IncompatibleFileException {
        File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            try (FileInputStream in = new FileInputStream(file);
                 FileOutputStream out = new FileOutputStream(tmpFile)) {
                int ver = (int) Util.readVarInt(in);
                PacketIO packetIO = PacketIO.get(ver);
                packetIO.writeFileHeader(out);
                if (directoryScan > 0) {
                    packetIO.write(out, new PacketParentDirectoryDistance(directoryScan));
                }
                boolean continueReading = true;
                while (continueReading) {
                    Packet packet = packetIO.read(in);
                    switch (packet.getPacketType()) {
                        case FILE_LIST:
                            ((PacketFileList) packet).getFileList().replaceAll(
                                    s -> prefix + s.substring(s.lastIndexOf("/") + 1)
                            );
                            break;
                        case PARENT_DIRECTORY_DISTANCE:
                            continue;
                        case END:
                            continueReading = false;
                            break;
                    }
                    packetIO.write(out, packet);
                }
                Util.copy(in, out);
            }
            tmpFile.renameTo(file);
        } finally {
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
    }
}
