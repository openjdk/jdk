/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.httpserver/sun.net.httpserver.simpleserver
 * @run junit FileServerHandlerTest
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
import sun.net.httpserver.simpleserver.FileServerHandler;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

public class FileServerHandlerTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final Class<RuntimeException> RE = RuntimeException.class;

    public static Arguments[] validRanges() {
        return new Arguments[] {
                Arguments.of("bytes=2-3", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(2, 3) }),
                Arguments.of("bYTes=2-3", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(2, 3) }),
                Arguments.of("bytes=2-", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(2, 14) }),
                Arguments.of("bytes=-3", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(12, 14) }),
                Arguments.of("bytes=0-100", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(0, 14) }),
                Arguments.of("bytes=0-1,4-6", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(0, 1),
                        new FileServerHandler.Range(4, 6) }),
                Arguments.of("bytes=12-14,0-0,4-5", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(0, 0),
                        new FileServerHandler.Range(4, 5),
                        new FileServerHandler.Range(12, 14) }),
                Arguments.of("bytes=0-4,3-6,5-7,9-13,12-15", new FileServerHandler.Range[] {
                        new FileServerHandler.Range(0, 7),
                        new FileServerHandler.Range(9, 14) }),
        };
    }

    @ParameterizedTest
    @MethodSource("validRanges")
    public void testParseValidRanges(String header, FileServerHandler.Range[] expected) {
        assertArrayEquals(expected, FileServerHandler.parseRanges(header, 15L));
    }

    @Test
    public void testParseDuplicateRanges() {
        var expected = new FileServerHandler.Range[] {
                new FileServerHandler.Range(0, 99),
                new FileServerHandler.Range(200, 499)
        };
        var actual = FileServerHandler.parseRanges(
                "bytes=0-99,200-399,0-99,400-499,200-499", 500L);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testParseTooManyRanges() {
        var header = new StringBuilder("bytes=");
        for (int i = 0; i < 33; i++) {
            if (i > 0) {
                header.append(",");
            }
            header.append(i).append("-").append(i);
        }

        assertArrayEquals(new FileServerHandler.Range[0],
                FileServerHandler.parseRanges(header.toString(), 100L));
    }

    public static Arguments[] unsatisfiableRangeHeaders() {
        return new Arguments[] {
                Arguments.of(15L, "bytes=15-20"),
                Arguments.of(15L, "bytes=20-"),
        };
    }

    @ParameterizedTest
    @MethodSource("unsatisfiableRangeHeaders")
    public void testParseUnsatisfiableRanges(long fileSize, String header) {
        assertArrayEquals(new FileServerHandler.Range[0],
                FileServerHandler.parseRanges(header, fileSize));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "bytes=0-",
            "bytes=-1",
            "bytes=0-0"
    })
    public void testParseRangesIgnoredForNoContent(String header) {
        assertNull(FileServerHandler.parseRanges(header, 0L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "bytes=",
            "bytes=-",
            "bytes=meow-5",
            "bytes=0-meow",
            "meows=0-3",
            "bytes=3-1",
            "bytes=+2-5",
            "bytes=2-+5",
            "bytes=400--500",
            "bytes=500-+600",
            "bytes=500+-600",
            "bytes=500-600+",
            "bytes=--",
            "bytes=-+1",
            "bytes=+1-",
            "bytes=400-500,",
            "bytes=,400-500",
            "bytes=400-500, "
    })
    public void testParseInvalidRanges(String header) {
        assertNull(FileServerHandler.parseRanges(header, 15L));
    }

    public static Object[][] notAllowedMethods() {
        var l = List.of("POST", "PUT", "DELETE", "TRACE", "OPTIONS");
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("notAllowedMethods")
    public void testNotAllowedRequestMethod(String requestMethod) throws Exception {
        var handler = SimpleFileServer.createFileHandler(CWD);
        var exchange = new MethodHttpExchange(requestMethod);
        handler.handle(exchange);
        assertEquals(405, exchange.rCode);
        assertEquals("HEAD, GET", exchange.getResponseHeaders().getFirst("allow"));
    }

    public static Object[][] notImplementedMethods() {
        var l = List.of("GARBAGE", "RUBBISH", "TRASH", "FOO", "BAR");
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("notImplementedMethods")
    public void testNotImplementedRequestMethod(String requestMethod) throws Exception {
        var handler = SimpleFileServer.createFileHandler(CWD);
        var exchange = new MethodHttpExchange(requestMethod);
        handler.handle(exchange);
        assertEquals(501, exchange.rCode);
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
            var t = assertThrows(RE, () -> h.handle(exchange));
            assertEquals("getRequestBody", t.getMessage());
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public Headers getResponseHeaders() {
                    throw new RuntimeException("getResponseHeaders");
                }
            };
            var t = assertThrows(RE, () -> h.handle(exchange));
            assertEquals("getResponseHeaders", t.getMessage());
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public void sendResponseHeaders(int rCode, long responseLength) {
                    throw new RuntimeException("sendResponseHeaders");
                }
            };
            var t = assertThrows(RE, () -> h.handle(exchange));
            assertEquals("sendResponseHeaders", t.getMessage());
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public OutputStream getResponseBody() {
                    throw new RuntimeException("getResponseBody");
                }
            };
            var t = assertThrows(RE, () -> h.handle(exchange));
            assertEquals("getResponseBody", t.getMessage());
        }
        {
            var exchange = new ThrowingHttpExchange("GET") {
                public void close() {
                    throw new RuntimeException("close");
                }
            };
            var t = assertThrows(RE, () -> h.handle(exchange));
            assertEquals("close", t.getMessage());
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
