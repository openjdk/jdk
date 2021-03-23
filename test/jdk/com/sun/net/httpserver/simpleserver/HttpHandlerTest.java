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
 * @summary Basic tests for HttpHandler
 * @run testng/othervm HttpHandlerTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.*;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;

public class HttpHandlerTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final InetSocketAddress WILDCARD_ADDR = new InetSocketAddress(0);
    static final boolean ENABLE_LOGGING = true;

    @BeforeTest
    public void setup() {
        if (ENABLE_LOGGING) {
            Logger logger = Logger.getLogger("com.sun.net.httpserver");
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
    }

    Request request;

    @Test
    public void testAdaptRequest() throws Exception {
        var handler = ((HttpHandler) exchange ->  request = exchange );
        var adaptedHandler = HttpHandlers
                .adaptRequest(handler, r -> r.with("Foo", List.of("Bar")));
        adaptedHandler.handle(new TestHttpExchange(new Headers()));
        assertEquals(request.getRequestHeaders().size(), 1);
        assertEquals(request.getRequestHeaders().getFirst("Foo"), "Bar");
    }

    @Test
    public void testAdaptRequestWithGET() throws Exception {
        var handler = ((HttpHandler) exchange -> {
            var headers = exchange.getRequestHeaders();
            if (headers.containsKey("X-Bar")
                    && headers.getFirst("X-Bar").equals("barValue")) {
                exchange.sendResponseHeaders(200, 0);
                try (var os = exchange.getResponseBody()) {
                    os.write("Hello World".getBytes(UTF_8));
                }
            } else {
                System.out.println("Server received: " + headers);
                exchange.sendResponseHeaders(400, -1);
            }
        });
        var adaptedHandler = HttpHandlers
                .adaptRequest(handler, r -> r.with("X-Bar", List.of("barValue")));

        var ss = HttpServer.create(WILDCARD_ADDR, 0);
        ss.createContext("/", adaptedHandler);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "Hello World");
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testNull() {
        final var handler = new TestHttpHandler();
        assertThrows(NPE, () -> HttpHandlers.handleOrElse(null, handler, null));
        assertThrows(NPE, () -> HttpHandlers.handleOrElse(p -> true, handler, null));
        assertThrows(NPE, () -> HttpHandlers.handleOrElse(null, handler,  new TestHttpHandler()));

        assertThrows(NPE, () -> HttpHandlers.adaptRequest(handler, null));
    }

    static class TestHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            throw new AssertionError("should not reach here");
        }
    }

    static URI uri(HttpServer server, String path) {
        return URI.create("http://localhost:%s/%s".formatted(server.getAddress().getPort(), path));
    }

    static class TestHttpExchange extends StubHttpExchange {
        final Headers headers;
        TestHttpExchange(Headers headers) {
            this.headers = headers;
        }
        @Override
        public Headers getRequestHeaders() {
            return headers;
        }
    }

    static class StubHttpExchange extends HttpExchange {
        @Override public Headers getRequestHeaders() { return null; }
        @Override public Headers getResponseHeaders() { return null; }
        @Override public URI getRequestURI() { return null; }
        @Override public String getRequestMethod() { return null; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return null; }
        @Override public OutputStream getResponseBody() { return null; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { }
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
