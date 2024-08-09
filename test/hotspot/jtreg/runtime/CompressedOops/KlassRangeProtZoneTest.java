/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=with-cds
 * @summary Test that the start of the narrow Klass range is protected with a no-access zone
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver KlassRangeProtZoneTest true
 */

/*
 * @test id=no-cds
 * @summary Test that the start of the narrow Klass range is protected with a no-access zone
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver KlassRangeProtZoneTest false
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;

public class KlassRangeProtZoneTest {

    private static void do_test_no_cds() throws IOException {
        // In the non-CDS case, we fix the position of the class space to one that inhibits
        // zero-based encoding, (only then a protection zone is established.
        long tryAddresses[] = {
                0x8_0000_0000L, 0x10_0000_0000L, 0x80_0000_0000L
        };

        boolean reservedOk = false;

        for (long base : tryAddresses) {
            String hexBase = "0x" + String.format("%1$016x", base);
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                    "-Xshare:off",
                    "-Xmx128m",
                    "-XX:CompressedClassSpaceBaseAddress=" + hexBase,
                    "-Xlog:metaspace*",
                    "-version");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            reservedOk = output.contains("Successfully forced class space address to " + hexBase);
            if (reservedOk) {
                // Example Output:
                // [0.015s][info][metaspace] Successfully forced class space address to 0x0000008000000000
                // [0.015s][info][metaspace] Protected no-access zone (class space): [0x0000008000000000 - 0x0000008000004000), (16384 bytes)
                // [0.015s][info][gc,metaspace] CDS archive(s) not mapped
                // [0.015s][info][gc,metaspace] Compressed class space mapped at: 0x0000008000000000-0x0000008040000000, reserved size: 1073741824
                // [0.015s][info][gc,metaspace] Narrow klass base: 0x0000008000000000, Narrow klass shift: 0, Narrow klass range: 0x40000000
                output.shouldContain("Protected no-access zone (class space): [" + hexBase);
                output.shouldContain("Narrow klass base: " + hexBase);
                // We are done.
                break;
            }
        }
    }

    private static void do_test_cds() throws IOException {
        // In the non-CDS case, CompressedClassSpaceBaseAddress won't work, nor would it be necessary, since
        // with CDS we never do zero-based encoding. We must, however, monitor which address (randomly chosen)
        // CDS attaches the archive to.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xshare:on",
                "-Xmx128m",
                "-Xlog:metaspace*",
                "-Xlog:cds",
                "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        // Example Output:
        // [0.021s][info   ][gc,metaspace] CDS archive(s) mapped at: [0x000007f000000000-0x000007f000d78000-0x000007f000d78000), size 14123008, SharedBaseAddress: 0x000007f000000000, ArchiveRelocationMode: 1.
        // [0.021s][info   ][gc,metaspace] Compressed class space mapped at: 0x000007f001000000-0x000007f041000000, reserved size: 1073741824
        // [0.021s][info   ][gc,metaspace] Narrow klass base: 0x000007f000000000, Narrow klass shift: 0, Narrow klass range: 0x100000000
        String hexBase = output.firstMatch("CDS archive\\(s\\) mapped at: \\[(0x[\\w]+)", 1);
        output.shouldContain("Protected no-access zone (CDS): [" + hexBase);
        output.shouldContain("Narrow klass base: " + hexBase);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Expected 1 argument");
        }
        boolean with_cds = Boolean.parseBoolean(args[0]);
        if (with_cds) {
            do_test_cds();
        } else {
            do_test_no_cds();
        }
    }
}
