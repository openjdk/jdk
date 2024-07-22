/*
 * Copyright (c) 2023, 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Checks that -XX:CompileCommand=MemLimit,...,crash causes C1 to crash
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit crash false
 */

/*
 * @test id=c2_crash
 * @requires vm.compiler2.enabled
 * @summary Checks that -XX:CompileCommand=MemLimit,...,crash causes C2 to crash
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit crash true
 */

/*
 * @test id=c1_stop
 * @requires vm.compiler1.enabled
 * @summary Checks that -XX:CompileCommand=MemLimit,...,stop causes C1 to stop
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit stop false
 */

/*
 * @test id=c2_stop
 * @requires vm.compiler2.enabled
 * @summary Checks that -XX:CompileCommand=MemLimit,...,stop causes C2 to stop
 * @library /test/lib
 * @run driver compiler.print.CompileCommandMemLimit stop true
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

    static boolean c2;
    static boolean test_crash;

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "crash" : test_crash = true; break;
            case "stop" : test_crash = false; break;
            default: throw new RuntimeException("invalid argument");
        }
        c2 = Boolean.parseBoolean(args[1]);

        List<String> options = new ArrayList<String>();
        options.add("-Xcomp");
        options.add("-XX:-Inline");
        options.add("-Xmx100m");
        options.add("-XX:-CreateCoredumpOnCrash");
        options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::*");

        // We want a final report
        options.add("-XX:CompileCommand=MemStat,*.*,print");

        // We limit method 2 to a very small limit that is guaranteed to trigger
        options.add("-XX:CompileCommand=MemLimit," + getTestMethod(METHOD2) + ",4k" + (test_crash ? "~crash" : ""));

        // We disable any limit set on method 3
        options.add("-XX:CompileCommand=MemLimit," + getTestMethod(METHOD3) + ",0");

        if (c2) {
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
        String ct = c2 ? "c2" : "c1";

        if (test_crash) {
            oa.shouldNotHaveExitValue(0);
            oa.shouldMatch("# *Internal Error.*");

            // method 2 should have hit its tiny limit
            oa.shouldMatch("# *fatal error: " + ct + " *" + method2regex + ".*: Hit MemLimit .*limit: 4096.*");

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

            // In debug builds we have an inbuilt MemLimit. It is very high, so we don't expect it to fire in this test.
            // But it will still show up in the final report.
            String implicitMemoryLimit = Platform.isDebugBuild() ? "1024M" : "-";

            // With C2, we print number of nodes, with C1 we don't
            String numberNodesRegex = c2 ? "\\d+" : "-";

            // method 2 should have hit its tiny limit
            oa.shouldMatch(ct + " " + method2regex + ".*: Hit MemLimit \\(limit: 4096 now: \\d+\\)");

            // neither of the other ones should have hit a limit
            oa.shouldNotMatch(method1regex + ".*Hit MemLimit");
            oa.shouldNotMatch(method3regex + ".*Hit MemLimit");

            // Final report:
            // Method 1 should show up as "ok" and with the default limit, e.g.
            // total     NA        RA        result  #nodes  limit   time    type  #rc thread              method
            // 32728     0         32728     ok     -       1024M   0.045   c1    1   0x000000011b019c10  compiler/print/CompileCommandMemLimit$TestMain::method1(()J)
            oa.shouldMatch("\\d+ +\\d+ +\\d+ +ok +" + numberNodesRegex + " +" + implicitMemoryLimit + " +.* +" + method1regex);

            // Method 2 should show up as "oom" and with its tiny limit, e.g.
            // total     NA        RA        result  #nodes  limit   time    type  #rc thread              method
            // 32728     0         32728     oom     -       4096B   0.045   c1    1   0x000000011b019c10  compiler/print/CompileCommandMemLimit$TestMain::method1(()J)
            oa.shouldMatch("\\d+ +\\d+ +\\d+ +oom +" + numberNodesRegex + " +4096B +.* +" + method2regex);

            // Method 3 should show up as "ok", and with no limit, even in debug builds, e.g.
            // total     NA        RA        result  #nodes  limit   time    type  #rc thread              method
            // 32728     0         32728     ok     -       -        0.045   c1    1   0x000000011b019c10  compiler/print/CompileCommandMemLimit$TestMain::method1(()J)
            oa.shouldMatch("\\d+ +\\d+ +\\d+ +ok +" + numberNodesRegex + " +- +.* +" + method3regex);
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

