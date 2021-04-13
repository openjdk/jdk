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
 * @summary Basic tests for static factory methods of Filter
 * @run testng/othervm FilterTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;
import static org.testng.Assert.*;

public class FilterTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;

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

    @Test
    public void testNull() {
        expectThrows(NPE, () -> Filter.beforeResponse(null, (HttpExchange e) -> e.getResponseHeaders().set("X-Foo", "Bar")));
        expectThrows(NPE, () -> Filter.beforeResponse("Some description", null));
        expectThrows(NPE, () -> Filter.afterResponse(null, HttpExchange::getResponseCode));
        expectThrows(NPE, () -> Filter.afterResponse("Some description", null));
    }

    @Test
    public void testDescription() {
        var desc = "Some description";
        var beforeFilter = Filter.beforeResponse(desc, HttpExchange::getRequestBody);
        var afterFilter = Filter.afterResponse(desc, HttpExchange::getRequestBody);
        assertEquals(desc, beforeFilter.description());
        assertEquals(desc, afterFilter.description());
    }

    @Test
    public void testAddResponseHeader() throws Exception {
        var handler = new TestHttpHandler();
        var filter = Filter.beforeResponse("Add x-foo response header",
                (var e) -> e.getResponseHeaders().set("x-foo", "bar"));
        var server = HttpServer.create(new InetSocketAddress(0), 10);
        server.createContext("/", handler).getFilters().add(filter);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().map().size(), 3);
            assertEquals(response.headers().firstValue("x-foo").orElseThrow(), "bar");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testLogResponseCode() throws Exception {
        var handler = new TestHttpHandler();
        var respCode = new int[]{0};
        var filter = Filter.afterResponse("Log response code",
                (var e) -> respCode[0] = e.getResponseCode());
        var server = HttpServer.create(new InetSocketAddress(0), 10);
        server.createContext("/", handler).getFilters().add(filter);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.statusCode(), respCode[0]);
        } finally {
            server.stop(0);
        }
    }

    static URI uri(HttpServer server, String path) {
        return URI.create("http://localhost:%s/%s".formatted(server.getAddress().getPort(), path));
    }

    /**
     * A test handler that discards the request and sends no response
     */
    static class TestHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
                exchange.sendResponseHeaders(200, -1);
            }
        }
    }
}
