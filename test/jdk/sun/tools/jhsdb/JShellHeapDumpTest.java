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
 */

/**
 * @test
 * @bug 8225715
 * @requires vm.hasSAandCanAttach
 * @library /test/lib
 * @compile JShellHeapDumpTest.java
 * @run main/timeout=240 JShellHeapDumpTest
 */

import static jdk.test.lib.Asserts.assertTrue;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.Arrays;
import java.util.Map;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.hprof.parser.HprofReader;

import jdk.jshell.JShell;

public class JShellHeapDumpTest {

    protected static Process process;

    private static long pid;

    public static void launch(String expectedMessage, List<String> toolArgs)
        throws IOException {

        try {
            launchJshell();

            System.out.println("Starting " + toolArgs.get(0) + " against " + pid);
            JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");

            for (String cmd : toolArgs) {
                launcher.addToolArg(cmd);
            }

            launcher.addToolArg("--pid=" + Long.toString(pid));

            ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
            System.out.println("stdout:");
            System.out.println(output.getStdout());
            System.out.println("stderr:");
            System.out.println(output.getStderr());
            output.shouldNotContain("null");
            output.shouldHaveExitValue(0);
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
           if (process.isAlive()) {
             process.destroy();
           }
        }
    }

    public static void launch(String expectedMessage, String... toolArgs)
        throws IOException {

        launch(expectedMessage, Arrays.asList(toolArgs));
    }

    public static void printStackTraces(String file) throws IOException {
        try {
            String output = HprofReader.getStack(file, 0);
            if (!output.contains("JShellToolProvider")) {
                throw new RuntimeException("'JShellToolProvider' missing from stdout/stderr");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        }
    }

    public static void testHeapDump() throws IOException {
        File dump = new File("jhsdb.jmap.heap." +
                             System.currentTimeMillis() + ".hprof");
        if (dump.exists()) {
            dump.delete();
        }

        launch("heap written to", "jmap",
               "--binaryheap", "--dumpfile=" + dump.getAbsolutePath());

        assertTrue(dump.exists() && dump.isFile(),
                   "Could not create dump file " + dump.getAbsolutePath());

        printStackTraces(dump.getAbsolutePath());

        dump.delete();
    }

    public static void launchJshell() throws IOException {
        System.out.println("Starting Jshell");
        String jdkPath = System.getProperty("test.jdk");
        if (jdkPath == null) {
          // we are not under jtreg, try env
            Map<String, String> env = System.getenv();
            jdkPath = env.get("TESTJAVA");
        }
        if (jdkPath == null) {
            throw new RuntimeException("Can't determine jdk path neither test.jdk property no TESTJAVA env are set");
        }
        String osname = System.getProperty("os.name");
        String jshell = jdkPath + ((osname.startsWith("window")) ? "/bin/jshell.exe" : "/bin/jshell");
        process = Runtime.getRuntime().exec(jshell);
        pid = process.pid();
    }

    public static void main(String[] args) throws Exception {

        testHeapDump();

        // The test throws RuntimeException on error.
        // IOException is thrown if Jshell can't start because of some bad
        // environment condition
        System.out.println("Test PASSED");
    }
}
