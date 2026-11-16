/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm/timeout=480 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
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
import java.util.ArrayDeque;
import java.util.ArrayList;

public class DeterministicDump {
    static int NUM_CONTEXT_LINES = 4; // Number of context lines to be printed in diffs
    static int MAX_DIFF_LINES = 40; // Maximum number of diffs to print (to avoid excessive output)

    public static void main(String[] args) throws Exception {
        doTest(false);

        if (Platform.is64bit()) {
            // There's no oop/klass compression on 32-bit.
            doTest(true);
        }
    }

    public static void doTest(boolean compressed) throws Exception {
        ArrayList<String> baseArgs = new ArrayList<>();

        // Try to reduce indeterminism of GC heap sizing and evacuation.
        baseArgs.add("-Xmx128M");
        baseArgs.add("-Xms128M");
        baseArgs.add("-Xmn120M");

        if (Platform.is64bit()) {
            // This option is available only on 64-bit.
            String sign = (compressed) ?  "+" : "-";
            baseArgs.add("-XX:" + sign + "UseCompressedOops");
        }

        String baseArchive = dump(baseArgs);
        File baseArchiveFile = new File(baseArchive + ".jsa");

        // (1) Dump with the same args. Should produce the same archive.
        String baseArchive2 = dump(baseArgs);
        compare(baseArchive, baseArchive2, baseArchiveFile);

        // (2) This will cause the archive to be relocated during dump time. We should
        //     still get the same bits. This simulates relocation that happens when
        //     Address Space Layout Randomization prevents the archive space to
        //     be mapped at the default location.
        String relocatedArchive = dump(baseArgs, "-XX:+UnlockDiagnosticVMOptions", "-XX:ArchiveRelocationMode=1");
        compare(baseArchive, relocatedArchive, baseArchiveFile);
    }

    static int id = 0;
    static String dump(ArrayList<String> args, String... more) throws Exception {
        String logName = "SharedArchiveFile" + (id++);
        String archiveName = logName + ".jsa";
        String mapName = logName + ".map";
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-Xint") // Override any -Xmixed/-Xcomp flags from jtreg -vmoptions
            .addPrefix("-Xlog:cds=debug,gc=debug")
            .addPrefix("-Xlog:aot+map*=trace:file=" + mapName + ":none:filesize=0")
            .setArchiveName(archiveName)
            .addSuffix(args)
            .addSuffix(more);
        CDSTestUtils.createArchiveAndCheck(opts);

        return logName;
    }

    static void compare(String file0, String file1, File archiveFile) throws Exception {
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
                      print_diff(file0 + ".map", file1 + ".map");
                      throw new RuntimeException("File content different at byte #" + (total + i) + ", b0 = " + b0 + ", b1 = " + b1);
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

    // Print a text diff of the map files
    static void print_diff(String mapName0, String mapName1) throws Exception {
        try (FileReader f0 = new FileReader(mapName0);
             BufferedReader b0 = new BufferedReader(f0);
             FileReader f1 = new FileReader(mapName1);
             BufferedReader b1 = new BufferedReader(f1)) {

            ArrayDeque<String> context = new ArrayDeque<String>();

            int numLines = 0;
            int trailContextNeeded = 0;
            int printedDiffs = 0;
            int totalDiffs = 0;
            boolean needLineNum = false;

            // This is a simplified diff -- we just compare line X in mapName0 with line X in mapName1.
            // This catches the usual problems we've encountered in the past -- the two files
            // have identical structure, but some data values are different due to random values
            // in the gaps in C++ data structures.
            //
            // More drastic problem (e.g., the objects in the archives are sorted differently) will
            // show up as a large amount of random differences (limited by MAX_DIFF_LINES). A more
            // intellgent diff algorithm (such as /bin/diff) won't be useful anyway, so we will just
            // stick with this simplified version.
            while (true) {
                // Read one line from each file. Missing lines from either file is represented as "<eof>"
                String s0 = b0.readLine();
                String s1 = b1.readLine();
                if (s0 == null && s1 == null) {
                    break;
                }

                boolean isEof = false;
                if (s0 == null) {
                    s0 = "<eof>";
                    isEof = true;
                }
                if (s1 == null) {
                    s1 = "<eof>";
                    isEof = true;
                }
                numLines ++;

                if (!s0.equals(s1) || isEof) {
                    // Print the diff, prefixed by the context (if available)
                    if (printedDiffs < MAX_DIFF_LINES) {
                        if (needLineNum) {
                            System.out.println();
                            System.out.println((numLines - context.size()) + ":");
                            needLineNum = false;
                        }
                        for (String s : context) {
                            System.out.println("  " + s);
                        }
                        context.clear();
                        System.out.println("- " + s0);
                        System.out.println("+ " + s1);
                        trailContextNeeded = NUM_CONTEXT_LINES;
                        printedDiffs ++;
                    }
                    totalDiffs ++;
                } else {
                    if (trailContextNeeded > 0) {
                        // The lines are identical, but print it anyway to show the trailing context
                        System.out.println("  " + s0);
                        trailContextNeeded --;
                    } else {
                        while (context.size() >= NUM_CONTEXT_LINES) {
                            context.removeFirst();
                            // Print the line number on the next diff ONLY if we have
                            // omitted some identical lines.
                            needLineNum = true;
                        }
                        context.addLast(s0);
                    }
                }
            }
            if (printedDiffs < totalDiffs) {
                System.out.println("... " + (totalDiffs - printedDiffs) + " more lines of diffs omitted");
            }
        }
    }
}
