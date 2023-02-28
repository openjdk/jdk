/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278398
 * @summary Tests the jwebserver's maximum request time
 * @modules jdk.httpserver
 * @library /test/lib
 * @run testng/othervm MaxRequestTimeTest
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import jdk.test.lib.Platform;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.*;

/**
 * This test confirms that the jwebserver does not wait indefinitely for
 * a request to arrive.
 *
 * The jwebserver has a maximum request time of 5 seconds, which is set with the
 * "sun.net.httpserver.maxReqTime" system property. If this threshold is
 * reached, for example in the case of an HTTPS request where the server keeps
 * waiting for a plaintext request, the server closes the connection. Subsequent
 * requests are expected to be handled as normal.
 *
 * The test checks in the following order that:
 *    1. an HTTP request is handled successfully,
 *    2. an HTTPS request fails due to the server closing the connection
 *    3. another HTTP request is handled successfully.
 */
public class MaxRequestTimeTest {
    static final Path JAVA_HOME = Path.of(System.getProperty("java.home"));
    static final String JWEBSERVER = getJwebserver(JAVA_HOME);
    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("MaxRequestTimeTest");
    static final String LOOPBACK_ADDR = InetAddress.getLoopbackAddress().getHostAddress();
    static final AtomicInteger PORT = new AtomicInteger();

    static SSLContext sslContext;

    @BeforeTest
    public void setup() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
        Files.createDirectories(TEST_DIR);

        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");
    }

    @Test
    public void testMaxRequestTime() throws Throwable {
        final var sb = new StringBuffer();  // stdout & stderr
        final var p = startProcess("jwebserver", sb);
        try {
            sendHTTPSRequest();  // server expected to terminate connection
            sendHTTPRequest();   // server expected to respond successfully
            sendHTTPSRequest();  // server expected to terminate connection
            sendHTTPRequest();   // server expected to respond successfully
        } finally {
            p.destroy();
            int exitCode = p.waitFor();
            checkOutput(sb, exitCode);
        }
    }

    static String expectedBody = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8"/>
                </head>
                <body>
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                </ul>
                </body>
                </html>
                """;

    void sendHTTPRequest() throws IOException, InterruptedException {
        out.println("\n--- sendHTTPRequest");
        var client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .build();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT.get() + "/")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(response.body(), expectedBody);
    }

    void sendHTTPSRequest() throws IOException, InterruptedException {
        out.println("\n--- sendHTTPSRequest");
        var client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .proxy(NO_PROXY)
                .build();
        var request = HttpRequest.newBuilder(URI.create("https://localhost:" + PORT.get() + "/")).build();
        try {
            client.send(request, HttpResponse.BodyHandlers.ofString());
            throw new RuntimeException("Expected SSLException not thrown");
        } catch (SSLException expected) {  // server closes connection when max request time is reached
            expected.printStackTrace(System.out);
        }
    }

    @AfterTest
    public void teardown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
    }

    // --- infra ---

    static String getJwebserver(Path image) {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        Path jwebserver = image.resolve("bin").resolve(isWindows ? "jwebserver.exe" : "jwebserver");
        if (Files.notExists(jwebserver))
            throw new RuntimeException(jwebserver + " not found");
        return jwebserver.toAbsolutePath().toString();
    }

    // The stdout/stderr output line to wait for when starting the jwebserver
    static final String REGULAR_STARTUP_LINE_STRING_1 = "URL http://";
    static final String REGULAR_STARTUP_LINE_STRING_2 = "Serving ";

    static void parseAndSetPort(String line) {
        PORT.set(Integer.parseInt(line.split(" port ")[1]));
    }

    static Process startProcess(String name, StringBuffer sb) throws Throwable {
        // starts the process, parses the port and awaits startup line before sending requests
        return ProcessTools.startProcess(name,
                new ProcessBuilder(JWEBSERVER, "-p", "0").directory(TEST_DIR.toFile()),
                line -> {
                    if (line.startsWith(REGULAR_STARTUP_LINE_STRING_2)) { parseAndSetPort(line); }
                    sb.append(line + "\n");
                },
                line -> line.startsWith(REGULAR_STARTUP_LINE_STRING_1),
                30,  // suitably high default timeout, not expected to timeout
                TimeUnit.SECONDS);
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

    static void checkOutput(StringBuffer sb, int exitCode) {
        out.println("\n--- server output: \n" + sb);
        var outputAnalyser = new OutputAnalyzer(sb.toString(), "", exitCode);
            outputAnalyser.shouldHaveExitValue(NORMAL_EXIT_CODE)
                          .shouldContain("Binding to loopback by default. For all interfaces use \"-b 0.0.0.0\" or \"-b ::\".")
                          .shouldContain("Serving " + TEST_DIR + " and subdirectories on " + LOOPBACK_ADDR + " port " + PORT)
                          .shouldContain("URL http://" + LOOPBACK_ADDR);
    }
}
