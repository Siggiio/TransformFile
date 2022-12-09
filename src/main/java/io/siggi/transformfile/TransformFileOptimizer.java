package io.siggi.transformfile;

import io.siggi.transformfile.io.RafInputStream;

import io.siggi.transformfile.packet.PacketIO;
import io.siggi.transformfile.packet.types.PacketEnd;
import io.siggi.transformfile.packet.types.PacketFileList;
import io.siggi.transformfile.packet.types.PacketFileName;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

import static io.siggi.transformfile.io.Util.*;

public class TransformFileOptimizer {
    public static void optimize(TransformFile tf, FileOutputStream out) throws IOException {
        boolean[] use = new boolean[tf.files.length];
        for (DataChunk chunk : tf.chunks) {
            use[chunk.file] = true;
        }
        List<String> newFiles = new ArrayList<>(tf.files.length);
        Map<String, Integer> remap = new HashMap<>();
        for (int i = 1; i < tf.files.length; i++) {
            String file = tf.files[i];
            if (file.equals("") || !use[i]) continue;
            Integer idx = remap.get(file);
            if (idx == null) {
                remap.put(file, remap.size() + 1);
                newFiles.add(file);
            }
        }
        remap.put("", 0);

        PacketIO packetIO = PacketIO.getDefault();

        packetIO.writeFileHeader(out);

        if (tf.getFilename() != null) {
            packetIO.write(out, new PacketFileName(tf.getFilename()));
        }

        packetIO.write(out, new PacketFileList(newFiles));

        List<DataChunk> chunks = new LinkedList<>();

        for (DataChunk chunk : tf.chunks) {
            if (!chunks.isEmpty()) {
                int lastItem = chunks.size() - 1;
                DataChunk combined = combine(chunks.get(lastItem), chunk);
                if (combined != null) {
                    chunks.set(lastItem, combined);
                    continue;
                }
            }
            chunks.add(chunk);
        }

        for (DataChunk chunk : chunks) {
            packetIO.write(out, chunk);
        }
        packetIO.write(out, PacketEnd.instance);

        RandomAccessFile raf = tf.rafs[0];
        raf.seek(tf.dataFileOffset);
        RafInputStream in = new RafInputStream(raf, false);
        copy(in, out);
    }

    private static DataChunk combine(DataChunk chunk1, DataChunk chunk2) {
        if (chunk1.file != chunk2.file) return null;
        if (chunk1.offset + chunk1.length != chunk2.offset) return null;
        if (chunk1.transformedOffset + chunk1.length != chunk2.transformedOffset) return null;
        return new DataChunk(chunk1.transformedOffset, chunk1.file, chunk1.offset, chunk1.length + chunk2.length);
    }
}
