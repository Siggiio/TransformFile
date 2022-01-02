package io.siggi.transformfile;

import io.siggi.transformfile.io.LimitInputStream;
import io.siggi.transformfile.io.RafInputStream;
import io.siggi.transformfile.io.Util;

import java.io.*;
import java.util.*;

import static io.siggi.transformfile.io.Util.*;

public class TransformFileComposer implements Closeable {
    private static final int bufferSize = 16384;
    private final long lookahead;
    private final long lookbehind;
    private final int matchSize;
    private final File transformerFile;
    private final File finalFile;
    private final File[] originFiles;
    private final RandomAccessFile finalRaf;
    private final RandomAccessFile[] originRafs;
    private final TransformFile[] translateFiles;
    private final List<UseRange>[] usageRanges;
    private final long[] highByte;

    private final OutputStream out;
    private final long fileLength;
    private final byte[] searchBuffer;
    private final byte[] expansionBytesA;
    private final byte[] expansionBytesB;
    private long filePointer = 0L;
    private long destXfrPointer = 0L;
    private List<SearchResult> resultsFromDestination = new LinkedList<>();
    private byte[] bufferA = new byte[bufferSize];
    private byte[] bufferB = new byte[bufferSize];
    private byte[] tmpBuffer = new byte[bufferA.length + bufferB.length];

    private TransformFileComposer(long lookahead, long lookbehind, int matchSize, String transformerFile, String finalFile, String... originFiles) throws IOException {
        this.lookahead = lookahead;
        this.lookbehind = lookbehind;
        this.matchSize = matchSize;
        this.transformerFile = new File(transformerFile);
        this.finalFile = new File(finalFile);
        this.originFiles = new File[originFiles.length];
        this.translateFiles = new TransformFile[originFiles.length];
        boolean success = false;
        try {
            for (int i = 0; i < originFiles.length; i++) {
                String filename = originFiles[i];
                String xfr = null;
                int colonSymbol = filename.indexOf(":");
                if (colonSymbol >= 0) {
                    xfr = filename.substring(colonSymbol + 1);
                    filename = filename.substring(0, colonSymbol);
                }
                if (xfr != null) {
                    translateFiles[i] = new TransformFile(new File(xfr));
                }
                this.originFiles[i] = new File(filename);
            }
            this.finalRaf = new RandomAccessFile(this.finalFile, "r");
            this.fileLength = this.finalRaf.length();
            this.originRafs = new RandomAccessFile[this.originFiles.length];
            this.usageRanges = new List[this.originFiles.length];
            for (int i = 0; i < this.originRafs.length; i++) {
                this.originRafs[i] = new RandomAccessFile(this.originFiles[i], "r");
                this.usageRanges[i] = new LinkedList<>();
            }
            this.highByte = new long[this.originFiles.length];
            this.out = new FileOutputStream(transformerFile);
            success = true;
        } finally {
            if (!success)
                close();
        }
        searchBuffer = new byte[matchSize];
        expansionBytesA = new byte[matchSize];
        expansionBytesB = new byte[matchSize];
    }

    public static void transform(long lookahead, long lookbehind, int matchSize, String transformerFile, String finalFile, String... originFiles) throws IOException {
        long now = System.currentTimeMillis();
        long lastUpdate = now;
        try (TransformFileComposer composer = new TransformFileComposer(lookahead, lookbehind, matchSize, transformerFile, finalFile, originFiles)) {
            composer.writeHeader();
            while (true) {
                if (!composer.step()) break;
                now = System.currentTimeMillis();
                if (now - lastUpdate > 1000L) {
                    lastUpdate = now;
                    double percentComplete = ((double) (composer.filePointer * 1000L / composer.fileLength)) / 10.0;
                    System.out.println("Progress: " + Util.sizeToHumanReadable(composer.filePointer) + " / " + Util.sizeToHumanReadable(composer.fileLength) + " (" + percentComplete + "%)");
                    System.out.println("Filepointer: " + Util.sizeToHumanReadable(composer.filePointer) + " (" + composer.filePointer + ")");
                    for (int i = 0; i < composer.originFiles.length; i++) {
                        System.out.println("Filepointer " + (i + 1) + ": " + Util.sizeToHumanReadable(composer.highByte[i]) + " (" + composer.highByte[i] + ")");
                    }
                    System.out.println("Dest-only: " + Util.sizeToHumanReadable(composer.destXfrPointer) + " (" + composer.destXfrPointer + ")");
                    System.out.println();
                }
            }
            System.out.println("Copying non-redundant data");
            composer.finish();
        }
    }

    @Override
    public void close() {
        if (finalRaf != null) {
            try {
                finalRaf.close();
            } catch (Exception e) {
            }
        }
        if (originRafs != null) {
            for (RandomAccessFile raf : originRafs) {
                try {
                    raf.close();
                } catch (Exception e) {
                }
            }
        }
        if (translateFiles != null) {
            for (TransformFile f : translateFiles) {
                try {
                    f.close();
                } catch (Exception e) {
                }
            }
        }
        if (out != null) {
            try {
                out.close();
            } catch (Exception e) {
            }
        }
    }

    private void writeHeader() throws IOException {
        writeVarInt(out, 0); // version

        writeVarInt(out, 3); // destination file name
        writeString(out, finalFile.getName());

        writeVarInt(out, 1); // file list
        writeVarInt(out, originFiles.length);
        for (int i = 0; i < originFiles.length; i++) {
            File originFile = originFiles[i];
            TransformFile translate = translateFiles[i];
            if (translate != null) {
                writeString(out, translate.files[1]); // the first file is at index 1
            } else {
                writeString(out, originFile.toString().replace("\\", "/"));
            }
        }
    }

    private void addResult(SearchResult result) throws IOException {
        markDataUsed(result);
        TransformFile translate = result.fileIndex == 0 ? null : translateFiles[result.fileIndex - 1];
        if (translate != null) {
            List<SearchResult> newResults = new LinkedList<>();
            List<DataChunk> chunks = translate.getChunks(result.offset, result.offset + result.length);
            long destPoint = result.destinationOffset;
            long leftover = result.length;
            for (DataChunk chunk : chunks) {
                long veryOriginOffset = chunk.offset;
                long chunkOffset = chunk.transformedOffset;
                long chunkLength = chunk.length;
                long chunkEnd = chunkOffset + chunkLength; // End point in the XFR'd file
                if (chunkOffset < result.offset) {
                    long difference = result.offset - chunkOffset;
                    chunkLength -= difference;
                    veryOriginOffset += difference;
                    chunkOffset = result.offset;
                }
                if (chunkEnd > result.offset + result.length) {
                    chunkEnd = result.offset + result.length;
                    chunkLength = chunkEnd - chunkOffset;
                }
                SearchResult newResult;
                if (chunk.file == 0) {
                    newResult = new SearchResult(0, chunkOffset, chunkLength, destPoint);
                    long seekOffset = destPoint;
                    long maxRead = chunkLength;
                    newResult.overrideInput = () -> {
                        finalRaf.seek(seekOffset);
                        return new LimitInputStream(new RafInputStream(finalRaf, false), maxRead, false);
                    };
                } else {
                    newResult = new SearchResult(result.fileIndex, veryOriginOffset, chunkLength, destPoint);
                }
                destPoint += chunkLength;
                leftover -= chunkLength;
                if (newResult.fileIndex == 0) resultsFromDestination.add(newResult);
                writeResult(newResult);
                if (leftover <= 0L) break;
            }
        } else {
            if (result.fileIndex == 0) resultsFromDestination.add(result);
            writeResult(result);
        }
    }

    private void writeResult(SearchResult result) throws IOException {
        writeVarInt(out, 2);
        writeVarInt(out, result.destinationOffset);
        writeVarInt(out, result.fileIndex);
        if (result.fileIndex == 0) {
            writeVarInt(out, destXfrPointer);
            destXfrPointer += result.length;
        } else {
            writeVarInt(out, result.offset);
        }
        writeVarInt(out, result.length);
    }

    private void finish() throws IOException {
        writeVarInt(out, 0); // end of commands
        for (SearchResult result : resultsFromDestination) {
            finalRaf.seek(result.offset);
            try (InputStream in = result.overrideInput != null
                ? result.overrideInput.get()
                : new LimitInputStream(new RafInputStream(finalRaf, false), result.length, false)) {
                copy(in, out);
            }
        }
    }

    private boolean step() throws IOException {
        long lastWritten = filePointer;
        try {
            SearchResult result;
            do {
                result = searchStep();
            } while (result == null);
            SearchResult expanded = expand(lastWritten, result);
            if (expanded.destinationOffset > lastWritten) {
                SearchResult prefix = new SearchResult(0, lastWritten, expanded.destinationOffset - lastWritten, lastWritten);
                addResult(prefix);
            }
            addResult(expanded);
            filePointer = expanded.destinationOffset + expanded.length;
            return true;
        } catch (EOFException e) {
            if (lastWritten != filePointer) {
                SearchResult result = new SearchResult(0, lastWritten, filePointer - lastWritten, lastWritten);
                addResult(result);
            }
            return false;
        }
    }

    private SearchResult expand(long lowestExpansionPoint, SearchResult result) throws IOException {
        if (result.fileIndex == 0)
            return result;
        RandomAccessFile rafA = finalRaf;
        RandomAccessFile rafB = originRafs[result.fileIndex - 1];
        InputStream inA = new RafInputStream(rafA, false);
        InputStream inB = new RafInputStream(rafB, false);
        int lowExpansion = 0;
        lowExpansion:
        {
            long maximumExpansionL = result.destinationOffset - lowestExpansionPoint;
            int maximumExpansion = Math.max(0, (int) Math.min(result.offset, Math.min(maximumExpansionL, matchSize)));
            if (maximumExpansion == 0) {
                break lowExpansion;
            }
            long destinationOffset = result.destinationOffset - maximumExpansion;
            long originOffset = result.offset - maximumExpansion;
            rafA.seek(destinationOffset);
            rafB.seek(originOffset);
            readFully(inA, expansionBytesA);
            readFully(inB, expansionBytesB);
            for (int i = maximumExpansion - 1; i >= 0; i--) {
                if (expansionBytesA[i] == expansionBytesB[i])
                    lowExpansion += 1;
                else
                    break;
            }
        }
        long highExpansion = 0L;
        {
            rafA.seek(result.destinationOffset + result.length);
            rafB.seek(result.offset + result.length);
            checkLoop:
            while (true) {
                int readA = readFully(inA, bufferA);
                int readB = readFully(inB, bufferB);
                int amountToCheck = Math.min(readA, readB);
                for (int i = 0; i < amountToCheck; i++) {
                    if (bufferA[i] != bufferB[i]) {
                        highExpansion += i;
                        break checkLoop;
                    }
                }
                highExpansion += amountToCheck;
                if (amountToCheck < bufferSize) break;
            }
        }
        if (lowExpansion == 0 && highExpansion == 0L)
            return result;
        return new SearchResult(result.fileIndex, result.offset - lowExpansion, result.length + lowExpansion + highExpansion, result.destinationOffset - lowExpansion);
    }

    private SearchResult searchStep() throws IOException {
        finalRaf.seek(filePointer);
        long leftoverBytes = finalRaf.length() - filePointer;
        if (leftoverBytes < matchSize) {
            if (leftoverBytes == 0L)
                throw new EOFException();
            filePointer += leftoverBytes;
            return null;
        }
        byte[] buffer = searchBuffer;
        readFully(finalRaf, buffer);
        SearchResult result = search(buffer, filePointer);
        filePointer += matchSize;
        return result;
    }

    private SearchResult search(byte[] buffer, long filePointer) throws IOException {
        for (int i = 0; i < originFiles.length; i++) {
            SearchResult result = search(buffer, i, filePointer);
            if (result != null) return result;
        }
        return null;
    }

    private SearchResult search(byte[] buffer, int fileIndex, long filePointer) throws IOException {
        RandomAccessFile raf = originRafs[fileIndex];
        RafInputStream in = new RafInputStream(raf, false);
        long currentPosition = 0L;
        long filesize = raf.length();
        if (lookahead > 0L) {
            filesize = Math.min(filesize, highByte[fileIndex] + lookahead);
        }
        if (lookbehind >= 0L) {
            currentPosition = Math.max(0L, highByte[fileIndex] - lookbehind);
            currentPosition -= currentPosition % 16384;
        }
        raf.seek(currentPosition);
        boolean first = true;
        boolean hitEnd = false;
        int haystackSize;
        while (currentPosition < filesize && !hitEnd) {
            long skipOverUsedData = skipOverUsedData(fileIndex, currentPosition);
            if (skipOverUsedData != -1) {
                first = true;
                currentPosition = skipOverUsedData;
                raf.seek(currentPosition);
            }
            if (first) {
                first = false;
                haystackSize = readFully(in, bufferA) + readFully(in, bufferB);
            } else {
                currentPosition += bufferA.length;
                byte[] tmp = bufferA;
                bufferA = bufferB;
                bufferB = tmp;
                haystackSize = bufferA.length + readFully(in, bufferB);
            }
            if (haystackSize < bufferA.length + bufferB.length)
                hitEnd = true;
            int searchPosition = search(buffer, bufferA, bufferB, tmpBuffer, haystackSize);
            if (searchPosition != -1) {
                return new SearchResult(fileIndex + 1, currentPosition + searchPosition, buffer.length, filePointer);
            }
        }
        return null;
    }

    private int search(byte[] needle, byte[] haystackPart1, byte[] haystackPart2, byte[] tmpBuffer, int haystackSize) {
        int lastSearch = Math.min(haystackSize - needle.length + 1, haystackPart1.length);
        System.arraycopy(haystackPart1, 0, tmpBuffer, 0, haystackPart1.length);
        System.arraycopy(haystackPart2, 0, tmpBuffer, haystackPart1.length, haystackPart2.length);
        outerLoop:
        for (int i = 0; i < lastSearch; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (tmpBuffer[i + j] != needle[j]) continue outerLoop;
            }
            return i;
        }
        return -1;
    }

    private long skipOverUsedData(int fileIndex, long currentPosition) {
        //if (true) return -1L;
        if (lookbehind >= 0L) return -1L;
        List<UseRange> ranges = usageRanges[fileIndex];
        for (UseRange range : ranges) {
            if (range.contains(currentPosition)) {
                return range.end;
            }
        }
        return -1L;
    }

    private void markDataUsed(SearchResult result) {
        //if (true) return;
        if (result.fileIndex == 0) return;
        int index = result.fileIndex - 1;
        highByte[index] = Math.max(highByte[index], result.offset + result.length);
        if (lookbehind >= 0L) return;
        List<UseRange> ranges = usageRanges[index];
        UseRange range = new UseRange(result.offset, result.offset + result.length);
        for (Iterator<UseRange> it = ranges.iterator(); it.hasNext(); ) {
            UseRange r = it.next();
            if (range.overlap(r) > -(matchSize * 8)) {
                it.remove();
                range = range.combine(r);
            }
        }
        ranges.add(range);
    }

    private int readFully(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        int c;
        while (read < buffer.length && (c = in.read(buffer, read, buffer.length - read)) != -1) {
            read += c;
        }
        return read;
    }

    private int readFully(RandomAccessFile raf, byte[] buffer) throws IOException {
        return readFully(new RafInputStream(raf, false), buffer);
    }

    @FunctionalInterface
    private interface InputProvider {
        InputStream get() throws IOException;
    }

    private class SearchResult {
        private final int fileIndex;
        private final long offset;
        private final long length;
        private final long destinationOffset;
        private InputProvider overrideInput;

        private SearchResult(int fileIndex, long offset, long length, long destinationOffset) {
            this.fileIndex = fileIndex;
            this.offset = offset;
            this.length = length;
            this.destinationOffset = destinationOffset;
            if (length < 0L) {
                throw new IllegalArgumentException("negative length");
            }
        }
    }

    private class UseRange {
        private final long start;
        private final long end;

        private UseRange(long start, long end) {
            this.start = start;
            this.end = end;
        }

        private boolean contains(long value) {
            return (value >= start && value < end);
        }

        private long overlap(UseRange other) {
            UseRange low, high;
            if (other.start < start) {
                low = other;
                high = this;
            } else {
                high = this;
                low = other;
            }
            return low.end - high.start;
        }

        private boolean overlaps(UseRange other) {
            return overlap(other) > 0;
        }

        private UseRange combine(UseRange other) {
            return new UseRange(
                Math.min(this.start, other.start),
                Math.max(this.end, other.end)
            );
        }
    }
}
