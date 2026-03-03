/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @bug 8283544 8358942
 * @run junit/othervm
 *          -Djdk.httpclient.allowRestrictedHeaders=content-length
 *          -Djdk.internal.httpclient.debug=true
 *          ContentLengthHeaderTest
 */


import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import jdk.test.lib.net.URIBuilder;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ContentLengthHeaderTest implements HttpServerAdapters {

    static final String NO_BODY_PATH = "/no_body";
    static final String BODY_PATH = "/body";

    static HttpTestServer testContentLengthServerH1;
    static HttpTestServer testContentLengthServerH2;
    static HttpTestServer testContentLengthServerH3;
    static PrintStream testLog = System.err;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();

    private static HttpClient hc;
    private static URI testContentLengthURIH1;
    private static URI testContentLengthURIH2;
    private static URI testContentLengthURIH3;

    @BeforeAll
    public static void setup() throws IOException, URISyntaxException, InterruptedException {
        testContentLengthServerH1 = HttpTestServer.create(HTTP_1_1);
        testContentLengthServerH2 = HttpTestServer.create(HTTP_2, sslContext);
        testContentLengthServerH3 = HttpTestServer.create(HTTP_3, sslContext);

        // Create handlers for tests that check for the presence of a Content-length header
        testContentLengthServerH1.addHandler(new NoContentLengthHandler(), NO_BODY_PATH);
        testContentLengthServerH2.addHandler(new NoContentLengthHandler(), NO_BODY_PATH);
        testContentLengthServerH3.addHandler(new NoContentLengthHandler(), NO_BODY_PATH);
        testContentLengthServerH1.addHandler(new ContentLengthHandler(), BODY_PATH);
        testContentLengthServerH2.addHandler(new ContentLengthHandler(), BODY_PATH);
        testContentLengthServerH3.addHandler(new ContentLengthHandler(), BODY_PATH);
        testContentLengthURIH1 = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(testContentLengthServerH1.getAddress().getPort())
                .build();
        testContentLengthURIH2 = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(testContentLengthServerH2.getAddress().getPort())
                .build();
        testContentLengthURIH3 = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(testContentLengthServerH3.getAddress().getPort())
                .build();

        testContentLengthServerH1.start();
        testLog.println("HTTP/1.1 Server up at address: " + testContentLengthServerH1.getAddress());
        testLog.println("Request URI for Client: " + testContentLengthURIH1);

        testContentLengthServerH2.start();
        testLog.println("HTTP/2 Server up at address: " + testContentLengthServerH2.getAddress());
        testLog.println("Request URI for Client: " + testContentLengthURIH2);

        testContentLengthServerH3.start();
        testLog.println("HTTP/3 Server up at address: "
                + testContentLengthServerH3.getAddress());
        testLog.println("HTTP/3 Quic Endpoint up at address: "
                + testContentLengthServerH3.getH3AltService().get().getAddress());
        testLog.println("Request URI for Client: " + testContentLengthURIH3);

        hc = HttpServerAdapters.createClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .build();
        var firstReq = HttpRequest.newBuilder(URI.create(testContentLengthURIH3 + NO_BODY_PATH))
                .setOption(H3_DISCOVERY, testContentLengthServerH3.h3DiscoveryConfig())
                .HEAD()
                .version(HTTP_2)
                .build();
        // populate alt-service registry
        var resp = hc.send(firstReq, BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        testLog.println("**** setup done ****");
    }

    @AfterAll
    public static void teardown() {
        testLog.println("**** tearing down ****");
        if (testContentLengthServerH1 != null)
            testContentLengthServerH1.stop();
        if (testContentLengthServerH2 != null)
            testContentLengthServerH2.stop();
        if (testContentLengthServerH3 != null)
            testContentLengthServerH3.stop();
    }

    static Object[][] bodies() {
        return new Object[][]{
                {HTTP_1_1, URI.create(testContentLengthURIH1 + BODY_PATH)},
                {HTTP_2, URI.create(testContentLengthURIH2 + BODY_PATH)},
                {HTTP_3, URI.create(testContentLengthURIH3 + BODY_PATH)}
        };
    }

    static Object[][] h1body() {
        return new Object[][]{
                {HTTP_1_1, URI.create(testContentLengthURIH1 + BODY_PATH)}
        };
    }

    static Object[][] nobodies() {
        return new Object[][]{
                {HTTP_1_1, URI.create(testContentLengthURIH1 + NO_BODY_PATH)},
                {HTTP_2, URI.create(testContentLengthURIH2 + NO_BODY_PATH)},
                {HTTP_3, URI.create(testContentLengthURIH3 + NO_BODY_PATH)}
        };
    }

    @ParameterizedTest
    // A GET request with no request body should have no Content-length header
    @MethodSource("nobodies")
    public void getWithNoBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking GET with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .GET()
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A GET request with empty request body should have no Content-length header
    @MethodSource("nobodies")
    public void getWithEmptyBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking GET with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A GET request with empty request body and explicitly added Content-length header
    @MethodSource("bodies")
    public void getWithZeroContentLength(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking GET with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .header("Content-length", "0")
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A GET request with a request body should have a Content-length header
    // in HTTP/1.1
    @MethodSource("bodies")
    public void getWithBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking GET with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("GET", HttpRequest.BodyPublishers.ofString("GET Body"))
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A DELETE request with no request body should have no Content-length header
    @MethodSource("nobodies")
    public void deleteWithNoBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking DELETE with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .DELETE()
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A DELETE request with empty request body should have no Content-length header
    @MethodSource("nobodies")
    public void deleteWithEmptyBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking DELETE with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A DELETE request with a request body should have a Content-length header
    //   in HTTP/1.1
    @MethodSource("bodies")
    public void deleteWithBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking DELETE with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("DELETE", HttpRequest.BodyPublishers.ofString("DELETE Body"))
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A HEAD request with no request body should have no Content-length header
    @MethodSource("nobodies")
    public void headWithNoBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking HEAD with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .HEAD()
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A HEAD request with empty request body should have no Content-length header
    @MethodSource("nobodies")
    public void headWithEmptyBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking HEAD with no request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A HEAD request with a request body should have a Content-length header
    // in HTTP/1.1
    @MethodSource("bodies")
    public void headWithBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking HEAD with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("HEAD", HttpRequest.BodyPublishers.ofString("HEAD Body"))
                .uri(uri)
                .build();
        // Sending this request invokes sendResponseHeaders which emits a warning about including
        // a Content-length header with a HEAD request
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A POST request with empty request body should have a Content-length header
    // in HTTP/1.1
    @MethodSource("h1body")
    public void postWithEmptyBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking POST with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("POST", HttpRequest.BodyPublishers.noBody())
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A POST request with a request body should have a Content-length header
    // in HTTP/1.1
    @MethodSource("bodies")
    public void postWithBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking POST with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .POST(HttpRequest.BodyPublishers.ofString("POST Body"))
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A PUT request with empty request body should have a Content-length header
    // in HTTP/1.1
    @MethodSource("h1body")
    public void putWithEmptyBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking PUT with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    @ParameterizedTest
    // A PUT request with a request body should have a Content-length header
    // in HTTP/1.1
    @MethodSource("bodies")
    public void putWithBody(Version version, URI uri) throws IOException, InterruptedException {
        testLog.println(version + " Checking PUT with request body");
        HttpRequest req = HttpRequest.newBuilder()
                .version(version)
                .PUT(HttpRequest.BodyPublishers.ofString("PUT Body"))
                .uri(uri)
                .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(version, resp.version());
    }

    public static void handleResponse(long expected, HttpTestExchange ex, String body, int rCode) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            byte[] reqBody = is.readAllBytes();
            if (expected == -1 || expected == reqBody.length || rCode != 200) {
                sendResponse(ex, body, rCode);
            } else {
                body = body + ", but Content-Length:%s doesn't match body size %s"
                        .formatted(expected, reqBody.length);
                testLog.println("Unexpected content length value: " + body);
                sendResponse(ex, body, 400);
            }
        }
    }

    public static void sendResponse(HttpTestExchange ex, String body, int rCode) throws IOException {
        try (OutputStream os = ex.getResponseBody()) {
            byte[] bytes = body.getBytes(UTF_8);
            ex.sendResponseHeaders(rCode, bytes.length);
            os.write(bytes);
        }
    }

    static class NoContentLengthHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            testLog.println("NoContentLengthHandler: Received Headers " +
                    exchange.getRequestHeaders().entrySet() +
                            " from " + exchange.getRequestMethod() + " request.");
            Optional<String> contentLength = exchange.getRequestHeaders()
                    .firstValue("Content-length");

            // Check Content-length header was not set
            if (contentLength.isPresent()) {
                String responseBody = exchange.getRequestMethod() + " request contained an unexpected " +
                        "Content-length header of value: " +
                        exchange.getRequestHeaders().firstValue("Content-length").get();
                handleResponse(-1, exchange, responseBody, 400);
            } else {
                handleResponse(0, exchange, "Request completed",200);
            }
        }
    }

    static class ContentLengthHandler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            testLog.println("ContentLengthHandler: Received Headers " + exchange.getRequestHeaders().entrySet() +
                            " from " + exchange.getRequestMethod() + " request.");
            Optional<String> contentLength = exchange.getRequestHeaders()
                    .firstValue("Content-length");

            // Check Content-length header was set
            if (contentLength.isPresent()) {
                handleResponse(Long.parseLong(contentLength.get()), exchange, "Request completed", 200);
            } else {
                String responseBody = "Expected a Content-length header in " +
                        exchange.getRequestMethod() + " request but was not present.";
                handleResponse(-1, exchange, responseBody, 400);
            }
        }
    }
}
