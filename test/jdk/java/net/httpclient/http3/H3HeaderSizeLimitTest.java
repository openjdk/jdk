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

import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.internal.net.http.Http3ConnectionAccess;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.test.lib.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

/*
 * @test
 * @summary Verifies that the HTTP client respects the SETTINGS_MAX_FIELD_SECTION_SIZE setting on HTTP3 connection
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @library ../access
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @build java.net.http/jdk.internal.net.http.Http3ConnectionAccess
 * @run testng/othervm
 *              -Djdk.internal.httpclient.debug=true
 *              -Djdk.httpclient.HttpClient.log=requests,responses,errors H3HeaderSizeLimitTest
 */
public class H3HeaderSizeLimitTest implements HttpServerAdapters {

    private static final long HEADER_SIZE_LIMIT_BYTES = 1024;
    private SSLContext sslContext;
    private HttpTestServer h3Server;
    private String requestURIBase;

    @BeforeClass
    public void beforeClass() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        final QuicServer quicServer = Http3TestServer.quicServerBuilder()
                .sslContext(sslContext)
                .build();
        h3Server = HttpTestServer.of(new Http3TestServer(quicServer)
                .setConnectionSettings(new ConnectionSettings(HEADER_SIZE_LIMIT_BYTES, 0, 0)));
        h3Server.addHandler((exchange) -> exchange.sendResponseHeaders(200, 0), "/hello");
        h3Server.start();
        System.out.println("Server started at " + h3Server.getAddress());
        requestURIBase = URIBuilder.newBuilder().scheme("https").loopback()
                .port(h3Server.getAddress().getPort()).build().toString();
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (h3Server != null) {
            System.out.println("Stopping server " + h3Server.getAddress());
            h3Server.stop();
        }
    }

    /**
     * Issues a HTTP3 request with combined request headers size exceeding the limit set by the
     * test server. Verifies that such requests fail.
     */
    @Test
    public void testLargeHeaderSize() throws Exception {
        final HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(Version.HTTP_3)
                // the server drops 1 packet out of two!
                .connectTimeout(Duration.ofSeconds(Utils.adjustTimeout(10)))
                .sslContext(sslContext).build();
        final URI reqURI = new URI(requestURIBase + "/hello");
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                .version(Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        // issue a few requests so that enough time has passed to allow the SETTINGS frame from the
        // server to have reached the client.
        for (int i = 1; i <= 3; i++) {
            System.out.println("Issuing warmup request " + i + " to " + reqURI);
            final HttpResponse<Void> response = client.send(
                    reqBuilder.build(),
                    BodyHandlers.discarding());
            Assert.assertEquals(response.statusCode(), 200, "Unexpected status code");
            if (i == 3) {
                var cf = Http3ConnectionAccess.peerSettings(client, response);
                if (!cf.isDone()) {
                    System.out.println("Waiting for peer settings");
                    cf.join();
                }
                System.out.println("Got peer settings: " + cf.get());
            }
        }
        // at this point the client should have processed the SETTINGS frame the server
        // and we expect it to start honouring those settings. We start the real testing now
        // create headers that are larger than the headers size limit that has been configured on
        // the server by this test
        final String headerValue = headerValueOfLargeSize();
        for (int i = 0; i < 10; i++) {
            reqBuilder.setHeader("header-" + i, headerValue);
        }
        final HttpRequest request = reqBuilder.build();
        System.out.println("Issuing request to " + reqURI);
        final IOException thrown = Assert.expectThrows(ProtocolException.class,
                () -> client.send(request, BodyHandlers.discarding()));
        if (!thrown.getMessage().equals("Request headers size exceeds limit set by peer")) {
            throw thrown;
        }
        // test same with async
        System.out.println("Issuing async request to " + reqURI);
        final ExecutionException asyncThrown = Assert.expectThrows(ExecutionException.class,
                () -> client.sendAsync(request, BodyHandlers.discarding()).get());
        if (!(asyncThrown.getCause() instanceof ProtocolException)) {
            System.err.println("Received unexpected cause");
            throw asyncThrown;
        }
        if (!asyncThrown.getCause().getMessage().equals("Request headers size exceeds limit set by peer")) {
            System.err.println("Received unexpected message in cause");
            throw asyncThrown;
        }
    }

    private static String headerValueOfLargeSize() {
        return "abcdefgh".repeat(250);
    }
}
