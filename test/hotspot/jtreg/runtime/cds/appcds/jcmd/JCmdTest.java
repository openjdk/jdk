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
 * @summary Test jcmd to dump static and dynamic shared archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @modules jdk.jcmd/sun.tools.common:+open
 * @compile ../test-classes/Hello.java
 * @build sun.hotspot.WhiteBox
 * @build LingeredTestApp JCmdTest
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JCmdTest
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jtreg.SkippedException;
import sun.hotspot.WhiteBox;

import java.io.InputStreamReader;
import java.io.BufferedReader;

public class JCmdTest {
    static final String TEST_CLASSES[]      = {"LingeredTestApp", "jdk/test/lib/apps/LingeredApp", "Hello"};
    static final String BOOT_CLASSES[]      = {"Hello"};
    static final String SUBCMD_STATIC_DUMP  = "static_dump";
    static final String SUBCMD_DYNAMIC_DUMP = "dynamic_dump";

    static final String STATIC_DUMP_FILE    = "mystatic";
    static final String DYNAMIC_DUMP_FILE   = "mydynamic";


    static final String[] STATIC_MESSAGES   = {"LingeredTestApp source: shared objects file",
                                               "LingeredApp source: shared objects file",
                                               "Hello source: shared objects file"};
    static final String[] DYNAMIC_MESSAGES  = {"LingeredTestApp source: shared objects file (top)",
                                               "LingeredApp source: shared objects file (top)",
                                               "Hello source: shared objects file (top)"};

    static String testJar = null;
    static String bootJar = null;
    static String allJars = null;

    private static void buildJar() throws Exception {
        testJar = JarBuilder.build("test", TEST_CLASSES);
        bootJar = JarBuilder.build("boot", BOOT_CLASSES);
        Path testJarPath = FileSystems.getDefault().getPath(testJar);
        Path bootJarPath = FileSystems.getDefault().getPath(bootJar);
        System.out.println("Jar file created: " + testJarPath.toString());
        System.out.println("Jar file created: " + bootJarPath.toString());
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
        LingeredTestApp app  = new LingeredTestApp();
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
        app.stopApp();
        String output = app.getOutput().getStdout();
        if (messages != null) {
            for (String msg : messages) {
                if (!output.contains(msg)) {
                    throw new RuntimeException(msg + " missed from oupt");
                }
            }
        }
    }

    private static void test(String jcmdSub, String archiveFile,
                             long pid, boolean useBoot, boolean expectOK, String... messages) throws Exception {
        boolean isStatic = jcmdSub.equals(SUBCMD_STATIC_DUMP);
        String fileName = archiveFile != null ? archiveFile :
            ("java_pid" + pid + (isStatic ? "_static" : "_dynamic") + ".jsa");
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }

        String jcmd = "VM.cds " + jcmdSub;
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

    // Those two flags will not create a successful LingeredApp.
    private static String[] noDumpFlags  =
        {"-XX:+DumpSharedSpaces",
         "-Xshare:dump"};
    // Those flags will be excluded in static dumping,
    // See src/java.base/share/classes/jdk/internal/misc/CDS.java
    private static String[] excludeFlags = {
         "-XX:DumpLoadedClassList=AnyFileName.classlist",
         // this flag just dump archive, won't run app normally.
         // "-XX:+DumpSharedSpaces",
         "-XX:+DynamicDumpSharedSpaces",
         "-XX:+RecordDynamicDumpInfo",
         "-Xshare:on",
         "-Xshare:auto",
         "-XX:SharedClassListFile=non-exist.classlist",
         "-XX:SharedArchiveFile=non-exist.jsa",
         "-XX:ArchiveClassesAtExit=tmp.jsa",
         "-XX:+UseSharedSpaces",
         "-XX:+RequireSharedSpaces"};

    // Times to dump cds against same process.
    private static final int ITERATION_TIMES = 2;

    private static void test() throws Exception {
        LingeredApp app  = null;
        long pid;
        int  test_count = 1;
        final boolean useBoot = true;
        final boolean noBoot = !useBoot;
        final boolean EXPECT_PASS = true;
        final boolean EXPECT_FAIL = !EXPECT_PASS;

        // Static dump with default name multiple times.
        print2ln(test_count++ + " Static dump with default name multiple times.");
        app  = createLingeredApp("-cp", allJars);
        pid = app.getPid();
        for (int i = 0; i < ITERATION_TIMES; i++) {
            test(SUBCMD_STATIC_DUMP, null, pid, noBoot,  EXPECT_PASS, STATIC_MESSAGES);
        }
        app.stopApp();

        // Test static dump with given file name.
        print2ln(test_count++ + " Test static dump with given file name.");
        app = createLingeredApp("-cp", allJars);
        pid = app.getPid();
        for (int i = 0; i < ITERATION_TIMES; i++) {
            test(SUBCMD_STATIC_DUMP, STATIC_DUMP_FILE + "0" + i, pid, noBoot,  EXPECT_PASS, STATIC_MESSAGES);
        }
        app.stopApp();

        //  Test static dump with flags with which dumping should fail
        //  This test will result classes.jsa in default server dir if -XX:SharedArchiveFile= not set.
        print2ln(test_count++ + " Test static dump with flags with which dumping should fail.");
        for (String flag : noDumpFlags) {
            app = createLingeredApp("-cp", allJars, flag, "-XX:SharedArchiveFile=tmp.jsa");
            // Following should not be executed.
            if (app != null && app.getProcess().isAlive()) {
                pid = app.getPid();
                test(SUBCMD_STATIC_DUMP, null, pid, noBoot, EXPECT_FAIL);
                app.stopApp();
                // if above executed OK, mean failed.
                throw new RuntimeException("Should not dump successful with " + flag);
            }
        }

        // Test static with -Xbootclasspath/a:boot.jar
        print2ln(test_count++ + " Test static with -Xbootassath/a:boot.jar");
        app = createLingeredApp("-Xbootclasspath/a:" + bootJar, "-cp", testJar);
        pid = app.getPid();
        test(SUBCMD_STATIC_DUMP, null, pid, useBoot, EXPECT_PASS, STATIC_MESSAGES);

        // Test static with limit-modules java.base.
        print2ln(test_count++ + " Test static with --limit-modules java.base.");
        app = createLingeredApp("--limit-modules", "java.base", "-cp", allJars);
        pid = app.getPid();
        test(SUBCMD_STATIC_DUMP, null, pid, noBoot, EXPECT_FAIL);

        // Test static dump with flags which will be filtered before dumping.
        print2ln(test_count++ + " Test static dump with flags which will be filtered before dumping.");
        for (String flag : excludeFlags) {
            app = createLingeredApp("-cp", allJars, flag);
            pid = app.getPid();
            test(SUBCMD_STATIC_DUMP, null, pid, noBoot, EXPECT_PASS, STATIC_MESSAGES);
            app.stopApp();
        }


        // Test static with -Xshare:off will be OK to dump.
        print2ln(test_count++ + " Test static with -Xshare:off will be OK to dump.");
        app = createLingeredApp("-Xshare:off", "-cp", allJars);
        pid = app.getPid();
        test(SUBCMD_STATIC_DUMP, null, pid, noBoot,  EXPECT_PASS, STATIC_MESSAGES);
        app.stopApp();

        // Test dynamic dump with -XX:+RecordDynamicDumpInfo.
        print2ln(test_count++ + " Test dynamic dump with -XX:+RecordDynamicDumpInfo.");
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "01", pid, noBoot, EXPECT_PASS, DYNAMIC_MESSAGES);

        // Test dynamic dump twice to same process.
        print2ln(test_count++ + " Test dynamic dump second time to the same process.");
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "02", pid, noBoot,  EXPECT_FAIL);
        app.stopApp();

        // Test dynamic dump with -XX:-RecordDynamicDumpInfo.
        print2ln(test_count++ + " Test dynamic dump with -XX:-RecordDynamicDumpInfo.");
        app = createLingeredApp("-cp", allJars);
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "01", pid, noBoot, EXPECT_FAIL);
        app.stopApp();

        // Test dynamic dump with default archive name (null).
        print2ln(test_count++ + " Test dynamic dump with default archive name (null).");
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, null, pid, noBoot, EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test dynamic dump with flags -XX:+RecordDynamicDumpInfo -XX:-DynamicDumpSharedSpaces.
        print2ln(test_count++ + " Test dynamic dump with flags -XX:+RecordDynamicDumpInfo -XX:-DynamicDumpSharedSpaces.");
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo", "-XX:-DynamicDumpSharedSpaces");
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, null, pid, noBoot, EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test dynamic dump with flags -XX:-DynamicDumpSharedSpaces -XX:+RecordDynamicDumpInfo.
        print2ln(test_count++ + " Test dynamic dump with flags -XX:-DynamicDumpSharedSpaces -XX:+RecordDynamicDumpInfo.");
        app = createLingeredApp("-cp", allJars, "-XX:-DynamicDumpSharedSpaces", "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, null, pid, noBoot,  EXPECT_PASS, DYNAMIC_MESSAGES);
        app.stopApp();

        // Test dynamic with -Xbootclasspath/a:boot.jar
        print2ln(test_count++ + " Test dynamic with -Xbootclasspath/a:boot.jar");
        app = createLingeredApp("-cp", testJar, "-Xbootclasspath/a:" + bootJar, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, null, pid, useBoot, EXPECT_PASS, DYNAMIC_MESSAGES);

        // Test dynamic dump with -XX:ArchiveClassAtExit will fail.
        print2ln(test_count++ + " Test dynamic dump with -XX:ArchiveClassAtExit will fail.");
        app = createLingeredApp("-cp", allJars,
                                "-Xshare:auto",
                                "-XX:+RecordDynamicDumpInfo",
                                "-XX:ArchiveClassesAtExit=AnyName.jsa");

        if (app != null) {
            if (app.getProcess().isAlive()) {
                throw new RuntimeException("The LingeredTestApp should not start up!");
            }
        }
    }

    public static void main(String... args) throws Exception {
        boolean cdsEnabled = WhiteBox.getWhiteBox().getBooleanVMFlag("UseSharedSpaces");
        if (!cdsEnabled) {
            throw new SkippedException("CDS is not available for this JDK.");
        }
        buildJar();
        test();
    }
}
