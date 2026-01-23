/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.internal.net.http.AltServicesRegistry;
import jdk.internal.net.http.HttpClientAccess;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.AltSvcFrame;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static jdk.internal.net.http.AltServicesRegistry.AltService;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @summary This test verifies alt-svc registry updation for frames
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build java.net.http/jdk.internal.net.http.HttpClientAccess
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @modules java.net.http/jdk.internal.net.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.base/jdk.internal.util
 *          java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.packets
 *          java.net.http/jdk.internal.net.http.quic.frames
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.logging
 *          java.base/sun.net.www.http
 *          java.base/sun.net.www
 *          java.base/sun.net
 * @run testng/othervm
 *                    -Dtest.requiresHost=true
 *                   -Djdk.httpclient.HttpClient.log=headers
 *                   -Djdk.internal.httpclient.disableHostnameVerification
 *                   -Djdk.internal.httpclient.debug=true
 *                    AltSvcFrameTest
 */


public class AltSvcFrameTest {

    private static final String IGNORED_HOST = "www.should-be-ignored.com";
    private static final String ALT_SVC_TO_IGNORE = "h3=\"" + IGNORED_HOST + ":443\"";

    private static final String ACCEPTED_HOST = "www.example.com";
    private static final String ALT_SVC_TO_ACCEPT = "h3=\"" + ACCEPTED_HOST + ":443\"";

    private static final String STREAM_0_ACCEPTED_HOST = "jdk.java.net";
    private static final String STREAM_0_ALT_SVC_TO_ACCEPT = "h3=\"" + STREAM_0_ACCEPTED_HOST + ":443\"";

    private static final String FOO_BAR_ORIGIN = "https://www.foo-bar.hello-world:443";

    static Http2TestServer https2Server;
    static String https2URI;
    static HttpClient client;
    private static final SSLContext server = SimpleSSLContext.findSSLContext();

    @BeforeTest
    public void setUp() throws Exception {
        getRegistry();
        https2Server = new Http2TestServer("localhost", true, server);
        https2Server.addHandler(new AltSvcFrameTestHandler(), "/");
        https2Server.setExchangeSupplier(AltSvcFrameTest.CFTHttp2TestExchange::new);
        https2Server.start();
        https2URI = "https://" + https2Server.serverAuthority() + "/";


    }

    static AltServicesRegistry getRegistry() {
        client = HttpClient.newBuilder()
                .sslContext(server)
                .version(HttpClient.Version.HTTP_2)
                .build();
        return HttpClientAccess.getRegistry(client);
    }

    /*
     * Verify handling of alt-svc frame on a stream other than stream 0
     */
    @Test
    public void testNonStream0AltSvcFrame() throws URISyntaxException, IOException, InterruptedException {
        AltServicesRegistry registry = getRegistry();
        HttpRequest request = HttpRequest.newBuilder(new URI(https2URI))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, ofString());
        assertEquals(response.statusCode(), 200, "unexpected response code");
        final List<AltService> services = registry.lookup(URI.create(https2URI), "h3").toList();
        System.out.println("Alt services in registry for  " + https2URI + " = " + services);
        final boolean hasExpectedAltSvc = services.stream().anyMatch(
                alt -> alt.alpn().equals("h3")
                        && alt.host().contains(ACCEPTED_HOST));
        assertTrue(hasExpectedAltSvc, "missing entry in alt service registry for origin: " + https2URI);
    }

    /*
     * Verify handling of alt-svc frame on stream 0 of the connection
     */
    @Test
    public void testStream0AltSvcFrame() throws URISyntaxException, IOException, InterruptedException {
        AltServicesRegistry registry = getRegistry();
        HttpRequest request = HttpRequest.newBuilder(new URI(https2URI + "?altsvc-on-stream-0"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, ofString());
        assertEquals(response.statusCode(), 200, "unexpected response code");
        final List<AltService> services = registry.lookup(
                URI.create(FOO_BAR_ORIGIN), "h3").toList();
        System.out.println("Alt services in registry for  " + FOO_BAR_ORIGIN + " = " + services);
        final boolean containsIgnoredHost = services.stream().anyMatch(
                alt -> alt.alpn().equals("h3")
                        && alt.host().contains(IGNORED_HOST));
        assertFalse(containsIgnoredHost, "unexpected alt service in the registry for origin: "
                + FOO_BAR_ORIGIN);

        final List<AltService> svcs = registry.lookup(URI.create(https2URI), "h3").toList();
        System.out.println("Alt services in registry for  " + https2URI + " = " + svcs);
        final boolean hasExpectedAltSvc = svcs.stream().anyMatch(
                alt -> alt.alpn().equals("h3")
                        && alt.host().contains(STREAM_0_ACCEPTED_HOST));
        assertTrue(hasExpectedAltSvc, "missing entry in alt service registry for origin: " + https2URI);
    }

    static class AltSvcFrameTestHandler implements Http2Handler {

        @Override
        public void handle(Http2TestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }

    // A custom Http2TestExchangeImpl that overrides sendResponseHeaders to
    // allow headers to be sent with AltSvcFrame.
    static class CFTHttp2TestExchange extends Http2TestExchangeImpl {

        CFTHttp2TestExchange(int streamid, String method, HttpHeaders reqheaders,
                             HttpHeadersBuilder rspheadersBuilder, URI uri, InputStream is,
                             SSLSession sslSession, BodyOutputStream os,
                             Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheadersBuilder, uri, is, sslSession,
                    os, conn, pushAllowed);

        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            final String reqQuery = getRequestURI().getQuery();
            if (reqQuery != null && reqQuery.contains("altsvc-on-stream-0")) {
                final InetSocketAddress addr = this.getLocalAddress();
                final String connectionOrigin = "https://" + addr.getAddress().getHostAddress()
                        + ":" + addr.getPort();
                // send one alt-svc on stream 0 with the same Origin as the connection's Origin
                enqueueAltSvcFrame(new AltSvcFrame(0, 0,
                        // the Origin for which the alt-svc is being advertised
                        Optional.of(connectionOrigin),
                        STREAM_0_ALT_SVC_TO_ACCEPT));

                // send one alt-svc on stream 0 with a different Origin than the connection's Origin
                enqueueAltSvcFrame(new AltSvcFrame(0, 0,
                        // the Origin for which the alt-svc is being advertised
                        Optional.of(FOO_BAR_ORIGIN),
                        ALT_SVC_TO_IGNORE));
            } else {
                // send alt-svc on non-zero stream id.
                // for non-zero stream id, as per spec, the origin is inferred from the stream's origin
                // by the HTTP client
                enqueueAltSvcFrame(new AltSvcFrame(streamid, 0, Optional.empty(), ALT_SVC_TO_ACCEPT));
            }
            super.sendResponseHeaders(rCode, responseLength);
            System.out.println("Sent response headers " + rCode);
        }

        private void enqueueAltSvcFrame(final AltSvcFrame frame) throws IOException {
            System.out.println("enqueueing Alt-Svc frame: " + frame);
            conn.addToOutputQ(frame);
        }
    }
}
