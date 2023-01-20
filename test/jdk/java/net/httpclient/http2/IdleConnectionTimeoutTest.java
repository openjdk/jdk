/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8288717
 * @summary Tests that when the idleConnectionTimeoutEvent is configured in HTTP/2,
 *          an HTTP/2 connection will close within the specified interval if there
 *          are no active streams on the connection.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors -Djdk.httpclient.keepalive.timeout=1
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors -Djdk.httpclient.keepalive.timeout=2
 *                                                             IdleConnectionTimeoutTest
 *
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors -Djdk.httpclient.keepalive.timeout.h2=1
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors -Djdk.httpclient.keepalive.timeout.h2=2
 *                                                             IdleConnectionTimeoutTest
 *
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors -Djdk.httpclient.keepalive.timeout.h2=1
 *                                                            -Djdk.httpclient.keepalive.timeout=2
 *                                                             IdleConnectionTimeoutTest
 *
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors -Djdk.httpclient.keepalive.timeout.h2=-1
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors,trace -Djdk.httpclient.keepalive.timeout.h2=abc
 *                                                                   IdleConnectionTimeoutTest
 */

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2Handler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;

public class IdleConnectionTimeoutTest {

    Http2TestServer http2TestServer;
    URI timeoutUri;
    URI noTimeoutUri;
    final String IDLE_CONN_PROPERTY = "jdk.httpclient.keepalive.timeout.h2";
    final String KEEP_ALIVE_PROPERTY = "jdk.httpclient.keepalive.timeout";
    final String TIMEOUT_PATH = "/serverTimeoutHandler";
    final String NO_TIMEOUT_PATH = "/noServerTimeoutHandler";
    static final PrintStream testLog = System.err;

    @BeforeTest
    public void setup() throws Exception {
        http2TestServer = new Http2TestServer(false, 0);
        http2TestServer.addHandler(new ServerTimeoutHandler(), TIMEOUT_PATH);
        http2TestServer.addHandler(new ServerNoTimeoutHandler(), NO_TIMEOUT_PATH);

        http2TestServer.start();
        int port = http2TestServer.getAddress().getPort();
        timeoutUri = new URI("http://localhost:" + port + TIMEOUT_PATH);
        noTimeoutUri = new URI("http://localhost:" + port + NO_TIMEOUT_PATH);
    }

    /*
       If the InetSocketAddress of the first remote connection is not equal to the address of the
       second remote connection, then the idleConnectionTimeoutEvent has occurred and a new connection
       was made to carry out the second request by the client.
    */
    @Test
    public void test() throws InterruptedException {
        String timeoutVal = System.getProperty(IDLE_CONN_PROPERTY);
        String keepAliveVal = System.getProperty(KEEP_ALIVE_PROPERTY);
        testLog.println("Test run for " + IDLE_CONN_PROPERTY + "=" + timeoutVal);

        int sleepTime = 0;
        HttpClient hc = HttpClient.newBuilder().version(HTTP_2).build();
        HttpRequest hreq;
        HttpResponse<String> hresp;
        if (timeoutVal != null) {
            if (keepAliveVal != null) {
                // In this case, specified h2 timeout should override keep alive timeout.
                // Timeout should occur
                hreq = HttpRequest.newBuilder(timeoutUri).version(HTTP_2).GET().build();
                sleepTime = 2000;
                hresp = runRequest(hc, hreq, sleepTime);
                assertEquals(hresp.statusCode(), 200, "idleConnectionTimeoutEvent was expected but did not occur");
            } else if (timeoutVal.equals("1")) {
                // Timeout should occur
                hreq = HttpRequest.newBuilder(timeoutUri).version(HTTP_2).GET().build();
                sleepTime = 2000;
                hresp = runRequest(hc, hreq, sleepTime);
                assertEquals(hresp.statusCode(), 200, "idleConnectionTimeoutEvent was expected but did not occur");
            } else if (timeoutVal.equals("2")) {
                // Timeout should not occur
                hreq = HttpRequest.newBuilder(noTimeoutUri).version(HTTP_2).GET().build();
                sleepTime = 1000;
                hresp = runRequest(hc, hreq, sleepTime);
                assertEquals(hresp.statusCode(), 200, "idleConnectionTimeoutEvent was not expected but occurred");
            } else if (timeoutVal.equals("abc") || timeoutVal.equals("-1")) {
                // Timeout should not occur
                hreq = HttpRequest.newBuilder(noTimeoutUri).version(HTTP_2).GET().build();
                hresp = runRequest(hc, hreq, sleepTime);
                assertEquals(hresp.statusCode(), 200, "idleConnectionTimeoutEvent was not expected but occurred");
            }
        } else {
            // When no value is specified then no timeout should occur (default keep alive value of 600 used)
            hreq = HttpRequest.newBuilder(noTimeoutUri).version(HTTP_2).GET().build();
            hresp = runRequest(hc, hreq, sleepTime);
            assertEquals(hresp.statusCode(), 200, "idleConnectionTimeoutEvent should not occur, no value was specified for this property");
        }
    }

    private HttpResponse<String> runRequest(HttpClient hc, HttpRequest req, int sleepTime) throws InterruptedException {
        CompletableFuture<HttpResponse<String>> request = hc.sendAsync(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        HttpResponse<String> hresp = request.join();
        assertEquals(hresp.statusCode(), 200);

        Thread.sleep(sleepTime);
        request = hc.sendAsync(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        return request.join();
    }

    static class ServerTimeoutHandler implements Http2Handler {

        InetSocketAddress remote;

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (remote == null) {
                remote = exchange.getRemoteAddress();
                exchange.sendResponseHeaders(200, 0);
            } else if (!remote.equals(exchange.getRemoteAddress())) {
                testLog.println("ServerTimeoutHandler: New Connection was used, idleConnectionTimeoutEvent fired."
                        + " First remote: " + remote + ", Second Remote: " + exchange.getRemoteAddress());
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(400, 0);
            }
        }
    }

    static class ServerNoTimeoutHandler implements Http2Handler {

        InetSocketAddress oldRemote;

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            InetSocketAddress newRemote = exchange.getRemoteAddress();
            if (oldRemote == null) {
                oldRemote = newRemote;
                exchange.sendResponseHeaders(200, 0);
            } else if (oldRemote.equals(newRemote)) {
                testLog.println("ServerNoTimeoutHandler: Same Connection was used, idleConnectionTimeoutEvent did not fire."
                        + " First remote: " + oldRemote + ", Second Remote: " + newRemote);
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(400, 0);
            }
        }
    }
}
