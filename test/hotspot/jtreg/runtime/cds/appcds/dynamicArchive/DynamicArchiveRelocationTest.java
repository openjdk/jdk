/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @comment the test uses -XX:ArchiveRelocationMode=1 to force relocation.
 * @requires vm.cds
 * @summary Testing relocation of dynamic CDS archive (during both dump time and run time)
 * @comment JDK-8231610 Relocate the CDS archive if it cannot be mapped to the requested address
 * @bug 8231610
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller -jar hello.jar Hello
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. DynamicArchiveRelocationTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

public class DynamicArchiveRelocationTest extends DynamicArchiveTestBase {
    public static void main(String... args) throws Exception {
        try {
            testOuter();
        } catch (SkippedException s) {
            s.printStackTrace();
            throw new RuntimeException("Archive mapping should always succeed after JDK-8231610 (did the machine run out of memory?)");
        }
    }

    static void testOuter() throws Exception {
        testInner(true,  false);
        testInner(false, true);
        testInner(true,  true);
    }

    static boolean dump_top_reloc, run_reloc;

    // dump_top_reloc  - force relocation of archive when dumping top  archive
    // run_reloc       - force relocation of archive when running
    static void testInner(boolean dump_top_reloc, boolean run_reloc) throws Exception {
        DynamicArchiveRelocationTest.dump_top_reloc  = dump_top_reloc;
        DynamicArchiveRelocationTest.run_reloc       = run_reloc;

        runTest(DynamicArchiveRelocationTest::doTest);
    }

    static int caseCount = 0;
    static void doTest() throws Exception {
        caseCount += 1;
        System.out.println("============================================================");
        System.out.println("case = " + caseCount
                           + ", top_reloc = " + dump_top_reloc
                           + ", run = " + run_reloc);
        System.out.println("============================================================");

        String appJar = ClassFileInstaller.getJarPath("hello.jar");
        String mainClass = "Hello";
        String forceRelocation = "-XX:ArchiveRelocationMode=1";
        String dumpTopRelocArg  = dump_top_reloc  ? forceRelocation : "-showversion";
        String runRelocArg      = run_reloc       ? forceRelocation : "-showversion";
        String logArg = "-Xlog:cds=debug,cds+reloc=debug";

        String baseArchiveName = getNewArchiveName("base");
        String topArchiveName  = getNewArchiveName("top");

        String runtimeMsg = "Try to map archive(s) at an alternative address";
        String unlockArg = "-XX:+UnlockDiagnosticVMOptions";

        // (1) Dump base archive (static)

        TestCommon.dumpBaseArchive(baseArchiveName, unlockArg, logArg)
          .shouldContain("Relocating archive from");

        // (2) Dump top archive (dynamic)

        dump2(baseArchiveName, topArchiveName,
              unlockArg,
              dumpTopRelocArg,
              logArg,
              "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    if (dump_top_reloc) {
                        output.shouldContain(runtimeMsg);
                    }
                });

        run2(baseArchiveName, topArchiveName,
             unlockArg,
             runRelocArg,
             logArg,
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    if (run_reloc) {
                        output.shouldContain(runtimeMsg);
                        if (Platform.isDebugBuild()) {
                            // In debug builds, we try to thoroughly test unmapping: if ArchiveRelocationMode
                            // is 1, we always map all the regions at the requested address first, and then unmap
                            // them, and then map them again at an alternative. Let's check that
                            // this logic is indeed performed. We expect output like this:
                            //
                            // ArchiveRelocationMode == 1: always map archive(s) at an alternative address
                            // Unmapping region #0 at base 0x0000000800000000 (ReadWrite)
                            // Unmapping region #1 at base 0x0000000800492000 (ReadOnly)
                            // Unmapping region #0 at base 0x0000000800c2b000 (ReadWrite)
                            // Unmapping region #1 at base 0x0000000800c2c000 (ReadOnly)
                            // Released shared space (archive + class) 0x0000000800000000
                            // Try to map archive(s) at an alternative address
                            // Reserved archive_space_rs [0x00007f63cb000000 - 0x00007f63cc000000] (16777216) bytes
                            // Reserved class_space_rs   [0x00007f63cc000000 - 0x00007f640c000000] (1073741824) bytes
                            // Mapped static  region #0 at base 0x00007f63cb000000 top 0x00007f63cb492000 (ReadWrite)
                            // Mapped static  region #1 at base 0x00007f63cb492000 top 0x00007f63cbc2b000 (ReadOnly)
                            // runtime archive relocation start

                            String archiveRelocPattern = "ArchiveRelocationMode == 1.*";
                            String unmapRgn1Pattern    = "Unmapping region #1 at base 0x.*";
                            String unmapRgn0Pattern    = "Unmapping region #0 at base 0x.*";
                            String runtimeRelocMsg     = "runtime archive relocation start";
                            String regexp = archiveRelocPattern +
                                            unmapRgn0Pattern + // unmap static #0
                                            unmapRgn1Pattern + // unmap static #1
                                            unmapRgn0Pattern + // unmap dynamic #0
                                            unmapRgn1Pattern + // unmap dynamic #1
                                            runtimeRelocMsg;
                            Pattern pattern = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
                            Matcher stdoutMatcher = pattern.matcher(output.getStdout());
                            if (!stdoutMatcher.find()) {
                                throw new RuntimeException("'" + regexp
                                                           + "' missing from stdout\n");
                            }
                        }
                    }
                });
    }
}
