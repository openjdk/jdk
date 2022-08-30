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
 * @run testng/othervm IdleConnectionTimeoutTest
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;

public class IdleConnectionTimeoutTest {

    Http2TestServer http2TestServer;
    URI timeoutUri;
    URI noTimeoutUri;
    final String TIMEOUT_PATH = "/serverTimeoutHandler";
    final String NO_TIMEOUT_PATH = "/noServerTimeoutHandler";
    static boolean expectTimeout;
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
       was made to carry out the second request by the client. Otherwise, the old connection was reused.
    */
    @Test
    public void testTimeoutFires() throws InterruptedException {
        expectTimeout = true;
        System.setProperty("jdk.httpclient.http2IdleConnectionTimeout", "100");
        HttpClient hc = HttpClient.newBuilder().version(HTTP_2).build();
        HttpRequest hreq = HttpRequest.newBuilder(timeoutUri).version(HTTP_2).GET().build();

        CompletableFuture<HttpResponse<String>> request = hc.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8));
        HttpResponse<String> hresp = request.join();
        assertEquals(hresp.statusCode(), 200);
        // Sleep for 4x the timeout value to ensure that it occurs
        Thread.sleep(800);

        request = hc.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8));
        hresp = request.join();
        assertEquals(hresp.statusCode(), 200);
    }

    /*
        The opposite of testTimeoutFires(), if the same connection is used for both requests then the
        idleConnectionTimeoutEvent did not occur.
     */
    @Test
    public void testTimeoutDoesNotFire() throws InterruptedException {
        expectTimeout = false;
        System.setProperty("jdk.httpclient.idleConnectionTimeout", "800");
        HttpClient hc = HttpClient.newBuilder().version(HTTP_2).build();
        HttpRequest hreq = HttpRequest.newBuilder(noTimeoutUri).version(HTTP_2).GET().build();

        CompletableFuture<HttpResponse<String>> request = hc.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8));
        HttpResponse<String> hresp = request.join();
        assertEquals(hresp.statusCode(), 200);
        // Sleep for 1/8th of the timeout value to ensure it does not occur
        Thread.sleep(100);

        request = hc.sendAsync(hreq, HttpResponse.BodyHandlers.ofString(UTF_8));
        hresp = request.join();
        assertEquals(hresp.statusCode(), 200);
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
                testLog.println("ServerTimeoutHandler: Same Connection was used, idleConnectionTimeoutEvent did not fire."
                        + " First remote: " + oldRemote + ", Second Remote: " + newRemote);
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(400, 0);
            }
        }
    }
}
