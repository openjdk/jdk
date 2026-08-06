/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test Test logging deoptimization
 * @bug 8287010
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver DeoptLoggingTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;

public class DeoptLoggingTest {

    static String infoPattern = "[deoptimization]";

    static String[] debugPatterns = { "\\[deoptimization\\] DEOPT PACKING",
                                      "\\[deoptimization\\] DEOPT UNPACKING" };

    static String[] extraDebugPatterns = {
                        "\\[debug *\\]\\[deoptimization\\]  - Reconstructed local",
                        "\\[debug *\\]\\[deoptimization\\]  - Reconstructed expression",
                        "\\[debug\\]\\[deoptimization\\].*\\{method\\} \\{.*\\}" };


    static String[] tracePatterns = { "[trace][deoptimization] [BEFORE Deoptimization]",
                                      "[trace][deoptimization] [Describe stack]",
                                      "[trace][deoptimization] ~Stub::call_stub_stub (stub gen)",
                                      "[trace][deoptimization] [AFTER Deoptimization]",
                                      "[trace][deoptimization] ~return entry points" };


    static void test() throws Exception {
        // Test that nothing is printed on the info level.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:deoptimization=info",
                                                                             "-Xcomp", "DeoptLoggingTest", "test");
        OutputAnalyzer o = new OutputAnalyzer(pb.start());
        o.shouldNotContain(infoPattern).shouldHaveExitValue(0);

        // Test that packing, unpacking and nmethod are printed, including frames that are unpacked.
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:deoptimization=debug",
                                                              "-Xcomp", "DeoptLoggingTest", "test");
        o = new OutputAnalyzer(pb.start());
        for (var pattern : debugPatterns) {
           o.shouldMatch(pattern);
        }
        o.shouldHaveExitValue(0);

        // Test that nothing is printed to stdout everything is printed to the log file.
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:deoptimization=trace:rt.log",
                                                              "-Xcomp", "DeoptLoggingTest", "test");
        o = new OutputAnalyzer(pb.start());
        o.stdoutShouldBeEmpty().shouldHaveExitValue(0);

        // Read rt.log
        String log = Files.readString(Path.of("rt.log"));
        if (!log.contains("[deoptimization]")) {
            throw new RuntimeException("No deoptimization output in rt.log");
        }

        // Prints more debugging information in debug mode when -XX:+DeoptimizeALot is on. This replaced DebugDeoptimization.
        if (Platform.isDebugBuild()) {
            pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:deoptimization=trace",
                                                                  "-Xcomp",
                                                                  "-XX:+DeoptimizeALot", "DeoptLoggingTest", "test");
            o = new OutputAnalyzer(pb.start());
            for (var dbg : extraDebugPatterns) {
                o.shouldMatch(dbg); // There is some extra output in debug mode.
            }
            for (var pattern : tracePatterns) {
               o.shouldContain(pattern);
            }
            o.shouldHaveExitValue(0);

            // Test that nothing goes to stdout
            pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:deoptimization=trace:rt.log",
                                                                  "-Xcomp",
                                                                  "-XX:+DeoptimizeALot", "DeoptLoggingTest", "test");
            o = new OutputAnalyzer(pb.start());
            o.stdoutShouldBeEmpty().shouldHaveExitValue(0);
        }
    };

    public static void main(String... args) throws Exception {
        System.gc();
        if (args.length == 0) {
            test();
        }
    }
}
