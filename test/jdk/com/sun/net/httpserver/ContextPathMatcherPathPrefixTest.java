/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static java.net.http.HttpClient.Builder.NO_PROXY;

import org.junit.jupiter.api.AfterAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/*
 * @test id=default
 * @bug 8272758
 * @summary Verifies path prefix matching using defaults
 * @build EchoHandler
 * @run junit ${test.main.class}
 */

/*
 * @test id=withProperty
 * @bug 8272758
 * @summary Verifies path prefix matching by providing a system property
 * @build EchoHandler
 * @run junit/othervm
 *      -Dsun.net.httpserver.pathMatcher=pathPrefix
 *      ${test.main.class}
 */

/*
 * @test id=withInvalidProperty
 * @bug 8272758
 * @summary Verifies path prefix matching by providing a system property
 *          containing an invalid value, and observing it fall back to the
 *          default
 * @build EchoHandler
 * @run junit/othervm
 *      -Dsun.net.httpserver.pathMatcher=noSuchMatcher
 *      ${test.main.class}
 */

public class ContextPathMatcherPathPrefixTest {

    protected static final HttpClient CLIENT =
            HttpClient.newBuilder().proxy(NO_PROXY).build();

    @AfterAll
    static void stopClient() {
        CLIENT.shutdownNow();
    }

    @Test
    void testContextPathOfEmptyString() {
        var iae = assertThrows(IllegalArgumentException.class, () -> new Infra(""));
        assertEquals("Illegal value for path or protocol", iae.getMessage());
    }

    @Test
    void testContextPathAtRoot() throws Exception {
        try (var infra = new Infra("/")) {
            infra.expect(200, "/foo", "/foo/", "/foo/bar", "/foobar");
        }
    }

    @Test
    void testContextPathAtSubDir() throws Exception {
        try (var infra = new Infra("/foo")) {
            infra.expect(200, "/foo", "/foo/", "/foo/bar");
            infra.expect(404, "/foobar");
        }
    }

    @Test
    void testContextPathAtSubDirWithTrailingSlash() throws Exception {
        try (var infra = new Infra("/foo/")) {
            infra.expect(200, "/foo/", "/foo/bar");
            infra.expect(404, "/foo", "/foobar");
        }
    }

    protected static final class Infra implements AutoCloseable {

        private static final InetSocketAddress LO_SA_0 =
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        private static final HttpHandler HANDLER = new EchoHandler();

        private final HttpServer server;

        private final String contextPath;

        protected Infra(String contextPath) throws IOException {
            this.server = HttpServer.create(LO_SA_0, 10);
            server.createContext(contextPath, HANDLER);
            server.start();
            this.contextPath = contextPath;
        }

        protected void expect(int statusCode, String... requestPaths) throws Exception {
            for (String requestPath : requestPaths) {
                var requestURI = URI.create("http://%s:%s%s".formatted(
                        server.getAddress().getHostString(),
                        server.getAddress().getPort(),
                        requestPath));
                var request = HttpRequest.newBuilder(requestURI).build();
                var response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                assertEquals(
                        statusCode, response.statusCode(),
                        "unexpected status code " + Map.of(
                                "contextPath", contextPath,
                                "requestPath", requestPath));
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }

    }

}
