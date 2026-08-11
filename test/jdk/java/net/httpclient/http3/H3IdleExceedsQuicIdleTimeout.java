/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @test
 * @bug 8371802
 * @summary verify that if a higher idle timeout is configured for a HTTP/3 connection
 *          then a lower negotiated QUIC idle timeout doesn't cause QUIC to
 *          idle terminate the connection
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 *
 * @comment this test has some explicit delays to simulate a idle connection, the timeout=180
 *          is merely to provide some leeway to the test and prevent timing out the test on busy
 *          systems
 * @run junit/othervm/timeout=180 -Djdk.httpclient.quic.idleTimeout=30
 *                    -Djdk.httpclient.keepalive.timeout.h3=120
 *                    -Djdk.httpclient.HttpClient.log=quic:hs
 *                    ${test.main.class}
 */
class H3IdleExceedsQuicIdleTimeout {

    private static final String REQ_PATH = "/8371802";

    private static HttpTestServer h3Server;
    private static final SSLContext sslCtx = SimpleSSLContext.findSSLContext();

    @BeforeAll
    static void beforeAll() throws Exception {
        h3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslCtx);
        h3Server.addHandler(new Handler(), REQ_PATH);
        h3Server.start();
        System.err.println("HTTP/3 server started at " + h3Server.getAddress());
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (h3Server != null) {
            System.err.println("stopping server at " + h3Server.getAddress());
            h3Server.stop();
        }
    }

    /*
     * With QUIC idle connection timeout configured to be lower than the HTTP/3 idle timeout,
     * this test issues a HTTP/3 request and expects that request to establish a QUIC connection
     * and receive a successful response. The test then stays idle for a duration larger
     * than the QUIC idle timeout and issues a HTTP/3 request again after that idle period.
     * The test then expects that the second request too is responded by the previously opened
     * connection, thus proving that the QUIC layer did the necessary work to prevent the (idle)
     * connection from terminating.
     */
    @Test
    void testQUICKeepsConnAlive() throws Exception {
        final long quicIdleTimeoutSecs = 30;
        assertEquals(quicIdleTimeoutSecs,
                Integer.parseInt(System.getProperty("jdk.httpclient.quic.idleTimeout")),
                "unexpected QUIC idle timeout");
        assertEquals(120,
                Integer.parseInt(System.getProperty("jdk.httpclient.keepalive.timeout.h3")),
                "unexpected HTTP/3 idle timeout");
        try (final HttpClient client = HttpServerAdapters.createClientBuilderForH3()
                .sslContext(sslCtx)
                .proxy(NO_PROXY)
                .version(HTTP_3)
                .build()) {

            final URI req1URI = URIBuilder.newBuilder()
                    .scheme("https")
                    .host(h3Server.getAddress().getAddress())
                    .port(h3Server.getAddress().getPort())
                    .path(REQ_PATH)
                    .query("i=1")
                    .build();
            System.err.println("issuing request " + req1URI);
            final HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(req1URI)
                    .setOption(HttpOption.H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();
            final HttpResponse<Void> resp1 = client.send(req1, BodyHandlers.discarding());
            assertEquals(200, resp1.statusCode(), "unexpected status code");
            final String resp1ConnLabel = resp1.connectionLabel().orElse(null);
            System.err.println("first request handled by connection: " + resp1ConnLabel);
            assertNotNull(resp1ConnLabel, "missing connection label on response");
            assertEquals(HTTP_3, resp1.version(), "unexpected response version");
            // don't generate any more traffic from the HTTP/3 side for longer than the QUIC
            // idle timeout
            stayIdle(quicIdleTimeoutSecs + 13, TimeUnit.SECONDS);
            // now send the HTTP/3 request and expect the same previous connection to handle
            // respond to this request
            final URI req2URI = URIBuilder.newBuilder()
                    .scheme("https")
                    .host(h3Server.getAddress().getAddress())
                    .port(h3Server.getAddress().getPort())
                    .path(REQ_PATH)
                    .query("i=2")
                    .build();
            System.err.println("issuing request " + req2URI);
            final HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(req2URI)
                    .setOption(HttpOption.H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();
            final HttpResponse<Void> resp2 = client.send(req2, BodyHandlers.discarding());
            assertEquals(200, resp2.statusCode(), "unexpected status code");
            final String resp2ConnLabel = resp2.connectionLabel().orElse(null);
            System.err.println("second request handled by connection: " + resp2ConnLabel);
            assertEquals(resp1ConnLabel, resp2ConnLabel, "second request handled by a different connection");
            assertEquals(HTTP_3, resp2.version(), "unexpected response version");
        }
    }

    private static void stayIdle(final long time, final TimeUnit unit) throws InterruptedException {
        // await on a CountDownLatch which no one counts down. this is merely
        // to avoid using Thread.sleep(...) and other similar constructs and then
        // having to deal with spurious wakeups.
        final boolean countedDown = new CountDownLatch(1).await(time, unit);
        assertFalse(countedDown, "wasn't expected to be counted down");
    }

    private static final class Handler implements HttpTestHandler {
        private static final int NO_RESP_BODY = 0;

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            System.err.println("handling request " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, NO_RESP_BODY);
        }
    }
}
