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
 * @modules jdk.httpserver
 * @library /test/lib
 * @summary Check that URLClassLoader with HTTP paths lookups produce the expected http requests
 * @run junit HttpTest
 */
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpHandler;
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

    // RequestLog for capturing requests
    static class RequestLog {
        List<Request> log = new ArrayList<>();

        // Add a request to the log
        public synchronized void capture(String method, URI uri) {
            log.add(new Request(method, uri));
        }

        // Clear requests
        public synchronized void clear() {
            log.clear();
        }

        public synchronized List<Request> requests() {
            return List.copyOf(log);
        }
    }

    // Represents a single request
    record Request(String method, URI path) {}

    // Request log for this test
    static RequestLog log = new RequestLog();

    // Handlers specific to tests
    static Map<URI, HttpHandler> handlers = new ConcurrentHashMap<>();

    // URLClassLoader with HTTP URL class path
    private static URLClassLoader loader;

    @BeforeAll
    static void setup() throws Exception {
        server = HttpServer.create();
        server.bind(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", e -> {
            // Capture request in the log
            log.capture(e.getRequestMethod(), e.getRequestURI());
            // Check for custom handler
            HttpHandler custom = handlers.get(e.getRequestURI());
            if (custom != null) {
                custom.handle(e);
            } else {
                // Successful responses echo the request path in the body
                byte[] response = e.getRequestURI().getPath()
                        .getBytes(StandardCharsets.UTF_8);
                e.sendResponseHeaders(200, response.length);
                try (var out = e.getResponseBody()) {
                    out.write(response);
                }
            }
            e.close();
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

    // Add redirect handler for a given path
    private static void redirect(String path, String target) {
        handlers.put(URI.create(path), e -> {
            e.getResponseHeaders().set("Location", target);
            e.sendResponseHeaders(301, 0);
        });
    }

    // Return 404 not found for a given path
    private static void notFound(String path) {
        handlers.put(URI.create(path),  e ->
                e.sendResponseHeaders(404, 0));
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
        handlers.clear();
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

    // Check that getResource follows redirects
    @Test
    void getResourceShouldFollowRedirect() {
        redirect("/dir1/foo.gif", "/dir1/target.gif");
        URL url = loader.getResource("foo.gif");
        // Expect extra HEAD for redirect target
        assertRequests(e -> e
                .request("HEAD", "/dir1/foo.gif")
                .request("HEAD", "/dir1/target.gif")
        );

        /*
         * Note: Long-standing behavior is that URLClassLoader:getResource
         * returns a URL for the requested resource, not the location redirected to
         */
        assertEquals("/dir1/foo.gif", url.getPath());

    }

    // Check that getResource treats a redirect to a not-found resource as a not-found resource
    @Test
    void getResourceRedirectTargetNotFound() {
        redirect("/dir1/foo.gif", "/dir1/target.gif");
        notFound("/dir1/target.gif");
        URL url = loader.getResource("foo.gif");
        // Expect extra HEAD for redirect target and next URL in search path
        assertRequests(e -> e
                .request("HEAD", "/dir1/foo.gif")
                .request("HEAD", "/dir1/target.gif")
                .request("HEAD", "/dir2/foo.gif")

        );
        // Should find URL for /dir2
        assertEquals("/dir2/foo.gif", url.getPath());
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

    // Check that getResourceAsStream follows redirects
    @Test
    void getResourceAsStreamFollowRedirect() throws IOException {
        redirect("/dir1/foo.gif", "/dir1/target.gif");
        // Expect content from the redirected location
        try (var in = loader.getResourceAsStream("foo.gif")) {
            assertEquals("/dir1/target.gif",
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        /*
         * Note: Long standing behavior of URLClassLoader::getResourceAsStream
         * is to use HEAD during the findResource resource discovery and to not
         * "remember" the HEAD redirect location when performing the GET. This
         * explains why we observe two redirects here, one for HEAD, one for GET.
         */
        assertRequests( e -> e
                .request("HEAD", "/dir1/foo.gif")
                .request("HEAD", "/dir1/target.gif")
                .request("GET",  "/dir1/foo.gif")
                .request("GET",  "/dir1/target.gif")
        );
    }

    // getResourceAsStream on a 404 should try next path
    @Test
    void getResourceTryNextPath() throws IOException {
        // Make the first path return 404
        notFound("/dir1/foo.gif");
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
        notFound("/dir1/foos.gif");
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
        List<Request> requests = new ArrayList<>();

        Expect request(String method, String path) {
            requests.add(new Request(method, URI.create(path)));
            return this;
        }
    }

    static void assertRequests(Consumer<Expect> e) {
        // Collect expected requests
        Expect exp = new Expect();
        e.accept(exp);
        List<Request> expected = exp.requests;

        // Actual requests
        List<Request> requests = log.requests();

        // Verify expected number of requests
        assertEquals(expected.size(), requests.size(), "Unexpected request count");

        // Verify expected requests in order
        for (int i = 0; i < expected.size(); i++) {
            Request ex = expected.get(i);
            Request req = requests.get(i);
            // Verify method
            assertEquals(ex.method, req.method,
                    String.format("Request %s has unexpected method %s", i, ex.method)
            );
            // Verify path
            assertEquals(ex.path, req.path,
                    String.format("Request %s has unexpected request URI %s", i, ex.path)
            );
        }
    }
}
