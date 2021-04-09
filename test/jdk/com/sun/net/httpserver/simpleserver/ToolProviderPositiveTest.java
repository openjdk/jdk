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
 * @summary Positive tests for SimpleFileServerToolProvider
 * @library /test/lib
 * @modules jdk.httpserver
 * @build jdk.test.lib.util.FileUtils
 * @run testng ToolProviderPositiveTest
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ToolProviderPositiveTest {

    static final ToolProvider SIMPLESERVER_TOOL = ToolProvider.findFirst("simpleserver")
            .orElseThrow(() -> new RuntimeException("simpleserver tool not found"));
    static final String JAVA = System.getProperty("java.home") + "/bin/java";
    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("dir");
    static final Path TEST_FILE = TEST_DIR.resolve("file.txt");
    static final String TEST_DIR_STR = TEST_DIR.toString();
    static final String TOOL_PROVIDER_CLS_NAME = SimpleServerTool.class.getName();
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

    //    @Test
    // TODO: which addresses do we expect to succeed?
    public void testBindAddress() throws SocketException {
        NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .map(InetAddress::getHostAddress)
                .forEach(addr -> {
                    try {
                        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-b", addr)
                                .resultChecker(r -> assertContains(r.output,
                                        "Serving " + TEST_DIR_STR + " and subdirectories on\n" +
                                        "http://" + addr + ":8000/ ..."));
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                });
    }

    @Test
    public void testDirectory() throws Exception {
        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-d", TEST_DIR_STR)
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:8000\n" +
                                    "http://" + LOCALHOST_ADDR + ":8000/ ...");
                });
    }

    @DataProvider(name = "ports")
    public Object[][] ports() {
        return new Object[][] {
                {"0"},
                {"8000"},
                {"65535"}
        };
    }
    @Test(dataProvider = "ports")
    public void testPort(String port) throws Exception {
        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-p", port)
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:" + port + "\n" +
                                    "http://" + LOCALHOST_ADDR + ":" + port + "/ ...");
                });
    }

    @Test
    public void testHelp() throws Exception {
        var usageText =
                "Usage: java -m jdk.httpserver [-b bind address] [-d directory] [-o none|default|verbose] [-p port]";
        var optionsText = """
                Options:
                bind address    - Address to bind to. Default: 0.0.0.0 (all interfaces).
                directory       - Directory to serve. Default: current directory.
                output          - Output format. none|default|verbose. Default: default.
                port            - Port to listen on. Default: 8000.
                To stop the server, press Crtl + C.
                """;

        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-h")
                .resultChecker(r -> {
                    assertContains(r.output, usageText);
                    assertContains(r.output, optionsText);
                });
    }

    @Test
    public void testlastOneWinsBindAddress() throws Exception {
        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-b", "localhost", "-b", "127.0.0.1")
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on\n" +
                                    "http://127.0.0.1:8000/ ...");
                });
    }

    @Test
    public void testlastOneWinsDirectory() throws Exception {
        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-d", TEST_DIR_STR, "-d", TEST_DIR_STR)
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:8000\n" +
                                    "http://" + LOCALHOST_ADDR + ":8000/ ...");
                });
    }

    @Test
    public void testlastOneWinsOutput() throws Exception {
        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-o", "none", "-o", "verbose")
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:8000\n" +
                                    "http://" + LOCALHOST_ADDR + ":8000/ ...");
                });
    }

    @Test
    public void testlastOneWinsPort() throws Exception {
        simpleserver(JAVA, TOOL_PROVIDER_CLS_NAME, "-p", "8001", "-p", "8002")
                .resultChecker(r -> {
                    assertContains(r.output,
                            "Serving " + TEST_DIR_STR + " and subdirectories on 0.0.0.0:8002\n" +
                                    "http://" + LOCALHOST_ADDR + ":8002/ ...");
                });
    }

    @AfterTest
    public void deleteTestDirectory() throws IOException {
        if (Files.exists(TEST_DIR))
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
    }

    // --- helper methods ---

    static void assertContains(String output, String subString) {
        if (output.contains(subString))
            assertTrue(true);
        else fail("Expected to find [" + subString + "], in output [" + output + "]");
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
                try {
                    in.transferTo(out);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        t.start();
        Thread.sleep(5000);
        p.destroy();
        t.join();
        return new Result(out.toString(UTF_8));
    }

    static record Result(String output) {
        Result resultChecker(Consumer<Result> r) { r.accept(this); return this; }
    }

    static class SimpleServerTool {
        public static void main(String[] args) {
            var ps = new PrintStream(System.out);
            SIMPLESERVER_TOOL.run(ps, ps, args);
        }
    }
}
