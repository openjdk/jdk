/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.    See the GNU General Public License
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
 * @bug 8141211
 * @summary exceptions=info output should have an exception message for both interpreter and compiled methods
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @build jdk.test.lib.OutputAnalyzer jdk.test.lib.ProcessTools
 * @run driver ExceptionsTest
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;

public class ExceptionsTest {
    static void analyzeOutputOn(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("<a 'java/lang/RuntimeException': Test exception 1 for logging>");
        output.shouldContain(" thrown in interpreter method ");
        output.shouldContain(" thrown in compiled method ");
        output.shouldContain("Exception 2 caught.");
        output.shouldHaveExitValue(0);
    }

    static void analyzeOutputOff(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("[exceptions]");
        output.shouldHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xlog:exceptions=info", "-Xcomp",
            "-XX:CompileCommand=compileonly,ExceptionsTest$InternalClass::compileMe",
            InternalClass.class.getName());
        analyzeOutputOn(pb);

        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+TraceExceptions", "-Xcomp",
            "-XX:CompileCommand=compileonly,ExceptionsTest$InternalClass::compileMe",
            InternalClass.class.getName());
        analyzeOutputOn(pb);

        pb = ProcessTools.createJavaProcessBuilder(
            "-Xlog:exceptions=off", "-Xcomp",
            "-XX:CompileCommand=compileonly,ExceptionsTest$InternalClass::compileMe",
            InternalClass.class.getName());
        analyzeOutputOff(pb);

        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-TraceExceptions", "-Xcomp",
            "-XX:CompileCommand=compileonly,ExceptionsTest$InternalClass::compileMe",
            InternalClass.class.getName());
        analyzeOutputOff(pb);
    }

    public static class InternalClass {
        public static void compileMe() throws Exception {
            try {
                throw new RuntimeException("Test exception 2 for logging");
            } catch (Exception e) {
                System.out.println("Exception 2 caught.");
            }
        }

        public static void main(String[] args) throws Exception {
            try {
                throw new RuntimeException("Test exception 1 for logging");
            } catch (Exception e) {
                System.out.println("Exception 1 caught.");
            }
            compileMe();
        }
    }
}
