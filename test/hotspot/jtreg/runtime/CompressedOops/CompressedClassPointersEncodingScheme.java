/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.bits == 64 & vm.debug == true
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
                "-Xmx64m",
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

    final static long K = 1024;
    final static long M = K * 1024;
    final static long G = M * 1024;
    public static void main(String[] args) throws Exception {
        // Test ccs nestling right at the end of the 4G range
        // Expecting base=0, shift=0
        test(4 * G - 128 * M, false, 128 * M, 0, 0);

        // aarch64 does not do extended zero based encoding (shift>0)
        boolean expectExtendedZeroBasedEncoding = !Platform.isAArch64();

        // Test ccs nestling right at the end of the 32G range.
        // Expect all platforms but aarch64 to do shift-extended zero-based encoding;
        long forceAddress = 32 * G - 128 * M;
        test(forceAddress, false, 128 * M,
                expectExtendedZeroBasedEncoding ? 0 : forceAddress, // expected base
                expectExtendedZeroBasedEncoding ? 3 : 0             // expected shift
                );

        // Test ccs starting *below* 4G, but extending upwards beyond 4G.
        // Expect all platforms but aarch64 to do shift-extended zero-based encoding; aarch64 does not do that but
        // drops right to non-zero-based with shift = 0
        forceAddress = 4 * G - 128 * M;
        test(forceAddress, false, 2 * 128 * M,
                expectExtendedZeroBasedEncoding ? 0 : forceAddress, // expected base
                expectExtendedZeroBasedEncoding ? 3 : 0             // expected shift
        );

        // Compact Object Header Mode:
        // We expect the VM to chose the smallest possible shift value needed to cover the encoding range.
        // We expect the encoding Base to start at the class space start - but to enforce that,
        // we choose unsuited to even shift-extended zero-based mode.
        forceAddress = 32 * G;

        test(forceAddress, true, 128 * M, forceAddress, 6);
        test(forceAddress, true, 256 * M, forceAddress, 7);
        test(forceAddress, true, 512 * M, forceAddress, 8);
        test(forceAddress, true, G, forceAddress, 9);
        test(forceAddress, true, 3 * G, forceAddress, 10);

        // Test a "crooked" base address:
        // - just aligned enough to pass metaspace reserve alignment test of 16MB.
        // - not encodable on aarch64 as logical immediate
        // - sufficiently complex enough to need multiple moves on risc platforms to materialize as immediate
        // - small enough to not cause test errors on small devices (e.g. arm64 39bit address space)
        // - large enough to not end up with zero-based encoding
        forceAddress = 0x0000000d55000000L;
        test(forceAddress, true, 32 * M, forceAddress, 6);
        test(forceAddress, false, 32 * M, forceAddress, 0);

    }
}
