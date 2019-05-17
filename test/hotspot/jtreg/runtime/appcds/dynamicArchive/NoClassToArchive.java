/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @summary A few edge cases where there's no class to be included in the dynamic archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds /test/hotspot/jtreg/runtime/appcds/dynamicArchive/test-classes
 * @build StrConcatApp
 * @run driver ClassFileInstaller -jar strConcatApp.jar StrConcatApp
 * @run driver NoClassToArchive
 */

import java.io.File;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class NoClassToArchive extends DynamicArchiveTestBase {
    static final String warningMessage =
        "There is no class to be included in the dynamic archive";
    static final String classList = System.getProperty("test.classes") +
        File.separator + "NoClassToArchive.list";
    static final String appClass = "StrConcatApp";

    public static void main(String[] args) throws Exception {
        runTest(NoClassToArchive::testDefaultBase);
        runTest(NoClassToArchive::testCustomBase);
    }

    // (1) Test with default base archive + top archive
    static void testDefaultBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTest(null, topArchiveName);
    }

    // (2) Test with custom base archive + top archive
    static void testCustomBase() throws Exception {
        String topArchiveName = getNewArchiveName("top2");
        String baseArchiveName = getNewArchiveName("base");
        doTestCustomBase(baseArchiveName, topArchiveName);
    }

    private static void doTest(String baseArchiveName, String topArchiveName) throws Exception {
        dump2(baseArchiveName, topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-Xlog:class+load=trace",
             "-version")
            .assertNormalExit(output -> {
                    if (output.getStdout().contains("jrt:/")) {
                        System.out.println("test skipped: this platform uses non-archived classes when running -version");
                    } else {
                        output.shouldContain(warningMessage);
                    }
                });

        dump2(baseArchiveName, topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-Xlog:class+load=trace",
             "-help")
            .assertNormalExit(output -> {
                    // some classes will be loaded from the java.base module
                    output.shouldContain("java.text.MessageFormat source: jrt:/java.base");
                });
    }

    private static void doTestCustomBase(String baseArchiveName, String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("strConcatApp.jar");
        // dump class list by running the StrConcatApp
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            true,
            "-XX:DumpLoadedClassList=" + classList,
            "-cp",
            appJar,
            appClass);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "dumpClassList");
        TestCommon.checkExecReturn(output, 0, true, "length = 0");

        // create a custom base archive based on the class list
        dumpBaseArchive(baseArchiveName, "-XX:SharedClassListFile=" + classList);

        // create a dynamic archive with the custom base archive
        // no class should be included in the dynamic archive
        dump2(baseArchiveName, topArchiveName, "-version")
            .assertNormalExit(out -> {
                    out.shouldMatch(warningMessage);
                });
    }
}
