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
 * @build jdk.test.lib.util.FileUtils
 * @run testng/othervm CommandLinePositiveTest
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class CommandLinePositiveTest {

    static final String JAVA = System.getProperty("java.home") + "/bin/java";
    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("dir");
    static final Path TEST_FILE = TEST_DIR.resolve("file.txt");
    static final String TEST_DIR_STR = TEST_DIR.toString();
    static final String LOCALHOST_ADDR;

    static {
        try {
            LOCALHOST_ADDR = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Cannot determine local host address");
        }
    }

    @BeforeTest
    public void makeTestDirectoryAndFile() throws IOException {
        if (Files.exists(TEST_DIR))
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        Files.createDirectories(TEST_DIR);
        Files.createFile(TEST_FILE);
    }

    @DataProvider
    public Object[][] directoryOptions() { return new Object[][] {{"-d"}, {"--directory"}}; }

    @Test(dataProvider = "directoryOptions")
    public void testDirectory(String opt) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, TEST_DIR_STR)
                .assertExternalTermination()
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:");
                    assertContains(r.output,
                            "http://" + LOCALHOST_ADDR);
                });
    }

    @DataProvider
    public Object[][] portOptions() { return new Object[][] {{"-p"}, {"--port"}}; }

    @Test(dataProvider = "portOptions")
    public void testPort(String opt) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, "0")
                .assertExternalTermination()
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:");
                    assertContains(r.output,
                            "http://" + LOCALHOST_ADDR);
                });
    }

    @DataProvider
    public Object[][] helpOptions() { return new Object[][] {{"-?"}, {"-h"}, {"--help"}}; }

    @Test(dataProvider = "helpOptions")
    public void testHelp(String opt) throws Exception {
        var usageText = "Usage: java -m jdk.httpserver [-b bind address] [-p port] [-d directory]\n" +
                        "                              [-o none|info|verbose] [-h to show options]";
        var optionsText = """
                Options:
                -b, --bind-address    - Address to bind to. Default: 0.0.0.0 (all interfaces).
                -d, --directory       - Directory to serve. Default: current directory.
                -o, --output          - Output format. none|info|verbose. Default: info.
                -p, --port            - Port to listen on. Default: 8000.
                -?, -h, --help        - Print this help message.
                To stop the server, press Crtl + C.""";

        simpleserver(JAVA, "-m", "jdk.httpserver", opt)
                .resultChecker(r -> {
                    assertContains(r.output, usageText);
                    assertContains(r.output, optionsText);
                });
    }

    @DataProvider
    public Object[][] bindOptions() { return new Object[][] {{"-b"}, {"--bind-address"}}; }

    @Test(dataProvider = "bindOptions")
    public void testlastOneWinsBindAddress(String opt) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, "123.4.5.6", opt, LOCALHOST_ADDR)
                .assertExternalTermination()
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on\n" +
                                    "http://" + LOCALHOST_ADDR);
                });
    }

    @Test(dataProvider = "directoryOptions")
    public void testlastOneWinsDirectory(String opt) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, TEST_DIR_STR, opt, TEST_DIR_STR)
                .assertExternalTermination()
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:");
                    assertContains(r.output,
                            "http://" + LOCALHOST_ADDR);
                });
    }

    @DataProvider
    public Object[][] outputOptions() { return new Object[][] {{"-o"}, {"--output"}}; }

    @Test(dataProvider = "outputOptions")
    public void testlastOneWinsOutput(String opt) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "0", opt, "none", opt, "verbose")
                .assertExternalTermination()
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:");
                    assertContains(r.output,
                            "http://" + LOCALHOST_ADDR);
                });
    }

    @Test(dataProvider = "portOptions")
    public void testlastOneWinsPort(String opt) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", opt, "-999", opt, "0")
                .assertExternalTermination()
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:");
                    assertContains(r.output, "http://" + LOCALHOST_ADDR);
                });
    }

    @AfterTest
    public void deleteTestDirectory() throws IOException {
        if (Files.exists(TEST_DIR))
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
    }

    // --- helper methods ---

    static void assertContains(String output, String subString) {
        var outs = output.replaceAll("\n", System.lineSeparator());
        var subs = subString.replaceAll("\n", System.lineSeparator());
        if (outs.contains(subs))
            assertTrue(true);
        else
            fail("Expected to find [" + subs + "], in output [" + outs + "]");
    }

    static Result simpleserver(String... args) throws Exception {
        System.out.println("simpleserver " + Arrays.toString(args));
        var p = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .directory(TEST_DIR.toFile())
                .start();
        var in = new BufferedInputStream(p.getInputStream());
        var out = new ByteArrayOutputStream();
        var t = new Thread("read-server-output") {
            @Override
            public void run() {
                try (in; out) {
                    in.transferTo(out);
                    out.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        t.start();
        Thread.sleep(5000);
        p.destroyForcibly();
        p.waitFor();
        t.join();
        return new Result(p.exitValue(), out.toString(UTF_8));
    }

    static record Result(int exitCode, String output) {
        Result assertExternalTermination() { assertTrue(exitCode != 0, output); return this; }
        Result resultChecker(Consumer<Result> r) { r.accept(this); return this; }
    }
}
