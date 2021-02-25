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
import sun.hotspot.WhiteBox;

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
            throw e;
        }
        return app;

    }

    private static void test(String jcmdSub, String archiveFile, long pid, boolean expectSuccess) throws Exception {

        boolean isStatic = jcmdSub.equals("static_dump") ? true : false;

        String cdsFileName = archiveFile;
        if (cdsFileName == null) {
            cdsFileName = "java_pid" + pid + "_" + (isStatic  ? "static" : "dynamic") + ".jsa";
        } else {
            if (!cdsFileName.endsWith(".jsa")) {
                cdsFileName = cdsFileName + ".jsa";
            }
        }

        File file = new File(cdsFileName);
        if (file.exists() && file.isFile()) {
            file.delete();
        }

        String jcmd = "VM.cds " + jcmdSub + " " + cdsFileName;;

        PidJcmdExecutor cmdExecutor = new PidJcmdExecutor("" + pid);
        System.out.println("JCMD: " + jcmd);
        OutputAnalyzer output = cmdExecutor.execute(jcmd, false/*silent*/);
        output.shouldHaveExitValue(0);

        Path path = FileSystems.getDefault().getPath(cdsFileName);
        if (expectSuccess) {
            if (Files.notExists(path)) {
                throw new RuntimeException("Could not create shared archive " + cdsFileName);
            }
        } else {
            if (Files.exists(path)) {
                throw new RuntimeException("Should not create shared archive " + cdsFileName);
            }
        }
    }

    private static void print2ln(String arg) {
        System.out.println("\n" + arg + "\n");
    }

    private static void test_static() throws Exception {
        ArrayList<String> vmArgs = new ArrayList<String>();
        vmArgs.add("-cp");
        vmArgs.add(jarFile);
        vmArgs.add("-Xlog:class+path");
        vmArgs.add("-XX:+RecordDynamicDumpInfo");
        LingeredApp app  = null;
        app = createLingeredApp(vmArgs.toArray(new String[0]));
        long pid = app.getPid();

        // 1. Test static dump with -XX:+RecordDynamicDumpInfo to create archive multiple times
        print2ln("1. Test static dump with -XX:+RecordDynamicDumpInfo to create archive multiple times.");
        for (int i = 0; i < 3; i++) {
            test(SUBCMD_STATIC_DUMP, STATIC_DUMP_FILE + "0" + i, pid, EXPECT_PASS);
        }
        app.stopApp();
        vmArgs.clear();
        // 2. Static dump with default name multiple times.
        print2ln("2: Static dump with default name multiple times.");
        vmArgs.add("-cp");
        vmArgs.add(jarFile);
        app  = createLingeredApp(vmArgs.toArray(new String[0]));
        pid = app.getPid();
        for (int i = 0; i < 3; i++) {
            test(SUBCMD_STATIC_DUMP, null, pid, EXPECT_PASS);
        }
        app.stopApp();
    }

    private static void test_dynamic() throws Exception {
        ArrayList<String> vmArgs = new ArrayList<String>();
        // 3. Test dynamic dump with -XX:+RecordDynamicDumpInfo.
        print2ln("3. Test dynamic dump with -XX:+RecordDynamicDumpInfo.");
        vmArgs.add("-cp");
        vmArgs.add(jarFile);
        vmArgs.add("-XX:+RecordDynamicDumpInfo");
        LingeredApp app  = createLingeredApp(vmArgs.toArray(new String[0]));
        long pid = app.getPid();
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "01", pid, EXPECT_PASS);
        // 4. Test dynamic dump twice to same process
        print2ln("4. Test dynamic dump second time to the same process.");
        test(SUBCMD_DYNAMIC_DUMP, DYNAMIC_DUMP_FILE + "02", pid, EXPECT_FAIL);
        app.stopApp();
        vmArgs.clear();
        // 5. Test dynamic dump with -XX:ArchiveClassAtExit will fail.
        print2ln("5. Test dynamic dump with -XX:ArchiveClassAtExit will fail.");
        vmArgs.add("-Xshare:auto");
        vmArgs.add("-XX:+RecordDynamicDumpInfo");
        vmArgs.add("-XX:ArchiveClassesAtExit=noexist.jsa");
        vmArgs.add("-cp");
        vmArgs.add(jarFile);
        app = createLingeredApp(vmArgs.toArray(new String[0]));

        if (app != null) {
            if (app.getProcess().isAlive()) {
                throw new RuntimeException("The LingeredTestApp should not start up!");
            }
        }
    }

    public static void main(String... args) throws Exception {
        boolean cdsEnabled = WhiteBox.getWhiteBox().getBooleanVMFlag("UseSharedSpaces");
        if (!cdsEnabled) {
            System.out.println("CDS is not available for this JDK, skip the test.");
            return;
        }
        buildJar();
        test_static();
        test_dynamic();
    }
}
