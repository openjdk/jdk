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
 * @summary Basic tests for the file-server handler
 * @run testng/othervm FileServerHandlerTest
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.SimpleFileServer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class FileServerHandlerTest {

    static final Path CWD = Path.of(".").toAbsolutePath();

    @DataProvider
    public Object[][] requestMethods() {
        var l = List.of("POST", "PUT", "GARBAGE");
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "requestMethods")
    public void testBadRequestMethod(String requestMethod) throws Exception {
        var handler = SimpleFileServer.createFileHandler(CWD);
        var exchange = new MethodHttpExchange(requestMethod);
        handler.handle(exchange);
        assertEquals(exchange.rCode, 405);
        assertEquals(exchange.getResponseHeaders().getFirst("allow"), "HEAD, GET");
    }

    static class MethodHttpExchange extends StubHttpExchange {
        private final String method;
        volatile int rCode;
        volatile long responseLength;
        volatile Headers responseHeaders;
        MethodHttpExchange(String method) {
            this.method = method;
            responseHeaders = new Headers();
        }

        @Override public String getRequestMethod() { return method; }

        @Override public void sendResponseHeaders(int rCode, long responseLength) {
            this.rCode = rCode;
            this.responseLength = responseLength;
        }

        @Override public Headers getResponseHeaders() { return responseHeaders; }
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