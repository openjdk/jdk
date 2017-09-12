/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test CommandLineTests.sh
 * @bug  6521334 6965836 6965836
 * @ignore 8059906
 * @compile -XDignore.symbol.file CommandLineTests.java Pack200Test.java
 * @run main/timeout=1200 CommandLineTests
 * @summary An ad hoc test to verify the behavior of pack200/unpack200 CLIs,
 *           and a simulation of pack/unpacking in the install repo.
 * @author ksrini
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
/*
 * We try a potpouri of things ie. we have pack.conf to setup some
 * options as well as a couple of command line options. We also test
 * the packing and unpacking mechanism using the Java APIs. This also
 * simulates pack200 the install workspace, noting that this is a simulation
 * and can only test jars that are guaranteed to be available, also the
 * configuration may not be in sync with the installer workspace.
 */

public class CommandLineTests {
    private static final File CWD = new File(".");
    private static final File EXP_SDK = new File(CWD, "exp-sdk-image");
    private static final File EXP_SDK_LIB_DIR = new File(EXP_SDK, "lib");
    private static final File EXP_SDK_BIN_DIR = new File(EXP_SDK, "bin");
    private static final File EXP_JRE_DIR = new File(EXP_SDK, "jre");
    private static final File EXP_JRE_LIB_DIR = new File(EXP_JRE_DIR, "lib");
    private static final File RtJar = new File(EXP_JRE_LIB_DIR, "rt.jar");
    private static final File CharsetsJar = new File(EXP_JRE_LIB_DIR, "charsets.jar");
    private static final File JsseJar = new File(EXP_JRE_LIB_DIR, "jsse.jar");
    private static final File ToolsJar = new File(EXP_SDK_LIB_DIR, "tools.jar");
    private static final File javaCmd;
    private static final File javacCmd;
    private static final File ConfigFile = new File("pack.conf");
    private static final List<File> jarList;

    static {
        javaCmd = Utils.IsWindows
                    ? new File(EXP_SDK_BIN_DIR, "java.exe")
                    : new File(EXP_SDK_BIN_DIR, "java");

        javacCmd = Utils.IsWindows
                    ? new File(EXP_SDK_BIN_DIR, "javac.exe")
                    : new File(EXP_SDK_BIN_DIR, "javac");

        jarList = new ArrayList<File>();
        jarList.add(RtJar);
        jarList.add(CharsetsJar);
        jarList.add(JsseJar);
        jarList.add(ToolsJar);
    }

    // init test area with a copy of the sdk
    static void init() throws IOException {
            Utils.recursiveCopy(Utils.JavaSDK, EXP_SDK);
            creatConfigFile();
    }
    // cleanup the test area
    static void cleanup() throws IOException {
        Utils.recursiveDelete(EXP_SDK);
        Utils.cleanup();
    }

    // Hopefully, this should be kept in sync with what the installer does.
    static void creatConfigFile() throws IOException {
        FileOutputStream fos = null;
        PrintStream ps = null;
        try {
            fos = new FileOutputStream(ConfigFile);
            ps = new PrintStream(fos);
            ps.println("com.sun.java.util.jar.pack.debug.verbose=0");
            ps.println("pack.modification.time=keep");
            ps.println("pack.keep.class.order=true");
            ps.println("pack.deflate.hint=false");
            // Fail the build, if new or unknown attributes are introduced.
            ps.println("pack.unknown.attribute=error");
            ps.println("pack.segment.limit=-1");
            // BugId: 6328502,  These files will be passed-through as-is.
            ps.println("pack.pass.file.0=java/lang/Error.class");
            ps.println("pack.pass.file.1=java/lang/LinkageError.class");
            ps.println("pack.pass.file.2=java/lang/Object.class");
            ps.println("pack.pass.file.3=java/lang/Throwable.class");
            ps.println("pack.pass.file.4=java/lang/VerifyError.class");
        } finally {
            Utils.close(ps);
            Utils.close(fos);
        }
    }

    static void runPack200(boolean jre) throws IOException {
        List<String> cmdsList = new ArrayList<String>();
        for (File f : jarList) {
            if (jre && f.getName().equals("tools.jar")) {
                continue;  // need not worry about tools.jar for JRE
            }
            // make a backup copy for re-use
            File bakFile = new File(f.getName() + ".bak");
            if (!bakFile.exists()) {  // backup
                Utils.copyFile(f, bakFile);
            } else {  // restore
                Utils.copyFile(bakFile, f);
            }
            cmdsList.clear();
            cmdsList.add(Utils.getPack200Cmd());
            cmdsList.add("-J-esa");
            cmdsList.add("-J-ea");
            cmdsList.add(Utils.Is64Bit ? "-J-Xmx1g" : "-J-Xmx512m");
            cmdsList.add("--repack");
            cmdsList.add("--config-file=" + ConfigFile.getAbsolutePath());
            if (jre) {
                cmdsList.add("--strip-debug");
            }
            // NOTE: commented until 6965836 is fixed
            // cmdsList.add("--code-attribute=StackMapTable=strip");
            cmdsList.add(f.getAbsolutePath());
            Utils.runExec(cmdsList);
        }
    }

    static void testJRE() throws IOException {
        runPack200(true);
        // the speciment JRE
        List<String> cmdsList = new ArrayList<String>();
        cmdsList.add(javaCmd.getAbsolutePath());
        cmdsList.add("-verify");
        cmdsList.add("-version");
        Utils.runExec(cmdsList);
    }

    static void testJDK() throws IOException {
        runPack200(false);
        // test the specimen JDK
        List<String> cmdsList = new ArrayList<String>();
        cmdsList.add(javaCmd.getAbsolutePath());
        cmdsList.add("-verify");
        cmdsList.add("-version");
        Utils.runExec(cmdsList);

        // invoke javac to test the tools.jar
        cmdsList.clear();
        cmdsList.add(javacCmd.getAbsolutePath());
        cmdsList.add("-J-verify");
        cmdsList.add("-help");
        Utils.runExec(cmdsList);
    }
    public static void main(String... args) {
        try {
            init();
            testJRE();
            testJDK();
            cleanup(); // cleanup only if we pass successfully
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
}
