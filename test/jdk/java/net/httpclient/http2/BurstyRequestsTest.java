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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeSupplier;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.internal.net.http.HttpClientImplAccess;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8326498 8361091
 * @summary verify that the HttpClient does not leak connections when dealing with
 *          sudden rush of HTTP/2 requests
 * @library /test/lib /test/jdk/java/net/httpclient/lib ../access
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.httpclient.test.lib.http2.Http2Handler
 *        jdk.httpclient.test.lib.http2.Http2TestExchange
 *        jdk.httpclient.test.lib.http2.Http2TestExchangeSupplier
 *        java.net.http/jdk.internal.net.http.HttpClientImplAccess
 * @run junit ${test.main.class}
 */
class BurstyRequestsTest {

    private static final String HANDLER_PATH = "/8326498/";

    // we use a h2c server but it doesn't matter if it is h2c or h2
    private static Http2TestServer http2Server;

    @BeforeAll
    static void beforeAll() throws Exception {
        http2Server = new Http2TestServer(false, 0);
        http2Server.setExchangeSupplier(new ExchangeSupplier());
        http2Server.addHandler(new Handler(), HANDLER_PATH);
        http2Server.start();
        System.err.println("started HTTP/2 server " + http2Server.getAddress());
    }

    @AfterAll
    static void afterAll() {
        if (http2Server != null) {
            System.err.println("stopping server " + http2Server.getAddress());
            http2Server.stop();
        }
    }

    /*
     * Issues a burst of HTTP/2 requests to the same server (host/port) and expects all of
     * them to complete normally.
     * Once these requests have completed, the test then peeks into an internal field of the
     * HttpClientImpl to verify that the client is holding on to at most 1 connection.
     */
    @Test
    void testOpenConnections() throws Exception {
        final URI reqURI = URIBuilder.newBuilder()
                .scheme("http")
                .host(http2Server.getAddress().getAddress())
                .port(http2Server.getAddress().getPort())
                .path(HANDLER_PATH)
                .build();
        final HttpRequest req = HttpRequest.newBuilder().uri(reqURI).build();

        final int numRequests = 20;
        // latch for the tasks to wait on, before issuing the requests
        final CountDownLatch startLatch = new CountDownLatch(numRequests);
        final List<Future<Void>> futures = new ArrayList<>();

        try (final ExecutorService executor = Executors.newCachedThreadPool();
             final HttpClient client = HttpClient.newBuilder()
                     .proxy(NO_PROXY)
                     .version(HTTP_2)
                     .build()) {
            // our test needs to peek into the internal field of jdk.internal.net.http.HttpClientImpl
            final Set<?> openedConnections = HttpClientImplAccess.getOpenedConnections(client);
            assertNotNull(openedConnections, "HttpClientImpl#openedConnections field is null or not available");

            for (int i = 0; i < numRequests; i++) {
                final Future<Void> f = executor.submit(new RequestIssuer(startLatch, client, req));
                futures.add(f);
            }
            // wait for the requests to complete
            for (final Future<Void> f : futures) {
                f.get();
            }
            System.err.println("all " + numRequests + " requests completed successfully");
            // the request completion happens asynchronously to the closing of the HTTP/2 Stream
            // as well as the HTTP/2 connection. we wait for at most 1 connection to be retained
            // by HttpClientImpl.
            System.err.println("waiting for at least " + (numRequests - 1) + " connections to be closed");
            // now verify that the current open TCP connections is not more than 1.
            // we let the test timeout if we never reach that count.
            int size = openedConnections.size();
            System.err.println("currently " + size + " open connections: " + openedConnections);
            while (size > 1) {
                // wait
                Thread.sleep(100);
                final int prev = size;
                size = openedConnections.size();
                if (prev != size) {
                    System.err.println("currently " + size + " open connections: " + openedConnections);
                }
            }
            // we expect at most 1 connection will stay open
            assertTrue((size == 0 || size == 1),
                    "unexpected number of current open connections: " + size);
        }
    }

    private static final class RequestIssuer implements Callable<Void> {
        private final CountDownLatch startLatch;
        private final HttpClient client;
        private final HttpRequest request;

        private RequestIssuer(final CountDownLatch startLatch, final HttpClient client,
                              final HttpRequest request) {
            this.startLatch = startLatch;
            this.client = client;
            this.request = request;
        }

        @Override
        public Void call() throws Exception {
            this.startLatch.countDown(); // announce our arrival
            this.startLatch.await(); // wait for other threads to arrive
            // issue the request
            final HttpResponse<Void> resp = this.client.send(request, BodyHandlers.discarding());
            if (resp.statusCode() != 200) {
                throw new AssertionError("unexpected response status code: " + resp.statusCode());
            }
            return null;
        }
    }

    private static final class Handler implements Http2Handler {
        private static final int NO_RESP_BODY = -1;

        @Override
        public void handle(final Http2TestExchange exchange) throws IOException {
            System.err.println("handling request " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, NO_RESP_BODY);
        }
    }

    private static final class ExchangeSupplier implements Http2TestExchangeSupplier {

        @Override
        public Http2TestExchange get(int streamid, String method, HttpHeaders reqheaders,
                                     HttpHeadersBuilder rspheadersBuilder, URI uri, InputStream is,
                                     SSLSession sslSession, BodyOutputStream os,
                                     Http2TestServerConnection conn, boolean pushAllowed) {
            // don't close the connection when/if the client sends a GOAWAY
            conn.closeConnOnIncomingGoAway = false;
            return Http2TestExchangeSupplier.ofDefault().get(streamid, method, reqheaders,
                    rspheadersBuilder, uri, is, sslSession, os, conn, pushAllowed);
        }
    }
}
