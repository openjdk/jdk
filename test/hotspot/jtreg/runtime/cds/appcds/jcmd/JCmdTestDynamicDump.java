/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8259070
 * @summary Test jcmd to dump dynamic shared archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @modules jdk.jcmd/sun.tools.common:+open
 * @compile ../test-classes/Hello.java
 * @build sun.hotspot.WhiteBox
 * @build JCmdTestLingeredApp JCmdTestDynamicDump
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm/timeout=480 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JCmdTestDynamicDump
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.JDKToolFinder;
import jtreg.SkippedException;
import sun.hotspot.WhiteBox;

import java.io.InputStreamReader;
import java.io.BufferedReader;

public class JCmdTestDynamicDump {
    static final String TEST_CLASSES[]      = {"JCmdTestLingeredApp",
                                               "jdk/test/lib/apps/LingeredApp",
                                               "jdk/test/lib/apps/LingeredApp$1"};
    static final String BOOT_CLASSES[]      = {"Hello"};

    static final String DYNAMIC_DUMP_FILE   = "mydynamic";


    static final String[] DYNAMIC_MESSAGES  = {"JCmdTestLingeredApp source: shared objects file (top)",
                                               "LingeredApp source: shared objects file (top)",
                                               "Hello source: shared objects file (top)"};

    static String testJar = null;
    static String bootJar = null;
    static String allJars = null;

    private static void buildJar() throws Exception {
        testJar = JarBuilder.build("test", TEST_CLASSES);
        bootJar = JarBuilder.build("boot", BOOT_CLASSES);
        System.out.println("Jar file created: " + testJar);
        System.out.println("Jar file created: " + bootJar);
        allJars = testJar+ File.pathSeparator + bootJar;
    }

    private static boolean argsContain(String[] args, String flag) {
         for (String s: args) {
             if (s.contains(flag)) {
                 return true;
             }
         }
         return false;
    }

    private static boolean argsContainOpts(String[] args, String... opts) {
        boolean allIn = true;
        for (String f : opts) {
            allIn &= argsContain(args, f);
            if (!allIn) {
                break;
            }
        }
        return allIn;
    }

    private static LingeredApp createLingeredApp(String... args) throws Exception {
        JCmdTestLingeredApp app  = new JCmdTestLingeredApp();
        try {
            LingeredApp.startAppExactJvmOpts(app, args);
        } catch (Exception e) {
            // Check flags used.
            if (argsContainOpts(args, new String[] {"-Xshare:off", "-XX:+RecordDynamicDumpInfo"}) ||
                argsContainOpts(args, new String[] {"-XX:+RecordDynamicDumpInfo", "-XX:ArchiveClassesAtExit="})) {
                // app exit premature due to incompactible args
                return null;
            }
            Process proc = app.getProcess();
            if (e instanceof IOException && proc.exitValue() == 0) {
                // Process started and exit normally.
                return null;
            }
            throw e;
        }
        return app;
    }

    static int logFileCount = 0;
    private static void runWithArchiveFile(String archiveName, boolean useBoot,  String... messages) throws Exception {
        List<String> args = new ArrayList<String>();
        if (useBoot) {
            args.add("-Xbootclasspath/a:" + bootJar);
        }
        args.add("-cp");
        if (useBoot) {
            args.add(testJar);
        } else {
            args.add(allJars);
        }
        args.add("-Xshare:on");
        args.add("-XX:SharedArchiveFile=" + archiveName);
        args.add("-Xlog:class+load");

        LingeredApp app = createLingeredApp(args.toArray(new String[0]));
        app.setLogFileName("JCmdTestDynamicDump.log." + (logFileCount++));
        app.stopApp();
        String output = app.getOutput().getStdout();
        if (messages != null) {
            for (String msg : messages) {
                if (!output.contains(msg)) {
                    throw new RuntimeException(msg + " missed from output");
                }
            }
        }
    }

    private static void test(String archiveFile, long pid,
                             boolean useBoot, boolean expectOK, String... messages) throws Exception {
        System.out.println("Expected: " + (expectOK ? "SUCCESS" : "FAIL"));
        String fileName = archiveFile != null ? archiveFile :
            ("java_pid" + pid + "_dynamic.jsa");
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }

        String jcmd = "VM.cds dynamic_dump";
        if (archiveFile  != null) {
          jcmd +=  " " + archiveFile;
        }

        PidJcmdExecutor cmdExecutor = new PidJcmdExecutor(String.valueOf(pid));
        OutputAnalyzer output = cmdExecutor.execute(jcmd, true/*silent*/);

        if (expectOK) {
            output.shouldHaveExitValue(0);
            if (!file.exists()) {
                throw new RuntimeException("Could not create shared archive: " + fileName);
            } else {
                runWithArchiveFile(fileName, useBoot, messages);
                file.delete();
            }
        } else {
            if (file.exists()) {
                throw new RuntimeException("Should not create shared archive " + fileName);
            }
        }
    }

    private static void print2ln(String arg) {
        System.out.println("\n" + arg + "\n");
    }

    private static void test_dynamic() throws Exception {
        int  test_count = 1;
        final boolean useBoot = true;
        final boolean noBoot = !useBoot;
        final boolean EXPECT_PASS = true;
        final boolean EXPECT_FAIL = !EXPECT_PASS;

        LingeredApp app  = null;
        long pid;

        // Test dynamic dump with -XX:+RecordDynamicDumpInfo.
        print2ln(test_count++ + " Test dynamic dump with -XX:+RecordDynamicDumpInfo.");
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(DYNAMIC_DUMP_FILE + "01.jsa", pid, noBoot, EXPECT_PASS, DYNAMIC_MESSAGES);

        // Test dynamic dump twice to same process.
        print2ln(test_count++ + " Test dynamic dump second time to the same process.");
        test("02.jsa", pid, noBoot,  EXPECT_FAIL);
        app.stopApp();

        // Test dynamic dump with -XX:-RecordDynamicDumpInfo.
        print2ln(test_count++ + " Test dynamic dump with -XX:-RecordDynamicDumpInfo.");
        app = createLingeredApp("-cp", allJars);
        pid = app.getPid();
        test("01.jsa", pid, noBoot, EXPECT_FAIL);
        app.stopApp();

        // Test dynamic dump with default archive name (null).
        print2ln(test_count++ + " Test dynamic dump with default archive name (null).");
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(null, pid, noBoot, EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test dynamic dump with flags -XX:+RecordDynamicDumpInfo -XX:-DynamicDumpSharedSpaces.
        print2ln(test_count++ + " Test dynamic dump with flags -XX:+RecordDynamicDumpInfo -XX:-DynamicDumpSharedSpaces.");
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo", "-XX:-DynamicDumpSharedSpaces");
        pid = app.getPid();
        test(null, pid, noBoot, EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test dynamic dump with flags -XX:-DynamicDumpSharedSpaces -XX:+RecordDynamicDumpInfo.
        print2ln(test_count++ + " Test dynamic dump with flags -XX:-DynamicDumpSharedSpaces -XX:+RecordDynamicDumpInfo.");
        app = createLingeredApp("-cp", allJars, "-XX:-DynamicDumpSharedSpaces", "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(null, pid, noBoot,  EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test dynamic with -Xbootclasspath/a:boot.jar
        print2ln(test_count++ + " Test dynamic with -Xbootclasspath/a:boot.jar");
        app = createLingeredApp("-cp", testJar, "-Xbootclasspath/a:" + bootJar, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(null, pid, useBoot, EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test -XX:+RecordDynamicDump -XX:SharedArchiveFile=test_static.jsa
        print2ln(test_count++ + " Test -XX:+RecordDynamicDumpInfo -XX:SharedArchiveFile=test_static.jsa");
        // Dump a static archive as base (here do not use the default classes.jsa)
        String archiveFile = "test_static.jsa";
        dumpStaticArchive(archiveFile);
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo",
                                "-XX:SharedArchiveFile=" + archiveFile);
        pid = app.getPid();
        test(null, pid, noBoot, EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test dynamic dump with -XX:ArchiveClassAtExit will fail.
        print2ln(test_count++ + " Test dynamic dump with -XX:ArchiveClassAtExit will fail.");
        app = createLingeredApp("-cp", allJars,
                                "-Xshare:auto",
                                "-XX:+RecordDynamicDumpInfo",
                                "-XX:ArchiveClassesAtExit=AnyName.jsa");
        if (app != null) {
            if (app.getProcess().isAlive()) {
                throw new RuntimeException("The JCmdTestLingeredApp should not start up!");
            }
        }
    }

    // Dump a static archive, not using TestCommon.dump(...), we do not take jtreg args.
    private static void dumpStaticArchive(String archiveFile) throws Exception {
        String javapath = JDKToolFinder.getJDKTool("java");
        String cmd[] = {javapath, "-Xshare:dump",  "-XX:SharedArchiveFile=" + archiveFile};
        // Do not use ProcessTools.createTestJvm(cmd) here, it copies jtreg env.
        ProcessBuilder pb = new ProcessBuilder(cmd);
        CDSTestUtils.executeAndLog(pb, "dump")
            .shouldHaveExitValue(0);
        File file = new File(archiveFile);
        if (!file.exists()) {
            throw new RuntimeException("Shared archive file " + archiveFile + " is not created");
        }
    }

    public static void main(String... args) throws Exception {
        boolean cdsEnabled = WhiteBox.getWhiteBox().getBooleanVMFlag("UseSharedSpaces");
        if (!cdsEnabled) {
            throw new SkippedException("CDS is not available for this JDK.");
        }
        buildJar();
        test_dynamic();
    }
}
