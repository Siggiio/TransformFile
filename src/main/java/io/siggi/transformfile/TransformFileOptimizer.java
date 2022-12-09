package io.siggi.transformfile;

import io.siggi.transformfile.io.RafInputStream;

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

        writeVarInt(out, 0); // version

        if (tf.getFilename() != null) {
            writeVarInt(out, 3); // filename
            writeString(out, tf.getFilename());
        }

        writeVarInt(out, 1); // file list
        writeVarInt(out, newFiles.size());
        for (String newFile : newFiles)
            writeString(out, newFile);

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
            writeVarInt(out, 2); // chunk
            writeVarInt(out, chunk.transformedOffset);
            writeVarInt(out, remap.get(tf.files[chunk.file]));
            writeVarInt(out, chunk.offset);
            writeVarInt(out, chunk.length);
        }
        writeVarInt(out, 0); // end

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
