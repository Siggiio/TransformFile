package io.siggi.transformfile;

import io.siggi.transformfile.exception.TransformFileException;
import io.siggi.transformfile.io.CountingInputStream;
import io.siggi.transformfile.io.LimitInputStream;
import io.siggi.transformfile.io.RafInputStream;
import io.siggi.transformfile.io.Util;

import io.siggi.transformfile.packet.PacketIO;
import io.siggi.transformfile.packet.types.Packet;
import io.siggi.transformfile.packet.types.PacketDataChunk;
import io.siggi.transformfile.packet.types.PacketFileList;
import io.siggi.transformfile.packet.types.PacketFileName;
import io.siggi.transformfile.packet.types.PacketParentDirectoryDistance;
import java.io.*;
import java.util.LinkedList;
import java.util.List;

public class TransformFile extends InputStream {
    final String[] files;
    final DataChunk[] chunks;
    final long dataFileOffset;
    final RandomAccessFile[] rafs;
    private final String filename;
    private final File file;
    private final File parentDirectory;
    private final long length;
    private final byte[] one = new byte[1];
    private boolean closed = false;
    private int currentIndex = -1;
    private InputStream currentInput = null;
    private long currentOffset = 0L;
    private int getChunksLastPosition = 0;
    private final PacketIO packetIO;

    public TransformFile(File file) throws IOException, TransformFileException {
        boolean success = false;
        RandomAccessFile raf = null;
        try {
            long highLength = 0L;
            String filename = null;
            this.file = file;
            this.parentDirectory = file.getParentFile();
            String xfrName = file.getName();
            long dataFileOffset = -1L;
            List<String> fileList = null;
            List<DataChunk> dataChunks = new LinkedList<>();
            raf = new RandomAccessFile(file, "r");
            RafInputStream rafIn = new RafInputStream(raf, false);
            InputStream bufferedIn = new BufferedInputStream(rafIn);
            CountingInputStream in = new CountingInputStream(bufferedIn);
            int version = (int) Util.readVarInt(in);
            packetIO = PacketIO.get(version);
            int parentScan = 0;
            readLoop:
            while (true) {
                Packet packet = packetIO.read(in);
                switch (packet.getPacketType()) {
                    case END:
                        dataFileOffset = in.getCount();
                        break readLoop;
                    case FILE_LIST: {
                        PacketFileList packetFileList = (PacketFileList) packet;
                        fileList = packetFileList.getFileList();
                        fileList.add(0, "");
                        fileList:
                        for (int i = 1; i < fileList.size(); i++) {
                            String name = fileList.get(i);
                            String[] names = name.split(":");
                            for (int j = 0; j < names.length; j++) {
                                String n = names[j];
                                if (n.length() >= 2 && n.charAt(0) == '\0') {
                                    int removeFromEnd = (int) n.charAt(1);
                                    n = xfrName.substring(0, xfrName.length() - removeFromEnd) + n.substring(2);
                                }
                                names[j] = n;
                            }
                            for (int j = 0; j < names.length; j++) {
                                String parentScanPrefix = "";
                                for (int k = 0; k <= parentScan; k++) {
                                    String path = parentScanPrefix + names[j];
                                    File f = new File(parentDirectory, path);
                                    if (f.exists()) {
                                        fileList.set(i, path);
                                        continue fileList;
                                    }
                                    parentScanPrefix += "../";
                                }
                            }
                            fileList.set(i, names[0]);
                        }
                    }
                    break;
                    case DATA_CHUNK: {
                        DataChunk dataChunk = ((PacketDataChunk) packet).getDataChunk();
                        dataChunks.add(dataChunk);
                        highLength = Math.max(highLength, dataChunk.offset + dataChunk.length);
                    }
                    break;
                    case FILE_NAME: {
                        filename = ((PacketFileName) packet).getFileName();
                    }
                    break;
                    case PARENT_DIRECTORY_DISTANCE: {
                        parentScan = ((PacketParentDirectoryDistance) packet).getDistance();
                    }
                    break;
                    default: {
                        throw new IOException("Invalid TransformFile - Unhandled packet type " + packet.getPacketType());
                    }
                }
            }
            if (dataFileOffset == -1L)
                throw new IOException("Invalid TransformFile - Never got END command");
            if (fileList == null)
                throw new IOException("Invalid TransformFile - Never got File list");
            this.filename = filename;
            files = fileList.toArray(new String[fileList.size()]);
            chunks = dataChunks.toArray(dataChunks.toArray(new DataChunk[dataChunks.size()]));
            this.dataFileOffset = dataFileOffset;
            this.length = highLength;
            this.rafs = new RandomAccessFile[files.length];
            this.rafs[0] = raf;
            success = true;
        } finally {
            if (!success) {
                try {
                    raf.close();
                } catch (Exception e) {
                }
            }
        }
    }

    LinkedList<DataChunk> getChunks(int fileIndex) {
        LinkedList<DataChunk> list = new LinkedList<>();
        for (DataChunk chunk : chunks) {
            if (chunk.file == fileIndex) {
                list.add(chunk);
            }
        }
        return list;
    }

    LinkedList<DataChunk> getChunks(long start, long end) {
        LinkedList<DataChunk> list = new LinkedList<>();
        int startPosition = getChunksLastPosition;
        while (startPosition > 0 && chunks[startPosition].transformedOffset > start) {
            startPosition -= 1;
        }
        for (int i = startPosition; i < chunks.length; i++) {
            DataChunk chunk = chunks[i];
            if (end > chunk.transformedOffset && start < chunk.transformedOffset + chunk.length) {
                list.add(chunk);
                startPosition = i;
            } else if (end < chunk.transformedOffset) {
                break;
            }
        }
        getChunksLastPosition = startPosition;
        return list;
    }

    public String getFilename() {
        return filename;
    }

    private RandomAccessFile getRandomAccessFile(int fileIndex) throws IOException {
        if (closed) throw new IOException("Already closed");
        RandomAccessFile raf = rafs[fileIndex];
        if (raf != null)
            return raf;
        return rafs[fileIndex] = new RandomAccessFile(new File(parentDirectory, files[fileIndex]), "r");
    }

    private LimitInputStream getStream(int fileIndex, long offset, long length) throws IOException {
        RandomAccessFile raf = getRandomAccessFile(fileIndex);
        if (fileIndex == 0)
            raf.seek(offset + dataFileOffset);
        else
            raf.seek(offset);
        return new LimitInputStream(new RafInputStream(raf, false), length, false);
    }

    private LimitInputStream getStream(int chunkNumber) throws IOException {
        DataChunk chunk = chunks[chunkNumber];
        return getStream(chunk.file, chunk.offset, chunk.length);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        for (RandomAccessFile raf : rafs) {
            try {
                raf.close();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public int read() throws IOException {
        int amount = read(one, 0, 1);
        if (amount == -1) return -1;
        return one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (currentIndex == -1) {
            currentIndex = 0;
            currentInput = getStream(0);
        }
        while (true) {
            int amount = currentInput.read(buffer, offset, length);
            if (amount < 0) {
                currentIndex += 1;
                if (currentIndex >= chunks.length) {
                    currentIndex -= 1;
                    return -1;
                }
                currentInput = getStream(currentIndex);
            } else {
                currentOffset += amount;
                return amount;
            }
        }
    }

    public long getFilePointer() {
        return currentOffset;
    }

    public long length() {
        return length;
    }

    public void seek(long offset) throws IOException {
        for (int i = 0; i < chunks.length; i++) {
            if (chunks[i].offset <= offset && chunks[i].offset + chunks[i].length < offset) {
                currentIndex = i;
                currentInput = getStream(currentIndex);
                currentInput.skip(offset - chunks[i].offset);
                currentOffset = offset;
                return;
            }
        }
        throw new IOException("Invalid offset " + offset);
    }

    public long skip(long n) throws IOException {
        long oldPointer = getFilePointer();
        long newPointer = Math.max(0L, Math.min(length(), oldPointer + n));
        seek(newPointer);
        return newPointer - oldPointer;
    }
}
