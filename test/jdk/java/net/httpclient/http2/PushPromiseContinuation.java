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
 * @bug 8263031
 * @summary Tests that the HttpClient can correctly receive a Push Promise
 *          Frame with the END_HEADERS flag unset followed by one or more
 *          Continuation Frames.
 * @library /test/lib server
 * @build jdk.test.lib.net.SimpleSSLContext
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 * @run testng/othervm PushPromiseContinuation
 */


import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.ContinuationFrame;
import jdk.internal.net.http.frame.HeaderFrame;
import org.testng.TestException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class PushPromiseContinuation {

    static volatile HttpHeaders testHeaders;
    static volatile HttpHeadersBuilder testHeadersBuilder;
    static volatile int continuationCount;
    static final String mainPromiseBody = "Main Promise Body";
    static final String mainResponseBody = "Main Response Body";
    Http2TestServer server;
    URI uri;

    // Set up simple client-side push promise handler
    ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>> pushPromiseMap = new ConcurrentHashMap<>();
    HttpResponse.PushPromiseHandler<String> pph = (initial, pushRequest, acceptor) -> {
        HttpResponse.BodyHandler<String> s = HttpResponse.BodyHandlers.ofString(UTF_8);
        pushPromiseMap.put(pushRequest, acceptor.apply(s));
    };

    @BeforeMethod
    public void beforeMethod() {
        pushPromiseMap = new ConcurrentHashMap<>();
    }

    @BeforeTest
    public void setup() throws Exception {
        server = new Http2TestServer(false, 0);
        server.addHandler(new ServerPushHandler(), "/");

        // Need to have a custom exchange supplier to manage the server's push
        // promise with continuation flow
        server.setExchangeSupplier(Http2LPPTestExchangeImpl::new);

        System.err.println("PushPromiseContinuation: Server listening on port " + server.getAddress().getPort());
        server.start();
        int port = server.getAddress().getPort();
        uri = new URI("http://localhost:" + port + "/");
    }

    @AfterTest
    public void teardown() {
        pushPromiseMap = null;
        server.stop();
    }

    /**
     * Tests that when the client receives PushPromise Frame with the END_HEADERS
     * flag set to 0x0 and subsequently receives a continuation frame, no exception
     * is thrown and all headers from the PushPromise and Continuation Frames sent
     * by the server arrive at the client.
     */
    @Test
    public void testOneContinuation() {
        continuationCount = 1;
        HttpClient client = HttpClient.newHttpClient();

        // Carry out request
        HttpRequest hreq = HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_2).GET().build();
        CompletableFuture<HttpResponse<String>> cf =
                client.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8), pph);
        HttpResponse<String> resp = cf.join();

        // Verify results
        verify(resp);
    }

    /**
     * Same as above, but tests for the case where two Continuation Frames are sent
     * with the END_HEADERS flag set only on the last frame.
     */
    @Test
    public void testTwoContinuations() {
        continuationCount = 2;
        HttpClient client = HttpClient.newHttpClient();

        // Carry out request
        HttpRequest hreq = HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_2).GET().build();
        CompletableFuture<HttpResponse<String>> cf =
                client.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8), pph);
        HttpResponse<String> resp = cf.join();

        // Verify results
        verify(resp);
    }

    @Test
    public void testThreeContinuations() {
        continuationCount = 3;
        HttpClient client = HttpClient.newHttpClient();

        // Carry out request
        HttpRequest hreq = HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_2).GET().build();
        CompletableFuture<HttpResponse<String>> cf =
                client.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8), pph);
        HttpResponse<String> resp = cf.join();

        // Verify results
        verify(resp);
    }

    private void verify(HttpResponse<String> resp) {
        assertEquals(resp.statusCode(), 200);
        assertEquals(resp.body(), mainResponseBody);
        if (pushPromiseMap.size() > 1) {
            System.err.println(pushPromiseMap.entrySet());
            throw new TestException("Results map size is greater than 1");
        } else {
            // This will only iterate once
            for (HttpRequest r : pushPromiseMap.keySet()) {
                HttpResponse<String> serverPushResp = pushPromiseMap.get(r).join();
                // Received headers should be the same as the combined PushPromise
                // frame headers combined with the Continuation frame headers
                assertEquals(testHeaders, r.headers());
                // Check status code and push promise body are as expected
                assertEquals(serverPushResp.statusCode(), 200);
                assertEquals(serverPushResp.body(), mainPromiseBody);
            }
        }
    }

    static class Http2LPPTestExchangeImpl extends Http2TestExchangeImpl {

        HttpHeadersBuilder pushPromiseHeadersBuilder;
        List<ContinuationFrame> cfs;

        Http2LPPTestExchangeImpl(int streamid, String method, HttpHeaders reqheaders,
                                 HttpHeadersBuilder rspheadersBuilder, URI uri, InputStream is,
                                 SSLSession sslSession, BodyOutputStream os,
                                 Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession, os, conn, pushAllowed);
        }

        private void setPushHeaders(String name, String value) {
            pushPromiseHeadersBuilder.setHeader(name, value);
            testHeadersBuilder.setHeader(name, value);
        }

        private void assembleContinuations() {
            for (int i = 0; i < continuationCount; i++) {
                HttpHeadersBuilder builder = new HttpHeadersBuilder();
                for (int j = 0; j < 10; j++) {
                    String name = "x-cont-" + i + "-" + j;
                    builder.setHeader(name, "data_" + j);
                    testHeadersBuilder.setHeader(name, "data_" + j);
                }

                ContinuationFrame cf = new ContinuationFrame(streamid, 0x0, conn.encodeHeaders(builder.build()));
                // If this is the last Continuation Frame, set the END_HEADERS flag.
                if (i >= continuationCount - 1) {
                    cf.setFlag(HeaderFrame.END_HEADERS);
                }
                cfs.add(cf);
            }
        }

        @Override
        public void serverPush(URI uri, HttpHeaders headers, InputStream content) {
            pushPromiseHeadersBuilder = new HttpHeadersBuilder();
            testHeadersBuilder = new HttpHeadersBuilder();
            cfs = new ArrayList<>();

            setPushHeaders(":method", "GET");
            setPushHeaders(":scheme", uri.getScheme());
            setPushHeaders(":authority", uri.getAuthority());
            setPushHeaders(":path", uri.getPath());
            for (Map.Entry<String,List<String>> entry : headers.map().entrySet()) {
                for (String value : entry.getValue()) {
                    setPushHeaders(entry.getKey(), value);
                }
            }

            for (int i = 0; i < 10; i++) {
                setPushHeaders("x-push-header-" + i, "data_" + i);
            }

            // Create the Continuation Frame/s, done before Push Promise Frame for test purposes
            // as testHeaders contains all headers used in all frames
            assembleContinuations();

            HttpHeaders pushPromiseHeaders = pushPromiseHeadersBuilder.build();
            testHeaders = testHeadersBuilder.build();
            // Create the Push Promise Frame
            OutgoingPushPromise pp = new OutgoingPushPromise(streamid, uri, pushPromiseHeaders, content);
            // Indicates to the client that a continuation should be expected
            pp.setFlag(0x0);

            try {
                // Schedule push promise and continuation for sending
                conn.outputQ.put(pp);
                System.err.println("Server: Scheduled a Push Promise to Send");
                for (ContinuationFrame cf : cfs) {
                    conn.outputQ.put(cf);
                    System.err.println("Server: Scheduled a Continuation to Send");
                }
            } catch (IOException ex) {
                System.err.println("Server: pushPromise exception: " + ex);
            }
        }
    }

    static class ServerPushHandler implements Http2Handler {

        public void handle(Http2TestExchange exchange) throws IOException {
            System.err.println("Server: handle " + exchange);
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }

            if (exchange.serverPushAllowed()) {
                pushPromise(exchange);
            }

            // response data for the main response
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] bytes = mainResponseBody.getBytes(UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }

        static final BiPredicate<String,String> ACCEPT_ALL = (x, y) -> true;


        private void pushPromise(Http2TestExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            URI uri = requestURI.resolve("/promise");
            InputStream is = new ByteArrayInputStream(mainPromiseBody.getBytes(UTF_8));
            Map<String, List<String>> map = new HashMap<>();
            map.put("x-promise", List.of("promise-header"));
            HttpHeaders headers = HttpHeaders.of(map, ACCEPT_ALL);
            exchange.serverPush(uri, headers, is);
            System.err.println("Server: Push Promise complete");
        }
    }
}
