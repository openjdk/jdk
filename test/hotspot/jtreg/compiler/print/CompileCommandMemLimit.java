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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.List;

public class CompileCommandMemLimit {

    final static String METHOD1 = "method1";
    final static String METHOD2 = "method2";

    static boolean c2;
    static boolean test_crash;

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "crash" : test_crash = true; break;
            case "stop" : test_crash = false; break;
            default: throw new RuntimeException("invalid argument");
        }
        c2 = Boolean.parseBoolean(args[1]);
        test(METHOD1, METHOD2);
        test(METHOD2, METHOD1);
    }

    private static void test(String include, String exclude) throws Exception {

        // A method that is known to cost compilers a bit of memory to compile

        List<String> options = new ArrayList<String>();
        options.add("-Xcomp");
        options.add("-XX:-Inline");
        options.add("-Xmx100m");
        options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::*");
        // We pass a very small size to guarantee the crash
        options.add("-XX:CompileCommand=MemStat," + getTestMethod(include) + ",print");
        if (test_crash) {
            options.add("-XX:CompileCommand=MemLimit," + getTestMethod(include) + ",4k~crash");
            options.add("-XX:-CreateCoredumpOnCrash");
        } else {
            options.add("-XX:CompileCommand=MemLimit," + getTestMethod(include) + ",4k");
        }

        if (c2) {
            options.add("-XX:-TieredCompilation");
        } else {
            options.add("-XX:TieredStopAtLevel=1");
        }
        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJvm(options);

        oa.reportDiagnosticSummary();

        String expectedNameIncl = getTestMethod(include)
                .replace('.', '/')
                .replace("$", "\\$");
        String expectedNameExcl = getTestMethod(exclude)
                .replace('.', '/')
                .replace("$", "\\$");

        String ct = c2 ? "c2" : "c1";

        if (test_crash) {
            oa.shouldNotHaveExitValue(0);
            oa.shouldMatch("# *Internal Error.*");
            oa.shouldMatch("# *fatal error: " + ct + " *" + expectedNameIncl + ".*: Hit MemLimit .*limit: 4096.*");
            oa.shouldNotMatch(".*" + expectedNameExcl + ".*");
        } else {
            // Should see trace output when methods are compiled
            oa.shouldHaveExitValue(0)
                    .shouldMatch(".*" + expectedNameIncl + ".*")
                    .shouldNotMatch(".*" + expectedNameExcl + ".*");

            // Expect this log line
            oa.shouldMatch(".*" + expectedNameIncl + ".*Hit MemLimit.*");

            // Expect final output to contain "oom"
            oa.shouldMatch(".*oom.*" + expectedNameIncl + ".*");
        }
    }

    // Test class that is invoked by the sub process
    public static String getTestClass() {
        return TestMain.class.getName();
    }

    public static String getTestMethod(String method) {
        return getTestClass() + "::" + method;
    }

    public static class TestMain {
        public static void main(String[] args) {
            method1();
            method2();
        }

        static long method1() {
            return System.currentTimeMillis();
        }
        static void method2() {}
    }
}

