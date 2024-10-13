/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=invalid
 * @bug 8022865
 * @summary Tests for the -XX:CompressedClassSpaceSize command line option
 * @requires vm.bits == 64 & vm.opt.final.UseCompressedOops == true
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc java.management
 * @run driver CompressedClassSpaceSize invalid
 */

/*
 * @test id=valid_small
 * @bug 8022865
 * @summary Tests for the -XX:CompressedClassSpaceSize command line option
 * @requires vm.bits == 64 & vm.opt.final.UseCompressedOops == true
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc java.management
 * @run driver CompressedClassSpaceSize valid_small
 */

/*
 * @test id=valid_large_nocds
 * @bug 8022865
 * @summary Tests for the -XX:CompressedClassSpaceSize command line option
 * @requires vm.bits == 64 & vm.opt.final.UseCompressedOops == true
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc java.management
 * @run driver CompressedClassSpaceSize valid_large_nocds
 */

/*
 * @test id=valid_large_cds
 * @bug 8022865
 * @summary Tests for the -XX:CompressedClassSpaceSize command line option
 * @requires vm.bits == 64 & vm.opt.final.UseCompressedOops == true & vm.cds
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc java.management
 * @run driver CompressedClassSpaceSize valid_large_cds
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class CompressedClassSpaceSize {

    final static long MB = 1024 * 1024;

    final static long minAllowedClassSpaceSize = MB;
    final static long minRealClassSpaceSize = 16 * MB;
    final static long maxClassSpaceSize = 4096 * MB;

    // For the valid_large_cds sub test: we need to have a notion of what archive size to
    // maximally expect, with a generous fudge factor to avoid having to tweak this test
    // ofent. Note: today's default archives are around 16-20 MB.
    final static long maxExpectedArchiveSize = 512 * MB;

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer output;

        switch (args[0]) {
            case "invalid": {
                // < Minimum size
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=0",
                        "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldContain("outside the allowed range")
                        .shouldHaveExitValue(1);

                // Invalid size of -1 should be handled correctly
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=-1",
                        "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldContain("Improperly specified VM option 'CompressedClassSpaceSize=-1'")
                        .shouldHaveExitValue(1);

                // > Maximum size
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=" + maxClassSpaceSize + 1,
                        "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldContain("outside the allowed range")
                        .shouldHaveExitValue(1);

                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:-UseCompressedClassPointers",
                        "-XX:CompressedClassSpaceSize=" + minAllowedClassSpaceSize,
                        "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldContain("Setting CompressedClassSpaceSize has no effect when compressed class pointers are not used")
                        .shouldHaveExitValue(0);
            }
            break;
            case "valid_small": {
                // Make sure the minimum size is set correctly and printed
                // (Note: ccs size are rounded up to the next larger root chunk boundary (16m).
                // Note that this is **reserved** size and does not affect rss.
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                        "-XX:CompressedClassSpaceSize=" + minAllowedClassSpaceSize,
                        "-Xlog:gc+metaspace",
                        "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldMatch("Compressed class space.*" + minRealClassSpaceSize)
                        .shouldHaveExitValue(0);
            }
            break;
            case "valid_large_nocds": {
                // Without CDS, we should get 4G
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=" + maxClassSpaceSize,
                        "-Xshare:off", "-Xlog:metaspace*", "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldMatch("Compressed class space.*" + maxClassSpaceSize)
                        .shouldHaveExitValue(0);
            }
            break;
            case "valid_large_cds": {
                // Create archive
                pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                        "-XX:SharedArchiveFile=./abc.jsa", "-Xshare:dump", "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldHaveExitValue(0);

                // With CDS, class space should fill whatever the CDS archive leaves us (modulo alignment)
                pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=" + maxClassSpaceSize,
                        "-XX:SharedArchiveFile=./abc.jsa", "-Xshare:on", "-Xlog:metaspace*", "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldHaveExitValue(0);
                long reducedSize = Long.parseLong(
                        output.firstMatch("reducing class space size from " + maxClassSpaceSize + " to (\\d+)", 1));
                if (reducedSize < (maxClassSpaceSize - maxExpectedArchiveSize)) {
                    output.reportDiagnosticSummary();
                    throw new RuntimeException("Class space size too small?");
                }
                output.shouldMatch("Compressed class space.*" + reducedSize);
            }
            break;
            default:
                throw new RuntimeException("invalid sub test " + args[0]);
        }
    }
}
