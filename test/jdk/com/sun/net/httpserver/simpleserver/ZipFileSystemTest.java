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
 * @summary Tests for SimpleFileServer with a root that is of a zip file system
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.URIBuilder
 * @run junit/othervm ZipFileSystemTest
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
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

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ZipFileSystemTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final Path TEST_DIR = CWD.resolve("ZipFileSystemTest");

    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @BeforeAll
    public static void setup() throws Exception {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
        Files.createDirectories(TEST_DIR);
    }

    @Test
    public void testFileGET() throws Exception {
        var root = createZipFs(TEST_DIR.resolve("testFileGET.zip"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("some text", response.body());
            assertEquals("text/plain", response.headers().firstValue("content-type").get());
            assertEquals(expectedLength, response.headers().firstValue("content-length").get());
            assertEquals(lastModified, response.headers().firstValue("last-modified").get());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testFileGET.zip"));
        }
    }

    @Test
    public void testDirectoryGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                </ul>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createZipFs(TEST_DIR.resolve("testDirectoryGET.zip"));
        var lastModified = getLastModified(root);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("text/html; charset=UTF-8", response.headers().firstValue("content-type").get());
            assertEquals(expectedLength, response.headers().firstValue("content-length").get());
            assertEquals(lastModified, response.headers().firstValue("last-modified").get());
            assertEquals(expectedBody, response.body());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testDirectoryGET.zip"));
        }
    }

    @Test
    public void testFileHEAD() throws Exception {
        var root = createZipFs(TEST_DIR.resolve("testFileHEAD.zip"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile.txt"))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("text/plain", response.headers().firstValue("content-type").get());
            assertEquals(expectedLength, response.headers().firstValue("content-length").get());
            assertEquals(lastModified, response.headers().firstValue("last-modified").get());
            assertEquals("", response.body());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testFileHEAD.zip"));
        }
    }

    @Test
    public void testDirectoryHEAD() throws Exception {
        var expectedLength = Integer.toString(
                (openHTML + """
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                </ul>
                """ + closeHTML).getBytes(UTF_8).length);
        var root = createZipFs(TEST_DIR.resolve("testDirectoryHEAD.zip"));
        var lastModified = getLastModified(root);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, ""))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("text/html; charset=UTF-8", response.headers().firstValue("content-type").get());
            assertEquals(expectedLength, response.headers().firstValue("content-length").get());
            assertEquals(lastModified, response.headers().firstValue("last-modified").get());
            assertEquals("", response.body());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testDirectoryHEAD.zip"));
        }
    }

    public static Object[][] indexFiles() {
        var fileContent = openHTML + """
                <h1>This is an index file</h1>
                """ + closeHTML;
        var dirListing = openHTML + """
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                </ul>
                """ + closeHTML;
        return new Object[][] {
                {"1", "index.html", "text/html",                "116", fileContent, true},
                {"2", "index.htm",  "text/html",                "116", fileContent, true},
                {"3", "index.txt",  "text/html; charset=UTF-8", "134", dirListing,  false}
        };
    }

    @ParameterizedTest
    @MethodSource("indexFiles")
    public void testDirectoryWithIndexGET(String id,
                                          String filename,
                                          String contentType,
                                          String contentLength,
                                          String expectedBody,
                                          boolean serveIndexFile) throws Exception {
        var root = createZipFs(TEST_DIR.resolve("testDirectoryWithIndexGET"+id+".zip"));
        var lastModified = getLastModified(root);
        if (serveIndexFile) {
            var file = Files.writeString(root.resolve(filename), expectedBody, CREATE);
            lastModified = getLastModified(file);
        }

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(contentType, response.headers().firstValue("content-type").get());
            assertEquals(contentLength, response.headers().firstValue("content-length").get());
            assertEquals(lastModified, response.headers().firstValue("last-modified").get());
            assertEquals(expectedBody, response.body());
        } finally {
            server.stop(0);
            if (serveIndexFile) {
                Files.delete(root.resolve(filename));
            }
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testDirectoryWithIndexGET"+id+".zip"));
        }
    }

    // no testNotReadableGET() - Zip file system does not enforce access permissions
    // no testSymlinkGET() - Zip file system does not support symlink creation
    // no testHiddenFileGET() - Zip file system does not support hidden files

    @Test
    public void testInvalidRequestURIGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;aFile?#.txt</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createZipFs(TEST_DIR.resolve("testInvalidRequestURIGET.zip"));
        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile?#.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
            assertEquals(expectedLength, response.headers().firstValue("content-length").get());
            assertEquals(expectedBody, response.body());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testInvalidRequestURIGET.zip"));
        }
    }

    @Test
    public void testNotFoundGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;doesNotExist.txt</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createZipFs(TEST_DIR.resolve("testNotFoundGET.zip"));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "doesNotExist.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
            assertEquals(expectedLength, response.headers().firstValue("content-length").get());
            assertEquals(expectedBody, response.body());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testNotFoundGET.zip"));
        }
    }

    @Test
    public void testNotFoundHEAD() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;doesNotExist.txt</p>
                """
                + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createZipFs(TEST_DIR.resolve("testNotFoundHEAD.zip"));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "doesNotExist.txt"))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
            assertEquals(expectedLength, response.headers().firstValue("content-length").get());
            assertEquals("", response.body());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testNotFoundHEAD.zip"));
        }
    }

    @Test
    public void testMovedPermanently() throws Exception {
        var root = createZipFs(TEST_DIR.resolve("testMovedPermanently.zip"));
        Files.createDirectory(root.resolve("aDirectory"));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var uri = uri(server, "aDirectory");
            var request = HttpRequest.newBuilder(uri).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(301, response.statusCode());
            assertEquals("0", response.headers().firstValue("content-length").get());
            assertEquals("/aDirectory/", response.headers().firstValue("location").get());
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testMovedPermanently.zip"));
        }
    }

    static Path createZipFs(Path zipFile) throws Exception {
        var p = zipFile.toAbsolutePath().normalize();
        var fs = FileSystems.newFileSystem(p, Map.of("create", "true"));
        assert fs != FileSystems.getDefault();
        return fs.getPath("/");  // root entry
    }

    @Test
    public void testXss() throws Exception {
        var root = createZipFs(TEST_DIR.resolve("testXss.zip"));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "beginDelim%3C%3EEndDelim")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("beginDelim%3C%3EEndDelim"));
            assertTrue(response.body().contains("File not found"));
        } finally {
            server.stop(0);
            root.getFileSystem().close();
            Files.deleteIfExists(TEST_DIR.resolve("testXss.zip"));
        }
    }

    @AfterAll
    public static void teardown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
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
