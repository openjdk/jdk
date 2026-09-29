/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

/*
 * @test
 * @summary Test various printing functions in classfile directory
 * @bug 8211821 8323685
 * @requires vm.flagless
 * @library /test/lib
 * @run driver ClassfilePrintingTests
 */

class SampleClass {
    public static void main(java.lang.String[] unused) {
        System.out.println("Hello from the sample class");
    }
}

public class ClassfilePrintingTests {
    private static void printStringTableStatsTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+PrintStringTableStatistics",
            "--version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Number of buckets");
        output.shouldHaveExitValue(0);
    }

    private static void printSystemDictionaryAtExitTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+PrintSystemDictionaryAtExit",
            "SampleClass");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain(SampleClass.class.getName());
        output.shouldContain("jdk/internal/loader/ClassLoaders$AppClassLoader");
        output.shouldHaveExitValue(0);
    }

    public static void main(String... args) throws Exception {
        printStringTableStatsTest();
        if (Platform.isDebugBuild()) {
            printSystemDictionaryAtExitTest();
        }
    }
}
