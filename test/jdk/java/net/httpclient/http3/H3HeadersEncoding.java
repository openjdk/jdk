/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @compile ../ReferenceTracker.java
 * @run testng/othervm -Djdk.httpclient.qpack.encoderTableCapacityLimit=4096
 *                     -Djdk.httpclient.qpack.decoderMaxTableCapacity=4096
 *                     -Dhttp3.test.server.encoderAllowedHeaders=*
 *                     -Dhttp3.test.server.decoderMaxTableCapacity=4096
 *                     -Dhttp3.test.server.encoderTableCapacityLimit=4096
 *                     -Djdk.internal.httpclient.qpack.log.level=NORMAL
 *                     H3HeadersEncoding
 * @summary this test verifies that when QPACK dynamic table is enabled multiple
 *          random headers can be encoded/decoded correctly
 */

import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static jdk.httpclient.test.lib.common.HttpServerAdapters.*;

public class H3HeadersEncoding {

    private static final int REQUESTS_COUNT = 500;
    private static final int HEADERS_PER_REQUEST = 20;
    SSLContext sslContext;
    HttpTestServer http3TestServer;
    HeadersHandler serverHeadersHandler;
    String http3URI;

    @BeforeTest
    public void setup() throws Exception {
        System.out.println("Creating servers");
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        http3TestServer = HttpTestServer.create(Http3DiscoveryMode.HTTP_3_URI_ONLY, sslContext);
        serverHeadersHandler = new HeadersHandler();
        http3TestServer.addHandler(serverHeadersHandler, "/http3/headers");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/headers";

        http3TestServer.start();
    }

    @AfterTest
    public void tearDown() {
        http3TestServer.stop();
    }

    @Test
    public void serialRequests() throws Exception {
        try (HttpClient client = newClient()) {
            for (int i = 0; i < REQUESTS_COUNT; i++) {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(http3URI))
                        .version(HTTP_3)
                        .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
                List<TestHeader> rndHeaders = TestHeader.randomHeaders(HEADERS_PER_REQUEST);
                String[] requestHeaders = rndHeaders.stream()
                        .flatMap(th -> Stream.of(th.name(), th.value()))
                        .toArray(String[]::new);
                requestBuilder.headers(requestHeaders);

                // Send client request headers in request body for further check
                // on the server handler side
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                        TestHeader.headersToBodyContent(rndHeaders)));

                HttpResponse<String> response =
                        client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                // Headers received by the client
                var serverHeadersClientSide = TestHeader.fromHttpHeaders(response.headers());
                // Headers sent by the server handler
                var serverHeadersServerSide = TestHeader.bodyContentToHeaders(response.body());

                // Check that all headers that server sent are received on client side
                checkHeaders(serverHeadersServerSide, serverHeadersClientSide, "client");
            }
        }
    }

    @Test
    public void asyncRequests() throws Exception {
        try (HttpClient client = newClient()) {
            ArrayList<List<TestHeader>> requestsHeaders = new ArrayList<>(REQUESTS_COUNT);
            ArrayList<HttpRequest> requests = new ArrayList<>(REQUESTS_COUNT);
            CopyOnWriteArrayList<CompletableFuture<HttpResponse<String>>> futureReplies
                    = new CopyOnWriteArrayList<>();

            // Prepare all requests first
            for (int i = 0; i < REQUESTS_COUNT; i++) {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(http3URI))
                        .version(HTTP_3)
                        .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
                List<TestHeader> rndHeaders = TestHeader.randomHeaders(HEADERS_PER_REQUEST);
                String[] requestHeaders = rndHeaders.stream()
                        .flatMap(th -> Stream.of(th.name(), th.value()))
                        .toArray(String[]::new);
                requestBuilder.headers(requestHeaders);
                requestsHeaders.add(i, rndHeaders);

                // Send client request headers in request body for further check
                // on the server handler side
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                        TestHeader.headersToBodyContent(rndHeaders)));
                requests.add(i, requestBuilder.build());
            }

            // Send async request
            for (int i = 0; i < REQUESTS_COUNT; i++) {
                CompletableFuture<HttpResponse<String>> cf =
                        client.sendAsync(requests.get(i),
                                HttpResponse.BodyHandlers.ofString());
                futureReplies.add(i, cf);
            }

            // Join on all CF responses
            for (int i = 0; i < REQUESTS_COUNT; i++) {
                futureReplies.get(i).join();
            }

            // Check the responses
            for (int i = 0; i < REQUESTS_COUNT; i++) {
                HttpResponse<String> reply = futureReplies.get(i).get();
                // Headers received by the client
                var serverHeadersClientSide = TestHeader.fromHttpHeaders(reply.headers());
                // Headers sent by the server handler
                var serverHeadersServerSide = TestHeader.bodyContentToHeaders(reply.body());
                // Check that all headers that server sent are received on client side
                checkHeaders(serverHeadersServerSide, serverHeadersClientSide, "client");
            }
        }
    }


    private static void checkHeaders(List<TestHeader> mustPresent,
                                     List<TestHeader> allHeaders,
                                     String sideDescription) {
        List<TestHeader> notFound = new ArrayList<>();
        for (var header : mustPresent) {
            if (!allHeaders.contains(header)) {
                notFound.add(header);
            }
        }
        if (!notFound.isEmpty()) {
            System.err.println("The following headers was not found on "
                    + sideDescription + " side: " + notFound);
            throw new RuntimeException("Headers not found: " + notFound);
        }
    }

    HttpClient newClient() {
        var builder = createClientBuilderForH3()
                .sslContext(sslContext)
                .version(HTTP_3)
                .proxy(HttpClient.Builder.NO_PROXY);
        return builder.build();
    }

    private static final Random RND = RandomFactory.getRandom();

    record TestHeader(String name, String value) {
        static TestHeader randomHeader() {
            // It is better to have same id generated two or more
            // times during the test run, therefore the range below
            int headerId = RND.nextInt(10, 10 + HEADERS_PER_REQUEST * 3);
            return new TestHeader("test_header" + headerId, "TestValue");
        }

        static List<TestHeader> randomHeaders(int count) {
            return IntStream
                    .range(0, count)
                    .boxed()
                    .map(ign -> TestHeader.randomHeader())
                    .toList();
        }

        static List<TestHeader> fromHttpHeaders(HttpHeaders httpHeaders) {
            var headersMap = httpHeaders.map();
            return fromHeadersEntrySet(headersMap.entrySet());
        }

        static List<TestHeader> fromTestHttpHeaders(HttpTestRequestHeaders requestHeaders) {
            var headersSet = requestHeaders.entrySet();
            return fromHeadersEntrySet(headersSet);
        }

        private static List<TestHeader> fromHeadersEntrySet(Set<Map.Entry<String, List<String>>> entrySet) {
            return entrySet.stream()
                    .flatMap(entry -> {
                        var name = entry.getKey();
                        return entry.getValue()
                                .stream()
                                .map(value -> new TestHeader(name, value));
                    }).toList();
        }

        public static String headersToBodyContent(List<TestHeader> rndHeaders) {
            return rndHeaders.stream()
                    .map(TestHeader::toString)
                    .collect(Collectors.joining(System.lineSeparator()));
        }

        public static List<TestHeader> bodyContentToHeaders(String bodyContent) {
            return Arrays.stream(bodyContent.split(System.lineSeparator()))
                    .filter(Predicate.not(String::isBlank))
                    .map(String::strip)
                    .map(TestHeader::fromBodyHeaderLine)
                    .toList();
        }

        public static TestHeader fromBodyHeaderLine(String headerLine) {
            String[] parts = headerLine.split(":");
            if (parts.length != 2) {
                throw new RuntimeException("Internal test error");
            }
            return new TestHeader(parts[0], parts[1]);
        }

        public String toString() {
            return name + ":" + value;
        }
    }


    private class HeadersHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {

            var clientHeadersServerSide = TestHeader.fromTestHttpHeaders(t.getRequestHeaders());

            String requestBody;
            try (InputStream is = t.getRequestBody()) {
                byte[] body = is.readAllBytes();
                requestBody = new String(body);
            }
            var clientHeadersClientSide = TestHeader.bodyContentToHeaders(requestBody);

            // Check that all headers that client sent are received on tne server side
            checkHeaders(clientHeadersClientSide, clientHeadersServerSide,
                    "server handler");

            // Response back with a set of random headers
            var responseHeaders = t.getResponseHeaders();
            List<TestHeader> serverResp = new ArrayList<>();
            for (TestHeader h : TestHeader.randomHeaders(HEADERS_PER_REQUEST)) {
                serverResp.add(h);
                responseHeaders.addHeader(h.name(), h.value());
            }

            String responseBody = TestHeader.headersToBodyContent(serverResp);
            try (OutputStream os = t.getResponseBody()) {
                byte[] responseBodyBytes = responseBody.getBytes();
                t.sendResponseHeaders(200, responseBodyBytes.length);
                if (!t.getRequestMethod().equals("HEAD")) {
                    os.write(responseBodyBytes);
                }
            }
        }
    }

}
