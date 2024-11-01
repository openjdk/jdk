/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8241071
 * @summary The same JDK build should always generate the same archive file (no randomness).
 * @requires vm.cds & vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI DeterministicDump
 */

import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.Platform;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class DeterministicDump {

    static long HEADER_SIZE;      // Size of header in bytes
    static int HEADER_LEN = 106;  // Number of lines in CDS map file header
    static int LINE_OFFSET = 22;  // Offset from address to first word of data
    static int NUM_LINES = 5;     // Number of lines to be printed
    static int WORD_LEN = 16 + 1; // Length of word in map file

    public static void main(String[] args) throws Exception {
        doTest(false);

        if (Platform.is64bit()) {
            // There's no oop/klass compression on 32-bit.
            doTest(true);
        }
    }

    public static void doTest(boolean compressed) throws Exception {
        ArrayList<String> baseArgs = new ArrayList<>();

        // Use the same heap size as make/Images.gmk
        baseArgs.add("-Xmx128M");

        if (Platform.is64bit()) {
            // This option is available only on 64-bit.
            String sign = (compressed) ?  "+" : "-";
            baseArgs.add("-XX:" + sign + "UseCompressedOops");
        }

        String baseArchive = dump(baseArgs);
        File baseArchiveFile = new File(baseArchive + ".jsa");
        HEADER_SIZE = CDSArchiveUtils.fileHeaderSize(baseArchiveFile);

        // (1) Dump with the same args. Should produce the same archive.
        String baseArchive2 = dump(baseArgs);
        compare(baseArchive, baseArchive2);

        // (2) This will cause the archive to be relocated during dump time. We should
        //     still get the same bits. This simulates relocation that happens when
        //     Address Space Layout Randomization prevents the archive space to
        //     be mapped at the default location.
        String relocatedArchive = dump(baseArgs, "-XX:+UnlockDiagnosticVMOptions", "-XX:ArchiveRelocationMode=1");
        compare(baseArchive, relocatedArchive);
    }

    static int id = 0;
    static String dump(ArrayList<String> args, String... more) throws Exception {
        String logName = "SharedArchiveFile" + (id++);
        String archiveName = logName + ".jsa";
        String mapName = logName + ".map";
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-Xint") // Override any -Xmixed/-Xcomp flags from jtreg -vmoptions
            .addPrefix("-Xlog:cds=debug")
            .addPrefix("-Xlog:cds+map*=trace:file=" + mapName + ":none:filesize=0")
            .setArchiveName(archiveName)
            .addSuffix(args)
            .addSuffix(more);
        CDSTestUtils.createArchiveAndCheck(opts);

        return logName;
    }

    static void compare(String file0, String file1) throws Exception {
        byte[] buff0 = new byte[4096];
        byte[] buff1 = new byte[4096];
        try (FileInputStream in0 = new FileInputStream(file0 + ".jsa");
             FileInputStream in1 = new FileInputStream(file1 + ".jsa")) {
            int total = 0;
            while (true) {
                int n0 = read(in0, buff0);
                int n1 = read(in1, buff1);
                if (n0 != n1) {
                    throw new RuntimeException("File contents (file sizes?) are different after " + total + " bytes; n0 = "
                                               + n0 + ", n1 = " + n1);
                }
                if (n0 == 0) {
                    System.out.println("File contents are the same: " + total + " bytes");
                    break;
                }
                for (int i = 0; i < n0; i++) {
                    byte b0 = buff0[i];
                    byte b1 = buff1[i];
                    if (b0 != b1) {
                        if (total + i > HEADER_SIZE) {
                            print_diff(file0 + ".map", file1 + ".map", total + i);
                            throw new RuntimeException("File content different at byte #" + (total + i) + ", b0 = " + b0 + ", b1 = " + b1);
                        }
                    }
                }
                total += n0;
            }
        }
    }

    static int read(FileInputStream in, byte[] buff) throws IOException {
        int total = 0;
        while (total < buff.length) {
            int n = in.read(buff, total, buff.length - total);
            if (n <= 0) {
                return total;
            }
            total += n;
        }

        return total;
    }

    // Read the mapfile and print out the lines associated with the location
    static void print_diff(String mapName0, String mapName1, int location) throws Exception {
        FileReader f0 = new FileReader(mapName0);
        BufferedReader b0 = new BufferedReader(f0);

        FileReader f1 = new FileReader(mapName1);
        BufferedReader b1 = new BufferedReader(f1);

        int line_num = HEADER_LEN;
        int word = location / 8;
        int word_offset = word % 4; // Each line in the map file prints four words

        // Skip header text and go to first line
        for (int i = 0; i < HEADER_LEN; i++) {
            b0.readLine();
            b1.readLine();
        }

        String s0 = "";
        String s1 = "";
        int count = 0;

        // A line may contain 1-4 words so we iterate by word
        do {
            s0 = b0.readLine();
            s1 = b1.readLine();
            line_num++;
            // Skip lines with headers e.g.
            // [rw region          0x0000000800000000 - 0x00000008005a1f88   5906312 bytes]
            // or
            // 0x0000000800000b28: @@ TypeArrayU1       16
            if (!s0.contains(": @@") && !s0.contains("bytes]")) {
                int words = (s0.length() - LINE_OFFSET - 70) / 8;
                count += words;
            }
        } while (count < word);

        System.out.println("[First diff: map file #1 (" + mapName0 + ")]");
        String diff_f0 = print_diff_helper(b0, word_offset, s0);

        System.out.println("\n[First diff: map file #2 (" + mapName1 + ")]");
        String diff_f1 = print_diff_helper(b1, word_offset, s1);

        System.out.printf("\nByte #%d at line #%d word #%d:\n", location, line_num, word_offset);
        System.out.printf("%s: %s\n%s: %s\n", mapName0, diff_f0, mapName1, diff_f1);

        f0.close();
        f1.close();
    }

    static String print_diff_helper(BufferedReader b, int word_offset, String line) throws Exception {
        // Extract word from line
        int start = LINE_OFFSET + WORD_LEN * word_offset;
        int end = start + WORD_LEN;
        String diff = line.substring(start, end);

        // Print extra lines
        System.out.println(line);
        for (int i = 0; i < NUM_LINES; i++) {
            System.out.println(b.readLine());
        }
        return diff;
    }
}
