/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing that, faced with a given (possibly odd) mapping address of class space, the encoding
 *          scheme fits the address
 * @requires vm.bits == 64 & !vm.graal.enabled & vm.debug == true
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointersEncodingScheme
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

import java.io.IOException;

public class CompressedClassPointersEncodingScheme {

    private static void test(long forceAddress, boolean COH, long classSpaceSize, long expectedEncodingBase, int expectedEncodingShift) throws IOException {
        String forceAddressString = String.format("0x%016X", forceAddress).toLowerCase();
        String expectedEncodingBaseString = String.format("0x%016X", expectedEncodingBase).toLowerCase();
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xshare:off", // to make CompressedClassSpaceBaseAddress work
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:-UseCompressedOops", // keep VM from optimizing heap location
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:" + (COH ? "+" : "-") + "UseCompactObjectHeaders",
                "-XX:" + (COH ? "+" : "-") + "UseObjectMonitorTable",
                "-XX:CompressedClassSpaceBaseAddress=" + forceAddress,
                "-XX:CompressedClassSpaceSize=" + classSpaceSize,
                "-Xmx128m",
                "-Xlog:metaspace*",
                "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.reportDiagnosticSummary();

        // We ignore cases where we were not able to map at the force address
        if (output.contains("reserving class space failed")) {
            System.out.println("Skipping because we cannot force ccs to " + forceAddressString);
            return;
        }

        output.shouldHaveExitValue(0);
        output.shouldContain("Narrow klass base: " + expectedEncodingBaseString + ", Narrow klass shift: " + expectedEncodingShift);
    }

    private static void testFailure(String forceAddressString) throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xshare:off", // to make CompressedClassSpaceBaseAddress work
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:-UseCompactObjectHeaders",
                "-XX:CompressedClassSpaceBaseAddress=" + forceAddressString,
                "-Xmx128m",
                "-Xlog:metaspace*",
                "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.reportDiagnosticSummary();

        // We ignore cases where we were not able to map at the force address
        if (!output.contains("Successfully forced class space address to " + forceAddressString)) {
            throw new SkippedException("Skipping because we cannot force ccs to " + forceAddressString);
        }

        if (Platform.isAArch64()) {
            output.shouldHaveExitValue(1);
            output.shouldContain("Error occurred during initialization of VM");
            output.shouldContain("CompressedClassSpaceBaseAddress=" + forceAddressString +
                                 " given with shift 0, cannot be used to encode class pointers");
        } else {
            output.shouldHaveExitValue(0);
        }
    }

    final static long K = 1024;
    final static long M = K * 1024;
    final static long G = M * 1024;
    public static void main(String[] args) throws Exception {
        // Test ccs nestling right at the end of the 4G range
        // Expecting base=0, shift=0
        test(4 * G - 128 * M, false, 128 * M, 0, 0);

        // Test ccs nestling right at the end of the 32G range
        // Expecting:
        // - non-aarch64: base=0, shift=3
        // - aarch64: base to start of class range, shift 0
        if (Platform.isAArch64()) {
            // The best we can do on aarch64 is to be *near* the end of the 32g range, since a valid encoding base
            // on aarch64 must be 4G aligned, and the max. class space size is 3G.
            long forceAddress = 0x7_0000_0000L; // 28g, and also a valid EOR immediate
            test(forceAddress, false, 3 * G, forceAddress, 0);
        } else {
            test(32 * G - 128 * M, false, 128 * M, 0, 3);
        }

        // Test ccs starting *below* 4G, but extending upwards beyond 4G. All platforms except aarch64 should pick
        // zero based encoding. On aarch64, this test is excluded since the only valid mode would be XOR, but bit
        // pattern for base and bit pattern would overlap.
        if (!Platform.isAArch64()) {
            test(4 * G - 128 * M, false, 2 * 128 * M, 0, 3);
        }
        // add more...

        // Compact Object Header Mode:
        // On aarch64 and x64 we expect the VM to chose the smallest possible shift value needed to cover
        // the encoding range. We expect the encoding Base to start at the class space start - but to enforce that,
        // we choose a high address.
        if (Platform.isAArch64() || Platform.isX64() || Platform.isRISCV64()) {
            long forceAddress = 32 * G;

            long ccsSize = 128 * M;
            int expectedShift = 6;
            test(forceAddress, true, ccsSize, forceAddress, expectedShift);

            ccsSize = 512 * M;
            expectedShift = 8;
            test(forceAddress, true, ccsSize, forceAddress, expectedShift);

            ccsSize = G;
            expectedShift = 9;
            test(forceAddress, true, ccsSize, forceAddress, expectedShift);

            ccsSize = 3 * G;
            expectedShift = 10;
            test(forceAddress, true, ccsSize, forceAddress, expectedShift);
        }

        // Test failure for -XX:CompressedClassBaseAddress and -Xshare:off
        testFailure("0x0000040001000000");

    }
}
