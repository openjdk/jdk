/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cds
 * @library /test/lib
 * @run driver DeterministicDump
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.FileInputStream;
import java.io.IOException;

public class DeterministicDump {
    public static void main(String[] args) throws Exception {
        for (int c = 0; c < 2; c++) { //  oop/klass compression
            String sign = (c == 0) ?  "+" : "-";
            String coop = "-XX:" + sign + "UseCompressedOops";
            String ckls = "-XX:" + sign + "UseCompressedClassPointers";

            if (!Platform.is64bit()) {
                coop = "-showversion"; // no-op
                ckls = "-showversion"; // no-op
            }

            for (int gc = 0; gc < 2; gc++) { // should we trigger GC during dump
                for (int i = 0; i < 2; i++) {
                    String metaspaceSize = "-showversion"; // no-op
                    if (gc == 1 && i == 1) {
                        // This will cause GC to happen after we've allocated 1MB of metaspace objects
                        // while processing the built-in SharedClassListFile.
                        metaspaceSize = "-XX:MetaspaceSize=1M";
                    }
                    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                                      coop, ckls, metaspaceSize,
                                      "-XX:SharedArchiveFile=SharedArchiveFile" + i + ".jsa",
                                      "-Xshare:dump", "-Xlog:cds=debug");
                    OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "SharedArchiveFile" + i);
                    CDSTestUtils.checkDump(out);
                }
                compare("SharedArchiveFile0.jsa", "SharedArchiveFile1.jsa");
            }
        }
    }

    static void compare(String file0, String file1) throws Exception {
        byte[] buff0 = new byte[4096];
        byte[] buff1 = new byte[4096];
        try (FileInputStream in0 = new FileInputStream(file0);
             FileInputStream in1 = new FileInputStream(file1)) {
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
}
