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
 @test
 * @summary Trailing headers should be ignored by the client when using HTTP/2
 *          and not affect the rest of the exchange.
 * @bug 8296410
 * @library /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all TrailingHeadersTest
 */

import jdk.httpclient.test.lib.http2.OutgoingPushPromise;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.DataFrame;
import jdk.internal.net.http.frame.HeaderFrame;
import jdk.internal.net.http.frame.HeadersFrame;
import org.testng.TestException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;

import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.BodyOutputStream;

import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class TrailingHeadersTest {

    Http2TestServer http2TestServer;
    URI trailingURI, trailng1xxURI, trailingPushPromiseURI, warmupURI;
    static PrintStream testLog = System.err;

    // Set up simple client-side push promise handler
    ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>> pushPromiseMap = new ConcurrentHashMap<>();

    @BeforeMethod
    public void beforeMethod() {
        pushPromiseMap = new ConcurrentHashMap<>();
    }

    @BeforeTest
    public void setup() throws Exception {
        Properties props = new Properties();
        // For triggering trailing headers to send after Push Promise Response headers are sent
        props.setProperty("sendTrailingHeadersAfterPushPromise", "1");

        http2TestServer = new Http2TestServer("Test_Server",
                                              false,
                                              0,
                                              null,
                                              0,
                                              props,
                                       null);
        http2TestServer.setExchangeSupplier(TrailingHeadersExchange::new);
        http2TestServer.addHandler(new ResponseTrailersHandler(), "/ResponseTrailingHeaders");
        http2TestServer.addHandler(new InformationalTrailersHandler(), "/InfoRespTrailingHeaders");
        http2TestServer.addHandler(new PushPromiseTrailersHandler(), "/PushPromiseTrailingHeaders");
        http2TestServer.addHandler(new WarmupHandler(), "/WarmupHandler");

        http2TestServer.start();

        trailingURI = URI.create("http://" + http2TestServer.serverAuthority() + "/ResponseTrailingHeaders");
        trailng1xxURI = URI.create("http://" + http2TestServer.serverAuthority() + "/InfoRespTrailingHeaders");
        trailingPushPromiseURI = URI.create("http://" + http2TestServer.serverAuthority() + "/PushPromiseTrailingHeaders");

        // Used to ensure HTTP/2 upgrade takes place
        warmupURI = URI.create("http://" + http2TestServer.serverAuthority() + "/WarmupHandler");
    }

    @AfterTest
    public void teardown() {
        http2TestServer.stop();
    }

    @Test(dataProvider = "httpRequests")
    public void testTrailingHeaders(String description, HttpRequest hRequest, HttpResponse.PushPromiseHandler<String> pph) {
        testLog.println("testTrailingHeaders(): " + description);
        HttpClient httpClient = HttpClient.newBuilder().build();
        performWarmupRequest(httpClient);
        CompletableFuture<HttpResponse<String>> cf = httpClient.sendAsync(hRequest, BodyHandlers.ofString(UTF_8), pph);

        testLog.println("testTrailingHeaders(): Performing request: " + hRequest);
        HttpResponse<String> resp = cf.join();

        assertEquals(resp.statusCode(), 200, "Status code of response should be 200");

        // Verify Push Promise was successful if necessary
        if (pph != null)
            verifyPushPromise();

        testLog.println("testTrailingHeaders(): Request successfully completed");
    }

    private void verifyPushPromise()  {
        assertEquals(pushPromiseMap.size(), 1, "Push Promise should not be greater than 1");
        // This will only iterate once
        for (HttpRequest r : pushPromiseMap.keySet()) {
            CompletableFuture<HttpResponse<String>> serverPushResp = pushPromiseMap.get(r);
            // Get the push promise HttpResponse result if present
            HttpResponse<String> resp = serverPushResp.join();
            assertEquals(resp.body(), "Sample_Push_Data", "Unexpected Push Promise response body");
            assertEquals(resp.statusCode(), 200, "Status code of Push Promise response should be 200");
        }
    }

    private void performWarmupRequest(HttpClient httpClient) {
        HttpRequest warmupReq = HttpRequest.newBuilder(warmupURI).version(HTTP_2)
                .GET()
                .build();
        httpClient.sendAsync(warmupReq, BodyHandlers.discarding()).join();
    }

    @DataProvider(name = "httpRequests")
    public Object[][] uris() {
        HttpResponse.PushPromiseHandler<String> pph = (initial, pushRequest, acceptor) -> {
            HttpResponse.BodyHandler<String> s = HttpResponse.BodyHandlers.ofString(UTF_8);
            pushPromiseMap.put(pushRequest, acceptor.apply(s));
        };

        HttpRequest httpGetTrailing = HttpRequest.newBuilder(trailingURI).version(HTTP_2)
                .GET()
                .build();

        HttpRequest httpPost1xxTrailing = HttpRequest.newBuilder(trailng1xxURI).version(HTTP_2)
                .POST(HttpRequest.BodyPublishers.ofString("Test Post"))
                .expectContinue(true)
                .build();

        HttpRequest httpGetPushPromiseTrailing = HttpRequest.newBuilder(trailingPushPromiseURI).version(HTTP_2)
                .GET()
                .build();

        return new Object[][] {
                { "Test GET with Trailing Headers", httpGetTrailing, null },
                { "Test POST with 1xx response & Trailing Headers", httpPost1xxTrailing, null },
                { "Test Push Promise with Trailing Headers", httpGetPushPromiseTrailing, pph }
        };
    }

    static class TrailingHeadersExchange extends Http2TestExchangeImpl {

        byte[] resp = "Sample_Data".getBytes(StandardCharsets.UTF_8);


        TrailingHeadersExchange(int streamid, String method, HttpHeaders reqheaders, HttpHeadersBuilder rspheadersBuilder,
                                 URI uri, InputStream is, SSLSession sslSession, BodyOutputStream os,
                                 Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession, os, conn, pushAllowed);
        }

        public void sendResponseThenTrailers() throws IOException {
            /*
            HttpHeadersBuilder hb = this.conn.createNewHeadersBuilder();
            hb.setHeader("x-sample", "val");
            HeaderFrame headerFrame = new HeadersFrame(this.streamid, 0, this.conn.encodeHeaders(hb.build()));
            */
            // TODO: see if there is a safe way to encode headers without interrupting connection thread
            HeaderFrame headerFrame = new HeadersFrame(this.streamid, 0, List.of());
            headerFrame.setFlag(HeaderFrame.END_HEADERS);
            headerFrame.setFlag(HeaderFrame.END_STREAM);

            this.sendResponseHeaders(200, resp.length);
            DataFrame dataFrame = new DataFrame(this.streamid, 0, ByteBuffer.wrap(resp));
            this.conn.addToOutputQ(dataFrame);
            this.conn.addToOutputQ(headerFrame);
        }

        @Override
        public void serverPush(URI uri, HttpHeaders headers, InputStream content) {
            HttpHeadersBuilder headersBuilder = new HttpHeadersBuilder();
            headersBuilder.setHeader(":method", "GET");
            headersBuilder.setHeader(":scheme", uri.getScheme());
            headersBuilder.setHeader(":authority", uri.getAuthority());
            headersBuilder.setHeader(":path", uri.getPath());
            for (Map.Entry<String,List<String>> entry : headers.map().entrySet()) {
                for (String value : entry.getValue())
                    headersBuilder.addHeader(entry.getKey(), value);
            }
            HttpHeaders combinedHeaders = headersBuilder.build();
            OutgoingPushPromise pp = new OutgoingPushPromise(streamid, uri, combinedHeaders, content);
            pp.setFlag(HeaderFrame.END_HEADERS);

            try {
                this.conn.addToOutputQ(pp);
            } catch (IOException ex) {
                testLog.println("serverPush(): pushPromise exception: " + ex);
            }
        }
    }

    static class WarmupHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, 0);
        }
    }

    static class ResponseTrailersHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (exchange.getProtocol().equals("HTTP/2")) {
                if (exchange instanceof TrailingHeadersExchange trailingHeadersExchange) {
                    trailingHeadersExchange.sendResponseThenTrailers();
                }
            } else {
                testLog.println("ResponseTrailersHandler: Incorrect protocol version");
                exchange.sendResponseHeaders(400, 0);
            }
        }
    }

    static class InformationalTrailersHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (exchange.getProtocol().equals("HTTP/2")) {
                if (exchange instanceof TrailingHeadersExchange trailingHeadersExchange) {
                    testLog.println(this.getClass().getCanonicalName() + ": Sending status 100");
                    trailingHeadersExchange.sendResponseHeaders(100, 0);

                    try (InputStream is = exchange.getRequestBody()) {
                        is.readAllBytes();
                        trailingHeadersExchange.sendResponseThenTrailers();
                    }
                }
            } else {
                testLog.println("InformationalTrailersHandler: Incorrect protocol version");
                exchange.sendResponseHeaders(400, 0);
            }
        }
    }

    static class PushPromiseTrailersHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (exchange.getProtocol().equals("HTTP/2")) {
                if (exchange instanceof TrailingHeadersExchange trailingHeadersExchange) {
                    try (InputStream is = exchange.getRequestBody()) {
                        is.readAllBytes();
                    }

                    if (exchange.serverPushAllowed()) {
                        pushPromise(trailingHeadersExchange);
                    }

                    try (OutputStream os = trailingHeadersExchange.getResponseBody()) {
                        byte[] bytes = "Sample_Data".getBytes(UTF_8);
                        trailingHeadersExchange.sendResponseHeaders(200, bytes.length);
                        os.write(bytes);
                    }
                }
            }
        }

        static final BiPredicate<String,String> ACCEPT_ALL = (x, y) -> true;

        private void pushPromise(Http2TestExchange exchange) {
            URI requestURI = exchange.getRequestURI();
            URI uri = requestURI.resolve("/promise");
            InputStream is = new ByteArrayInputStream("Sample_Push_Data".getBytes(UTF_8));
            Map<String, List<String>> map = new HashMap<>();
            map.put("x-promise", List.of("promise-header"));
            HttpHeaders headers = HttpHeaders.of(map, ACCEPT_ALL);
            exchange.serverPush(uri, headers, is);
            testLog.println("PushPromiseTrailersHandler: Push Promise complete");
        }
    }
}