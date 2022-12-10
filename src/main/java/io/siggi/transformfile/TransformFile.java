package io.siggi.transformfile;

import io.siggi.transformfile.exception.TransformFileException;
import io.siggi.transformfile.io.CountingInputStream;
import io.siggi.transformfile.io.LimitInputStream;
import io.siggi.transformfile.io.RafInputStream;
import io.siggi.transformfile.io.Util;

import io.siggi.transformfile.packet.InputStreamPacketReader;
import io.siggi.transformfile.packet.MemoryDataChunkPacketReader;
import io.siggi.transformfile.packet.PacketIO;
import io.siggi.transformfile.packet.PacketReader;
import io.siggi.transformfile.packet.types.Packet;
import io.siggi.transformfile.packet.types.PacketCloseFile;
import io.siggi.transformfile.packet.types.PacketDataChunk;
import io.siggi.transformfile.packet.types.PacketFileList;
import io.siggi.transformfile.packet.types.PacketFileName;
import io.siggi.transformfile.packet.types.PacketOffsets;
import io.siggi.transformfile.packet.types.PacketParentDirectoryDistance;
import io.siggi.transformfile.packet.types.PacketType;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TransformFile extends InputStream {
    final String[] files;
    DataChunk[] chunks;
    final long dataFileOffset;
    final long startOfChunks;
    final long indexOffset;
    final RandomAccessFile[] rafs;
    private final String filename;
    private final File file;
    private final File parentDirectory;
    private final long length;
    private final byte[] one = new byte[1];
    private boolean closed = false;
    private PacketReader packetReader = null;
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
            long startOfChunks = -1L;
            long indexOffset = -1L;
            List<String> fileList = null;
            List<DataChunk> dataChunks = new LinkedList<>();
            boolean noDataChunks = false;
            raf = new RandomAccessFile(file, "r");
            RafInputStream rafIn = new RafInputStream(raf, false);
            InputStream bufferedIn = new BufferedInputStream(rafIn, 65536);
            CountingInputStream in = new CountingInputStream(bufferedIn);
            int version = (int) Util.readVarInt(in);
            packetIO = PacketIO.get(version);
            int parentScan = 0;
            long startOfPacket = 0L;
            long endOfPacket = in.getCount();
            Packet packet = null;
            readLoop:
            while (true) {
                packet = packetIO.read(in);
                startOfPacket = endOfPacket;
                endOfPacket = in.getCount();
                switch (packet.getPacketType()) {
                    case END:
                        dataFileOffset = endOfPacket;
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
                        if (startOfChunks == -1L) {
                            startOfChunks = startOfPacket;
                        }
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
                    case CLOSE_FILE:
                        break;
                    case OFFSETS: {
                        startOfChunks = endOfPacket;
                        PacketOffsets offsets = ((PacketOffsets) packet);
                        dataFileOffset = endOfPacket + offsets.getNonRedundantOffset();
                        indexOffset = endOfPacket + offsets.getAddressIndexOffset();
                        highLength = Math.max(highLength, offsets.getResultSize());
                        if (indexOffset >= 0L) {
                            noDataChunks = true;
                            break readLoop;
                        }
                    }
                    break;
                    default: {
                        throw new IOException("Invalid TransformFile - Unhandled packet type " + packet.getPacketType());
                    }
                }
            }
            if (dataFileOffset == -1L)
                throw new IOException("Invalid TransformFile - Never got END or OFFSETS command");
            if (fileList == null)
                throw new IOException("Invalid TransformFile - Never got File list");
            this.filename = filename;
            files = fileList.toArray(new String[fileList.size()]);
            chunks = noDataChunks ? null : dataChunks.toArray(dataChunks.toArray(new DataChunk[dataChunks.size()]));
            this.dataFileOffset = dataFileOffset;
            this.length = highLength;
            this.startOfChunks = startOfChunks;
            this.indexOffset = indexOffset;
            this.rafs = new RandomAccessFile[files.length];
            this.rafs[0] = raf;
            if (noDataChunks) {
                packetReader = new InputStreamPacketReader(new BufferedInputStream(new RafInputStream(rafs[0], startOfChunks, false), 65536), packetIO);
            } else {
                packetReader = new MemoryDataChunkPacketReader(chunks, 0);
            }
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

    void loadChunks() {
        if (chunks != null) return;
        List<DataChunk> chunkList = new ArrayList<>();
        try (RafInputStream in = new RafInputStream(rafs[0], startOfChunks, false)) {
            PacketReader reader = new InputStreamPacketReader(in, packetIO);
            Packet packet;
            while ((packet = reader.readPacket()) != null) {
                if (packet.getPacketType() != PacketType.DATA_CHUNK) continue;
                PacketDataChunk packetDataChunk = ((PacketDataChunk) packet);
                chunkList.add(packetDataChunk.getDataChunk());
            }
        } catch (Exception e) {
        }
        chunks = chunkList.toArray(new DataChunk[chunkList.size()]);
    }

    LinkedList<DataChunk> getChunks(int fileIndex) {
        if (chunks == null) loadChunks();
        LinkedList<DataChunk> list = new LinkedList<>();
        for (DataChunk chunk : chunks) {
            if (chunk.file == fileIndex) {
                list.add(chunk);
            }
        }
        return list;
    }

    LinkedList<DataChunk> getChunks(long start, long end) {
        if (chunks == null) loadChunks();
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
        if (currentInput == null) {
            currentInput = nextInput();
            if (currentInput == null) {
                return -1;
            }
        }
        while (true) {
            int amount = currentInput.read(buffer, offset, length);
            if (amount < 0) {
                currentInput = nextInput();
                if (currentInput == null) {
                    return -1;
                }
            } else {
                currentOffset += amount;
                return amount;
            }
        }
    }

    private InputStream getStream(DataChunk chunk) throws IOException {
        int fileIndex = chunk.file;
        long offset = chunk.offset;
        RandomAccessFile raf = getRandomAccessFile(fileIndex);
        if (fileIndex == 0)
            raf.seek(offset + dataFileOffset);
        else
            raf.seek(offset);
        return new LimitInputStream(new RafInputStream(raf, false), chunk.length, false);
    }

    private InputStream nextInput() throws IOException {
        Packet packet;
        while ((packet = packetReader.readPacket()) != null) {
            switch (packet.getPacketType()) {
                case END:
                    return null;
                case CLOSE_FILE: {
                    int fileIndex = ((PacketCloseFile) packet).getFileIndex();
                    if (fileIndex < 1) break;
                    try {
                        rafs[fileIndex].close();
                    } catch (IOException e) {
                    }
                    rafs[fileIndex] = null;
                }
                break;
                case DATA_CHUNK: {
                    return getStream(((PacketDataChunk) packet).getDataChunk());
                }
            }
        }
        return null;
    }

    public long getFilePointer() {
        return currentOffset;
    }

    public long length() {
        return length;
    }

    public void seek(long offset) throws IOException {
        if (chunks == null) {
            packetReader = createPacketReader(offset);
        } else {
            packetReader = new MemoryDataChunkPacketReader(chunks, 0);
        }
        Packet packet;
        while ((packet = packetReader.readPacket()) != null) {
            switch (packet.getPacketType()) {
                case END:
                    currentInput = null;
                    return;
                case DATA_CHUNK:
                    break;
                default:
                    continue;
            }
            DataChunk chunk = ((PacketDataChunk) packet).getDataChunk();
            if (chunk.transformedOffset <= offset && chunk.transformedOffset + chunk.length < offset) {
                currentInput = getStream(chunk);
                currentInput.skip(offset - chunk.transformedOffset);
                currentOffset = offset;
                return;
            }
        }
        throw new IOException("Invalid offset " + offset);
    }

    private PacketReader createPacketReader(long offset) throws IOException {
        long offsetInIndex = (offset / 131072L) * 8L;
        rafs[0].seek(indexOffset + offsetInIndex);
        RafInputStream in = new RafInputStream(rafs[0], false);
        long jumpTo = startOfChunks + Util.readLong(in);
        return new InputStreamPacketReader(new BufferedInputStream(new RafInputStream(rafs[0], jumpTo, false), 65536), packetIO);
    }

    public long skip(long n) throws IOException {
        long oldPointer = getFilePointer();
        long newPointer = Math.max(0L, Math.min(length(), oldPointer + n));
        seek(newPointer);
        return newPointer - oldPointer;
    }
}
