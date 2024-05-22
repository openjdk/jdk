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
 * @test
 * @bug 8022865
 * @summary Tests for the -XX:CompressedClassSpaceSize command line option
 * @requires vm.bits == 64 & vm.opt.final.UseCompressedOops == true & vm.cds
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassSpaceSize
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class CompressedClassSpaceSize {

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer output;
        // Minimum size is 1MB
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


        // Maximum size is 4GB
        long max_class_space_size = 4L * 1024 * 1024 * 1024;
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=" + max_class_space_size + 1,
                                                              "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("outside the allowed range")
              .shouldHaveExitValue(1);

        // Without CDS, we should get 4G
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=" + max_class_space_size,
                "-Xshare:off", "-Xlog:metaspace*", "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldMatch("Compressed class space mapped at: 0x\\w+-0x\\w+, reserved size: " + max_class_space_size)
                .shouldHaveExitValue(0);

        // With CDS, class space should fill whatever the CDS archive leaves us (modulo alignment)
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:CompressedClassSpaceSize=" + max_class_space_size,
                "-Xshare:on", "-Xlog:metaspace*", "-version");
        {
            output = new OutputAnalyzer(pb.start());
            long real_size = Long.parseLong(
                    output.firstMatch("reducing class space size from " + max_class_space_size + " to (\\d+)", 1));
            if (real_size < max_class_space_size * 0.75) {
                output.reportDiagnosticSummary();
                throw new RuntimeException("Class space size too small?");
            }
            output.shouldMatch("Compressed class space mapped at: 0x\\w+-0x\\w+, reserved size: " + real_size)
                    .shouldHaveExitValue(0);
        }

        // Make sure the minimum size is set correctly and printed
        // (Note: ccs size are rounded up to the next larger root chunk boundary (16m).
        // Note that this is **reserved** size and does not affect rss.
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                                                              "-XX:CompressedClassSpaceSize=1m",
                                                              "-Xlog:gc+metaspace",
                                                              "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldMatch("Compressed class space.*16777216")
              .shouldHaveExitValue(0);


        // Make sure the maximum size is set correctly and printed
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                                                              "-XX:CompressedClassSpaceSize=3g",
                                                              "-Xlog:gc+metaspace",
                                                              "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldMatch("Compressed class space.*3221225472")
              .shouldHaveExitValue(0);


        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:-UseCompressedClassPointers",
                                                              "-XX:CompressedClassSpaceSize=1m",
                                                              "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Setting CompressedClassSpaceSize has no effect when compressed class pointers are not used")
              .shouldHaveExitValue(0);
    }
}
