/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.net.http.HttpClientAccess;
import jdk.internal.net.http.AltServicesRegistry;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.frame.AltSvcFrame;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestExchangeImpl;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.BodyOutputStream;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static jdk.internal.net.http.AltServicesRegistry.AltService;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

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

    static Http2TestServer https2Server;
    static String https2URI;
    static HttpClient client;
    static SSLContext server;

    @BeforeTest
    public void setUp() throws Exception {
        server = SimpleSSLContext.getContext("TLS");
        getRegistry();
        final ExecutorService executor = Executors.newCachedThreadPool();
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
    @Test
    public void testAltSvcFrame() throws URISyntaxException, IOException, InterruptedException {
        AltServicesRegistry registry = getRegistry();
        HttpRequest request = HttpRequest.newBuilder(new URI(https2URI))
                                         .GET()
                                         .build();
        HttpResponse<String> response = client.send(request, ofString());
        Stream<AltService> service = registry.lookup(URI.create(https2URI), "h3");
        assert service.anyMatch( alt -> alt.alpn().equals("h3") && alt.host().contains("www.oracle.com"));
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
            // for non-zero stream id, as per spec, the origin is inferred from the stream's origin
            // by the HTTP client
            AltSvcFrame frame = new AltSvcFrame(streamid, 0, 0, Optional.empty(), "h3=\"www.oracle.com:443\"");
            conn.addToOutputQ(frame);
            super.sendResponseHeaders(rCode, responseLength);
            System.err.println("Sent response headers " + rCode);
        }
    }
}
