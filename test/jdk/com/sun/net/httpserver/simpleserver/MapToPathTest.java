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
 * @summary Tests the FileServerHandler's mapping of request URI path to file
 *          system path
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.URIBuilder
 * @run testng/othervm MapToPathTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.assertEquals;

public class MapToPathTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final Path TEST_DIR = CWD.resolve("MapToPathTest").normalize();

    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @BeforeTest
    public void setup() throws IOException {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
        createTestDirectories(TEST_DIR);
    }

    private void createTestDirectories(Path testDir) throws IOException {
        //      Create test directory of the following structure:
        //
        //      |-- TEST_DIR
        //          |-- foo
        //              |-- file.txt
        //          |-- foobar
        //              |-- file.txt
        //          |-- file.txt

        Files.createDirectories(TEST_DIR);
        Stream.of("foo", "foobar").forEach(s -> {
            try {
                Path p = testDir.resolve(s);
                Files.createDirectories(p);
                Files.writeString(p.resolve("file.txt"), s, CREATE);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
        Files.writeString(testDir.resolve("file.txt"), "testdir", CREATE);
    }

    @Test
    public void test() throws Exception {
        {
            var h = SimpleFileServer.createFileHandler(TEST_DIR);
            var ss = HttpServer.create(LOOPBACK_ADDR, 10, "/", h, SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE));
            ss.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
                var req1 = HttpRequest.newBuilder(uri(ss, "/")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(218L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR));

                var req2 = HttpRequest.newBuilder(uri(ss, "/../")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // cannot escape root
            } finally {
                ss.stop(0);
            }
        }
        {
            var h = SimpleFileServer.createFileHandler(TEST_DIR);
            var ss = HttpServer.create(LOOPBACK_ADDR, 10, "/browse/", h, SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE));
            ss.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
                var req1 = HttpRequest.newBuilder(uri(ss, "/browse/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.headers().firstValue("content-type").get(), "text/plain");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(7L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR));

                var req2 = HttpRequest.newBuilder(uri(ss, "/store/file.txt")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // no context found
            } finally {
                ss.stop(0);
            }
        }
        {
            var h = SimpleFileServer.createFileHandler(TEST_DIR.resolve("foo"));
            var ss = HttpServer.create(LOOPBACK_ADDR, 10, "/foo/", h, SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE));
            ss.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();

                var req1 = HttpRequest.newBuilder(uri(ss, "/foo/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.headers().firstValue("content-type").get(), "text/plain");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(3L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR));

                var req2 = HttpRequest.newBuilder(uri(ss, "/foobar/file.txt")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // no context found

                var req3 = HttpRequest.newBuilder(uri(ss, "/foo/../foobar/file.txt")).build();
                var res3 = client.send(req3, BodyHandlers.ofString());
                assertEquals(res3.statusCode(), 404);  // cannot escape context

                var req4 = HttpRequest.newBuilder(uri(ss, "/foo/../..")).build();
                var res4 = client.send(req4, BodyHandlers.ofString());
                assertEquals(res4.statusCode(), 404);  // cannot escape root
            } finally {
                ss.stop(0);
            }
        }
        {
            var h = SimpleFileServer.createFileHandler(TEST_DIR.resolve("foo"));
            var ss = HttpServer.create(LOOPBACK_ADDR, 10, "/foo", h, SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE));
            ss.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();

                var req1 = HttpRequest.newBuilder(uri(ss, "/foo/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.headers().firstValue("content-type").get(), "text/plain");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(3L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR));

                var req2 = HttpRequest.newBuilder(uri(ss, "/foobar/")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // handler corrects context to "/foo/"
            } finally {
                ss.stop(0);
            }
        }
    }

    @AfterTest
    public void teardown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
    }

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path(path)
                .buildUnchecked();
    }

    static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss v");

    static String getLastModified(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.of("GMT"))
                .format(HTTP_DATE_FORMATTER);
    }
}
