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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @test
 * @summary verifies that when the server sends a GOAWAY frame then
 *          the client correctly handles it and retries unprocessed requests
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 * @run junit/othervm
 *            -Djdk.httpclient.HttpClient.log=errors,headers,quic:hs,http3
 *            H3GoAwayTest
 */
public class H3GoAwayTest {

    private static String REQ_URI_BASE;
    private static final SSLContext sslCtx = SimpleSSLContext.findSSLContext();
    private static Http3TestServer server;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = new Http3TestServer(sslCtx);
        final RequestApprover reqApprover = new RequestApprover();
        server.setRequestApprover(reqApprover::allowNewRequest);
        server.addHandler("/test", new Handler());
        server.start();
        System.out.println("Server started at " + server.getAddress());
        REQ_URI_BASE = URIBuilder.newBuilder().scheme("https")
                .loopback()
                .port(server.getAddress().getPort())
                .path("/test")
                .build().toString();
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            System.out.println("stopping server at " + server.getAddress());
            server.close();
        }
    }

    /**
     * Verifies that when several requests are sent using send() and the server
     * connection is configured to send a GOAWAY after processing only a few requests, then
     * the remaining requests are retried on a different connection
     */
    @Test
    public void testSequential() throws Exception {
        try (final HttpClient client = HttpServerAdapters
                .createClientBuilderFor(HTTP_3)
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(HTTP_3)
                .sslContext(sslCtx).build()) {
            final String[] reqMethods = {"HEAD", "GET", "POST"};
            for (final String reqMethod : reqMethods) {
                final int numReqs = RequestApprover.MAX_REQS_PER_CONN + 3;
                final Set<String> connectionKeys = new LinkedHashSet<>();
                for (int i = 1; i <= numReqs; i++) {
                    final URI reqURI = new URI(REQ_URI_BASE + "?" + reqMethod + "=" + i);
                    final HttpRequest req = HttpRequest.newBuilder()
                            .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                            .uri(reqURI)
                            .method(reqMethod, BodyPublishers.noBody())
                            .build();
                    System.out.println("initiating request " + req);
                    final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
                    final String respBody = resp.body();
                    System.out.println("received response: " + respBody);
                    assertEquals(200, resp.statusCode(),
                            "unexpected status code for request " + resp.request());
                    // response body is the logical key of the connection on which the
                    // request was handled
                    connectionKeys.add(respBody);
                }
                System.out.println("connections involved in handling the requests: "
                        + connectionKeys);
                // all requests have finished, we now just do a basic check that
                // more than one connection was involved in processing these requests
                assertEquals(2, connectionKeys.size(),
                        "unexpected number of connections " + connectionKeys);
            }
        }
    }

    private static final class RequestApprover {
        private static final int MAX_REQS_PER_CONN = 6;
        private final Map<String, AtomicInteger> numApproved =
                new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> numDisapproved =
                new ConcurrentHashMap<>();

        public boolean allowNewRequest(final String connKey) {
            final AtomicInteger approved = numApproved.computeIfAbsent(connKey,
                    (k) -> new AtomicInteger());
            int curr = approved.get();
            while (curr < MAX_REQS_PER_CONN) {
                if (approved.compareAndSet(curr, curr + 1)) {
                    return true; // new request allowed
                }
                curr = approved.get();
            }
            final AtomicInteger disapproved = numDisapproved.computeIfAbsent(connKey,
                    (k) -> new AtomicInteger());
            final int numUnprocessed = disapproved.incrementAndGet();
            System.out.println(approved.get() + " processed, "
                    + numUnprocessed + " unprocessed requests so far," +
                    " sending GOAWAY on connection " + connKey);
            return false;
        }
    }

    private static final class Handler implements Http2Handler {
        @Override
        public void handle(final Http2TestExchange exchange) throws IOException {
            final String connectionKey = exchange.getConnectionKey();
            System.out.println(connectionKey + " responding to request: " + exchange.getRequestURI());
            final byte[] response = connectionKey.getBytes(UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
