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
 * @summary Tests for FileServerHandler with SecurityManager
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run main/othervm/java.security.policy=SecurityManagerTestRead.policy -ea SecurityManagerTest true
 * @run main/othervm/java.security.policy=SecurityManagerTestNoRead.policy -ea SecurityManagerTest false
 * @run main/othervm -ea SecurityManagerTest true
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.util.FileUtils;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Tests the permission checks during the creation of a FileServerHandler.
 *
 * A FileServerHandler can only be created if a "read" FilePermission is
 * granted for the root directory passed. The test consists of 3 runs:
 *     1) security manager enabled and "read" FilePermission granted
 *     2) security manager enabled and "read" FilePermission NOT granted
 *     3) security manager NOT enabled
 * 2) misses the required permissions to call many of the java.nio.file methods,
 * the test works around this by reusing the test directory created in the
 * previous run.
* */
public class SecurityManagerTest {

    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("SecurityManagerTest");
    static final InetSocketAddress LOOPBACK_ADDR =
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    static boolean readPermitted;
    static String lastModifiedDir;
    static String lastModifiedFile;

    public static void main(String[] args) throws Exception {
        setupLogging();
        readPermitted = Boolean.parseBoolean(args[0]);
        if (readPermitted) {
            createTestDir();
            testDirectoryGET();
            testFileGET();
        } else {  // no FilePermission "read" for TEST_DIR granted,
                  // assert handler cannot be created
            testCreateHandler();
        }
    }

    private static void setupLogging() {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
    }

    private static void createTestDir() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
        Files.createDirectories(TEST_DIR);
        var file = Files.writeString(TEST_DIR.resolve("aFile.txt"), "some text", CREATE);
        lastModifiedDir = getLastModified(TEST_DIR);
        lastModifiedFile = getLastModified(file);
    }

    private static void testDirectoryGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                <li><a href="aFile.txt">aFile.txt</a></li>
                </ul>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, TEST_DIR, OutputLevel.VERBOSE);

        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assert response.statusCode() == 200;
            assert response.body().equals(expectedBody);
            assert response.headers().firstValue("content-type").get().equals("text/html; charset=UTF-8");
            assert response.headers().firstValue("content-length").get().equals(expectedLength);
            assert response.headers().firstValue("last-modified").get().equals(lastModifiedDir);
        } finally {
            server.stop(0);
        }
    }

    private static void testFileGET() throws Exception {
        var expectedBody = "some text";
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, TEST_DIR, OutputLevel.VERBOSE);

        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assert response.statusCode() == 200;
            assert response.body().equals("some text");
            assert response.headers().firstValue("content-type").get().equals("text/plain");
            assert response.headers().firstValue("content-length").get().equals(expectedLength);
            assert response.headers().firstValue("last-modified").get().equals(lastModifiedFile);
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("removal")
    private static void testCreateHandler(){
        try {
            SimpleFileServer.createFileServer(LOOPBACK_ADDR, TEST_DIR, OutputLevel.NONE);
            throw new RuntimeException("Handler creation expected to fail");
        } catch (AccessControlException expected) { }

        try {
            SimpleFileServer.createFileHandler(TEST_DIR);
            throw new RuntimeException("Handler creation expected to fail");
        } catch (AccessControlException expected) { }
    }

    static final String openHTML = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8"/>
                </head>
                <body>
                """;

    static final String closeHTML = """
                </body>
                </html>
                """;

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    static String getLastModified(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.of("GMT"))
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}
