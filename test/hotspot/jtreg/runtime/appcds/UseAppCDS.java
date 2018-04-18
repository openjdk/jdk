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
 * @test
 * @summary Testing use of UseAppCDS flag
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build UseAppCDS_Test
 * @run main UseAppCDS
 */

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class UseAppCDS {

    // Class UseAppCDS_Test is loaded by the App loader

    static final String TEST_OUT = "UseAppCDS_Test.main--executed";

    private static final String TESTJAR = "./test.jar";
    private static final String TESTNAME = "UseAppCDS_Test";
    private static final String TESTCLASS = TESTNAME + ".class";

    private static final String CLASSES_DIR = System.getProperty("test.classes", ".");
    private static final String CLASSLIST_FILE = "./UseAppCDS.classlist";
    private static final String ARCHIVE_FILE = "./shared.jsa";
    private static final String BOOTCLASS = "java.lang.Class";

    public static void main(String[] args) throws Exception {

        // First create a jar file for the application "test" class
        JDKToolLauncher jar = JDKToolLauncher.create("jar")
            .addToolArg("-cf")
            .addToolArg(TESTJAR)
            .addToolArg("-C")
            .addToolArg(CLASSES_DIR)
            .addToolArg(TESTCLASS);

        ProcessBuilder pb = new ProcessBuilder(jar.getCommand());
        TestCommon.executeAndLog(pb, "jar01").shouldHaveExitValue(0);

        pb = new ProcessBuilder(jar.getCommand());
        TestCommon.executeAndLog(pb, "jar02").shouldHaveExitValue(0);

        // In all tests the BOOTCLASS should be loaded/dumped/used

        // Test 1: No AppCDS - dumping loaded classes excludes the "test" classes
        dumpLoadedClasses(false, new String[] { BOOTCLASS },
                          new String[] { TESTNAME });

        // Test 2:    AppCDS - dumping loaded classes includes "test" classes
        dumpLoadedClasses(true, new String[] { BOOTCLASS, TESTNAME },
                          new String[0]);

        // Next tests rely on the classlist we just dumped

        // Test 3: No AppCDS - "test" classes in classlist ignored when dumping
        // Although AppCDS isn't used, all classes will be found during dumping
        // after the fix for JDK-8193434. Classes which are not in the boot
        // loader dictionary will not be saved into the archive.
        dumpArchive(false, new String[] { BOOTCLASS },
                    new String[0]);

        // Test 4:    AppCDS - "test" classes in classlist are dumped
        dumpArchive(true, new String[] { BOOTCLASS, TESTNAME },
                    new String[0]);

        // Next tests rely on the archive we just dumped

        // Test 5: No AppCDS - Using archive containing "test" classes ignores them
        useArchive(false, new String[] { BOOTCLASS },
                   new String[] { TESTNAME });

        // Test 6:    AppCDS - Using archive containing "test" classes loads them
        useArchive(true, new String[] { BOOTCLASS, TESTNAME },
                   new String[0]);
    }

    public static List<String> toClassNames(String filename) throws IOException {
        ArrayList<String> classes = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)))) {
            for (; ; ) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                classes.add(line.replaceAll("/", "."));
            }
        }
        return classes;
    }

    static void dumpLoadedClasses(boolean useAppCDS, String[] expectedClasses,
                                  String[] unexpectedClasses) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true,
          TestCommon.makeCommandLineForAppCDS(
            "-XX:DumpLoadedClassList=" + CLASSLIST_FILE,
            "-cp",
            TESTJAR,
            useAppCDS ? "-XX:+UseAppCDS" : "-XX:-UseAppCDS",
            TESTNAME,
            TEST_OUT));

        OutputAnalyzer output = TestCommon.executeAndLog(pb, "dump-loaded-classes")
            .shouldHaveExitValue(0).shouldContain(TEST_OUT);

        List<String> dumpedClasses = toClassNames(CLASSLIST_FILE);

        for (String clazz : expectedClasses) {
            if (!dumpedClasses.contains(clazz)) {
                throw new RuntimeException(clazz + " missing in " +
                                           CLASSLIST_FILE);
            }
        }
        for (String clazz : unexpectedClasses) {
            if (dumpedClasses.contains(clazz)) {
                throw new RuntimeException("Unexpectedly found " + clazz +
                                           " in " + CLASSLIST_FILE);
            }
        }
    }

    static void dumpArchive(boolean useAppCDS, String[] expectedClasses,
                            String[] unexpectedClasses) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true,
          TestCommon.makeCommandLineForAppCDS(
            useAppCDS ? "-XX:-UnlockDiagnosticVMOptions" :
                        "-XX:+UnlockDiagnosticVMOptions",
            "-cp",
            TESTJAR,
            useAppCDS ? "-XX:+UseAppCDS" : "-XX:-UseAppCDS",
            "-XX:SharedClassListFile=" + CLASSLIST_FILE,
            "-XX:SharedArchiveFile=" + ARCHIVE_FILE,
            "-Xlog:cds",
            "-Xshare:dump"));

        OutputAnalyzer output = TestCommon.executeAndLog(pb, "dump-archive")
            .shouldHaveExitValue(0);

        for (String clazz : expectedClasses) {
            String failed = "Preload Warning: Cannot find " + clazz;
            output.shouldNotContain(failed);
        }
        for (String clazz : unexpectedClasses) {
            String failed = "Preload Warning: Cannot find " + clazz;
            output.shouldContain(failed);
        }
    }

    static void useArchive(boolean useAppCDS, String[] expectedClasses,
                           String[] unexpectedClasses) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true,
          TestCommon.makeCommandLineForAppCDS(
            useAppCDS ? "-XX:-UnlockDiagnosticVMOptions" :
                        "-XX:+UnlockDiagnosticVMOptions",
            "-cp",
            TESTJAR,
            useAppCDS ? "-XX:+UseAppCDS" : "-XX:-UseAppCDS",
            "-XX:SharedArchiveFile=" + ARCHIVE_FILE,
            "-verbose:class",
            "-Xshare:on",
            TESTNAME,
            TEST_OUT));

        OutputAnalyzer output = TestCommon.executeAndLog(pb, "use-archive");
        if (CDSTestUtils.isUnableToMap(output))
            System.out.println("Unable to map: test case skipped");
        else
            output.shouldHaveExitValue(0).shouldContain(TEST_OUT);

        // Quote the class name in the regex as it may contain $
        String prefix = ".class,load. ";
        String archive_suffix = ".*source: shared objects file.*";
        String jar_suffix = ".*source: .*\\.jar";

        for (String clazz : expectedClasses) {
            String pattern = prefix + clazz + archive_suffix;
            try {
                output.shouldMatch(pattern);
            } catch (Exception e) {
                TestCommon.checkCommonExecExceptions(output, e);
            }
        }

        for (String clazz : unexpectedClasses) {
            String pattern = prefix + clazz + archive_suffix;
            try {
                output.shouldNotMatch(pattern);
            } catch (Exception e) {
                TestCommon.checkCommonExecExceptions(output, e);
            }
            pattern = prefix + clazz + jar_suffix;
            try {
                output.shouldMatch(pattern);
            } catch (Exception e) {
                TestCommon.checkCommonExecExceptions(output, e);
            }
        }
    }
}
