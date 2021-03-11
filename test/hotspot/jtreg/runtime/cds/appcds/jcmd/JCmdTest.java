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
    static final String TEST_CLASS[] = {"LingeredTestApp", "jdk/test/lib/apps/LingeredApp"};
    static final String TEST_JAR   = "test.jar";
    static final String SUBCMD_STATIC_DUMP = "static_dump";
    static final String SUBCMD_DYNAMIC_DUMP= "dynamic_dump";

    static final String STATIC_DUMP_FILE = "mystatic";
    static final String DYNAMIC_DUMP_FILE = "mydynamic";

    static boolean EXPECT_PASS = true;
    static boolean EXPECT_FAIL = !EXPECT_PASS;

    static String jarFile = null;

    private static void buildJar() throws Exception {
        jarFile = JarBuilder.build("test", TEST_CLASS);
        Path path = FileSystems.getDefault().getPath(jarFile);
        System.out.println("Jar file created: " + path.toString());
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

    private static void test(String jcmdSub, String archiveFile,
                             long pid, boolean expectOK) throws Exception {
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
    // Those flags will be excluded in static dumping.
    private static String[] excludeFlags =
        {"-XX:DumpLoadedClassList=AnyFileName.classlist",
         "-XX:+UseSharedSpaces",
         "-XX:+RecordDynamicDumpInfo",
         "-XX:+RequireSharedSpaces",
         "-XX:+DynamicDumpSharedSpaces",
         "-XX:ArchiveClassesAtExit=tmp.jsa",
         "-Xshare:auto",
         "-Xshare:on"};

    // Times to dump cds against same process.
    private static final int ITERATION_TIMES = 2;

    private static void test() throws Exception {
        LingeredApp app  = null;
        long pid;

        // 1. Static dump with default name multiple times.
        print2ln("1: Static dump with default name multiple times.");
        app  = createLingeredApp("-cp", jarFile);
        pid = app.getPid();
        for (int i = 0; i < ITERATION_TIMES; i++) {
            test(SUBCMD_STATIC_DUMP, null, pid, EXPECT_PASS);
        }
        app.stopApp();

        // 2. Test static dump with given file name.
        print2ln("2. Test static dump with given file name.");
        app = createLingeredApp("-cp", jarFile);
        pid = app.getPid();
        for (int i = 0; i < ITERATION_TIMES; i++) {
            test(SUBCMD_STATIC_DUMP, STATIC_DUMP_FILE + "0" + i, pid, EXPECT_PASS);
        }
        app.stopApp();

        // 3. Test static dump with flags which no dump will happen.
        //    This test will result classes.jsa in default server dir if -XX:SharedArchiveFile= not set.
        print2ln("3. Test static dump with flags which will no dump will happen.");
        for (String flag : noDumpFlags) {
            app  = createLingeredApp("-cp", jarFile, flag, "-XX:SharedArchiveFile=tmp.jsa");
            // Following should not be executed.
            if (app != null && app.getProcess().isAlive()) {
                pid = app.getPid();
                test(SUBCMD_STATIC_DUMP, null, pid, EXPECT_FAIL);
                app.stopApp();
                // if above executed OK, mean failed.
                throw new RuntimeException("Should not dump successful with " + flag);
            }
        }

        // 4. Test static dump with flags which will be filtered before dumping.
        print2ln("4. Test static dump with flags which will be filtered before dumping.");
        for (String flag : excludeFlags) {
            app  = createLingeredApp("-cp", jarFile, flag);
            pid = app.getPid();
            test(SUBCMD_STATIC_DUMP, null, pid, EXPECT_PASS);
            app.stopApp();
        }


        // 5. Test static with -Xshare:off will be OK to dump.
        print2ln("5. Test static with -Xshare:off will be OK to dump.");
        app  = createLingeredApp("-Xshare:off", "-cp", jarFile);
        pid = app.getPid();
        test(SUBCMD_STATIC_DUMP, null, pid, EXPECT_PASS);
        app.stopApp();

        // 6. Test dynamic dump with -XX:+RecordDynamicDumpInfo.
        print2ln("6. Test dynamic dump with -XX:+RecordDynamicDumpInfo.");
        app  = createLingeredApp("-cp", jarFile, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "01", pid, EXPECT_PASS);

        // 7. Test dynamic dump twice to same process.
        print2ln("7. Test dynamic dump second time to the same process.");
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "02", pid, EXPECT_FAIL);
        app.stopApp();

        // 8. Test dynamic dump with -XX:-RecordDynamicDumpInfo.
        print2ln("8. Test dynamic dump with -XX:-RecordDynamicDumpInfo.");
        app  = createLingeredApp("-cp", jarFile);
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "01", pid, EXPECT_FAIL);
        app.stopApp();

        // 9. Test dynamic dump with default archive name (null).
        print2ln("9. Test dynamic dump with default archive name (null).");
        app = createLingeredApp("-cp", jarFile, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, null, pid, EXPECT_PASS);

        // 10. Test dynamic dump with -XX:ArchiveClassAtExit will fail.
        print2ln("10. Test dynamic dump with -XX:ArchiveClassAtExit will fail.");
        app = createLingeredApp("-cp", jarFile,
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
