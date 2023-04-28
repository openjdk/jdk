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
 * @bug 8306929
 * @summary Verify clean_previous_versions when run with JFR and CDS
 * @requires vm.jvmti
 * @requires vm.cds
 * @requires vm.hasJFR
 * @requires vm.opt.final.ClassUnloading
 * @requires vm.flagless
 * @library /test/lib
 * @run driver RedefineSharedClassJFR xshare-off
 * @run driver RedefineSharedClassJFR xshare-on
 */
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import jtreg.SkippedException;

public class RedefineSharedClassJFR {

    public static void main(String[] args) throws Exception {
        // Skip test if default archive is supported.
        if (!Platform.isDefaultCDSArchiveSupported()) {
            throw new SkippedException("Supported platform");
        }

        // Test is run with JFR which will transform a number of classes. Depending
        // on if the test is run with or without CDS the output will be different,
        // due to the fact that shared classes can never be cleaned out after retranform.
        if (args.length > 0) {
            // When run with an argument the class is used as driver and should parse
            // the output.
            if (args[0].equals("xshare-off")) {
                // First case is with -Xshare:off. In this case no classes are shared
                // and we should be able to clean out the retransformed classes. Verify
                // that the cleaning is done when the GC is triggered.
                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:StartFlightRecording",
                    "-Xshare:off",
                    "-Xlog:redefine+class+iklass+add=trace,redefine+class+iklass+purge=trace",
                    "RedefineSharedClassJFR");
                new OutputAnalyzer(pb.start())
                    .shouldContain("Class unloading: clean_previous_versions = true")
                    .shouldNotContain("Class unloading: clean_previous_versions = false")
                    // We expect at least one of the transformed classes to be in use, if
                    // not the above check that clean_previous should be true will also
                    // fail. This check is to show what is expected.
                    .shouldContain("scratch class added; one of its methods is on_stack.")
                    .shouldNotContain("scratch class added; class is shared")
                    .shouldHaveExitValue(0);
                return;
            } else if (args[0].equals("xshare-on")) {
                // With -Xshare:on, the shared classes can never be cleaned out. Check the
                // logs to verify we don't try to clean when we know it is not needed.
                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:StartFlightRecording",
                    "-Xshare:on",
                    "-Xlog:redefine+class+iklass+add=trace,redefine+class+iklass+purge=trace",
                    "RedefineSharedClassJFR");
                new OutputAnalyzer(pb.start())
                    .shouldContain("Class unloading: clean_previous_versions = false")
                    .shouldNotContain("Class unloading: clean_previous_versions = true")
                    .shouldContain("scratch class added; class is shared")
                    // If the below line occurs, then clean_previous_versions will be
                    // true and the above shouldContain will trigger. This check is to
                    // show the intention that we don't expect any non-shared transformed
                    // classes to be in use.
                    .shouldNotContain("scratch class added; one of its methods is on_stack.")
                    .shouldHaveExitValue(0);
                return;
            }
        }

        // When run without any argument this class acts as test and we do a system GC
        // to trigger cleaning and get the output we want to check.
        System.gc();
    }
}
