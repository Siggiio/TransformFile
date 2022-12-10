package io.siggi.transformfile.io;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Util {

    public static long readVarInt(InputStream in) throws IOException {
        long out = 0L;
        int val;
        while (true) {
            val = in.read();
            if (val == -1) {
                throw new IOException("End of stream");
            }
            out <<= 7L;
            out |= val & 0x7fL;
            if ((val & 0x80) == 0) {
                break;
            }
            out += 1L;
        }
        return out;
    }

    public static int writeVarInt(OutputStream out, long value) throws IOException {
        byte[] val = new byte[10];
        int bytes = 1;
        while (true) {
            val[val.length - bytes] = (byte) (((byte) (value & 0x7f)) | (bytes == 1 ? ((byte) 0x0) : ((byte) 0x80)));
            if (value >= 128L || value < 0L) { /*Negative number is less than 0, but still larger than 128 when unsigned*/

                value -= 0x80L;
                bytes += 1;
                value >>>= 7L;
            } else {
                break;
            }
        }
        if (out != null) {
            out.write(val, val.length - bytes, bytes);
        }
        return bytes;
    }

    public static int varIntSize(long value) {
        try {
            return writeVarInt(null, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int read(InputStream in) throws IOException {
        int value = in.read();
        if (value < 0) throw new IOException("End of stream");
        return value;
    }

    public static long readLong(InputStream in) throws IOException {
        return (((long) read(in)) << 56)
            | (((long) read(in)) << 48)
            | (((long) read(in)) << 40)
            | (((long) read(in)) << 32)
            | (((long) read(in)) << 24)
            | (((long) read(in)) << 16)
            | (((long) read(in)) << 8)
            | ((long) read(in));
    }

    public static void writeLong(OutputStream out, long value) throws IOException {
        out.write(((int) (value >> 56)) & 0xff);
        out.write(((int) (value >> 48)) & 0xff);
        out.write(((int) (value >> 40)) & 0xff);
        out.write(((int) (value >> 32)) & 0xff);
        out.write(((int) (value >> 24)) & 0xff);
        out.write(((int) (value >> 16)) & 0xff);
        out.write(((int) (value >> 8)) & 0xff);
        out.write(((int) value) & 0xff);
    }

    public static byte[] readBytes(InputStream in, int count) throws IOException {
        byte[] b = new byte[count];
        int c = 0;
        while (c < count) {
            int d = in.read(b, c, count - c);
            if (d == -1) {
                throw new IOException("End of stream");
            }
            c += d;
        }
        return b;
    }

    public static String readString(InputStream in, int maxSize) throws IOException {
        int size = (int) readVarInt(in);
        if (size > maxSize) {
            throw new IOException("Oversized string, length = " + size + ", max length = " + maxSize);
        }
        return new String(readBytes(in, size), StandardCharsets.UTF_8);
    }

    public static void writeString(OutputStream out, String string) throws IOException {
        byte[] data = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, data.length);
        out.write(data);
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[4096];
        int c;
        while ((c = in.read(b, 0, b.length)) != -1) {
            out.write(b, 0, c);
        }
    }

    public static long parseSize(String size) {
        size = size.replace(" ", "").toLowerCase();
        if (size.endsWith("b")) {
            size = size.substring(0, size.length() - 1);
        }
        if (size.endsWith("k")) {
            long sizeL = Long.parseLong(size.substring(0, size.length() - 1));
            return sizeL * 1024;
        }
        if (size.endsWith("m")) {
            long sizeL = Long.parseLong(size.substring(0, size.length() - 1));
            return sizeL * 1024 * 1024;
        }
        if (size.endsWith("g")) {
            long sizeL = Long.parseLong(size.substring(0, size.length() - 1));
            return sizeL * 1024 * 1024 * 1024;
        }
        if (size.endsWith("t")) {
            long sizeL = Long.parseLong(size.substring(0, size.length() - 1));
            return sizeL * 1024 * 1024 * 1024 * 1024;
        }
        return Long.parseLong(size);
    }

    public static String sizeToHumanReadable(long size) {
        if (size >= 1024L * 1024L * 1024L * 1024L) {
            double sizeF = (double) (size / 1024L / 1024L / 1024L);
            sizeF /= 1024.0;
            return round(sizeF, 2) + " TB";
        } else if (size >= 1024L * 1024L * 1024L) {
            double sizeF = (double) (size / 1024L / 1024L);
            sizeF /= 1024.0;
            return round(sizeF, 2) + " GB";
        } else if (size >= 1024L * 1024L) {
            double sizeF = (double) (size / 1024L);
            sizeF /= 1024.0;
            return round(sizeF, 2) + " MB";
        } else if (size >= 1024L) {
            double sizeF = (double) (size);
            sizeF /= 1024.0;
            return round(sizeF, 2) + " kB";
        } else {
            return size + " B";
        }
    }

    private static double round(double value, int decimalPlaces) {
        double pow = Math.pow(10, (double) decimalPlaces);
        return Math.round(value * pow) / pow;
    }
}
