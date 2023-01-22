package io.siggi.transformfile;

import io.siggi.transformfile.exception.TransformFileException;
import io.siggi.transformfile.io.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.siggi.transformfile.io.Util.copy;

public class TransformFileMain {
    public static void main(String[] args) throws IOException, TransformFileException {
        // compose matchSize output.xfr destination.dat origin.dat [origin2.dat] [origin3.dat]
        // transform file.xfr output.xfr
        // transform file.xfr (stdout)
        int cut = 0;
        for (; cut < args.length; cut++) {
            if (args[cut].startsWith("-D") && args[cut].contains("=")) {
                String data = args[cut].substring(2);
                int equalPos = data.indexOf("=");
                String key = data.substring(0, equalPos);
                String val = data.substring(equalPos + 1);
                System.getProperties().put(key, val);
            } else break;
        }
        if (cut > 0) {
            args = Arrays.copyOfRange(args, cut, args.length);
        }
        String command = null;
        if (args.length > 0) {
            command = args[0];
        } else {
            System.out.println("Create a new xfr:");
            System.out.println("    compose output.xfr destination.dat origin.dat [origin2.dat] [origin3.dat]");
            System.out.println("      Origins can be specified as intermediateDestination:xfrfile where the");
            System.out.println("      xfrfile produces the intermediate destinationfile. The output xfr will point");
            System.out.println("      to the original file.");
            System.out.println("    aliases: c");
            System.out.println("Get information on an xfr file:");
            System.out.println("    info file.xfr - Print info on the xfr");
            System.out.println("    biginfo file.xfr - Print info on the xfr and also chunk data");
            System.out.println("Transform a file into another:");
            System.out.println("    transform file.xfr output.dat - destination filename specified by you");
            System.out.println("    transform file.xfr - destination filename specified inside the xfr");
            System.out.println("      use \"-\" as destination to output to stdout");
            System.out.println("    aliases: t, tt");
            System.out.println("      if alias \"tt\" is used, output will be to stdout unless specified otherwise");
            System.out.println("      this could be useful if the xfr produces a tar file which can be piped to tar xf");
            System.out.println("Flip transformation:");
            System.out.println("    flip file.xfr dependencyIndex output.xfr [newSourceFileName.dat]");
            System.out.println("Optimize xfr:");
            System.out.println("    optimize input.xfr output.xfr");
            System.out.println();
            System.out.println("Options:");
            System.out.println("-Dmatchsize=[512] = minimum size to consider identical data a match");
            System.out.println("-Dlookahead=[-1] = maximum distance to look ahead when composing");
            System.out.println("-Dlookbehind=[-1] = maximum distance to look behind when composing");
            System.out.println("-Dskipxfrchunks=[0] = set to 1 to skip non redundant data");
            System.out.println("    - useful if you are going to flip then discard the original xfr file.");
            return;
        }
        switch (command) {
            case "compose":
            case "c": {
                int matchSize = Integer.parseInt(System.getProperty("matchsize", "512"));
                long lookahead = Util.parseSize(System.getProperty("lookahead", "0"));
                long lookbehind = Util.parseSize(System.getProperty("lookbehind", "-1"));
                boolean skipNonRedundantData = Integer.parseInt(System.getProperty("skipxfrchunks", "0")) != 0;
                String outputFile = new String(args[1]);
                String finalFile = new String(args[2]);
                List<String> originFiles = new ArrayList<>();
                for (int i = 3; i < args.length; i++) {
                    originFiles.add(args[i]);
                }
                TransformFileComposer.transform(lookahead, lookbehind, matchSize, !skipNonRedundantData, outputFile, finalFile, originFiles.toArray(new String[originFiles.size()]));
            }
            break;
            case "info":
            case "biginfo": {
                try (TransformFile file = new TransformFile(new File(args[1]))) {
                    file.loadChunks();
                    // starting at 1 is not a mistake
                    // index 0 refers to the xfr file itself
                    System.out.println("File name: " + file.getFilename());
                    System.out.println();
                    System.out.println("Dependencies:");
                    for (int i = 1; i < file.files.length; i++) {
                        System.out.println(i + " " + file.files[i]);
                    }
                    System.out.println();
                    System.out.println("Chunk count: " + file.chunks.length);
                    System.out.println();
                    long xfrChunks = 0L;
                    long nonXfrChunks = 0L;
                    long totalSizeInXfr = 0L;
                    long totalSizeOutsideXfr = 0L;
                    for (DataChunk chunk : file.chunks) {
                        if (chunk.file == 0) {
                            xfrChunks += 1L;
                            totalSizeInXfr += chunk.length;
                        } else {
                            nonXfrChunks += 1L;
                            totalSizeOutsideXfr += chunk.length;
                        }
                    }
                    System.out.println("XFR chunks: " + xfrChunks);
                    System.out.println("XFR chunk total size: " + Util.sizeToHumanReadable(totalSizeInXfr) + " (" + totalSizeInXfr + ")");
                    System.out.println();
                    System.out.println("Non-XFR chunks: " + nonXfrChunks);
                    System.out.println("Non-XFR chunk total size: " + Util.sizeToHumanReadable(totalSizeOutsideXfr) + " (" + totalSizeOutsideXfr + ")");
                    System.out.println();
                    if (command.equals("biginfo")) {
                        for (DataChunk chunk : file.chunks) {
                            System.out.println(chunk.file + " 0x" + Long.toString(chunk.offset + (chunk.file == 0 ? file.dataFileOffset : 0L), 16) + " 0x" + Long.toString(chunk.length, 16) + " -> 0x" + Long.toString(chunk.transformedOffset, 16));
                        }
                    }
                }
            }
            break;
            case "t":
            case "tt":
            case "transform": {
                File xfrFile = new File(args[1]);
                if (command.equals("tt") && args.length == 2) {
                    args = Arrays.copyOf(args, 3);
                    args[2] = "-";
                }
                if (args.length == 2) {
                    try (TransformFile in = new TransformFile(xfrFile)) {
                        String destinationFile = in.getFilename();
                        if (destinationFile == null) {
                            System.out.println("XFR does not specify a destination filename, you need to specify one.");
                            return;
                        }
                        try (FileOutputStream out = new FileOutputStream(new File(xfrFile.getParentFile(), destinationFile))) {
                            copy(in, out);
                        }
                    }
                } else if (args.length == 3) {
                    if (args[2].equals("-")) {
                        try (TransformFile in = new TransformFile(xfrFile)) {
                            copy(in, System.out);
                        }
                    } else {
                        try (FileOutputStream out = new FileOutputStream(args[2]);
                             TransformFile in = new TransformFile(xfrFile)) {
                            copy(in, out);
                        }
                    }
                }
            }
            break;
            case "flip":
            case "f": {
                try (TransformFile tf = new TransformFile(new File(args[1]))) {
                    try (FileOutputStream out = new FileOutputStream(args[3])) {
                        int fileIndex = Integer.parseInt(args[2]);
                        String newSourceName = args.length > 4 ? args[4] : tf.getFilename();
                        TransformFileFlipper.flip(tf, fileIndex, out, newSourceName, new File(tf.files[fileIndex]));
                    }
                }
            }
            break;
            case "optimize":
            case "compact": {
                try (TransformFile tf = new TransformFile(new File(args[1]))) {
                    try (FileOutputStream out = new FileOutputStream(args[2])) {
                        TransformFileOptimizer.optimize(tf, out);
                    }
                }
            }
            break;
        }
//		if (args.length > 0 && args[0].equalsIgnoreCase("compose")) {
//			TransformFileComposer.transform(512, new File("sample.xfr"), new File("sample.m4v"), new File("sample.mkv"));
//		} else {
//			try (TransformFile in = new TransformFile(new File("testfile.xfr"))) {
//				copy(in, System.out);
//			}
//		}
    }
}
