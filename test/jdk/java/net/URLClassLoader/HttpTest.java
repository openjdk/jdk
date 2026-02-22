/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4636331
 * @library /test/lib
 * @summary Check that URLClassLoader with HTTP paths lookups produce the expected http requests
 * @run junit HttpTest
 */
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpTest {

    // HTTP server used to track requests
    static HttpServer server;

    // RequestLog for capturing and asserting requests
    static class RequestLog {
        List<Request> log = new ArrayList<>();

        // Expect a given number of requests
        synchronized void expectSize(int expected) {
            assertEquals(expected, log.size());
        }

        // Expect nth request to have a given method and path
        synchronized void expect(int nth, String method, String path) {
            Request request = log.get(nth);
            assertEquals(method, request.method);
            assertEquals(path, request.path.getPath());
        }

        // Add a request to the log
        public synchronized void capture(String method, URI uri) {
            log.add(new Request(method, uri));
        }

        // Clear requests
        public synchronized void clear() {
            log.clear();
        }
    }

    // Represents a single request
    record Request(String method, URI path) {}

    // Request log for this test
    static RequestLog log = new RequestLog();

    // Any paths which should give 404 responses
    static Set<URI> invalidPaths = new CopyOnWriteArraySet<>();

    // URLClassLoader with HTTP URL class path
    private static URLClassLoader loader;

    @BeforeAll
    static void setup() throws Exception {
        server = HttpServer.create();
        server.bind(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            // Capture request in the log
            log.capture(ex.getRequestMethod(), ex.getRequestURI());
            // Check for invalid paths
            if (invalidPaths.contains(ex.getRequestURI())) {
                ex.sendResponseHeaders(404, 0);
            } else {
                // Successful responses echo the request path in the body
                byte[] response = ex.getRequestURI().getPath()
                        .getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, response.length);
                try (var out = ex.getResponseBody()) {
                    out.write(response);
                }
                ex.close();
            }
        });
        server.start();
        int port = server.getAddress().getPort();

        // Create class loader with two HTTP URLs
        URL[] searchPath = new URL[] {
                getHttpUri("/dir1/", port),
                getHttpUri("/dir2/", port)
        };
        loader = new URLClassLoader(searchPath);
    }

    // Create an HTTP URL for the given path and port using the loopback address
    private static URL getHttpUri(String path, int port) throws Exception {
        return URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path(path).toURL();
    }

    @AfterAll
    static void shutdown() {
        server.stop(2000);
    }

    @BeforeEach
    void reset() {
        synchronized (log) {
            log.clear();
        }
        invalidPaths.clear();
    }

    // Check that getResource does single HEAD request
    @Test
    void getResourceSingleHead() {
        URL url = loader.getResource("foo.gif");
        // Expect one HEAD
        assertRequests(e -> e
                .request("HEAD", "/dir1/foo.gif")
        );
    }

    // Check that getResourceAsStream does one HEAD and one GET request
    @Test
    void getResourceAsStreamSingleGet() throws IOException {
        // Expect content from the first path
        try (var in = loader.getResourceAsStream("foo2.gif")) {
            assertEquals("/dir1/foo2.gif",
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        // Expect one HEAD, one GET
        assertRequests( e -> e
                .request("HEAD", "/dir1/foo2.gif")
                .request("GET",  "/dir1/foo2.gif")
        );
    }

    // getResourceAsStream on a 404 should try next path
    @Test
    void getResourceTryNextPath() throws IOException {
        // Make the first path return 404
        invalidPaths.add(URI.create("/dir1/foo.gif"));
        // Expect content from the second path
        try (var in = loader.getResourceAsStream("foo.gif")) {
            assertEquals("/dir2/foo.gif",
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        // Expect two HEADs, one GET
        assertRequests(e -> e
                .request("HEAD", "/dir1/foo.gif")
                .request("HEAD", "/dir2/foo.gif")
                .request("GET",  "/dir2/foo.gif")
        );
    }

    // Check that getResources only does HEAD requests
    @Test
    void getResourcesOnlyHead() throws IOException {
        Collections.list(loader.getResources("foos.gif"));
        // Expect one HEAD for each path
        assertRequests(e ->  e
                .request("HEAD", "/dir1/foos.gif")
                .request("HEAD", "/dir2/foos.gif")
        );
    }

    // Check that getResources skips 404 URL
    @Test
    void getResourcesShouldSkipFailedHead() throws IOException {
        // Make first path fail with 404
        invalidPaths.add(URI.create("/dir1/foos.gif"));
        List<URL> resources = Collections.list(loader.getResources("foos.gif"));
        // Expect one HEAD for each path
        assertRequests(e ->  e
                .request("HEAD", "/dir1/foos.gif")
                .request("HEAD", "/dir2/foos.gif")
        );

        // Expect a single URL to be returned
        assertEquals(1, resources.size());
    }

    // Utils for asserting requests
    static class Expect {
        List<Request> log = new ArrayList<>();

        Expect request(String method, String path) {
            log.add(new Request(method, URI.create(path)));
            return this;
        }
    }

    static void assertRequests(Consumer<Expect> e) {
        Expect expected = new Expect();
        e.accept(expected);

        // Verify expected number of requests
        assertEquals(expected.log.size(), log.log.size(), "Unexpected request count");

        // Verify expected requests in order
        for (int i = 0; i < expected.log.size(); i++) {
            Request ex = expected.log.get(i);
            Request req = log.log.get(i);
            // Verify method
            assertEquals(ex.method, req.method,
                    String.format("Request #%s has unexpected method %s", i, ex.method)
            );
            // Verify path
            assertEquals(ex.path, req.path,
                    String.format("Request %s has unexpected request URI %s", i, ex.path)
            );
        }
    }
}
