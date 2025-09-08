/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8288109
 * @summary Tests for HttpExchange set/getAttribute
 * @library /test/lib
 * @run junit/othervm ExchangeAttributeTest
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.junit.jupiter.api.Assertions.*;

public class ExchangeAttributeTest {

    static final InetAddress LOOPBACK_ADDR = InetAddress.getLoopbackAddress();
    static final boolean ENABLE_LOGGING = true;
    static final Logger logger = Logger.getLogger("com.sun.net.httpserver");

    @BeforeAll
    public static void setup() {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
    }

    @Test
    public void testExchangeAttributes() throws Exception {
        var handler = new AttribHandler();
        var server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDR,0), 10);
        server.createContext("/", handler);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        } finally {
            server.stop(0);
        }
    }

    // --- infra ---

    static URI uri(HttpServer server, String path) throws URISyntaxException {
        return URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path(path)
                .build();
    }

    /**
     * A test handler that discards the request and sends a response
     */
    static class AttribHandler implements HttpHandler {
        @java.lang.Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                exchange.setAttribute("attr", "val");
                assertEquals("val", exchange.getAttribute("attr"));
                exchange.setAttribute("attr", null);
                assertNull(exchange.getAttribute("attr"));
                exchange.sendResponseHeaders(200, -1);
            } catch (Throwable t) {
                t.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }
}
