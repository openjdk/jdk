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
 * @summary Verify should_clean_previous_versions when run with JFR and CDS
 * @requires vm.jvmti
 * @requires vm.cds
 * @requires vm.hasJFR
 * @requires vm.opt.final.ClassUnloading
 * @requires vm.flagless
 * @library /test/lib
 * @run driver RedefineSharedClassJFR xshare-off
 * @run driver RedefineSharedClassJFR xshare-on
 */
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import jtreg.SkippedException;

public class RedefineSharedClassJFR {

    private static final String SHOULD_CLEAN_TRUE = "Class unloading: should_clean_previous_versions = true";
    private static final String SHOULD_CLEAN_FALSE = "Class unloading: should_clean_previous_versions = false";
    private static final String SCRATCH_CLASS_ADDED_SHARED = "scratch class added; class is shared";
    private static final String SCRATCH_CLASS_ADDED_ON_STACK = "scratch class added; one of its methods is on_stack.";

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
            // the output to verify it is correct given the command line.
            List<String> baseCommand = List.of(
                "-XX:StartFlightRecording",
                "-Xlog:redefine+class+iklass+add=trace,redefine+class+iklass+purge=trace",
                "RedefineSharedClassJFR");

            if (args[0].equals("xshare-off")) {
                // First case is with -Xshare:off. In this case no classes are shared
                // and we should be able to clean out the retransformed classes. There
                // is no guarantee that any classes will be in use, so just verify that
                // no classes are added due to being shared.
                List<String> offCommand = new ArrayList<>();
                offCommand.add("-Xshare:off");
                offCommand.addAll(baseCommand);
                ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(offCommand);
                new OutputAnalyzer(pb.start())
                    // We can't expect any of the transformed classes to be in use
                    // so the only thing we can verify is that no scratch classes
                    // are added because they are shared.
                    .shouldNotContain(SCRATCH_CLASS_ADDED_SHARED)
                    .shouldHaveExitValue(0);
                return;
            } else if (args[0].equals("xshare-on")) {
                // With -Xshare:on, the shared classes can never be cleaned out. Check the
                // logs to verify we don't try to clean when we know it is not needed.
                List<String> onCommand = new ArrayList<>();
                onCommand.add("-Xshare:on");
                onCommand.addAll(baseCommand);
                ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(onCommand);
                new OutputAnalyzer(pb.start())
                    .shouldContain(SHOULD_CLEAN_FALSE)
                    .shouldNotContain(SHOULD_CLEAN_TRUE)
                    .shouldContain(SCRATCH_CLASS_ADDED_SHARED)
                    // If the below line occurs, then should_clean_previous_versions will be
                    // true and the above shouldContain will trigger. This check is to
                    // show the intention that we don't expect any non-shared transformed
                    // classes to be in use.
                    .shouldNotContain(SCRATCH_CLASS_ADDED_ON_STACK)
                    .shouldHaveExitValue(0);
                return;
            }
        }

        // When run without any argument this class acts as test and we do a system GC
        // to trigger cleaning and get the output we want to check.
        System.gc();
    }
}
