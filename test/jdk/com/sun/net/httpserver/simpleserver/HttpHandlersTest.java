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
 * @summary Basic tests for HttpHandlers
 * @run testng/othervm HttpHandlersTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.*;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.*;

public class HttpHandlersTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @BeforeTest
    public void setup() {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
    }

    @Test
    public void testNull() {
        final var handler = new TestHandler();
        assertThrows(NPE, () -> HttpHandlers.handleOrElse(null, handler, new TestHandler()));
        assertThrows(NPE, () -> HttpHandlers.handleOrElse(p -> true, null, handler));
        assertThrows(NPE, () -> HttpHandlers.handleOrElse(p -> true, handler, null));
    }

    @Test
    public void testFallbackHandler() throws Exception {
        var handler = HttpHandlers.fallbackHandler();
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.body(), "");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testHandleOrElseTrue() throws Exception {
        var h1 = new TestHandler("TestHandler-1");
        var h2 = new TestHandler("TestHandler-2");
        var handler = HttpHandlers.handleOrElse(p -> true, h1, h2);
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "TestHandler-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testHandleOrElseFalse() throws Exception {
        var h1 = new TestHandler("TestHandler-1");
        var h2 = new TestHandler("TestHandler-2");
        var handler = HttpHandlers.handleOrElse(p -> false, h1, h2);
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "TestHandler-2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testHandleOrElseNested() throws Exception {
        var h1 = new TestHandler("TestHandler-1");
        var h2 = new TestHandler("TestHandler-2");
        var h3 = new TestHandler("TestHandler-3");
        var h4 = HttpHandlers.handleOrElse(p -> false, h1, h2);
        var handler = HttpHandlers.handleOrElse(p -> false, h3, h4);
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "TestHandler-2");
        } finally {
            server.stop(0);
        }
    }

    /**
     * A test handler that discards the request and returns its name
     */
    static class TestHandler implements HttpHandler {
        final String name;
        TestHandler(String name) { this.name = name; }
        TestHandler() { this("no name"); }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                is.readAllBytes();
                var resp = name.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                os.write(resp);
            }
        }
    }

    static URI uri(HttpServer server, String path) {
        return URI.create("http://localhost:%s/%s".formatted(server.getAddress().getPort(), path));
    }
}
