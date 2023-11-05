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
 * @bug 8276848
 * @summary Tests the java -m jdk.httpserver command with port not specified
 * @modules jdk.httpserver
 * @library /test/lib
 * @run testng/othervm/manual CommandLinePortNotSpecifiedTest
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
import org.testng.annotations.Test;
import static java.lang.System.out;

public class CommandLinePortNotSpecifiedTest {

    static final Path JAVA_HOME = Path.of(System.getProperty("java.home"));
    static final String JAVA = getJava(JAVA_HOME);
    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("CommandLinePortNotSpecifiedTest");
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

    /**
     * This is a manual test to confirm the command-line tool starts successfully
     * in the case where the port is not specified. In this case the server uses
     * the default port 8000. The test is manual to avoid a BindException in the
     * unlikely but not impossible case that the port is already in use.
     */
    @Test
    public void testPortNotSpecified() throws Throwable {
        out.println("\n--- testPortNotSpecified");
        simpleserver(JAVA, "-m", "jdk.httpserver")
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
