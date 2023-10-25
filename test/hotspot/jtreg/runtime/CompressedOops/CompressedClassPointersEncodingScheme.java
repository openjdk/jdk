/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

    private static void test(long forceAddress, long classSpaceSize, long expectedEncodingBase, int expectedEncodingShift) throws IOException {
        String forceAddressString = String.format("0x%016X", forceAddress).toLowerCase();
        String expectedEncodingBaseString = String.format("0x%016X", expectedEncodingBase).toLowerCase();
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-Xshare:off", // to make CompressedClassSpaceBaseAddress work
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:-UseCompressedOops", // keep VM from optimizing heap location
                "-XX:CompressedClassSpaceBaseAddress=" + forceAddress,
                "-XX:CompressedClassSpaceSize=" + classSpaceSize,
                "-Xmx128m",
                "-Xlog:metaspace*",
                "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.reportDiagnosticSummary();

        // We ignore cases where we were not able to map at the force address
        if (output.contains("reserving class space failed")) {
            throw new SkippedException("Skipping because we cannot force ccs to " + forceAddressString);
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
        test(4 * G - 128 * M, 128 * M, 0, 0);

        // add more...

    }
}
