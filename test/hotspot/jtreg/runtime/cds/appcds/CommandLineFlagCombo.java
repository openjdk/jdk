/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cds.write.archived.java.heap
 * @comment This test explicitly chooses the type of GC to be used by sub-processes. It may conflict with the GC type set
 * via the -vmoptions command line option of JTREG. vm.gc==null will help the test case to discard the explicitly passed
 * vm options.
 * @requires (vm.gc=="null")
 * @summary Test command line flag combinations that
 *          could likely affect the behaviour of AppCDS
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @compile test-classes/Hello.java
 * @run main/othervm/timeout=240 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. CommandLineFlagCombo
 */

import java.io.File;

import jdk.test.lib.BuildHelper;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import jdk.test.whitebox.code.Compiler;
import jdk.test.whitebox.WhiteBox;

public class CommandLineFlagCombo {

    private static String HELLO_WORLD = "Hello World";
    // shared base address test table
    private static final String[] testTable = {
        "-XX:+UseG1GC", "-XX:+UseSerialGC", "-XX:+UseParallelGC",
        "-XX:+UseLargePages", // may only take effect on machines with large-pages
        "-XX:+UseCompressedClassPointers",
        "-XX:+UseCompressedOops",
        "-XX:ObjectAlignmentInBytes=16",
        "-XX:ObjectAlignmentInBytes=32",
        "-XX:ObjectAlignmentInBytes=64",
        "-Xint",
        "-Xmixed",
        "-Xcomp",
    };

    public static void main(String[] args) throws Exception {
        String appJar = JarBuilder.getOrCreateHelloJar();
        String classList[] = {"Hello"};

        for (String testEntry : testTable) {
            System.out.println("CommandLineFlagCombo = " + testEntry);

            if (skipTestCase(testEntry))
                continue;

            OutputAnalyzer dumpOutput = TestCommon.dump(appJar, classList, testEntry);
            if (!TestCommon.isDynamicArchive()) {
                TestCommon.checkDump(dumpOutput, "Loading classes to share");
            } else {
                if (testEntry.contains("ObjectAlignmentInBytes")) {
                   dumpOutput.shouldHaveExitValue(1)
                             .shouldMatch("The shared archive file's ObjectAlignmentInBytes of .* does not equal the current ObjectAlignmentInBytes of");
                } else {
                   TestCommon.checkDump(dumpOutput, "Loading classes to share");
                }
            }

            if ((TestCommon.isDynamicArchive() && !testEntry.contains("ObjectAlignmentInBytes")) ||
                !TestCommon.isDynamicArchive()) {
                OutputAnalyzer execOutput = TestCommon.exec(appJar, testEntry, "Hello");
                TestCommon.checkExec(execOutput, HELLO_WORLD);
            }
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
            TestCommon.checkExec(execOutput, HELLO_WORLD);
        }

        testExtraCase(appJar, classList);
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

        if (!WhiteBox.getWhiteBox().isJFRIncluded())
        {
            System.out.println("JFR does not exist");
            return true;
        }
        return false;
    }

    // { -Xshare:dump, -XX:ArchiveClassesAtExit} x { -XX:DumpLoadedClassList }
    private static void testExtraCase(String jarFile, String[] classList) throws Exception {
        // 1. -Xshare:dump -XX:-XX:DumpLoadedClassFile
        String dumpedListName = "tmpClassList.list";
        File listFile = new File(dumpedListName);
        if (listFile.exists()) {
            listFile.delete();
        }
        OutputAnalyzer dumpOutput = TestCommon.dump(jarFile, classList, "-XX:DumpLoadedClassList=" + dumpedListName);
        TestCommon.checkDump(dumpOutput, "Loading classes to share");
        if (!listFile.exists()) {
            throw new RuntimeException("ClassList file " + dumpedListName + " should be created");
        }

        // 2. -XX:ArchiveClassesAtExit -XX:DumpLoadedClassFile
        String dynName = "tmpDyn.jsa";
        File dynFile = new File(dynName);
        if (dynFile.exists()) {
            dynFile.delete();
        }
        if (listFile.exists()) {
            listFile.delete();
        }
        String[] args = new String[] {
            "-cp", jarFile, "-XX:ArchiveClassesAtExit=" + dynName, "-XX:DumpLoadedClassList=" + dumpedListName, "Hello"};
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "combo");
        output.shouldHaveExitValue(0)
              .shouldContain(HELLO_WORLD);
        if (!dynFile.exists()) {
            throw new RuntimeException("Dynamic archive file " + dynName + " should be created");
        }
        if (!listFile.exists()) {
            throw new RuntimeException("ClassList file " + dumpedListName + " should be created");
        }
    }
}
