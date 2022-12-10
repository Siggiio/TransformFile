package io.siggi.transformfile;

import io.siggi.transformfile.io.LimitInputStream;
import io.siggi.transformfile.io.RafInputStream;

import io.siggi.transformfile.io.Util;
import io.siggi.transformfile.packet.PacketIO;
import io.siggi.transformfile.packet.types.PacketCloseFile;
import io.siggi.transformfile.packet.types.PacketEnd;
import io.siggi.transformfile.packet.types.PacketFileList;
import io.siggi.transformfile.packet.types.PacketFileName;

import io.siggi.transformfile.packet.types.PacketOffsets;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

import static io.siggi.transformfile.io.Util.*;

public class TransformFileOptimizer {
    public static void optimize(TransformFile tf, FileOutputStream out) throws IOException {
        tf.loadChunks();

        boolean[] use = new boolean[tf.files.length];
        for (DataChunk chunk : tf.chunks) {
            use[chunk.file] = true;
        }
        List<String> newFiles = new ArrayList<>(tf.files.length);
        int[] mapping = new int[tf.files.length];
        Map<String, Integer> remap = new HashMap<>();
        for (int i = 1; i < tf.files.length; i++) {
            String file = tf.files[i];
            if (file.equals("") || !use[i]) continue;
            int idx = remap.getOrDefault(file, -1);
            if (idx == -1) {
                remap.put(file, idx = remap.size() + 1);
                newFiles.add(file);
            }
            mapping[i] = idx;
        }

        PacketIO packetIO = PacketIO.getDefault();

        packetIO.writeFileHeader(out);

        if (tf.getFilename() != null) {
            packetIO.write(out, new PacketFileName(tf.getFilename()));
        }

        packetIO.write(out, new PacketFileList(newFiles));

        List<DataChunk> chunks = new LinkedList<>();

        for (DataChunk chunk : tf.chunks) {
            DataChunk transformedChunk = new DataChunk(chunk.transformedOffset, mapping[chunk.file], chunk.offset, chunk.length);
            if (!chunks.isEmpty()) {
                int lastItem = chunks.size() - 1;
                DataChunk combined = combine(chunks.get(lastItem), transformedChunk);
                if (combined != null) {
                    chunks.set(lastItem, combined);
                    continue;
                }
            }
            chunks.add(transformedChunk);
        }

        long resultFileSize = 0L;
        long nonRedundantSize = 0L;
        long[] highestPoint = new long[newFiles.size()];

        for (DataChunk chunk : chunks) {
            resultFileSize = chunk.transformedOffset + chunk.length;
            int fileIndex = chunk.file;
            if (fileIndex < 1) {
                nonRedundantSize = Math.max(nonRedundantSize, chunk.offset + chunk.length);
                continue;
            }
            highestPoint[fileIndex - 1] = chunk.transformedOffset + chunk.length;
        }

        List<Long> offsets = new LinkedList<>();

        ByteArrayOutputStream chunksBuffer = new ByteArrayOutputStream();
        for (DataChunk chunk : chunks) {
            long offsetOfLastByte = chunk.transformedOffset + chunk.length - 1L;
            long offsetFromStartOfChunks = chunksBuffer.size();
            int indexAddress = (int) (offsetOfLastByte / 131072L);
            while (offsets.size() <= indexAddress) offsets.add(offsetFromStartOfChunks);
            packetIO.write(chunksBuffer, chunk);
            int fileIndex = chunk.file;
            if (fileIndex < 1) continue;
            long highPoint = highestPoint[fileIndex - 1];
            if (chunk.transformedOffset + chunk.length == highPoint) {
                packetIO.write(chunksBuffer, new PacketCloseFile(fileIndex));
            }
        }
        packetIO.write(chunksBuffer, PacketEnd.instance);

        int chunksBufferSize = chunksBuffer.size();
        packetIO.write(out, new PacketOffsets(chunksBufferSize, chunksBufferSize + nonRedundantSize, resultFileSize));
        chunksBuffer.writeTo(out);

        RandomAccessFile raf = tf.rafs[0];
        raf.seek(tf.dataFileOffset);
        LimitInputStream in = new LimitInputStream(new RafInputStream(raf, false), nonRedundantSize, false);
        copy(in, out);

        for (long l : offsets) {
            Util.writeLong(out, l);
        }
    }

    private static DataChunk combine(DataChunk chunk1, DataChunk chunk2) {
        if (chunk1.file != chunk2.file) return null;
        if (chunk1.offset + chunk1.length != chunk2.offset) return null;
        if (chunk1.transformedOffset + chunk1.length != chunk2.transformedOffset) return null;
        return new DataChunk(chunk1.transformedOffset, chunk1.file, chunk1.offset, chunk1.length + chunk2.length);
    }
}
