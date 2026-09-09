/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.internal.net.http.frame.ErrorFrame;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8335181 8371903
 * @summary verify that the HttpClient correctly handles incoming GOAWAY frames and
 *          retries any unprocessed requests on a new connection
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestExchangeImpl
 *        jdk.httpclient.test.lib.http2.Http2TestServerConnection
 * @run junit ${test.main.class}
 */
public class H2GoAwayTest {
    private static final String CONN_CLOSING_HANDLER_REQ_PATH = "/closeConn";
    private static final String TEST_REQ_PATH = "/test";
    private static HttpTestServer server;
    private static final SSLContext sslCtx = SimpleSSLContext.findSSLContext();

    @BeforeAll
    static void beforeAll() throws Exception {
        server = HttpTestServer.create(HTTP_2, sslCtx);
        server.addHandler(new Handler(), TEST_REQ_PATH);
        server.addHandler(new ProcessingCapacityExceededHandler(), CONN_CLOSING_HANDLER_REQ_PATH);
        server.start();
        System.out.println("Server started at " + server.getAddress());
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            System.out.println("Stopping server at " + server.getAddress());
            server.stop();
        }
    }


    /**
     * Issues a HTTP/2 request against a server which is expected to send a GOAWAY and close the
     * connection, without responding to the request. The test then verifies that the request fails
     * with an IOException and the exception message contains the error code that was contained
     * in the GOAWAY frame.
     */
    @Test
    public void testGoAwayErrorCode() throws Exception {
        final URI reqURI = URIBuilder.newBuilder().scheme("https")
                .loopback()
                .port(server.getAddress().getPort())
                .path(CONN_CLOSING_HANDLER_REQ_PATH)
                .build();
        final HttpRequest req = HttpRequest.newBuilder().uri(reqURI).version(HTTP_2).build();
        boolean receivedExpectedFailure = false;
        try (final HttpClient client = HttpClient.newBuilder().version(HTTP_2)
                .sslContext(sslCtx).build()) {
            final String expectedExMsg =
                    ErrorFrame.stringForCode(ProcessingCapacityExceededHandler.GOAWAY_ERROR_CODE);
            final int numReqs = 10;
            // it can't be guaranteed that the HttpClient will have finished processing the
            // incoming GOAWAY before the connection gets closed and request fails. so we issue
            // the request a reasonable number of times and expect that at least one of the
            // failing request manages to have its failure exception attributed to the error
            // code from the incoming GOAWAY frame.
            for (int i = 1; i <= 10; i++) {
                System.err.println("iteration - " + i + " issuing request " + req);
                final IOException ioe = assertThrows(IOException.class,
                        () -> client.send(req, BodyHandlers.discarding()));
                final String actual = ioe.getMessage();
                if (actual != null && actual.contains(expectedExMsg)) {
                    receivedExpectedFailure = true;
                    System.err.println("iteration - " + i + " got the expected exception: " + ioe);
                    break; // no need to issue any more requests
                }
                // print the (unexpected) exception for debugging
                System.err.println("iteration " + i + " - ignoring the following IOException" +
                        " that had an unexpected exception message");
                ioe.printStackTrace();
            }
            assertTrue(receivedExpectedFailure, "did not receive the expected exception message: \""
                    + expectedExMsg + "\" in any of the " + numReqs + " request failures");
        }
    }

    /**
     * Verifies that when several requests are sent using send() and the server
     * connection is configured to send a GOAWAY after processing only a few requests, then
     * the remaining requests are retried on a different connection
     */
    @Test
    public void testSequential() throws Exception {
        final String reqURIBase = URIBuilder.newBuilder().scheme("https")
                .loopback()
                .port(server.getAddress().getPort())
                .path(TEST_REQ_PATH)
                .build().toString();
        final LimitedPerConnRequestApprover reqApprover = new LimitedPerConnRequestApprover();
        server.setRequestApprover(reqApprover::allowNewRequest);
        try (final HttpClient client = HttpClient.newBuilder().version(HTTP_2)
                .sslContext(sslCtx).build()) {
            final String[] reqMethods = {"HEAD", "GET", "POST"};
            for (final String reqMethod : reqMethods) {
                final int numReqs = LimitedPerConnRequestApprover.MAX_REQS_PER_CONN + 3;
                final Set<String> connectionKeys = new LinkedHashSet<>();
                for (int i = 1; i <= numReqs; i++) {
                    final URI reqURI = new URI(reqURIBase + "?seq&" + reqMethod + "=" + i);
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
        final String reqURIBase = URIBuilder.newBuilder().scheme("https")
                .loopback()
                .port(server.getAddress().getPort())
                .path(TEST_REQ_PATH)
                .build().toString();
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
                        final URI reqURI = new URI(reqURIBase + reqQueryPart);
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
        final String reqURIBase = URIBuilder.newBuilder().scheme("https")
                .loopback()
                .port(server.getAddress().getPort())
                .path(TEST_REQ_PATH)
                .build().toString();
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
                        final URI reqURI = new URI(reqURIBase + "?async&" + reqMethod + "=" + i);
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
                                    + reqURIBase + reqQueryPart);
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
                                        + ", for request " + reqURIBase + reqQueryPart);
                                throw cause;
                            }
                            // verify it failed for the right reason
                            if (ioe.getMessage() == null
                                    || !ioe.getMessage().contains("request not processed by peer")) {
                                System.err.println("unexpected exception message: " + ioe.getMessage()
                                        + ", for request " + reqURIBase + reqQueryPart);
                                // propagate the original failure
                                throw ioe;
                            }
                            numFailed++; // failed due to the right reason
                            System.out.println("received expected failure: " + ioe
                                    + ", for request " + reqURIBase + reqQueryPart);
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

    /**
     * A handler which always responds with a GOAWAY frame and closes the connection, without
     * sending any HTTP response for the request.
     */
    private static final class ProcessingCapacityExceededHandler implements HttpTestHandler {

        private static final int GOAWAY_ERROR_CODE = ErrorFrame.ENHANCE_YOUR_CALM;

        @Override
        public void handle(final HttpTestExchange exchg) throws IOException {
            System.err.println("handling request " + exchg.getRequestURI());
            final Http2TestExchangeImpl exchgImpl =
                    exchg.getUnderlyingExchange(Http2TestExchangeImpl.class);
            final Http2TestServerConnection conn = exchgImpl.getConnection();
            System.err.println("handler closing connection " + conn
                    + " with error: " + ErrorFrame.stringForCode(GOAWAY_ERROR_CODE));
            // close will send a GOAWAY with the given error code
            conn.close(GOAWAY_ERROR_CODE);
        }
    }
}
