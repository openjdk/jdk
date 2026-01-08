/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8141211 8147477 8358080
 * @summary exceptions=info output should have an exception message for interpreter methods
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver ExceptionsTest
 */

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ExceptionsTest {
    static void updateEnvironment(ProcessBuilder pb, String environmentVariable, String value) {
        Map<String, String> env = pb.environment();
        env.put(environmentVariable, value);
    }

    static void analyzeOutputOn(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldContain("<a 'java/lang/RuntimeException'").shouldContain(": Test exception 1 for logging>");
        output.shouldContain(" thrown in interpreter method ");
        output.shouldMatch("info..exceptions,stacktrace.*at ExceptionsTest[$]InternalClass.bar[(]ExceptionsTest.java:[0-9]+" +
                           ".*\n.*" +
                           "info..exceptions,stacktrace.*at ExceptionsTest[$]InternalClass.foo[(]ExceptionsTest.java:[0-9]+" +
                           ".*\n.*" +
                           "info..exceptions,stacktrace.*at ExceptionsTest[$]InternalClass.main[(]ExceptionsTest.java:[0-9]+");

        // Note: "(?s)" means that the "." in the regexp can match the newline character.
        // To avoid verbosity, stack trace for bar2()->baz2() should be printed only once:
        // - It should be printed when the exception is thrown inside bzz2()
        // - It should not be printed when the interpreter is looking for an exception handler inside bar2()
        output.shouldMatch("(?s)baz2.*bar2");
        output.shouldNotMatch("(?s)baz2.*bar2,*baz2.*bar2");

        // Two stack traces should include main()->foo2(), as an exception is thrown at two different BCIs in bar2().
        output.shouldMatch("(?s)foo2.*main.*foo2.*main");
    }

    static void analyzeOutputOff(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("[exceptions]");
        output.shouldHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:exceptions,exceptions+stacktrace",
                                                                             InternalClass.class.getName());
        analyzeOutputOn(pb);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:exceptions=off",
                                                              InternalClass.class.getName());
        analyzeOutputOff(pb);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(InternalClass.class.getName());
        updateEnvironment(pb, "_JAVA_OPTIONS", "-Xlog:exceptions,exceptions+stacktrace");
        analyzeOutputOn(pb);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(InternalClass.class.getName());
        updateEnvironment(pb, "JAVA_TOOL_OPTIONS", "-Xlog:exceptions=info -Xlog:exceptions=off");
        analyzeOutputOff(pb);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:VMOptionsFile=" + System.getProperty("test.src", ".")
                                                              + File.separator + "ExceptionsTest_options_file",
                                                              InternalClass.class.getName());
        analyzeOutputOn(pb);
    }

    public static class InternalClass {
        public static void main(String[] args) {
            foo();
            foo2();
        }

        static void foo() {
            bar();
        }

        static void bar() {
            try {
                throw new RuntimeException("Test exception 1 for logging");
            } catch (Exception e) {
                System.out.println("Exception 1 caught.");
            }
        }

        static void foo2() {
            try {
                bar2();
            } catch (Exception e) {
                System.out.println("Exception 2 caught.");
            }
        }

        static void bar2() {
            try {
                baz2();
            } catch (RuntimeException e) {
                throw e; // Rethrow -- should print a new callstack.
            }
        }

        static void baz2() {
            bzz2();
        }

        static void bzz2() {
            throw new RuntimeException("Test exception 2 for logging");
        }
    }
}
