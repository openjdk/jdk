/*
 * Copyright (c) 2023, 2025, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=c1_crash
 * @requires vm.compiler1.enabled
 * @summary Checks that -XX:CompileCommand=MemLimit,...,xx~crash causes C1 to crash
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit c1 crash
 */

/*
 * @test id=c2_crash
 * @requires vm.compiler2.enabled
 * @summary Checks that -XX:CompileCommand=MemLimit,...,xx~crash causes C2 to crash
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit c2 crash
 */

/*
 * @test id=c1_stop
 * @requires vm.compiler1.enabled
 * @summary Checks that -XX:CompileCommand=MemLimit,...,xx causes C1 to bail out from the compilation
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit c1 stop
 */

/*
 * @test id=c2_stop
 * @requires vm.compiler2.enabled
 * @summary Checks that -XX:CompileCommand=MemLimit,...,xx causes C2 to bail out from the compilation
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit c2 stop
 */

package compiler.print;

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CompileCommandMemLimit {

    // Method we don't specify; default memlimit should apply
    final static String METHOD1 = "method1";
    // Method we explicitly limit to 4K limit
    final static String METHOD2 = "method2";
    // Method for which we explicitly disable a limit on the command line.
    final static String METHOD3 = "method3";

    enum TestMode { crash, stop };
    enum CompilerType { c1, c2 };

    public static void main(String[] args) throws Exception {
        CompilerType ctyp = CompilerType.valueOf(args[0]);
        TestMode mode = TestMode.valueOf(args[1]);

        List<String> options = new ArrayList<String>();
        options.add("-Xcomp");
        options.add("-XX:-Inline");
        options.add("-Xmx100m");
        options.add("-XX:-CreateCoredumpOnCrash");
        options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::*");

        // We want a final report
        options.add("-XX:CompileCommand=MemStat," + getTestMethod(METHOD2) + ",print");

        String suffix = mode == TestMode.crash ? "~crash" : "";

        // About the limit:
        //
        // In the debug JVM, for this test class, compilers will allocate (near the very end of the compilation)
        // 32MB of arena memory.
        //
        // C1 will allocate them in a single step from RA, leaked until end of compilation.
        //
        // C2 will allocate them in two steps: first 2MB inside phase "testPhase1" in a temporary arena
        // that will be gone by phase end. So, in the phase timeline these 2MB must show up as
        // "significant temporary peak".
        // In a second phase "testPhase2", we allocate 32MB from resource area, which is leaked until
        // the end of the compilation. This means that these 32MB will show up as permanent memory
        // increase in the per-phase-timeline.
        //
        // We then set the limit to 31MB (just shy of the 32MB we allocate), which should reliably trigger the mem limit.
        // The 32MB are deliberately chosen to be large, because this will harden the test against normal allocation fluctuations
        // (the methods are tiny, so compiling them should accrue normally only a few dozen KB).
        //
        // In the release JVM, we just use a very tiny memlimit that we are sure to hit every time.

        long limit = Platform.isDebugBuild() ? (1024 * 1024 * 31) : 4096;

        options.add("-XX:CompileCommand=MemLimit," + getTestMethod(METHOD2) + "," + limit + suffix);

        if (ctyp == CompilerType.c2) {
            options.add("-XX:-TieredCompilation");
        } else {
            options.add("-XX:TieredStopAtLevel=1");
        }

        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);

        oa.reportDiagnosticSummary();

        String method1regex = testMethodNameForRegex(getTestMethod(METHOD1));
        String method2regex = testMethodNameForRegex(getTestMethod(METHOD2));
        String method3regex = testMethodNameForRegex(getTestMethod(METHOD3));
        String limitHitRegex = ctyp + " \\(\\d+\\) compiler/print/CompileCommandMemLimit\\$TestMain::method2.*: Hit MemLimit - limit: " + limit + " now: \\d+";

        if (mode == TestMode.crash) {
            oa.shouldNotHaveExitValue(0);
            oa.shouldMatch("# *Internal Error.*");

            // method 2 should have hit its tiny limit
            oa.shouldMatch("# *fatal error: " + limitHitRegex);

            // none of the other ones should have hit a limit
            oa.shouldNotMatch(method1regex + ".*Hit MemLimit");
            oa.shouldNotMatch(method3regex + ".*Hit MemLimit");

            // Make sure we get a non-zero-sized replay file (JDK-8331314)
            oa.shouldContain("# Compiler replay data is saved as:");
            String replayfile = oa.firstMatch("# (\\S+replay_pid\\d+\\.log)", 1);
            if (replayfile == null) {
                throw new RuntimeException("Found no replay file in output");
            }
            File f = new File(replayfile);
            if (!f.exists()) {
                throw new RuntimeException("Replayfile " + replayfile + " not found");
            }
            if (f.length() == 0) {
                throw new RuntimeException("Replayfile " + replayfile + " has size 0");
            }
        } else {
            oa.shouldHaveExitValue(0);

            // method 2 should have hit its tiny limit
            oa.shouldMatch(limitHitRegex);

            // Compilation should have been aborted and marked as oom
            oa.shouldMatch(ctyp + " \\(\\d+\\) \\(oom\\) Arena usage " + method2regex + ".*\\d+.*");

            // neither of the other ones should have hit a limit
            oa.shouldNotMatch(method1regex + ".*Hit MemLimit");
            oa.shouldNotMatch(method3regex + ".*Hit MemLimit");
        }

        // In C2, analyze phase timeline and per-phase accumulation
        if (ctyp == CompilerType.c2) {
            oa.shouldMatch("--- Arena Usage by Arena Type and compilation phase, at arena usage peak of \\d+ ---");
            oa.shouldContain("--- Allocation timelime by phase ---");
            if (Platform.isDebugBuild()) {
                oa.shouldMatch(".*testPhase2 +33554432 +33554432 +0 +0 +0 +0 +0.*");
                oa.shouldMatch(" +>\\d+ +testPhase1.*significant temporary peak: \\d+ \\(\\+2098136\\)");
                oa.shouldMatch(" +>\\d+ +testPhase2 +\\d+ +\\(\\+33554432\\).*");
            }
        }

    }

    // Test class that is invoked by the sub process
    public static String getTestClass() {
        return TestMain.class.getName();
    }

    public static String getTestMethod(String method) {
        return getTestClass() + "::" + method;
    }

    private static String testMethodNameForRegex(String m) {
        return m.replace('.', '/')
                .replace("$", "\\$");
    }

    public static class TestMain {
        public static void main(String[] args) {
            method1();
            method2();
            method3();
        }

        static long method1() {
            return System.currentTimeMillis();
        }
        static void method2() {}
        static void method3() {}
    }
}

