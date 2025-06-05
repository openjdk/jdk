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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.internal.net.http.common.Utils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/*
 * @test
 * @bug 8312433
 * @summary verify that the HttpClient's HTTP2 idle connection management doesn't close a connection
 *          when that connection has been handed out from the pool to a caller
 * @library /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *
 * @run junit/othervm   -Djdk.internal.httpclient.debug=true
 *                      -Djdk.httpclient.keepalive.timeout.h2=3
 *                      IdlePooledConnectionTest
 */
public class IdlePooledConnectionTest {

    private static final String ALL_OK_PATH = "/allOK";
    private static HttpTestServer h2Server;
    private static URI allOKUri;
    private static final String H2_KEEPALIVE_TIMEOUT_PROP = "jdk.httpclient.keepalive.timeout.h2";
    private static final String KEEPALIVE_TIMEOUT_PROP = "jdk.httpclient.keepalive.timeout";

    @BeforeAll
    static void beforeAll() throws Exception {
        h2Server = HttpTestServer.create(HTTP_2);
        h2Server.addHandler(new AllOKHandler(), ALL_OK_PATH);
        h2Server.start();
        System.err.println("Started H2 server at " + h2Server.serverAuthority());
        allOKUri = new URI("http://" + h2Server.serverAuthority() + ALL_OK_PATH);
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (h2Server != null) {
            System.err.println("Stopping h2 server: " + h2Server.serverAuthority());
            h2Server.stop();
        }
    }

    // just returns a 200 HTTP response for all requests
    private static final class AllOKHandler implements HttpServerAdapters.HttpTestHandler {

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            System.err.println("Responding with 200 response code for request "
                    + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, 0);
        }
    }

    /*
     * Issues a HTTP2 request against a server and expects it to succeed.
     * The connection that was used is internally pooled by the HttpClient implementation.
     * Then waits for the H2 idle connection timeout, before again firing several concurrent HTTP2
     * requests against the same server. It is expected that all these requests complete
     * successfully without running into a race condition where the H2 idle connection management
     * closes the (pooled) connection during the time connection has been handed out to a caller
     * and a new stream hasn't yet been created.
     */
    @Test
    public void testPooledConnection() throws Exception {
        final Duration h2TimeoutDuration = getEffectiveH2IdleTimeoutDuration();
        assertNotNull(h2TimeoutDuration, "H2 idle connection timeout cannot be null");
        // the wait time, which represents the time to wait before firing off additional requests,
        // is intentionally a few milliseconds smaller than the h2 idle connection timeout,
        // to allow for the requests to reach the place where connection checkout from the pool
        // happens and thus allow the code to race with the idle connection timer task
        // closing the connection.
        final long waitTimeMillis = TimeUnit.of(ChronoUnit.MILLIS).convert(h2TimeoutDuration) - 5;
        try (final HttpClient client = HttpClient.newBuilder().proxy(NO_PROXY).build()) {
            final HttpRequest request = HttpRequest.newBuilder(allOKUri)
                    .GET().version(HTTP_2).build();
            // keep ready the additional concurrent requests that we will fire later.
            // we do this now so that when it's time to fire off these additional requests,
            // this main thread does as little work as possible to increase the chances of a
            // race condition in idle connection management closing a pooled connection
            // and new requests being fired
            final Callable<HttpResponse<Void>> task = () -> client.send(request,
                    BodyHandlers.discarding());
            final List<Callable<HttpResponse<Void>>> tasks = new ArrayList<>();
            final int numAdditionalReqs = 20;
            for (int i = 0; i < numAdditionalReqs; i++) {
                tasks.add(task);
            }
            // issue the first request
            System.err.println("issuing first request: " + request);
            final HttpResponse<Void> firstResp = client.send(request, BodyHandlers.discarding());
            assertEquals(200, firstResp.statusCode(), "unexpected response code for request "
                    + request);
            System.err.println("waiting for " + waitTimeMillis + " milli seconds" +
                    " before issuing additional requests");
            Thread.sleep(waitTimeMillis);
            // issue additional concurrent requests
            final List<Future<HttpResponse<Void>>> responses;
            try (final ExecutorService executor = Executors.newFixedThreadPool(numAdditionalReqs)) {
                responses = executor.invokeAll(tasks);
            }
            System.err.println("All " + responses.size() + " requests completed, now" +
                    " verifying each response");
            // verify all requests succeeded
            for (final Future<HttpResponse<Void>> future : responses) {
                final HttpResponse<Void> rsp = future.get();
                assertEquals(200, rsp.statusCode(), "unexpected response code for request "
                        + request);
            }
        }
    }

    // returns the effective idle timeout duration of a HTTP2 connection
    private static Duration getEffectiveH2IdleTimeoutDuration() {
        final long keepAliveTimeoutInSecs = getNetProp(KEEPALIVE_TIMEOUT_PROP, 30);
        final long h2TimeoutInSecs = getNetProp(H2_KEEPALIVE_TIMEOUT_PROP, keepAliveTimeoutInSecs);
        return Duration.of(h2TimeoutInSecs, ChronoUnit.SECONDS);
    }

    private static long getNetProp(final String prop, final long def) {
        final String s = Utils.getNetProperty(prop);
        if (s == null) {
            return def;
        }
        try {
            final long timeoutVal = Long.parseLong(s);
            return timeoutVal >= 0 ? timeoutVal : def;
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}
