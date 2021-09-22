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
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandlers;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.assertEquals;

public class MapToPathTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final Path TEST_DIR = CWD.resolve("MapToPathTest").normalize();

    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    static final Filter OUTPUT_FILTER = SimpleFileServer.createOutputFilter(out, OutputLevel.VERBOSE);

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
        createDirectories(TEST_DIR);
    }

    private void createDirectories(Path testDir) throws IOException {
        //      Create directory tree:
        //
        //      |-- TEST_DIR
        //          |-- foo
        //              |-- bar
        //                  |-- baz
        //                      |-- file.txt
        //              |-- file.txt
        //          |-- foobar
        //              |-- file.txt
        //          |-- file.txt

        Files.createDirectories(TEST_DIR);
        Stream.of("foo", "foobar", "foo/bar/baz").forEach(s -> {
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
        var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
        {
            var handler = SimpleFileServer.createFileHandler(TEST_DIR);
            var server = HttpServer.create(LOOPBACK_ADDR, 10, "/", handler, OUTPUT_FILTER);
            server.start();
            try {
                var req1 = HttpRequest.newBuilder(uri(server, "/")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(257L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR));

                var req2 = HttpRequest.newBuilder(uri(server, "/../")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // cannot escape root

                var req3 = HttpRequest.newBuilder(uri(server, "/foo/bar/baz/c://")).build();
                var res3 = client.send(req3, BodyHandlers.ofString());
                assertEquals(res3.statusCode(), 404);  // not found

                var req4 = HttpRequest.newBuilder(uri(server, "/foo/file:" + TEST_DIR.getParent())).build();
                var res4 = client.send(req4, BodyHandlers.ofString());
                assertEquals(res4.statusCode(), 404);  // not found

                var req5 = HttpRequest.newBuilder(uri(server, "/foo/bar/\\..\\../")).build();
                var res5 = client.send(req5, BodyHandlers.ofString());
                assertEquals(res5.statusCode(), 404);  // not found

                var req6 = HttpRequest.newBuilder(uri(server, "/foo")).build();
                var res6 = client.send(req6, BodyHandlers.ofString());
                assertEquals(res6.statusCode(), 301);  // redirect
                assertEquals(res6.headers().firstValue("content-length").get(), "0");
                assertEquals(res6.headers().firstValue("location").get(), "/foo/");
            } finally {
                server.stop(0);
            }
        }
        {
            var handler = SimpleFileServer.createFileHandler(TEST_DIR);
            var server = HttpServer.create(LOOPBACK_ADDR, 10, "/browse/", handler, OUTPUT_FILTER);
            server.start();
            try {
                var req1 = HttpRequest.newBuilder(uri(server, "/browse/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.body(), "testdir");
                assertEquals(res1.headers().firstValue("content-type").get(), "text/plain");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(7L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR.resolve("file.txt")));

                var req2 = HttpRequest.newBuilder(uri(server, "/store/file.txt")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // no context found
            } finally {
                server.stop(0);
            }
        }
        {
            // Test "/foo/" context (with trailing slash)
            var handler = SimpleFileServer.createFileHandler(TEST_DIR.resolve("foo"));
            var server = HttpServer.create(LOOPBACK_ADDR, 10, "/foo/", handler, OUTPUT_FILTER);
            server.start();
            try {
                var req1 = HttpRequest.newBuilder(uri(server, "/foo/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.body(), "foo");
                assertEquals(res1.headers().firstValue("content-type").get(), "text/plain");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(3L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR.resolve("foo").resolve("file.txt")));

                var req2 = HttpRequest.newBuilder(uri(server, "/foobar/file.txt")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // no context found

                var req3 = HttpRequest.newBuilder(uri(server, "/foo/../foobar/file.txt")).build();
                var res3 = client.send(req3, BodyHandlers.ofString());
                assertEquals(res3.statusCode(), 404);  // cannot escape context

                var req4 = HttpRequest.newBuilder(uri(server, "/foo/../..")).build();
                var res4 = client.send(req4, BodyHandlers.ofString());
                assertEquals(res4.statusCode(), 404);  // cannot escape root

                var req5 = HttpRequest.newBuilder(uri(server, "/foo/bar")).build();
                var res5 = client.send(req5, BodyHandlers.ofString());
                assertEquals(res5.statusCode(), 301);  // redirect
                assertEquals(res5.headers().firstValue("content-length").get(), "0");
                assertEquals(res5.headers().firstValue("location").get(), "/foo/bar/");
            } finally {
                server.stop(0);
            }
        }
        {
            // Test "/foo" context (without trailing slash)
            var handler = SimpleFileServer.createFileHandler(TEST_DIR.resolve("foo"));
            var server = HttpServer.create(LOOPBACK_ADDR, 10, "/foo", handler, OUTPUT_FILTER);
            server.start();
            try {
                var req1 = HttpRequest.newBuilder(uri(server, "/foo/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.body(), "foo");
                assertEquals(res1.headers().firstValue("content-type").get(), "text/plain");
                assertEquals(res1.headers().firstValue("content-length").get(), Long.toString(3L));
                assertEquals(res1.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR.resolve("foo").resolve("file.txt")));

                var req2 = HttpRequest.newBuilder(uri(server, "/foobar/")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 404);  // handler prevents mapping to /foo/bar

                var req3 = HttpRequest.newBuilder(uri(server, "/foobar/file.txt")).build();
                var res3 = client.send(req3, BodyHandlers.ofString());
                assertEquals(res3.statusCode(), 404);  // handler prevents mapping to /foo/bar/file.txt

                var req4 = HttpRequest.newBuilder(uri(server, "/file.txt")).build();
                var res4 = client.send(req4, BodyHandlers.ofString());
                assertEquals(res4.statusCode(), 404);

                var req5 = HttpRequest.newBuilder(uri(server, "/foo/bar")).build();
                var res5 = client.send(req5, BodyHandlers.ofString());
                assertEquals(res5.statusCode(), 301);  // redirect
                assertEquals(res5.headers().firstValue("content-length").get(), "0");
                assertEquals(res5.headers().firstValue("location").get(), "/foo/bar/");

                var req6 = HttpRequest.newBuilder(uri(server, "/foo")).build();
                var res6 = client.send(req6, BodyHandlers.ofString());
                assertEquals(res6.statusCode(), 301);  // redirect
                assertEquals(res6.headers().firstValue("content-length").get(), "0");
                assertEquals(res6.headers().firstValue("location").get(), "/foo/");
            } finally {
                server.stop(0);
            }
        }
    }

    // Tests with a mixture of in-memory and file handlers.
    @Test
    public void multipleContexts() throws Exception {
        var rootHandler = HttpHandlers.of(200, Headers.of(), "root response body");
        var fooHandler = SimpleFileServer.createFileHandler(TEST_DIR.resolve("foo"));
        var foobarHandler = SimpleFileServer.createFileHandler(TEST_DIR.resolve("foobar"));
        var barHandler = HttpHandlers.of(200, Headers.of(), "bar response body");

        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", rootHandler);
        server.createContext("/foo/", fooHandler);
        server.createContext("/bar/", barHandler);
        server.createContext("/foobar/", foobarHandler);
        server.start();
        var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
        try {
            for (String uriPath : List.of("/", "/blah", "/xyz/t/z", "/txt") ) {
                out.println("uri.Path=" + uriPath);
                var req1 = HttpRequest.newBuilder(uri(server, uriPath)).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.body(), "root response body");
            }
            {
                var req1 = HttpRequest.newBuilder(uri(server, "/foo/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.body(), "foo");

                var req2 = HttpRequest.newBuilder(uri(server, "/foo/bar/baz/file.txt")).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 200);
                assertEquals(res2.body(), "foo/bar/baz");
            }
            {
                var req1 = HttpRequest.newBuilder(uri(server, "/foobar/file.txt")).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.body(), "foobar");
            }
            for (String uriPath : List.of("/bar/", "/bar/t", "/bar/t/z", "/bar/index.html") ) {
                out.println("uri.Path=" + uriPath);
                var req1 = HttpRequest.newBuilder(uri(server, uriPath)).build();
                var res1 = client.send(req1, BodyHandlers.ofString());
                assertEquals(res1.statusCode(), 200);
                assertEquals(res1.body(), "bar response body");
            }
        } finally {
            server.stop(0);
        }
    }

    // Tests requests with queries, which are simply ignored by the handler
    @Test
    public void requestWithQuery() throws Exception {
        var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
        var handler = SimpleFileServer.createFileHandler(TEST_DIR);
        var server = HttpServer.create(LOOPBACK_ADDR, 10, "/", handler, OUTPUT_FILTER);
        server.start();
        try {
            for (String query : List.of("x=y", "x=", "xxx", "#:?") ) {
                out.println("uri.Query=" + query);
                var req = HttpRequest.newBuilder(uri(server, "", query)).build();
                var res = client.send(req, BodyHandlers.ofString());
                assertEquals(res.statusCode(), 200);
                assertEquals(res.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
                assertEquals(res.headers().firstValue("content-length").get(), Long.toString(257L));
                assertEquals(res.headers().firstValue("last-modified").get(), getLastModified(TEST_DIR));
            }
        } finally {
            server.stop(0);
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

    static URI uri(HttpServer server, String path, String query) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path(path)
                .query(query)
                .buildUnchecked();
    }

    static String getLastModified(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.of("GMT"))
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}
