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
 * @library /test/lib
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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jdk.test.lib.net.URIBuilder;


import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;


public class ContentLengthHeaderTest {

    final String NO_BODY_PATH = "/no_body";
    final String BODY_PATH = "/body";

    static HttpServer testContentLengthServer;
    static PrintStream testLog = System.err;

    HttpClient hc;
    URI testContentLengthURI;

    @BeforeTest
    public void setup() throws IOException, URISyntaxException {
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        testContentLengthServer = HttpServer.create(sa, 0);

        // Create handlers for tests that check for the presence of a Content-length header
        testContentLengthServer.createContext(NO_BODY_PATH, new NoContentLengthHandler());
        testContentLengthServer.createContext(BODY_PATH, new ContentLengthHandler());
        testContentLengthURI = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(testContentLengthServer.getAddress().getPort())
                .build();

        testContentLengthServer.start();
        testLog.println("Server up at address: " + testContentLengthServer.getAddress());
        testLog.println("Request URI for Client: " + testContentLengthURI);

        hc = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(HTTP_1_1)
                .build();
    }

    @AfterTest
    public void teardown() {
        if (testContentLengthServer != null)
            testContentLengthServer.stop(0);
    }

    @Test
    // A GET request with no request body should have no Content-length header
    public void getWithNoBody() throws IOException, InterruptedException {
        testLog.println("Checking GET with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .GET()
                .uri(URI.create(testContentLengthURI + NO_BODY_PATH))
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp.statusCode(), 200, resp.body());
    }

    @Test
    // A GET request with a request body should have a Content-length header
    public void getWithBody() throws IOException, InterruptedException {
        testLog.println("Checking GET with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .method("GET", HttpRequest.BodyPublishers.ofString("GET Body"))
                .uri(URI.create(testContentLengthURI + BODY_PATH))
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp.statusCode(), 200, resp.body());
    }

    @Test
    // A DELETE request with no request body should have no Content-length header
    public void deleteWithNoBody() throws IOException, InterruptedException {
        testLog.println("Checking DELETE with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .DELETE()
                .uri(URI.create(testContentLengthURI + NO_BODY_PATH))
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp.statusCode(), 200, resp.body());
    }

    @Test
    // A DELETE request with a request body should have a Content-length header
    public void deleteWithBody() throws IOException, InterruptedException {
        testLog.println("Checking DELETE with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .method("DELETE", HttpRequest.BodyPublishers.ofString("DELETE Body"))
                .uri(URI.create(testContentLengthURI + BODY_PATH))
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp.statusCode(), 200, resp.body());
    }

    @Test
    // A HEAD request with no request body should have no Content-length header
    public void headWithNoBody() throws IOException, InterruptedException {
        testLog.println("Checking HEAD with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .HEAD()
                .uri(URI.create(testContentLengthURI + NO_BODY_PATH))
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp.statusCode(), 200, resp.body());
    }

    @Test
    // A HEAD request with a request body should have a Content-length header
    public void headWithBody() throws IOException, InterruptedException {
        testLog.println("Checking HEAD with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(HTTP_1_1)
                .method("HEAD", HttpRequest.BodyPublishers.ofString("HEAD Body"))
                .uri(URI.create(testContentLengthURI + BODY_PATH))
                .build();
        // Sending this request invokes sendResponseHeaders which emits a warning about including
        // a Content-length header with a HEAD request
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(resp.statusCode(), 200, resp.body());
    }

    public static void handleResponse(HttpExchange ex, String body, int rCode) throws IOException {
        try (InputStream is = ex.getRequestBody();
             OutputStream os = ex.getResponseBody()) {
            is.readAllBytes();
            byte[] bytes = body.getBytes(UTF_8);
            ex.sendResponseHeaders(rCode, bytes.length);
            os.write(bytes);
        }
    }

    static class NoContentLengthHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            testLog.println("NoContentLengthHandler: Received Headers " + exchange.getRequestHeaders().entrySet() +
                            " from " + exchange.getRequestMethod() + " request.");
            String contentLength = exchange.getRequestHeaders().getFirst("Content-length");

            // Check Content-length header was not set
            if (contentLength == null) {
                handleResponse(exchange, "Request completed",200);
            } else {
                String responseBody = exchange.getRequestMethod() + " request contained an unexpected " +
                        "Content-length header of value: " + exchange.getRequestHeaders().getFirst("Content-length");
                handleResponse(exchange, responseBody, 400);
            }
        }
    }

    static class ContentLengthHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            testLog.println("ContentLengthHandler: Received Headers " + exchange.getRequestHeaders().entrySet() +
                            " from " + exchange.getRequestMethod() + " request.");
            String contentLength = exchange.getRequestHeaders().getFirst("Content-length");

            // Check Content-length header was set
            if (contentLength != null) {
                handleResponse(exchange, "Request completed",200);
            } else {
                String responseBody = "Expected a Content-length header in " +
                        exchange.getRequestMethod() + " request but was not present.";
                handleResponse(exchange, responseBody, 400);
            }
        }
    }
}
