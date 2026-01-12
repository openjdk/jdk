/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2Handler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;

/*
 * @test
 * @bug 8288717
 * @summary Tests that when the idle connection timeout is configured for a HTTP connection,
 *          then the connection is closed if it has been idle for that long
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.httpclient.test.lib.http3.Http3TestServer
 *
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout=1
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout=20
 *                                                             IdleConnectionTimeoutTest
 *
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h2=1
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h2=20
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h2=abc
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h2=-1
 *                                                             IdleConnectionTimeoutTest
 *
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h3=1
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h3=20
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h3=abc
 *                                                             IdleConnectionTimeoutTest
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=all -Djdk.httpclient.keepalive.timeout.h3=-1
 *                                                             IdleConnectionTimeoutTest
 */
public class IdleConnectionTimeoutTest {

    URI timeoutUriH2, noTimeoutUriH2, timeoutUriH3, noTimeoutUriH3, getH3;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static volatile QuicServerConnection latestServerConn;
    final String KEEP_ALIVE_PROPERTY = "jdk.httpclient.keepalive.timeout";
    final String IDLE_CONN_PROPERTY_H2 = "jdk.httpclient.keepalive.timeout.h2";
    final String IDLE_CONN_PROPERTY_H3 = "jdk.httpclient.keepalive.timeout.h3";
    final String TIMEOUT_PATH = "/serverTimeoutHandler";
    final String NO_TIMEOUT_PATH = "/noServerTimeoutHandler";
    static Http2TestServer http2TestServer;
    static Http3TestServer http3TestServer;
    static final PrintStream testLog = System.err;

    @BeforeTest
    public void setup() throws Exception {
        http2TestServer = new Http2TestServer(false, 0);
        http2TestServer.addHandler(new ServerTimeoutHandlerH2(), TIMEOUT_PATH);
        http2TestServer.addHandler(new ServerNoTimeoutHandlerH2(), NO_TIMEOUT_PATH);
        http2TestServer.setExchangeSupplier(TestExchange::new);

        http3TestServer = new Http3TestServer(sslContext) {
            @Override
            public boolean acceptIncoming(SocketAddress source, QuicServerConnection quicConn) {
                final boolean accepted = super.acceptIncoming(source, quicConn);
                if (accepted) {
                    // Quic Connection maps to Http3Connection, can use this to verify h3 timeouts
                    latestServerConn = quicConn;
                }
                return accepted;
            }
        };
        http3TestServer.addHandler(TIMEOUT_PATH, new ServerTimeoutHandlerH3());
        http3TestServer.addHandler(NO_TIMEOUT_PATH, new ServerNoTimeoutHandlerH3());

        http2TestServer.start();
        http3TestServer.start();
        int port = http2TestServer.getAddress().getPort();
        timeoutUriH2 = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path(TIMEOUT_PATH)
                .build();
        noTimeoutUriH2 = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path(NO_TIMEOUT_PATH)
                .build();

        port = http3TestServer.getAddress().getPort();
        getH3 = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(port)
                .path("/get")
                .build();
        timeoutUriH3 = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(port)
                .path(TIMEOUT_PATH)
                .build();
        noTimeoutUriH3 = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(port)
                .path(NO_TIMEOUT_PATH)
                .build();
    }

    @Test
    public void testRoot() {
        String keepAliveVal = System.getProperty(KEEP_ALIVE_PROPERTY);
        String idleConnectionH2Val = System.getProperty(IDLE_CONN_PROPERTY_H2);
        String idleConnectionH3Val = System.getProperty(IDLE_CONN_PROPERTY_H3);

        if (keepAliveVal != null) {
            try (HttpClient hc = HttpClient.newBuilder().version(HTTP_2).build()) {
                // test H2 inherits value
                testLog.println("Testing HTTP/2 connections set idleConnectionTimeout value to keep alive value");
                test(hc, keepAliveVal, HTTP_2, timeoutUriH2, noTimeoutUriH2);
            }
            try (HttpClient hc = HttpServerAdapters.createClientBuilderForH3().sslContext(sslContext).build()) {
                // test H3 inherits value
                testLog.println("Testing HTTP/3 connections set idleConnectionTimeout value to keep alive value");
                test(hc, keepAliveVal, HTTP_3, timeoutUriH3, noTimeoutUriH3);
            }
        } else if (idleConnectionH2Val != null) {
            try (HttpClient hc = HttpClient.newBuilder().version(HTTP_2).build()) {
                testLog.println("Testing HTTP/2 idleConnectionTimeout");
                test(hc, idleConnectionH2Val, HTTP_2, timeoutUriH2, noTimeoutUriH2);
            }
        } else if (idleConnectionH3Val != null) {
            try (HttpClient hc = HttpServerAdapters.createClientBuilderForH3().sslContext(sslContext).build()) {
                testLog.println("Testing HTTP/3 idleConnectionTimeout");
                test(hc, idleConnectionH3Val, HTTP_3, timeoutUriH3, noTimeoutUriH3);
            }
        }

    }

    private void test(HttpClient hc, String propVal, Version version, URI timeoutUri, URI noTimeoutUri) {
        if (propVal.equals("1")) {
            testTimeout(hc, timeoutUri, version);
        } else if (propVal.equals("20")) {
            testNoTimeout(hc, noTimeoutUri, version);
        } else if (propVal.equals("abc") || propVal.equals("-1")) {
            testNoTimeout(hc, noTimeoutUri, version);
        } else {
            throw new RuntimeException("Unexpected timeout value");
        }
    }

    private void testTimeout(HttpClient hc, URI uri, Version version) {
        // Timeout should occur
        var config = version == HTTP_3 ? HTTP_3_URI_ONLY : null;
        HttpRequest hreq = HttpRequest.newBuilder(uri).version(version).GET()
                .setOption(H3_DISCOVERY, config).build();
        HttpResponse<String> hresp = runRequest(hc, hreq, 2750);
        assertEquals(hresp.statusCode(), 200, "idleConnectionTimeoutEvent was not expected but occurred");
    }

    private void testNoTimeout(HttpClient hc, URI uri, Version version) {
        // Timeout should not occur
        var config = version == HTTP_3 ? HTTP_3_URI_ONLY : null;
        HttpRequest hreq = HttpRequest.newBuilder(uri).version(version).GET()
                .setOption(H3_DISCOVERY,  config).build();
        HttpResponse<String> hresp = runRequest(hc, hreq, 0);
        assertEquals(hresp.statusCode(), 200, "idleConnectionTimeoutEvent was not expected but occurred");
    }

    private HttpResponse<String> runRequest(HttpClient hc, HttpRequest req, int sleepTime) {
        CompletableFuture<HttpResponse<String>> request = hc.sendAsync(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        HttpResponse<String> hresp = request.join();
        assertEquals(hresp.statusCode(), 200);
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        request = hc.sendAsync(req, HttpResponse.BodyHandlers.ofString(UTF_8));
        return request.join();
    }

    static class ServerTimeoutHandlerH2 implements Http2Handler {

        volatile Object firstConnection = null;

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (exchange instanceof TestExchange exch) {
                if (firstConnection == null) {
                    firstConnection = exch.getServerConnection();
                    exch.sendResponseHeaders(200, 0);
                } else {
                    var secondConnection = exch.getServerConnection();

                    if (firstConnection != secondConnection) {
                        testLog.println("ServerTimeoutHandlerH2: New Connection was used, idleConnectionTimeoutEvent fired."
                                + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                        exch.sendResponseHeaders(200, 0);
                    } else {
                        testLog.println("ServerTimeoutHandlerH2: Same Connection was used, idleConnectionTimeoutEvent did not fire."
                                + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                        exch.sendResponseHeaders(400, 0);
                    }
                }
            }
        }
    }

    static class ServerNoTimeoutHandlerH2 implements Http2Handler {

        volatile Object firstConnection;

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (exchange instanceof TestExchange exch) {
                if (firstConnection == null) {
                    firstConnection = exch.getServerConnection();
                    exch.sendResponseHeaders(200, 0);
                } else {
                    var secondConnection = exch.getServerConnection();

                    if (firstConnection == secondConnection) {
                        testLog.println("ServerTimeoutHandlerH2: Same Connection was used, idleConnectionTimeoutEvent did not fire."
                                + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                        exch.sendResponseHeaders(200, 0);
                    } else {
                        testLog.println("ServerTimeoutHandlerH2: Different Connection was used, idleConnectionTimeoutEvent fired."
                                + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                        exch.sendResponseHeaders(400, 0);
                    }
                }
            }
        }
    }

    static class ServerTimeoutHandlerH3 implements Http2Handler {

        volatile Object firstConnection;

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
                if (firstConnection == null) {
                    firstConnection = latestServerConn;
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    var secondConnection = latestServerConn;
                    if (firstConnection != secondConnection) {
                        testLog.println("ServerTimeoutHandlerH3: New Connection was used, idleConnectionTimeoutEvent fired."
                                + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                        exchange.sendResponseHeaders(200, 0);
                    } else {
                        testLog.println("ServerTimeoutHandlerH3: Same Connection was used, idleConnectionTimeoutEvent did not fire."
                                + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                        exchange.sendResponseHeaders(400, 0);
                    }
                }
                exchange.close();
        }
    }

    static class ServerNoTimeoutHandlerH3 implements Http2Handler {

        volatile Object firstConnection = null;

        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            if (firstConnection == null) {
                firstConnection = latestServerConn;
                exchange.sendResponseHeaders(200, 0);
            } else {
                var secondConnection = latestServerConn;

                if (firstConnection == secondConnection) {
                    testLog.println("ServerTimeoutHandlerH3: Same Connection was used, idleConnectionTimeoutEvent did not fire."
                            + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    testLog.println("ServerTimeoutHandlerH3: New Connection was used, idleConnectionTimeoutEvent fired."
                            + " First Connection: " + firstConnection + ", Second Connection Hash: " + secondConnection);
                    exchange.sendResponseHeaders(400, 0);
                }
            }
            exchange.close();
        }
    }

    static class TestExchange extends Http2TestExchangeImpl {

        public TestExchange(int streamid, String method,
                            HttpHeaders reqheaders, HttpHeadersBuilder rspheadersBuilder,
                            URI uri, InputStream is, SSLSession sslSession, BodyOutputStream os,
                            Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession, os,
                    conn, pushAllowed);
        }

        public Http2TestServerConnection getServerConnection() {
            return this.conn;
        }
    }
}
