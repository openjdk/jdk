/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test options that are incompatible with use of shared strings
 *          Also test mismatch in oops encoding between dump time and run time
 * @requires vm.cds.archived.java.heap
 * @requires (vm.gc=="null")
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds
 * @modules jdk.jartool/sun.tools.jar
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @build HelloString
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. IncompatibleOptions
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

import sun.hotspot.code.Compiler;
import sun.hotspot.gc.GC;

public class IncompatibleOptions {
    static final String COOPS_DUMP_WARNING =
        "Cannot dump shared archive when UseCompressedOops or UseCompressedClassPointers is off";
    static final String COOPS_EXEC_WARNING =
        "UseCompressedOops and UseCompressedClassPointers must be on for UseSharedSpaces";
    static final String GC_WARNING =
        "Archived java heap is not supported";
    static final String OBJ_ALIGNMENT_MISMATCH =
        "The shared archive file's ObjectAlignmentInBytes of .* does not equal the current ObjectAlignmentInBytes of";
    static final String COMPACT_STRING_MISMATCH =
        "The shared archive file's CompactStrings setting .* does not equal the current CompactStrings setting";

    static String appJar;
    static String[] globalVmOptions;

    public static void main(String[] args) throws Exception {
        globalVmOptions = args; // specified by "@run main" in IncompatibleOptions_*.java
        appJar = JarBuilder.build("IncompatibleOptions", "HelloString");

        // Uncompressed OOPs
        testDump(1, "-XX:+UseG1GC", "-XX:-UseCompressedOops", COOPS_DUMP_WARNING, true);
        if (GC.Z.isSupported()) { // ZGC is included in build.
            testDump(1, "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC", COOPS_DUMP_WARNING, true);
        }

        // incompatible GCs
        testDump(2, "-XX:+UseParallelGC", "", GC_WARNING, false);
        testDump(3, "-XX:+UseSerialGC", "", GC_WARNING, false);
        if (!Compiler.isGraalEnabled()) { // Graal does not support CMS
            testDump(4, "-XX:+UseConcMarkSweepGC", "", GC_WARNING, false);
        }

        // ======= archive with compressed oops, run w/o
        testDump(5, "-XX:+UseG1GC", "-XX:+UseCompressedOops", null, false);
        testExec(5, "-XX:+UseG1GC", "-XX:-UseCompressedOops",
                 COOPS_EXEC_WARNING, true);

        // NOTE: No warning is displayed, by design
        // Still run, to ensure no crash or exception
        testExec(6, "-XX:+UseParallelGC", "", "", false);
        testExec(7, "-XX:+UseSerialGC", "", "", false);
        if (!Compiler.isGraalEnabled()) { // Graal does not support CMS
            testExec(8, "-XX:+UseConcMarkSweepGC", "", "", false);
        }

        // Test various oops encodings, by varying ObjectAlignmentInBytes and heap sizes
        testDump(9, "-XX:+UseG1GC", "-XX:ObjectAlignmentInBytes=8", null, false);
        testExec(9, "-XX:+UseG1GC", "-XX:ObjectAlignmentInBytes=16",
                 OBJ_ALIGNMENT_MISMATCH, true);

        // See JDK-8081416 - Oops encoding mismatch with shared strings
        // produces unclear or incorrect warning
        // Correct the test case once the above is fixed
        // @ignore JDK-8081416 - for tracking purposes
        // for now, run test as is until the proper behavior is determined
        testDump(10, "-XX:+UseG1GC", "-Xmx1g", null, false);
        testExec(10, "-XX:+UseG1GC", "-Xmx32g", null, true);

        // CompactStrings must match between dump time and run time
        testDump(11, "-XX:+UseG1GC", "-XX:-CompactStrings", null, false);
        testExec(11, "-XX:+UseG1GC", "-XX:+CompactStrings",
                 COMPACT_STRING_MISMATCH, true);
        testDump(12, "-XX:+UseG1GC", "-XX:+CompactStrings", null, false);
        testExec(12, "-XX:+UseG1GC", "-XX:-CompactStrings",
                 COMPACT_STRING_MISMATCH, true);
    }

    static void testDump(int testCaseNr, String collectorOption, String extraOption,
        String expectedWarning, boolean expectedToFail) throws Exception {

        System.out.println("Testcase: " + testCaseNr);
        OutputAnalyzer output = TestCommon.dump(appJar, TestCommon.list("Hello"),
            TestCommon.concat(globalVmOptions,
                "-XX:+UseCompressedOops",
                collectorOption,
                "-XX:SharedArchiveConfigFile=" + TestCommon.getSourceFile("SharedStringsBasic.txt"),
                "-Xlog:cds,cds+hashtables",
                extraOption));

        if (expectedWarning != null) {
            output.shouldContain(expectedWarning);
        }

        if (expectedToFail) {
            Asserts.assertNE(output.getExitValue(), 0,
            "JVM is expected to fail, but did not");
        }
    }

    static void testExec(int testCaseNr, String collectorOption, String extraOption,
        String expectedWarning, boolean expectedToFail) throws Exception {

        OutputAnalyzer output;
        System.out.println("Testcase: " + testCaseNr);

        // needed, otherwise system considers empty extra option as a
        // main class param, and fails with "Could not find or load main class"
        if (!extraOption.isEmpty()) {
            output = TestCommon.exec(appJar,
                TestCommon.concat(globalVmOptions,
                    "-XX:+UseCompressedOops",
                    collectorOption, "-Xlog:cds", extraOption, "HelloString"));
        } else {
            output = TestCommon.exec(appJar,
                TestCommon.concat(globalVmOptions,
                    "-XX:+UseCompressedOops",
                    collectorOption, "-Xlog:cds", "HelloString"));
        }

        if (expectedWarning != null) {
            output.shouldMatch(expectedWarning);
        }

        if (expectedToFail) {
            Asserts.assertNE(output.getExitValue(), 0);
        } else {
            SharedStringsUtils.checkExec(output);
        }
    }
}
