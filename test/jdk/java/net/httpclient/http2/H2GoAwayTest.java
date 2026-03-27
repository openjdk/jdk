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
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8335181
 * @summary verify that the HttpClient correctly handles incoming GOAWAY frames and
 *          retries any unprocessed requests on a new connection
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit H2GoAwayTest
 */
public class H2GoAwayTest {
    private static final String REQ_PATH = "/test";
    private static HttpTestServer server;
    private static String REQ_URI_BASE;
    private static final SSLContext sslCtx = SimpleSSLContext.findSSLContext();

    @BeforeAll
    static void beforeAll() throws Exception {
        server = HttpTestServer.create(HTTP_2, sslCtx);
        server.addHandler(new Handler(), REQ_PATH);
        server.start();
        System.out.println("Server started at " + server.getAddress());
        REQ_URI_BASE = URIBuilder.newBuilder().scheme("https")
                .loopback()
                .port(server.getAddress().getPort())
                .path(REQ_PATH)
                .build().toString();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            System.out.println("Stopping server at " + server.getAddress());
            server.stop();
        }
    }

    /**
     * Verifies that when several requests are sent using send() and the server
     * connection is configured to send a GOAWAY after processing only a few requests, then
     * the remaining requests are retried on a different connection
     */
    @Test
    public void testSequential() throws Exception {
        final LimitedPerConnRequestApprover reqApprover = new LimitedPerConnRequestApprover();
        server.setRequestApprover(reqApprover::allowNewRequest);
        try (final HttpClient client = HttpClient.newBuilder().version(HTTP_2)
                .sslContext(sslCtx).build()) {
            final String[] reqMethods = {"HEAD", "GET", "POST"};
            for (final String reqMethod : reqMethods) {
                final int numReqs = LimitedPerConnRequestApprover.MAX_REQS_PER_CONN + 3;
                final Set<String> connectionKeys = new LinkedHashSet<>();
                for (int i = 1; i <= numReqs; i++) {
                    final URI reqURI = new URI(REQ_URI_BASE + "?seq&" + reqMethod + "=" + i);
                    final HttpRequest req = HttpRequest.newBuilder()
                            .uri(reqURI)
                            .method(reqMethod, HttpRequest.BodyPublishers.noBody())
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
        } finally {
            server.setRequestApprover(null); // reset
        }
    }

    /**
     * Verifies that when a server responds with a GOAWAY and then never processes the new retried
     * requests on a new connection too, then the application code receives the request failure.
     * This tests the send() API of the HttpClient.
     */
    @Test
    public void testUnprocessedRaisesException() throws Exception {
        try (final HttpClient client = HttpClient.newBuilder().version(HTTP_2)
                .sslContext(sslCtx).build()) {
            final Random random = new Random();
            final String[] reqMethods = {"HEAD", "GET", "POST"};
            for (final String reqMethod : reqMethods) {
                final int maxAllowedReqs = 2;
                final int numReqs = maxAllowedReqs + 3; // 3 more requests than max allowed
                // configure the approver
                final LimitedRequestApprover reqApprover = new LimitedRequestApprover(maxAllowedReqs);
                server.setRequestApprover(reqApprover::allowNewRequest);
                try {
                    int numSuccess = 0;
                    int numFailed = 0;
                    for (int i = 1; i <= numReqs; i++) {
                        final String reqQueryPart = "?sync&" + reqMethod + "=" + i;
                        final URI reqURI = new URI(REQ_URI_BASE + reqQueryPart);
                        final HttpRequest req = HttpRequest.newBuilder()
                                .uri(reqURI)
                                .method(reqMethod, HttpRequest.BodyPublishers.noBody())
                                .build();
                        System.out.println("initiating request " + req);
                        if (i <= maxAllowedReqs) {
                            // expected to successfully complete
                            numSuccess++;
                            final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
                            final String respBody = resp.body();
                            System.out.println("received response: " + respBody);
                            assertEquals(200, resp.statusCode(),
                                    "unexpected status code for request " + resp.request());
                        } else {
                            // expected to fail as unprocessed
                            try {
                                final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
                                fail("Request was expected to fail as unprocessed,"
                                        + " but got response: " + resp.body() + ", status code: "
                                        + resp.statusCode());
                            } catch (IOException ioe) {
                                // verify it failed for the right reason
                                if (ioe.getMessage() == null
                                        || !ioe.getMessage().contains("request not processed by peer")) {
                                    // propagate the original failure
                                    throw ioe;
                                }
                                numFailed++; // failed due to right reason
                                System.out.println("received expected failure: " + ioe
                                        + ", for request " + reqURI);
                            }
                        }
                    }
                    // verify the correct number of requests succeeded/failed
                    assertEquals(maxAllowedReqs, numSuccess, "unexpected number of requests succeeded");
                    assertEquals((numReqs - maxAllowedReqs), numFailed, "unexpected number of requests failed");
                } finally {
                    server.setRequestApprover(null); // reset
                }
            }
        }
    }

    /**
     * Verifies that when a server responds with a GOAWAY and then never processes the new retried
     * requests on a new connection too, then the application code receives the request failure.
     * This tests the sendAsync() API of the HttpClient.
     */
    @Test
    public void testUnprocessedRaisesExceptionAsync() throws Throwable {
        try (final HttpClient client = HttpClient.newBuilder().version(HTTP_2)
                .sslContext(sslCtx).build()) {
            final Random random = new Random();
            final String[] reqMethods = {"HEAD", "GET", "POST"};
            for (final String reqMethod : reqMethods) {
                final int maxAllowedReqs = 2;
                final int numReqs = maxAllowedReqs + 3; // 3 more requests than max allowed
                // configure the approver
                final LimitedRequestApprover reqApprover = new LimitedRequestApprover(maxAllowedReqs);
                server.setRequestApprover(reqApprover::allowNewRequest);
                try {
                    final List<Future<HttpResponse<String>>> futures = new ArrayList<>();
                    for (int i = 1; i <= numReqs; i++) {
                        final URI reqURI = new URI(REQ_URI_BASE + "?async&" + reqMethod + "=" + i);
                        final HttpRequest req = HttpRequest.newBuilder()
                                .uri(reqURI)
                                .method(reqMethod, HttpRequest.BodyPublishers.noBody())
                                .build();
                        System.out.println("initiating request " + req);
                        final Future<HttpResponse<String>> f = client.sendAsync(req, BodyHandlers.ofString());
                        futures.add(f);
                    }
                    // wait for responses
                    int numFailed = 0;
                    int numSuccess = 0;
                    for (int i = 1; i <= numReqs; i++) {
                        final String reqQueryPart = "?async&" + reqMethod + "=" + i;
                        try {
                            System.out.println("waiting response of request "
                                    + REQ_URI_BASE + reqQueryPart);
                            final HttpResponse<String> resp = futures.get(i - 1).get();
                            numSuccess++;
                            final String respBody = resp.body();
                            System.out.println("request: " + resp.request()
                                    + ", received response: " + respBody);
                            assertEquals(200, resp.statusCode(),
                                    "unexpected status code for request " + resp.request());
                        } catch (ExecutionException ee) {
                            final Throwable cause = ee.getCause();
                            if (!(cause instanceof IOException ioe)) {
                                System.err.println("unexpected exception: " + cause
                                        + ", for request " + REQ_URI_BASE + reqQueryPart);
                                throw cause;
                            }
                            // verify it failed for the right reason
                            if (ioe.getMessage() == null
                                    || !ioe.getMessage().contains("request not processed by peer")) {
                                System.err.println("unexpected exception message: " + ioe.getMessage()
                                        + ", for request " + REQ_URI_BASE + reqQueryPart);
                                // propagate the original failure
                                throw ioe;
                            }
                            numFailed++; // failed due to the right reason
                            System.out.println("received expected failure: " + ioe
                                    + ", for request " + REQ_URI_BASE + reqQueryPart);
                        }
                    }
                    // verify the correct number of requests succeeded/failed
                    assertEquals(maxAllowedReqs, numSuccess, "unexpected number of requests succeeded");
                    assertEquals((numReqs - maxAllowedReqs), numFailed, "unexpected number of requests failed");
                } finally {
                    server.setRequestApprover(null); // reset
                }
            }
        }
    }

    // only allows fixed number of requests, irrespective of which server connection handles
    // it. requests that are rejected will either be sent a GOAWAY on the connection
    // or a RST_FRAME with a REFUSED_STREAM on the stream
    private static final class LimitedRequestApprover {
        private final int maxAllowedReqs;
        private final AtomicInteger numApproved = new AtomicInteger();

        private LimitedRequestApprover(final int maxAllowedReqs) {
            this.maxAllowedReqs = maxAllowedReqs;
        }

        public boolean allowNewRequest(final String serverConnKey) {
            final int approved = numApproved.incrementAndGet();
            return approved <= maxAllowedReqs;
        }
    }

    // allows a certain number of requests per server connection.
    // requests that are rejected will either be sent a GOAWAY on the connection
    // or a RST_FRAME with a REFUSED_STREAM on the stream
    private static final class LimitedPerConnRequestApprover {
        private static final int MAX_REQS_PER_CONN = 6;
        private final Map<String, AtomicInteger> numApproved =
                new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> numDisapproved =
                new ConcurrentHashMap<>();

        public boolean allowNewRequest(final String serverConnKey) {
            final AtomicInteger approved = numApproved.computeIfAbsent(serverConnKey,
                    (k) -> new AtomicInteger());
            int curr = approved.get();
            while (curr < MAX_REQS_PER_CONN) {
                if (approved.compareAndSet(curr, curr + 1)) {
                    return true; // new request allowed
                }
                curr = approved.get();
            }
            final AtomicInteger disapproved = numDisapproved.computeIfAbsent(serverConnKey,
                    (k) -> new AtomicInteger());
            final int numUnprocessed = disapproved.incrementAndGet();
            System.out.println(approved.get() + " processed, "
                    + numUnprocessed + " unprocessed requests on connection " + serverConnKey);
            return false;
        }
    }

    private static final class Handler implements HttpTestHandler {

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            final String connectionKey = exchange.getConnectionKey();
            System.out.println("responding to request: " + exchange.getRequestURI()
                    + " on connection " + connectionKey);
            final byte[] response = connectionKey.getBytes(UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
