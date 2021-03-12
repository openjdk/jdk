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
 * @summary Basic tests for Request
 * @run testng/othervm RequestTest
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.*;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class RequestTest {

    @Test
    public void testAddToEmpty() {
        var headers = new Headers();  // empty
        Request request = new TestHttpExchange(headers);
        request = request.with("X-Foo", List.of("Bar"));
        assertEquals(request.getRequestHeaders().size(), 1);
        assertEquals(request.getRequestHeaders().get("X-Foo"), List.of("Bar"));
        assertReadOnly(request.getRequestHeaders());
    }

    @Test
    public void testAddition() {
        var headers = new Headers();
        headers.add("X-Bar", "barValue");
        Request request = new TestHttpExchange(headers);
        request = request.with("X-Foo", List.of("fooValue"));
        assertEquals(request.getRequestHeaders().size(), 2);
        assertEquals(request.getRequestHeaders().get("X-Bar"), List.of("barValue"));
        assertEquals(request.getRequestHeaders().get("X-Foo"), List.of("fooValue"));
        assertReadOnly(request.getRequestHeaders());
    }

    @Test
    public void testAddWithExisting() {
        final String headerName = "X-Baz";
        var headers = new Headers();
        headers.add(headerName, "bazValue");
        Request request = new TestHttpExchange(headers);
        request = request.with(headerName, List.of("blahblahblah"));
        assertEquals(request.getRequestHeaders().size(), 1);
        assertEquals(request.getRequestHeaders().get(headerName), List.of("bazValue"));
        assertReadOnly(request.getRequestHeaders());
    }

    @Test
    public void testAddSeveral() {
        var headers = new Headers();
        Request request = new TestHttpExchange(headers);
        request = request.with("Larry", List.of("a"))
                         .with("Curly", List.of("b"))
                         .with("Moe",   List.of("c"));
        assertEquals(request.getRequestHeaders().size(), 3);
        assertEquals(request.getRequestHeaders().getFirst("Larry"), "a");
        assertEquals(request.getRequestHeaders().getFirst("Curly"), "b");
        assertEquals(request.getRequestHeaders().getFirst("Moe"  ), "c");
        assertReadOnly(request.getRequestHeaders());
    }

    static final Class<UnsupportedOperationException> UOP = UnsupportedOperationException.class;

    static void assertReadOnly(Headers headers) {
        //assertThrows(UOP, () -> headers.put("a", List.of("b")));   /// << TODO ARGH!!
        assertThrows(UOP, () -> headers.set("c", "d"));
        assertThrows(UOP, () -> headers.add("e", "f"));
        assertThrows(UOP, () -> headers.remove("g"));
        assertThrows(UOP, () -> headers.putAll(Map.of()));
        assertThrows(UOP, () -> headers.clear());
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
