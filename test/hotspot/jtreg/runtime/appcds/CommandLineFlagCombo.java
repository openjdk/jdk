/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test CommandLineFlagCombo
 * @requires vm.cds
 * @requires (vm.gc=="null")
 * @summary Test command line flag combinations that
 *          could likely affect the behaviour of AppCDS
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @compile test-classes/Hello.java
 * @run main/othervm/timeout=240 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. CommandLineFlagCombo
 */

import jdk.test.lib.BuildHelper;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

import sun.hotspot.code.Compiler;

public class CommandLineFlagCombo {

    // shared base address test table
    private static final String[] testTable = {
        "-XX:+UseG1GC", "-XX:+UseSerialGC", "-XX:+UseParallelGC", "-XX:+UseConcMarkSweepGC",
        "-XX:+FlightRecorder",
        "-XX:+UseLargePages", // may only take effect on machines with large-pages
        "-XX:+UseCompressedClassPointers",
        "-XX:+UseCompressedOops",
        "-XX:ObjectAlignmentInBytes=16",
        "-XX:ObjectAlignmentInBytes=32",
        "-XX:ObjectAlignmentInBytes=64"
    };

    public static void main(String[] args) throws Exception {
        String appJar = JarBuilder.getOrCreateHelloJar();
        String classList[] = {"Hello"};

        for (String testEntry : testTable) {
            System.out.println("CommandLineFlagCombo = " + testEntry);

            if (skipTestCase(testEntry))
                continue;

            OutputAnalyzer dumpOutput = TestCommon.dump(appJar, classList, testEntry);
            TestCommon.checkDump(dumpOutput, "Loading classes to share");

            OutputAnalyzer execOutput = TestCommon.exec(appJar, testEntry, "Hello");
            TestCommon.checkExec(execOutput, "Hello World");
        }

        for (int i=0; i<2; i++) {
            String g1Flag, serialFlag;

            // Interned strings are supported only with G1GC. However, we should not crash if:
            // 0: archive has shared strings, but run time doesn't support shared strings
            // 1: archive has no shared strings, but run time supports shared strings

            String dump_g1Flag     = "-XX:" + (i == 0 ? "+" : "-") + "UseG1GC";
            String run_g1Flag      = "-XX:" + (i != 0 ? "+" : "-") + "UseG1GC";
            String dump_serialFlag = "-XX:" + (i != 0 ? "+" : "-") + "UseSerialGC";
            String run_serialFlag  = "-XX:" + (i == 0 ? "+" : "-") + "UseSerialGC";

            OutputAnalyzer dumpOutput = TestCommon.dump(
               appJar, classList, dump_g1Flag, dump_serialFlag);

            TestCommon.checkDump(dumpOutput, "Loading classes to share");

            OutputAnalyzer execOutput = TestCommon.exec(appJar, run_g1Flag, run_serialFlag, "Hello");
            TestCommon.checkExec(execOutput, "Hello World");
        }
    }

    private static boolean skipTestCase(String testEntry) throws Exception {
        if (Platform.is32bit())
        {
            if (testEntry.equals("-XX:+UseCompressedOops") ||
                testEntry.equals("-XX:+UseCompressedClassPointers") ||
                testEntry.contains("ObjectAlignmentInBytes") )
            {
                System.out.println("Test case not applicable on 32-bit platforms");
                return true;
            }
        }

        if (Compiler.isGraalEnabled() && testEntry.equals("-XX:+UseConcMarkSweepGC"))
        {
            System.out.println("Graal does not support CMS");
            return true;
        }

        return false;
    }
}
