/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib server
 * @bug 8296410
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors,requests,headers TrailingHeadersTest
 */

import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.DataFrame;
import jdk.internal.net.http.frame.HeaderFrame;
import jdk.internal.net.http.frame.HeadersFrame;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;

public class TrailingHeadersTest {

    Http2TestServer http2TestServer;
    URI trailingURI, trailng1xxURI, warmupURI;
    static PrintStream testLog = System.err;

    @BeforeTest
    public void setup() throws Exception {
        http2TestServer = new Http2TestServer(false, 0);
        http2TestServer.setExchangeSupplier(TrailingHeadersExchange::new);
        http2TestServer.addHandler(new ResponseTrailersHandler(), "/ResponseTrailingHeaders");
        http2TestServer.addHandler(new InformationalTrailersHandler(), "/InfoRespTrailingHeaders");
        http2TestServer.addHandler(new WarmupHandler(), "/WarmupHandler");

        http2TestServer.start();

        trailingURI = URI.create("http://" + http2TestServer.serverAuthority() + "/ResponseTrailingHeaders");
        trailng1xxURI = URI.create("http://" + http2TestServer.serverAuthority() + "/InfoRespTrailingHeaders");
        warmupURI = URI.create("http://" + http2TestServer.serverAuthority() + "/WarmupHandler");

        testLog.println(this.getClass().getCanonicalName() + ": setup(): trailing = " + trailingURI);
        testLog.println(this.getClass().getCanonicalName() + ": setup(): trailing1xxURI = " + trailng1xxURI);
    }

    @AfterTest
    public void teardown() {
        http2TestServer.stop();
    }

    @Test
    public void testTrailingHeaders() {
        HttpClient httpClient = HttpClient.newBuilder().build();
        testLog.println(this.getClass().getCanonicalName() + ": testTrailingHeaders(): Performing warmup request, upgrade to HTTP/2");
        performWarmupRequest(httpClient);

        HttpRequest hRequest = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .GET()
                .uri(trailingURI)
                .build();
        testLog.println(this.getClass().getCanonicalName() + ": testTrailingHeaders(): Client sending GET");
        CompletableFuture<HttpResponse<String>> hSend = httpClient.sendAsync(hRequest, BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> hResponse = hSend.join();
        assertEquals(hResponse.statusCode(), 200, ("Expected 200 but got " + hResponse.statusCode()));
    }

    /**
     * Changes concerned with this test alters behavior of how partial http responses are handled (1XX Codes).
     * Therefore, an additional test case checking that partial responses, when expected by the client, behave
     * as expected.
     */
    @Test
    public void testTrailingHeaders1XX() {
        HttpClient httpClient = HttpClient.newBuilder().build();
        testLog.println(this.getClass().getCanonicalName() + ": testTrailingHeaders1XX(): Performing warmup request, upgrade to HTTP/2");
        performWarmupRequest(httpClient);

        HttpRequest hRequest = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .POST(HttpRequest.BodyPublishers.ofString("Test Post"))
                .expectContinue(true)
                .uri(trailng1xxURI)
                .build();
        testLog.println(this.getClass().getCanonicalName() + ": testTrailingHeaders1XX(): Client sending POST with 100-continue header");
        CompletableFuture<HttpResponse<String>> hSend = httpClient.sendAsync(hRequest, BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> hResponse = hSend.join();
        assertEquals(hResponse.statusCode(), 200, ("Expected 200 but got " + hResponse.statusCode()));
    }

    private void performWarmupRequest(HttpClient httpClient) {
        HttpRequest warmupReq = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .GET()
                .uri(warmupURI)
                .build();
        CompletableFuture<HttpResponse<String>> warmup = httpClient.sendAsync(warmupReq, BodyHandlers.ofString(StandardCharsets.UTF_8));
        warmup.join();
    }

    static class TrailingHeadersExchange extends Http2TestExchangeImpl {

        byte[] resp = "Test Response".getBytes(StandardCharsets.UTF_8);


        TrailingHeadersExchange(int streamid, String method, HttpHeaders reqheaders, HttpHeadersBuilder rspheadersBuilder,
                                 URI uri, InputStream is, SSLSession sslSession, BodyOutputStream os,
                                 Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession, os, conn, pushAllowed);
        }

        public void sendResponseThenTrailers() throws IOException {
            this.sendResponseHeaders(200, resp.length);
            DataFrame dataFrame = new DataFrame(this.streamid, 0, ByteBuffer.wrap(resp));
            this.conn.outputQ.put(dataFrame);
            int flags = HeaderFrame.END_HEADERS & HeaderFrame.END_STREAM;
            HeaderFrame headerFrame = new HeadersFrame(this.streamid, flags, List.of());
            this.conn.outputQ.put(headerFrame);
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
            }
        }
    }

    static class InformationalTrailersHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (exchange.getProtocol().equals("HTTP/2")) {
                if (exchange instanceof TrailingHeadersExchange trailingHeadersExchange) {
                    testLog.println(this.getClass().getCanonicalName() + ": InformationalTrailersHandler: Sending status 100");
                    trailingHeadersExchange.sendResponseHeaders(100, 0);

                    try (InputStream is = exchange.getRequestBody()) {
                        is.readAllBytes();
                        trailingHeadersExchange.sendResponseThenTrailers();
                    }
                }
            } else {
                testLog.println(this.getClass().getCanonicalName() + ": InformationalTrailersHandler: Incorrect protocol version");
                exchange.sendResponseHeaders(400, 0);
            }
        }


    }
}