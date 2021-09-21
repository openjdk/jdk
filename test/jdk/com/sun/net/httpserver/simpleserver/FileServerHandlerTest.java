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
 * @summary Tests for FileServerHandler
 * @run testng FileServerHandlerTest
 */

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class FileServerHandlerTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final Class<RuntimeException> RE = RuntimeException.class;

    @DataProvider
    public Object[][] notAllowedMethods() {
        var l = List.of("POST", "PUT", "DELETE", "TRACE", "OPTIONS");
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "notAllowedMethods")
    public void testNotAllowedRequestMethod(String requestMethod) throws Exception {
        var handler = SimpleFileServer.createFileHandler(CWD);
        var exchange = new MethodHttpExchange(requestMethod);
        handler.handle(exchange);
        assertEquals(exchange.rCode, 405);
        assertEquals(exchange.getResponseHeaders().getFirst("allow"), "HEAD, GET");
    }

    @DataProvider
    public Object[][] notImplementedMethods() {
        var l = List.of("GARBAGE", "RUBBISH", "TRASH", "FOO", "BAR");
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "notImplementedMethods")
    public void testNotImplementedRequestMethod(String requestMethod) throws Exception {
        var handler = SimpleFileServer.createFileHandler(CWD);
        var exchange = new MethodHttpExchange(requestMethod);
        handler.handle(exchange);
        assertEquals(exchange.rCode, 501);
    }

    // 301 and 404 response codes tested in SimpleFileServerTest

    @Test
    public void testThrowingExchange() {
        var h = SimpleFileServer.createFileHandler(CWD);
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public InputStream getRequestBody() {
                    throw new RuntimeException("getRequestBody");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "getRequestBody");
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public Headers getResponseHeaders() {
                    throw new RuntimeException("getResponseHeaders");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "getResponseHeaders");
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public void sendResponseHeaders(int rCode, long responseLength) {
                    throw new RuntimeException("sendResponseHeaders");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "sendResponseHeaders");
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public OutputStream getResponseBody() {
                    throw new RuntimeException("getResponseBody");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "getResponseBody");
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public void close() {
                    throw new RuntimeException("close");
                }
            };
            var t = expectThrows(RE, () -> h.handle(exchange));
            assertEquals(t.getMessage(), "close");
        }
    }

    static class ThrowingHttpExchange extends StubHttpExchange {
        private final String method;
        volatile int rCode;
        volatile long responseLength;
        volatile Headers responseHeaders;
        volatile Headers requestHeaders;
        volatile InputStream requestBody;

        ThrowingHttpExchange(String method) {
            this.method = method;
            responseHeaders = new Headers();
            requestHeaders = new Headers();
            requestBody = new ByteArrayInputStream(new byte[]{});
        }

        @Override public String getRequestMethod() { return method; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public InputStream getRequestBody() { return requestBody; }
        @Override public URI getRequestURI() { return URI.create("/"); }
        @Override public OutputStream getResponseBody() {
            return OutputStream.nullOutputStream();
        }
        @Override public void sendResponseHeaders(int rCode, long responseLength) {
            this.rCode = rCode;
            this.responseLength = responseLength;
        }
        @Override public HttpContext getHttpContext() {
            return new HttpContext() {
                @Override public HttpHandler getHandler() { return null; }
                @Override public void setHandler(HttpHandler handler) { }
                @Override public String getPath() {
                    return "/";
                }
                @Override public HttpServer getServer() {
                    return null;
                }
                @Override public Map<String, Object> getAttributes() {
                    return null;
                }
                @Override public List<Filter> getFilters() {
                    return null;
                }
                @Override public Authenticator setAuthenticator(Authenticator auth) {
                    return null;
                }
                @Override public Authenticator getAuthenticator() {
                    return null;
                }
            };
        }
    }

    static class MethodHttpExchange extends StubHttpExchange {
        private final String method;
        volatile int rCode;
        volatile long responseLength;
        volatile Headers responseHeaders;
        volatile InputStream requestBody;

        MethodHttpExchange(String method) {
            this.method = method;
            responseHeaders = new Headers();
            requestBody = InputStream.nullInputStream();
        }

        @Override public String getRequestMethod() { return method; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public InputStream getRequestBody() { return requestBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) {
            this.rCode = rCode;
            this.responseLength = responseLength;
        }
    }

    static class StubHttpExchange extends HttpExchange {
        @Override public Headers getRequestHeaders() { return null; }
        @Override public Headers getResponseHeaders() { return null; }
        @Override public URI getRequestURI() { return null; }
        @Override public String getRequestMethod() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return null; }
        @Override public OutputStream getResponseBody() { return null; }
        @Override public HttpContext getHttpContext() { return null; }
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
