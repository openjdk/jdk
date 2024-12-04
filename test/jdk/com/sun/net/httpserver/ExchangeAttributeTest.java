/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8288109 8235786
 * @summary Tests for HttpExchange set/getAttribute
 * @library /test/lib
 * @run junit/othervm ExchangeAttributeTest
 */

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
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

    private static final InetAddress LOOPBACK_ADDR = InetAddress.getLoopbackAddress();
    private static final boolean ENABLE_LOGGING = true;
    private static final Logger logger = Logger.getLogger("com.sun.net.httpserver");

    private static HttpServer server;

    @BeforeAll
    public static void setup() throws Exception {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
        server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDR, 0), 10);
        server.createContext("/normal", new AttribHandler());
        final HttpContext filteredCtx = server.createContext("/filtered", new AttribHandler());
        filteredCtx.getFilters().add(new AttributeAddingFilter());
        server.start();
        System.out.println("Server started at " + server.getAddress());
    }

    @AfterAll
    public static void afterAll() {
        if (server != null) {
            System.out.println("Stopping server " + server.getAddress());
            server.stop(0);
        }
    }

    /*
     * Verifies that HttpExchange.setAttribute() allows for null value.
     */
    @Test
    public void testNullAttributeValue() throws Exception {
        try (var client = HttpClient.newBuilder().proxy(NO_PROXY).build()) {
            var request = HttpRequest.newBuilder(uri(server, "/normal", null)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    /*
     * Verifies that an attribute set on one exchange is accessible to another exchange that
     * belongs to the same HttpContext.
     */
    @Test
    public void testSharedAttribute() throws Exception {
        try (var client = HttpClient.newBuilder().proxy(NO_PROXY).build()) {
            final var firstReq = HttpRequest.newBuilder(uri(server, "/filtered", "firstreq"))
                    .build();
            System.out.println("issuing request " + firstReq);
            final var firstResp = client.send(firstReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, firstResp.statusCode());

            // issue the second request
            final var secondReq = HttpRequest.newBuilder(uri(server, "/filtered", "secondreq"))
                    .build();
            System.out.println("issuing request " + secondReq);
            final var secondResp = client.send(secondReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, secondResp.statusCode());

            // verify that the filter was invoked for both the requests. the filter internally
            // does the setAttribute() and getAttribute() and asserts that the attribute value
            // set by the first exchange was available through the second exchange.
            assertTrue(AttributeAddingFilter.filteredFirstReq, "Filter wasn't invoked for "
                    + firstReq.uri());
            assertTrue(AttributeAddingFilter.filteredSecondReq, "Filter wasn't invoked for "
                    + secondReq.uri());
        }
    }

    // --- infra ---

    static URI uri(HttpServer server, String path, String query) throws URISyntaxException {
        return URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path(path)
                .query(query)
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

    private static final class AttributeAddingFilter extends Filter {

        private static final String ATTR_NAME ="foo-bar";
        private static final String ATTR_VAL ="hello-world";
        private static volatile boolean filteredFirstReq;
        private static volatile boolean filteredSecondReq;

        @Override
        public void doFilter(final HttpExchange exchange, final Chain chain) throws IOException {
            if (exchange.getRequestURI().getQuery().contains("firstreq")) {
                filteredFirstReq = true;
                // add a request attribute through the exchange, for this first request
                // and at the same time verify that the attribute doesn't already exist
                final Object attrVal = exchange.getAttribute(ATTR_NAME);
                if (attrVal != null) {
                    throw new IOException("attribute " + ATTR_NAME + " with value: " + attrVal
                            + " unexpectedly present in exchange: " + exchange.getRequestURI());
                }
                // set the value
                exchange.setAttribute(ATTR_NAME, ATTR_VAL);
                System.out.println(exchange.getRequestURI() + " set attribute "
                        + ATTR_NAME + "=" + ATTR_VAL);
            } else if (exchange.getRequestURI().getQuery().contains("secondreq")) {
                filteredSecondReq = true;
                // verify the attribute is already set and the value is the expected one.
                final Object attrVal = exchange.getAttribute(ATTR_NAME);
                if (attrVal == null) {
                    throw new IOException("attribute " + ATTR_NAME + " is missing in exchange: "
                            + exchange.getRequestURI());
                }
                if (!ATTR_VAL.equals(attrVal)) {
                    throw new IOException("unexpected value: " + attrVal + " for attribute "
                            + ATTR_NAME + " in exchange: " + exchange.getRequestURI());
                }
                System.out.println(exchange.getRequestURI() + " found attribute "
                        + ATTR_NAME + "=" + attrVal);
            } else {
                // unexpected request
                throw new IOException("unexpected request " + exchange.getRequestURI());
            }
            // let the request proceed
            chain.doFilter(exchange);
        }

        @Override
        public String description() {
            return "AttributeAddingFilter";
        }
    }
}
