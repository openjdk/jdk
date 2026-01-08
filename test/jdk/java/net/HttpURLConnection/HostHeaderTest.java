/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * @test
 * @bug 8359709
 * @summary verify that if the Host header is allowed to be set by the application
 *          then the correct value gets set in a HTTP request issued through
 *          java.net.HttpURLConnection
 * @library /test/lib
 * @run junit HostHeaderTest
 * @run junit/othervm -Dsun.net.http.allowRestrictedHeaders=true HostHeaderTest
 * @run junit/othervm -Dsun.net.http.allowRestrictedHeaders=false HostHeaderTest
 */
class HostHeaderTest {

    private static final String SERVER_CTX_ROOT = "/8359709/";
    private static final boolean allowsHostHeader = Boolean.getBoolean("sun.net.http.allowRestrictedHeaders");

    private static HttpServer server;

    @BeforeAll
    static void beforeAll() throws Exception {
        final InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        server = HttpServer.create(addr, 0);
        server.createContext(SERVER_CTX_ROOT, new Handler());
        server.start();
        System.err.println("started server at " + server.getAddress());
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            System.err.println("stopping server " + server.getAddress());
            server.stop(0);
        }
    }

    @Test
    void testHostHeader() throws Exception {
        final InetSocketAddress serverAddr = server.getAddress();
        final URL reqURL = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(serverAddr.getPort())
                .path(SERVER_CTX_ROOT)
                .build().toURL();
        final URLConnection conn = reqURL.openConnection(Proxy.NO_PROXY);

        conn.setRequestProperty("Host", "foobar");
        if (!allowsHostHeader) {
            // if restricted headers aren't allowed to be set by the user, then
            // we expect the previous call to setRequestProperty to not set the Host
            // header
            assertNull(conn.getRequestProperty("Host"), "Host header unexpectedly set");
        }

        assertInstanceOf(HttpURLConnection.class, conn);
        final HttpURLConnection httpURLConn = (HttpURLConnection) conn;

        // send the HTTP request
        System.err.println("sending request " + reqURL);
        final int respCode = httpURLConn.getResponseCode();
        assertEquals(200, respCode, "unexpected response code");
        // verify that the server side handler received the expected
        // Host header value in the request
        try (final InputStream is = httpURLConn.getInputStream()) {
            final byte[] resp = is.readAllBytes();
            // if Host header wasn't explicitly set, then we expect it to be
            // derived from the request URL
            final String expected = allowsHostHeader
                    ? "foobar"
                    : reqURL.getHost() + ":" + reqURL.getPort();
            final String actual = new String(resp, US_ASCII);
            assertEquals(expected, actual, "unexpected Host header received on server side");
        }
    }

    private static final class Handler implements HttpHandler {
        private static final int NO_RESPONSE_BODY = -1;

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final List<String> headerVals = exchange.getRequestHeaders().get("Host");
            System.err.println("Host header has value(s): " + headerVals);
            // unexpected Host header value, respond with 400 status code
            if (headerVals == null || headerVals.size() != 1) {
                System.err.println("Unexpected header value(s) for Host header: " + headerVals);
                exchange.sendResponseHeaders(400, NO_RESPONSE_BODY);
                return;
            }
            // respond back with the Host header value that we found in the request
            final byte[] response = headerVals.getFirst().getBytes(US_ASCII);
            exchange.sendResponseHeaders(200, response.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
