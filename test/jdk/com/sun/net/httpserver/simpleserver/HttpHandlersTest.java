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
 * @summary Tests for HttpHandlers
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
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
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.test.lib.net.URIBuilder;
import com.sun.net.httpserver.*;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;

public class HttpHandlersTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<RuntimeException> RE = RuntimeException.class;
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

        final var headers = new Headers();
        final var body = "";
        assertThrows(NPE, () -> HttpHandlers.of(200, null, body));
        assertThrows(NPE, () -> HttpHandlers.of(200, headers, null));
    }

    @Test
    public void testOfStatusCode() {
        final var headers = new Headers();
        final var body = "";
        assertThrows(IAE, () -> HttpHandlers.of(99, headers, body));
        assertThrows(IAE, () -> HttpHandlers.of(1000, headers, body));
    }

    @Test
    public void testOfNoBody() throws Exception {
        var handler = HttpHandlers.of(200, Headers.of("foo", "bar"), "");
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertTrue(response.headers().map().containsKey("date"));
            assertEquals(response.headers().firstValue("foo").get(), "bar");
            assertEquals(response.headers().firstValue("content-length").get(), "0");
            assertEquals(response.headers().map().size(), 3);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testOfWithBody() throws Exception {
        var handler = HttpHandlers.of(200, Headers.of("foo", "bar"), "hello world");
        var expectedLength = Integer.toString("hello world".getBytes(UTF_8).length);
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertTrue(response.headers().map().containsKey("date"));
            assertEquals(response.headers().firstValue("foo").get(), "bar");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().map().size(), 3);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "hello world");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testOfHeadRequest() throws Exception {
        var handler = HttpHandlers.of(200, Headers.of("content-length", "999"), "hello world");
        var expectedLength = Integer.toString("hello world".getBytes(UTF_8).length);
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, ""))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertTrue(response.headers().map().containsKey("date"));
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().map().size(), 2);
            assertEquals(response.statusCode(), 200);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testOfOverwriteHeaders() throws Exception {
        var headers = Headers.of("content-length", "1000", "date", "12345");
        var handler = HttpHandlers.of(200, headers, "hello world");
        var expectedLength = Integer.toString("hello world".getBytes(UTF_8).length);
        var server = HttpServer.create(LOOPBACK_ADDR, 0);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertNotEquals(response.headers().firstValue("date").get(), "12345");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().map().size(), 2);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "hello world");
        } finally {
            server.stop(0);
        }
    }

    @DataProvider
    public Object[][] responseBodies() {
        return new Object[][] { {"hello world"}, {""} };
    }

    @Test(dataProvider = "responseBodies")
    public void testOfThrowingExchange(String body) {
        var h = HttpHandlers.of(200, Headers.of(), body);
        {
            var exchange = new ThrowingHttpExchange() {
                public InputStream getRequestBody() {
                    throw new RuntimeException("getRequestBody");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "getRequestBody");
        }
        {
            var exchange = new ThrowingHttpExchange() {
                public Headers getResponseHeaders() {
                    throw new RuntimeException("getResponseHeaders");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "getResponseHeaders");
        }
        {
            var exchange = new ThrowingHttpExchange() {
                public void sendResponseHeaders(int rCode, long responseLength) {
                    throw new RuntimeException("sendResponseHeaders");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "sendResponseHeaders");
        }
        {
            var exchange = new ThrowingHttpExchange() {
                public OutputStream getResponseBody() {
                    throw new RuntimeException("getResponseBody");
                }
            };
            if (!body.isEmpty()) {  // getResponseBody not called if no responseBody
                var t = expectThrows(RE, () -> h.handle(exchange));
                assertEquals(t.getMessage(), "getResponseBody");
            }
        }
        {
            var exchange = new ThrowingHttpExchange() {
                public void close() {
                    throw new RuntimeException("close");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "close");
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
                var resp = name.getBytes(UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                os.write(resp);
            }
        }
    }

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    static class ThrowingHttpExchange extends HttpExchange {
        static final String requestMethod = "GET";
        volatile int responseCode;
        volatile long responseLength;
        volatile Headers responseHeaders;
        volatile InputStream requestBody;

        ThrowingHttpExchange() {
            responseHeaders = new Headers();
            this.requestBody = InputStream.nullInputStream();
        }

        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public InputStream getRequestBody() { return requestBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
            this.responseLength = responseLength;
        }
        @Override public OutputStream getResponseBody() {
            return OutputStream.nullOutputStream();
        }
        @Override public String getRequestMethod() { return requestMethod; }

        @Override public Headers getRequestHeaders() { return null; }
        @Override public URI getRequestURI() { return null; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return 0; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return null; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream i, OutputStream o) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
