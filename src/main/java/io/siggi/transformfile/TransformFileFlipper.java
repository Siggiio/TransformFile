package io.siggi.transformfile;

import io.siggi.transformfile.io.LimitInputStream;
import io.siggi.transformfile.io.RafInputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;

import static io.siggi.transformfile.io.Util.*;

public class TransformFileFlipper {
    public static void flip(TransformFile file, int indexToFlip, OutputStream out, String newSourceName, File newDestination) throws IOException {
        if (indexToFlip == 0) {
            throw new IllegalArgumentException("Can't flip index 0");
        }
        List<DataChunk> chunks = file.getChunks(indexToFlip);
        chunks.sort((chunkA, chunkB) -> {
            if (chunkA.offset > chunkB.offset) {
                return 1;
            } else if (chunkA.offset < chunkB.offset) {
                return -1;
            } else {
                return 0;
            }
        });
        long xfrPosition = 0L;
        long currentPosition = 0L;
        long fileLength = newDestination.length();
        List<DataChunk> indexZeroChunks = new LinkedList<>();
        List<DataChunk> newChunks = new LinkedList<>();
        for (DataChunk chunk : chunks) {
            if (chunk.offset < currentPosition) {
                if (chunk.offset + chunk.length < currentPosition)
                    continue;
                long skipBytes = currentPosition - chunk.offset;
                chunk = new DataChunk(null, chunk.transformedOffset + skipBytes, chunk.file, chunk.offset + skipBytes, chunk.length - skipBytes);
            }
            if (currentPosition < chunk.offset) {
                DataChunk selfChunk = new DataChunk(null, currentPosition, 0, xfrPosition, chunk.offset - currentPosition);
                newChunks.add(selfChunk);
                indexZeroChunks.add(selfChunk);
                xfrPosition += chunk.offset - currentPosition;
                currentPosition += selfChunk.length;
            }
            // swapping offset and transformedOffset is not a mistake!
            newChunks.add(new DataChunk(null, chunk.offset, 1, chunk.transformedOffset, chunk.length));
            currentPosition += chunk.length;
        }
        if (currentPosition < fileLength) {
            DataChunk selfChunk = new DataChunk(null, currentPosition, 0, xfrPosition, fileLength - currentPosition);
            newChunks.add(selfChunk);
            indexZeroChunks.add(selfChunk);
            xfrPosition += fileLength - currentPosition;
            currentPosition += selfChunk.length;
        }


        writeVarInt(out, 0); // version

        if (file.getFilename() != null) {
            writeVarInt(out, 3); // filename
            writeString(out, file.getFilename());
        }

        writeVarInt(out, 1); // file list
        writeVarInt(out, 1);
        writeString(out, newSourceName);

        for (DataChunk chunk : newChunks) {
            writeVarInt(out, 2); // chunk
            writeVarInt(out, chunk.transformedOffset);
            writeVarInt(out, chunk.file);
            writeVarInt(out, chunk.offset);
            writeVarInt(out, chunk.length);
        }
        writeVarInt(out, 0); // end

        if (!indexZeroChunks.isEmpty()) {
            try (RandomAccessFile raf = new RandomAccessFile(newDestination, "r")) {
                RafInputStream rIn = new RafInputStream(raf, false);
                for (DataChunk chunk : indexZeroChunks) {
                    raf.seek(chunk.transformedOffset);
                    LimitInputStream lIn = new LimitInputStream(rIn, chunk.length, false);
                    copy(lIn, out);
                }
            }
        }
    }
}
