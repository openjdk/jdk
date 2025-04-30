/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8354276
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm
 *      -Djdk.internal.httpclient.debug=true
 *      -Djdk.httpclient.HttpClient.log=errors,requests,responses,trace
 *      BadPushPromiseTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.List.of;
import static org.testng.Assert.*;

public class BadPushPromiseTest {

    private static final List<Map<String, List<String>>> BAD_HEADERS = of(
            Map.of(":hello", of("GET")),                      // Unknown pseudo-header
            Map.of("hell o", of("value")),                    // Space in the name
            Map.of("hello", of("line1\r\n  line2\r\n")),      // Multiline value
            Map.of("hello", of("DE" + ((char) 0x7F) + "L")),  // Bad byte in value
            Map.of(":status", of("200"))                     // Response pseudo-header in request
    );

    static final String MAIN_RESPONSE_BODY = "the main response body";

    HttpServerAdapters.HttpTestServer server;
    URI uri;

    @BeforeTest
    public void setup() throws Exception {
        server = HttpServerAdapters.HttpTestServer.create(HTTP_2);
        HttpServerAdapters.HttpTestHandler handler = new ServerPushHandler(MAIN_RESPONSE_BODY);
        server.addHandler(handler, "/");
        server.start();
        String authority = server.serverAuthority();
        System.err.println("Server listening on address " + authority);
        uri = new URI("http://" + authority + "/foo/a/b/c");
    }

    @AfterTest
    public void teardown() {
        server.stop();
    }

    /*
     * Malformed push promise headers should kill the connection
     */
    @Test
    public void test() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        for (int i=0; i< BAD_HEADERS.size(); i++) {
            URI uriWithQuery = URI.create(uri +  "?BAD_HEADERS=" + i);
            HttpRequest request = HttpRequest.newBuilder(uriWithQuery)
                    .build();
            System.out.println("\nSending request:" + uriWithQuery);
            final HttpClient cc = client;
            try {
                ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>> promises
                        = new ConcurrentHashMap<>();
                PushPromiseHandler<String> pph = PushPromiseHandler
                        .of((r) -> BodyHandlers.ofString(), promises);
                HttpResponse<String> response = cc.sendAsync(request, BodyHandlers.ofString(), pph).join();
                fail("Expected exception, got :" + response + ", " + response.body());
            } catch (CompletionException ce) {
                System.out.println("Got EXPECTED: " + ce);
                assertDetailMessage(ce.getCause(), i);
            }
        }
    }

    // Assertions based on implementation specific detail messages. Keep in
    // sync with implementation.
    static void assertDetailMessage(Throwable throwable, int iterationIndex) {
        try {
            assertTrue(throwable instanceof ProtocolException,
                    "Expected ProtocolException, got " + throwable);

            if (iterationIndex == 0) { // unknown
                assertTrue(throwable.getMessage().contains("Unknown pseudo-header"),
                        "Expected \"Unknown pseudo-header\" in: " + throwable.getMessage());
            } else if (iterationIndex == 4) { // unexpected type
                assertTrue(throwable.getMessage().contains("not valid in context"),
                        "Expected \"not valid in context\" in: " + throwable.getMessage());
            } else {
                assertTrue(throwable.getMessage().contains("Bad header"),
                        "Expected \"Bad header\" in: " + throwable.getMessage());
            }
        } catch (AssertionError e) {
            System.out.println("Exception does not match expectation: " + throwable);
            throwable.printStackTrace(System.out);
            throw e;
        }
    }

    // --- server push handler ---
    static class ServerPushHandler implements HttpServerAdapters.HttpTestHandler {

        private final String mainResponseBody;

        public ServerPushHandler(String mainResponseBody) {
            this.mainResponseBody = mainResponseBody;
        }

        public void handle(HttpServerAdapters.HttpTestExchange exchange) throws IOException {
            System.err.println("Server: handle " + exchange);
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }

            pushPromise(exchange);

            // response data for the main response
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = mainResponseBody.getBytes(UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }

        private void pushPromise(HttpServerAdapters.HttpTestExchange exchange) {
            URI requestURI = exchange.getRequestURI();
            String query = exchange.getRequestURI().getQuery();
            int badHeadersIndex = Integer.parseInt(query.substring(query.indexOf("=") + 1));
            URI uri = requestURI.resolve("/push/"+badHeadersIndex);
            InputStream is = new ByteArrayInputStream(mainResponseBody.getBytes(UTF_8));
            HttpHeaders headers = HttpHeaders.of(BAD_HEADERS.get(badHeadersIndex), (x, y) -> true);
            exchange.serverPush(uri, headers, is);
            System.err.println("Server: push sent");
        }
    }
}
