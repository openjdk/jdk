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

/**
 * @test
 * @bug 8133686
 * @summary Ensuring that multiple header values for a given field-name are returned in
 *          the order they were added for HttpURLConnection.getRequestProperties
 *          and HttpURLConnection.getHeaderFields
 * @library /test/lib
 * @run testng HttpURLConnectionHeadersOrder
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.List;

public class HttpURLConnectionHeadersOrder {
    private static final String LOCAL_TEST_ENDPOINT = "/headertest";
    private static final String ERROR_MESSAGE_TEMPLATE = "Expected Request Properties = %s, Actual Request Properties = %s";
    private static final List<String> EXPECTED_HEADER_VALUES = Arrays.asList("a", "b", "c");
    private static HttpServer server;
    private static URL serverUrl;


    @BeforeTest
    public void beforeTest() throws Exception {
        SimpleHandler handler = new SimpleHandler();
        server = createSimpleHttpServer(handler);
        serverUrl = URIBuilder.newBuilder()
                .scheme("http")
                .host(server.getAddress().getAddress())
                .port(server.getAddress().getPort())
                .path(LOCAL_TEST_ENDPOINT)
                .toURL();
    }

    @AfterTest
    public void afterTest() {
        if (server != null)
            server.stop(0);
    }

    /**
     * - This tests requestProperty insertion-order
     * - on the client side by sending a HTTP GET
     * - request to a "dummy" server with additional
     * - custom request properties
     *
     * @throws Exception
     */
    @Test (priority = 1)
    public void testRequestPropertiesOrder() throws Exception {
        final var conn = (HttpURLConnection) serverUrl.openConnection();

        conn.addRequestProperty("test_req_prop", "a");
        conn.addRequestProperty("test_req_prop", "b");
        conn.addRequestProperty("test_req_prop", "c");

        conn.setRequestMethod("GET");

        var requestProperties = conn.getRequestProperties();
        var customRequestProps = requestProperties.get("test_req_prop");

        conn.disconnect();
        Assert.assertNotNull(customRequestProps);
        Assert.assertEquals(customRequestProps, EXPECTED_HEADER_VALUES, String.format(ERROR_MESSAGE_TEMPLATE, EXPECTED_HEADER_VALUES.toString(), customRequestProps.toString()));
    }

    /**
     * - This tests whether or not the insertion order is preserved for custom headers
     * - on the server's side.
     * - The server will return a custom status code (999) if the expected headers
     * - are not equal to the actual headers
     *
     * @throws Exception
     */
    @Test (priority = 2)
    public void testServerSideRequestHeadersOrder() throws Exception {
        final var conn = (HttpURLConnection) serverUrl.openConnection();
        conn.addRequestProperty("test_server_handling", "a");
        conn.addRequestProperty("test_server_handling", "b");
        conn.addRequestProperty("test_server_handling", "c");

        int statusCode = conn.getResponseCode();
        conn.disconnect();
        Assert.assertEquals(statusCode, 999, "The insertion-order was not preserved on the server-side response headers handling");
    }

    @Test (priority = 3)
    public void testClientSideResponseHeadersOrder() throws Exception {
        final var conn = (HttpURLConnection) serverUrl.openConnection();
        conn.setRequestMethod("GET");

        var actualCustomResponseHeaders = conn.getHeaderFields().get("Test_response");
        Assert.assertNotNull(actualCustomResponseHeaders, "Error in reading custom response headers");
        Assert.assertEquals(EXPECTED_HEADER_VALUES, actualCustomResponseHeaders, String.format(ERROR_MESSAGE_TEMPLATE, EXPECTED_HEADER_VALUES.toString(), actualCustomResponseHeaders.toString()));
    }

    private static HttpServer createSimpleHttpServer(SimpleHandler handler) throws IOException {
        var serverAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        var server = HttpServer.create(serverAddress, 0);
        server.createContext(LOCAL_TEST_ENDPOINT, handler);
        server.start();
        System.out.println("Server started on " + server.getAddress());
        return server;
    }

    private static class SimpleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int statusCode = testRequestHeadersOrder(exchange);
            sendCustomResponse(exchange, statusCode);
        }

        private int testRequestHeadersOrder(HttpExchange exchange) {
            var requestHeaders = exchange.getRequestHeaders();
            var actualTestRequestHeaders = requestHeaders.get("test_server_handling");

            if (actualTestRequestHeaders == null) {
                System.out.println("Error: requestHeaders.get(\"test_server_handling\") returned null");
                return -1;
            }

            if (!actualTestRequestHeaders.equals(EXPECTED_HEADER_VALUES)) {
                System.out.println("Error: " + String.format(ERROR_MESSAGE_TEMPLATE, EXPECTED_HEADER_VALUES.toString(), actualTestRequestHeaders.toString()));
                return -1;
            }
            return 999;
        }

        private void sendCustomResponse(HttpExchange exchange, int statusCode) throws IOException {
            var responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add("test_response", "a");
            responseHeaders.add("test_response", "b");
            responseHeaders.add("test_response", "c");

            var outputStream = exchange.getResponseBody();
            var response = "Testing headers";
            exchange.sendResponseHeaders(statusCode, response.length());

            outputStream.write(response.getBytes());
            outputStream.flush();
            outputStream.close();
        }
    }
}
