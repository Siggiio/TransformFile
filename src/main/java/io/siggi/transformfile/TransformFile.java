package io.siggi.transformfile;

import io.siggi.transformfile.exception.TransformFileException;
import io.siggi.transformfile.io.CountingInputStream;
import io.siggi.transformfile.io.LimitInputStream;
import io.siggi.transformfile.io.RandomAccessData;
import io.siggi.transformfile.io.RandomAccessDataFile;
import io.siggi.transformfile.io.RandomAccessInputStream;
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
    final RandomAccessData[] rads;
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

    public static TransformFile open(File file) throws IOException, TransformFileException {
        return new TransformFile(file, null);
    }

    public static TransformFile open(RandomAccessData data) throws IOException, TransformFileException {
        return new TransformFile(null, data);
    }

    private TransformFile(File file, RandomAccessData rad) throws IOException, TransformFileException {
        assert file != null || rad != null;
        boolean success = false;
        boolean shouldCloseRadOnFail = false;
        try {
            long highLength = 0L;
            String filename = null;
            this.file = file;
            this.parentDirectory = file == null ? null : file.getAbsoluteFile().getParentFile();
            String xfrName = file == null ? null : file.getName();
            long dataFileOffset = -1L;
            long startOfChunks = -1L;
            long indexOffset = -1L;
            List<String> fileList = null;
            List<DataChunk> dataChunks = new LinkedList<>();
            boolean noDataChunks = false;
            if (rad == null) {
                shouldCloseRadOnFail = true;
                rad = new RandomAccessDataFile(new RandomAccessFile(file, "r"));
            }
            RandomAccessInputStream radIn = new RandomAccessInputStream(rad, false);
            InputStream bufferedIn = new BufferedInputStream(radIn, 65536);
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
                            fileList.set(i, names[0]);
                            if (xfrName == null || parentDirectory == null) continue;
                            for (int j = 0; j < names.length; j++) {
                                String n = names[j];
                                if (n.length() >= 2 && n.charAt(0) == '\0') {
                                    int removeFromEnd = (int) n.charAt(1);
                                    n = xfrName.substring(0, xfrName.length() - removeFromEnd) + n.substring(2);
                                }
                                if (j == 0) fileList.set(i, n);
                                String parentScanPrefix = "";
                                for (int k = 0; k <= parentScan; k++) {
                                    String path = parentScanPrefix + n;
                                    File f = new File(parentDirectory, path);
                                    if (f.exists()) {
                                        fileList.set(i, path);
                                        continue fileList;
                                    }
                                    parentScanPrefix += "../";
                                }
                            }
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
            this.rads = new RandomAccessData[files.length];
            this.rads[0] = rad;
            if (noDataChunks) {
                packetReader = new InputStreamPacketReader(new BufferedInputStream(new RandomAccessInputStream(rads[0], startOfChunks, false), 65536), packetIO);
            } else {
                packetReader = new MemoryDataChunkPacketReader(chunks, 0);
            }
            success = true;
        } finally {
            if (!success && shouldCloseRadOnFail) {
                try {
                    rad.close();
                } catch (Exception e) {
                }
            }
        }
    }

    void loadChunks() {
        if (chunks != null) return;
        List<DataChunk> chunkList = new ArrayList<>();
        try (RandomAccessInputStream in = new RandomAccessInputStream(rads[0], startOfChunks, false)) {
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

    private RandomAccessData getRandomAccessData(int fileIndex) throws IOException {
        if (closed) throw new IOException("Already closed");
        RandomAccessData rad = rads[fileIndex];
        if (rad != null)
            return rad;
        return rads[fileIndex] = new RandomAccessDataFile(new RandomAccessFile(new File(parentDirectory, files[fileIndex]), "r"));
    }

    @Override
    public void close() throws IOException {
        closed = true;
        for (RandomAccessData rad : rads) {
            try {
                rad.close();
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
        RandomAccessData rad = getRandomAccessData(fileIndex);
        if (fileIndex == 0)
            rad.seek(offset + dataFileOffset);
        else
            rad.seek(offset);
        return new LimitInputStream(new RandomAccessInputStream(rad, false), chunk.length, false);
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
                    if (rads[fileIndex] == null && rads[fileIndex].isCloseable()) {
                        try {
                            rads[fileIndex].close();
                        } catch (IOException e) {
                        }
                        rads[fileIndex] = null;
                    }
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
                closeAllRads();
                currentInput = getStream(chunk);
                currentInput.skip(offset - chunk.transformedOffset);
                currentOffset = offset;
                return;
            }
        }
        throw new IOException("Invalid offset " + offset);
    }

    private void closeAllRads() {
        for (int i = 1; i < rads.length; i++) { // starting from 1, never close 0 except when we're explicitly closed.
            if (rads[i] == null) continue;
            if (rads[i].isCloseable()) {
                try {
                    rads[i].close();
                    rads[i] = null;
                } catch (Exception e) {
                } finally {
                    rads[i] = null;
                }
            }
        }
    }

    private PacketReader createPacketReader(long offset) throws IOException {
        long offsetInIndex = (offset / 131072L) * 8L;
        rads[0].seek(indexOffset + offsetInIndex);
        RandomAccessInputStream in = new RandomAccessInputStream(rads[0], false);
        long jumpTo = startOfChunks + Util.readLong(in);
        return new InputStreamPacketReader(new BufferedInputStream(new RandomAccessInputStream(rads[0], jumpTo, false), 65536), packetIO);
    }

    public long skip(long n) throws IOException {
        long oldPointer = getFilePointer();
        long newPointer = Math.max(0L, Math.min(length(), oldPointer + n));
        seek(newPointer);
        return newPointer - oldPointer;
    }
}
