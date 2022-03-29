/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests that a Content-length header is not sent for GET requests
 *          that do not have a body. Also checks that the header is sent when
 *          a body is present on the GET request.
 * @bug 8283544
 * @run testng/othervm ContentLengthHeaderTest
 */


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;


public class ContentLengthHeaderTest {

    final String GET_NO_BODY_PATH = "/get_no_body";
    final String GET_BODY_PATH = "/get_body";
    static final String GET_BODY_STRING = "Get with body";

    static HttpServer testContentLengthServer;
    static PrintStream testLog = System.err;
    static String responseString = "Test Response Body";

    String testContentLenURI;

    @BeforeTest
    public void setup() throws IOException {
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        testContentLengthServer = HttpServer.create(sa, 0);

        // Create handlers for tests that check for the presence of a Content-length header
        testContentLengthServer.createContext(GET_NO_BODY_PATH, new NoContentLengthHandler());
        testContentLengthServer.createContext(GET_BODY_PATH, new ContentLengthHandler());
        testContentLenURI = "http://" + testContentLengthServer.getAddress().getHostString() +
                             ":" + testContentLengthServer.getAddress().getPort();

        testContentLengthServer.start();
        testLog.println("Server up at address: " + testContentLengthServer.getAddress());
        testLog.println("Request URI for Client: " + testContentLenURI);
    }

    @AfterTest
    public void teardown() {
        testContentLengthServer.stop(0);
    }

    @Test
    // A GET request with no request body should have no Content-length header
    public void getWithNoBody() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .GET()
                .uri(URI.create(testContentLenURI + GET_NO_BODY_PATH))
                .build();
        HttpClient hc = HttpClient.newHttpClient();
        hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    }

    @Test
    // A GET request with a request body should have a Content-length header
    public void getWithBody() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .method("GET", HttpRequest.BodyPublishers.ofString(GET_BODY_STRING))
                .uri(URI.create(testContentLenURI + GET_BODY_PATH))
                .build();
        HttpClient hc = HttpClient.newHttpClient();
        hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    }

    public static void handleResponse(HttpExchange ex) throws IOException {
        // Meaningless response to finish off the exchange
        try (OutputStream os = ex.getResponseBody()) {
            byte[] bytes = responseString.getBytes(UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            os.write(bytes);
        }
    }

    static class NoContentLengthHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            testLog.println("NoContentLengthHandler Received Headers: " + exchange.getRequestHeaders().entrySet());
            String contentLength = exchange.getRequestHeaders().getFirst("Content-length");

            // Check that content length was not set
            assertNull(contentLength);
            // Finish exchange with canned response
            handleResponse(exchange);
        }
    }

    static class ContentLengthHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            testLog.println("ContentLengthHandler Received Headers: " +
                            exchange.getRequestHeaders().entrySet());
            String contentLength = exchange.getRequestHeaders().getFirst("Content-length");

            // Check that the Content-length is correctly set
            assertNotNull(contentLength);
            assertEquals(Integer.valueOf(contentLength), Integer.valueOf(GET_BODY_STRING.getBytes(UTF_8).length));
            // Finish exchange with canned response
            handleResponse(exchange);
        }
    }
}
