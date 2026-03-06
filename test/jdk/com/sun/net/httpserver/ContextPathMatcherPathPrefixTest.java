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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.net.http.HttpClient.Builder.NO_PROXY;

import org.junit.jupiter.api.AfterAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // Repeating all cases with both known (GET) and unknown (DOH) request methods to stress both paths
        try (var infra = new Infra("/")) {
            // 200
            infra.expect(200, "GET /foo");
            infra.expect(200, "GET /foo/");
            infra.expect(200, "GET /foo/bar");
            infra.expect(200, "GET /foobar");
            infra.expect(200, "DOH /foo");
            infra.expect(200, "DOH /foo/");
            infra.expect(200, "DOH /foo/bar");
            infra.expect(200, "DOH /foobar");
            // 404
            infra.expect(404, "GET foo");
            infra.expect(404, "GET *");
            infra.expect(404, "GET ");
            infra.expect(404, "DOH foo");
            infra.expect(404, "DOH *");
            infra.expect(404, "DOH ");
            // 400
            infra.expect(400, "GET");
            infra.expect(400, "DOH");
        }
    }

    @Test
    void testContextPathAtSubDir() throws Exception {
        // Repeating all cases with both known (GET) and unknown (DOH) request methods to stress both paths
        try (var infra = new Infra("/foo")) {
            // 200
            infra.expect(200, "GET /foo");
            infra.expect(200, "GET /foo/");
            infra.expect(200, "GET /foo/bar");
            infra.expect(200, "DOH /foo");
            infra.expect(200, "DOH /foo/");
            infra.expect(200, "DOH /foo/bar");
            // 404
            infra.expect(404, "GET /foobar"); // Differs from string prefix matching!
            infra.expect(404, "GET foo");
            infra.expect(404, "GET *");
            infra.expect(404, "GET ");
            infra.expect(404, "DOH /foobar"); // Differs from string prefix matching!
            infra.expect(404, "DOH foo");
            infra.expect(404, "DOH *");
            infra.expect(404, "DOH ");
            // 400
            infra.expect(400, "GET");
            infra.expect(400, "DOH");
        }
    }

    @Test
    void testContextPathAtSubDirWithTrailingSlash() throws Exception {
        // Repeating all cases with both known (GET) and unknown (DOH) request methods to stress both paths
        try (var infra = new Infra("/foo/")) {
            // 200
            infra.expect(200, "GET /foo/");
            infra.expect(200, "GET /foo/bar");
            infra.expect(200, "DOH /foo/");
            infra.expect(200, "DOH /foo/bar");
            // 404
            infra.expect(404, "GET /foo");
            infra.expect(404, "GET /foobar");
            infra.expect(404, "GET foo");
            infra.expect(404, "GET *");
            infra.expect(404, "GET ");
            infra.expect(404, "DOH /foo");
            infra.expect(404, "DOH /foobar");
            infra.expect(404, "DOH foo");
            infra.expect(404, "DOH *");
            infra.expect(404, "DOH ");
            // 400
            infra.expect(400, "GET");
            infra.expect(400, "DOH");
        }
    }

    protected static final class Infra implements AutoCloseable {

        /** Charset used for network and file I/O. */
        private static final Charset CHARSET = StandardCharsets.US_ASCII;

        /** Socket address the server will bind to. */
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

        protected void expect(int statusCode, String requestLinePrefix) {
            try {
                expect0(statusCode, requestLinePrefix);
            } catch (Throwable exception) {
                var extendedMessage = exception.getMessage() + " " + Map.of(
                        "contextPath", contextPath,
                        "requestLinePrefix", requestLinePrefix);
                var extendedException = new RuntimeException(extendedMessage);
                extendedException.setStackTrace(exception.getStackTrace());
                throw extendedException;
            }
        }

        private void expect0(int statusCode, String requestLinePrefix) throws IOException {

            // Connect to the server
            try (Socket socket = new Socket()) {
                socket.connect(server.getAddress());

                // Obtain the I/O streams
                try (OutputStream outputStream = socket.getOutputStream();
                     InputStream inputStream = socket.getInputStream();
                     BufferedReader inputReader = new BufferedReader(new InputStreamReader(inputStream, CHARSET))) {

                    // Write the request
                    byte[] requestBytes = String
                            // `Connection: close` is required for `BufferedReader::readLine` to work.
                            .format("%s HTTP/1.1\r\nConnection: close\r\n\r\n", requestLinePrefix)
                            .getBytes(CHARSET);
                    outputStream.write(requestBytes);
                    outputStream.flush();

                    // Read the response status code
                    String statusLine = inputReader.readLine();
                    assertNotNull(statusLine, "Unexpected EOF while reading status line");
                    Matcher statusLineMatcher = Pattern.compile("^HTTP/1\\.1 (\\d+) .+$").matcher(statusLine);
                    assertTrue(statusLineMatcher.matches(), "Couldn't match status line: \"" + statusLine + "\"");
                    assertEquals(statusCode, Integer.parseInt(statusLineMatcher.group(1)));

                }

            }

        }

        @Override
        public void close() {
            server.stop(0);
        }

    }

}
