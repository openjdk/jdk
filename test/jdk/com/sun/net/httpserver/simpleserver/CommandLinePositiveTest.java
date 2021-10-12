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
 */

/*
 * @test
 * @summary Positive tests for simpleserver command-line tool
 * @library /test/lib
 * @modules jdk.httpserver
 * @run testng/othervm CommandLinePositiveTest
 */

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;

public class CommandLinePositiveTest {

    static final Path JAVA_HOME = Path.of(System.getProperty("java.home"));
    static final String JAVA = getJava(JAVA_HOME);
    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("CommandLinePositiveTest");
    static final Path TEST_FILE = TEST_DIR.resolve("file.txt");
    static final String TEST_DIR_STR = TEST_DIR.toString();
    static final String LOOPBACK_ADDR = InetAddress.getLoopbackAddress().getHostAddress();

    @BeforeTest
    public void setup() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
        Files.createDirectories(TEST_DIR);
        Files.createFile(TEST_FILE);
    }

    static final int SIGTERM = 15;
    static final int NORMAL_EXIT_CODE = normalExitCode();

    static int normalExitCode() {
        if (Platform.isWindows()) {
            return 1; // expected process destroy exit code
        } else {
            // signal terminated exit code on Unix is 128 + signal value
            return 128 + SIGTERM;
        }
    }

    @DataProvider
    public Object[][] directoryOptions() { return new Object[][] {{"-d"}, {"--directory"}}; }

    @Test(dataProvider = "directoryOptions")
    public void testDirectory(String opt) throws Throwable {
        out.println("\n--- testDirectory, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, TEST_DIR_STR)
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @DataProvider
    public Object[][] portOptions() { return new Object[][] {{"-p"}, {"--port"}}; }

    @Test(dataProvider = "portOptions")
    public void testPort(String opt) throws Throwable {
        out.println("\n--- testPort, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, "0")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @DataProvider
    public Object[][] helpOptions() { return new Object[][] {{"-h"}, {"-?"}, {"--help"}}; }

    static final String USAGE_TEXT = """
            Usage: java -m jdk.httpserver [-b bind address] [-p port] [-d directory]
                                          [-o none|info|verbose] [-h to show options]""";

    static final String OPTIONS_TEXT = """
            Options:
            -b, --bind-address    - Address to bind to. Default: %s (loopback).
                                    For all interfaces use "-b 0.0.0.0" or "-b ::".
            -d, --directory       - Directory to serve. Default: current directory.
            -o, --output          - Output format. none|info|verbose. Default: info.
            -p, --port            - Port to listen on. Default: 8000.
            -h, -?, --help        - Print this help message.
            To stop the server, press Ctrl + C.""".formatted(LOOPBACK_ADDR);

    @Test(dataProvider = "helpOptions")
    public void testHelp(String opt) throws Throwable {
        out.println("\n--- testHelp, opt=\"%s\" ".formatted(opt));
        simpleserver(WaitForLine.HELP_STARTUP_LINE,
                     false,  // do not explicitly destroy the process
                     JAVA, "-m", "jdk.httpserver", opt)
                .shouldHaveExitValue(0)
                .shouldContain(USAGE_TEXT)
                .shouldContain(OPTIONS_TEXT);
    }

    @DataProvider
    public Object[][] bindOptions() { return new Object[][] {{"-b"}, {"--bind-address"}}; }

    @Test(dataProvider = "bindOptions")
    public void testBindAllInterfaces(String opt) throws Throwable {
        out.println("\n--- testPort, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, "0.0.0.0")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0 (all interfaces) port")
                .shouldContain("URL http://" + InetAddress.getLocalHost().getHostAddress());
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, "::0")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0 (all interfaces) port")
                .shouldContain("URL http://" + InetAddress.getLocalHost().getHostAddress());
    }

    @Test(dataProvider = "bindOptions")
    public void testLastOneWinsBindAddress(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsBindAddress, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, "123.4.5.6", opt, LOOPBACK_ADDR)
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);

    }

    @Test(dataProvider = "directoryOptions")
    public void testLastOneWinsDirectory(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsDirectory, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, TEST_DIR_STR, opt, TEST_DIR_STR)
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @DataProvider
    public Object[][] outputOptions() { return new Object[][] {{"-o"}, {"--output"}}; }

    @Test(dataProvider = "outputOptions")
    public void testLastOneWinsOutput(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsOutput, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, "none", opt, "verbose")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @Test(dataProvider = "portOptions")
    public void testLastOneWinsPort(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsPort, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, "-999", opt, "0")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + TEST_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @AfterTest
    public void teardown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
    }

    // --- infra ---

    static String getJava(Path image) {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        Path java = image.resolve("bin").resolve(isWindows ? "java.exe" : "java");
        if (Files.notExists(java))
            throw new RuntimeException(java + " not found");
        return java.toAbsolutePath().toString();
    }

    static final String REGULAR_STARTUP_LINE1_STRING = "Serving";
    static final String REGULAR_STARTUP_LINE2_STRING = "URL http://";

    // The stdout/stderr output line to wait for when starting the simpleserver
    enum WaitForLine {
        REGULAR_STARTUP_LINE (REGULAR_STARTUP_LINE2_STRING) ,
        HELP_STARTUP_LINE (OPTIONS_TEXT.lines().reduce((first, second) -> second).orElseThrow());

        final String value;
        WaitForLine(String value) { this.value = value; }
    }

    static OutputAnalyzer simpleserver(String... args) throws Throwable {
        return simpleserver(WaitForLine.REGULAR_STARTUP_LINE, true, args);
    }

    static OutputAnalyzer simpleserver(WaitForLine waitForLine, boolean destroy, String... args) throws Throwable {
        StringBuffer sb = new StringBuffer();  // stdout & stderr
        // start the process and await the waitForLine before returning
        var p = ProcessTools.startProcess("simpleserver",
                new ProcessBuilder(args).directory(TEST_DIR.toFile()),
                line -> sb.append(line + "\n"),
                line -> line.startsWith(waitForLine.value),
                30,  // suitably high default timeout, not expected to timeout
                TimeUnit.SECONDS);
        if (destroy) {
            p.destroy();  // SIGTERM on Unix
        }
        int ec = p.waitFor();
        var outputAnalyser = new OutputAnalyzer(sb.toString(), "", ec);
        return outputAnalyser;
    }
}
