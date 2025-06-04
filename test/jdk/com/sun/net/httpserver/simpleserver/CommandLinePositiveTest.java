/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Positive tests for java -m jdk.httpserver command
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @modules jdk.httpserver
 * @run testng/othervm CommandLinePositiveTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import jdk.test.lib.Platform;
import jdk.test.lib.net.IPSupport;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;

public class CommandLinePositiveTest {

    static final String JAVA_VERSION = System.getProperty("java.version");
    static final Path JAVA_HOME = Path.of(System.getProperty("java.home"));
    static final String LOCALE_OPT = "-Duser.language=en -Duser.country=US";
    static final String JAVA = getJava(JAVA_HOME);

    /**
     * The <b>real path</b> to the current working directory where
     * <ol>
     * <li>the web server process will be started in,</li>
     * <li>and hence, unless given an explicit content root directory, the web
     * server will be serving from.</li>
     * </ol>
     */
    private static final Path CWD;

    static {
        try {
            CWD = Path.of(".").toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final String CWD_STR = CWD.toString();

    /**
     * The <b>real path</b> to the web server content root directory, if one
     * needs to be provided explicitly.
     */
    private static final Path ROOT_DIR = CWD.resolve("www");

    private static final String ROOT_DIR_STR = ROOT_DIR.toString();

    static final String LOOPBACK_ADDR = InetAddress.getLoopbackAddress().getHostAddress();

    @BeforeTest
    public void setup() throws IOException {
        if (Files.exists(ROOT_DIR)) {
            FileUtils.deleteFileTreeWithRetry(ROOT_DIR);
        }
        Files.createDirectories(ROOT_DIR);
        Files.createFile(ROOT_DIR.resolve("file.txt"));
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
    public void testAbsDirectory(String opt) throws Throwable {
        out.printf("\n--- testAbsDirectory, opt=\"%s\"%n", opt);
        testDirectory(opt, ROOT_DIR_STR);
    }

    @Test(dataProvider = "directoryOptions")
    public void testRelDirectory(String opt) throws Throwable {
        out.printf("\n--- testRelDirectory, opt=\"%s\"%n", opt);
        Path rootRelDir = CWD.relativize(ROOT_DIR);
        testDirectory(opt, rootRelDir.toString());
    }

    private static void testDirectory(String opt, String rootDir) throws Throwable {
        simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", "-p", "0", opt, rootDir)
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + ROOT_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @DataProvider
    public Object[][] portOptions() { return new Object[][] {{"-p"}, {"--port"}}; }

    @Test(dataProvider = "portOptions")
    public void testPort(String opt) throws Throwable {
        out.println("\n--- testPort, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", opt, "0")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + CWD_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @DataProvider
    public Object[][] helpOptions() { return new Object[][] {{"-h"}, {"-?"}, {"--help"}}; }

    static final String USAGE_TEXT = """
            Usage: java -m jdk.httpserver [-b bind address] [-p port] [-d directory]
                                          [-o none|info|verbose] [-h to show options]
                                          [-version to show version information]""";

    static final String OPTIONS_TEXT = """
            Options:
            -b, --bind-address    - Address to bind to. Default: %s (loopback).
                                    For all interfaces use "-b 0.0.0.0" or "-b ::".
            -d, --directory       - Directory to serve. Default: current directory.
            -o, --output          - Output format. none|info|verbose. Default: info.
            -p, --port            - Port to listen on. Default: 8000.
            -h, -?, --help        - Prints this help message and exits.
            -version, --version   - Prints version information and exits.
            To stop the server, press Ctrl + C.""".formatted(LOOPBACK_ADDR);

    @Test(dataProvider = "helpOptions")
    public void testHelp(String opt) throws Throwable {
        out.println("\n--- testHelp, opt=\"%s\" ".formatted(opt));
        simpleserver(WaitForLine.HELP_STARTUP_LINE,
                     false,  // do not explicitly destroy the process
                     JAVA, LOCALE_OPT, "-m", "jdk.httpserver", opt)
                .shouldHaveExitValue(0)
                .shouldContain(USAGE_TEXT)
                .shouldContain(OPTIONS_TEXT);
    }

    @DataProvider
    public Object[][] versionOptions() { return new Object[][] {{"-version"}, {"--version"}}; }

    @Test(dataProvider = "versionOptions")
    public void testVersion(String opt) throws Throwable {
        out.println("\n--- testVersion, opt=\"%s\" ".formatted(opt));
        simpleserver(WaitForLine.VERSION_STARTUP_LINE,
                false,  // do not explicitly destroy the process
                JAVA, LOCALE_OPT, "-m", "jdk.httpserver", opt)
                .shouldHaveExitValue(0);
    }

    @DataProvider
    public Object[][] bindOptions() { return new Object[][] {{"-b"}, {"--bind-address"}}; }

    @Test(dataProvider = "bindOptions")
    public void testBindAllInterfaces(String opt) throws Throwable {
        out.println("\n--- testBindAllInterfaces, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", "-p", "0", opt, "0.0.0.0")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Serving " + CWD_STR + " and subdirectories on 0.0.0.0 (all interfaces) port")
                .shouldContain("URL http://" + InetAddress.getLocalHost().getHostAddress());
        if (IPSupport.hasIPv6()) {
            simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", opt, "::0")
                    .shouldHaveExitValue(NORMAL_EXIT_CODE)
                    .shouldContain("Serving " + CWD_STR + " and subdirectories on 0.0.0.0 (all interfaces) port")
                    .shouldContain("URL http://" + InetAddress.getLocalHost().getHostAddress());
        }
    }

    @Test(dataProvider = "bindOptions")
    public void testLastOneWinsBindAddress(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsBindAddress, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", "-p", "0", opt, "123.4.5.6", opt, LOOPBACK_ADDR)
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Serving " + CWD_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);

    }

    @Test(dataProvider = "directoryOptions")
    public void testLastOneWinsDirectory(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsDirectory, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", "-p", "0", opt, ROOT_DIR_STR, opt, ROOT_DIR_STR)
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + ROOT_DIR_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @DataProvider
    public Object[][] outputOptions() { return new Object[][] {{"-o"}, {"--output"}}; }

    @Test(dataProvider = "outputOptions")
    public void testLastOneWinsOutput(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsOutput, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", "-p", "0", opt, "none", opt, "verbose")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + CWD_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @Test(dataProvider = "portOptions")
    public void testLastOneWinsPort(String opt) throws Throwable {
        out.println("\n--- testLastOneWinsPort, opt=\"%s\" ".formatted(opt));
        simpleserver(JAVA, LOCALE_OPT, "-m", "jdk.httpserver", opt, "-999", opt, "0")
                .shouldHaveExitValue(NORMAL_EXIT_CODE)
                .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                .shouldContain("Serving " + CWD_STR + " and subdirectories on " + LOOPBACK_ADDR + " port")
                .shouldContain("URL http://" + LOOPBACK_ADDR);
    }

    @AfterTest
    public void teardown() throws IOException {
        if (Files.exists(ROOT_DIR)) {
            FileUtils.deleteFileTreeWithRetry(ROOT_DIR);
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
    static final String VERSION_STARTUP_LINE_STRING = "java " + JAVA_VERSION;

    // The stdout/stderr output line to wait for when starting the simpleserver
    enum WaitForLine {
        REGULAR_STARTUP_LINE (REGULAR_STARTUP_LINE2_STRING) ,
        HELP_STARTUP_LINE (OPTIONS_TEXT.lines().reduce((first, second) -> second).orElseThrow()),
        VERSION_STARTUP_LINE (VERSION_STARTUP_LINE_STRING);

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
                new ProcessBuilder(args),
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
