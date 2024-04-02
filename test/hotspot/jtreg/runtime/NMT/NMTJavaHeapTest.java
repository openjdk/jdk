/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8306841
 * @summary Sanity check Java Heap size values
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NMTJavaHeapTest
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

public class NMTJavaHeapTest {
    public static void main(String args[]) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
              "-XX:+UnlockDiagnosticVMOptions",
              "-XX:+PrintNMTStatistics",
              "-XX:NativeMemoryTracking=summary",
              "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Java Heap (reserved=786432KB, committed=49152KB)
        String pattern = ".*Java Heap \\(reserved=.*, committed=(.*)\\).*";
        String committed = output.firstMatch(pattern, 1);
        Asserts.assertNotNull(committed, "Couldn't find pattern '" + pattern
                + "': in output '" + output.getOutput() + "'");

        long committedBytes = committedStringToBytes(committed);

        // Must be more than zero
        Asserts.assertGT(committedBytes, 0L);

        // Compare against the max heap size
        long maxBytes = Runtime.getRuntime().maxMemory();
        Asserts.assertLTE(committedBytes, maxBytes);
    }

    private static long K = 1024;
    private static long M = K * 1024;
    private static long G = M * 1024;

    private static long committedStringToBytes(String committed) {
        long multiplier = 1;
        if (committed.endsWith("GB")) {
            multiplier = G;
            committed = committed.replace("GB", "");
        } else if (committed.endsWith("MB")) {
            multiplier = M;
            committed = committed.replace("MB", "");
        } else if (committed.endsWith("KB")) {
            multiplier = K;
            committed = committed.replace("KB", "");
        }

        return Long.parseLong(committed) * multiplier;
    }
}
