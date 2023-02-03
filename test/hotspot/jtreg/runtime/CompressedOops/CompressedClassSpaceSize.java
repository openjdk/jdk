/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.bits == 64 & vm.opt.final.UseCompressedOops == true
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassSpaceSize
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class CompressedClassSpaceSize {

    // Sizes beyond this will be rejected by hotspot arg parsing
    // (Lilliput: see Metaspace::max_class_space_size() for details)
    static final long max_class_space_size = 2013265920;

    // Below this size class space will be silently enlarged to a multiple of this size
    static final long min_class_space_size = 16777216;

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer output;

        // Invalid size of -1 should be handled correctly
        pb = ProcessTools.createJavaProcessBuilder("-XX:CompressedClassSpaceSize=-1",
                                                   "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Improperly specified VM option 'CompressedClassSpaceSize=-1'")
              .shouldHaveExitValue(1);

        ///////////

        // Going below the minimum size for class space (one root chunk size = atm 4M) should be transparently
        // handled by the hotspot, which should round up class space size and not report an error.
        pb = ProcessTools.createJavaProcessBuilder("-XX:CompressedClassSpaceSize=1m",
                                                   "-Xlog:gc+metaspace=trace",
                                                   "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldMatch("Compressed class space.*" + min_class_space_size)
              .shouldHaveExitValue(0);

        ///////////

        // Try 0. Same result expected.
        pb = ProcessTools.createJavaProcessBuilder("-XX:CompressedClassSpaceSize=0",
                "-Xlog:gc+metaspace=trace",
                "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldMatch("Compressed class space.*" + min_class_space_size)
                .shouldHaveExitValue(0);

        ///////////

        // Try max allowed size, which should be accepted
        pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                                                   "-XX:CompressedClassSpaceSize=" + max_class_space_size,
                                                   "-Xlog:gc+metaspace=trace",
                                                   "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldMatch("Compressed class space.*" + max_class_space_size)
              .shouldHaveExitValue(0);

        ///////////

        // Set max allowed size + 1, which should graciously fail
        pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                "-XX:CompressedClassSpaceSize=" + (max_class_space_size + 1),
                "-Xlog:gc+metaspace=trace",
                "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("CompressedClassSpaceSize " + (max_class_space_size + 1) + " too large (max: " + max_class_space_size)
              .shouldHaveExitValue(1);

    }
}
