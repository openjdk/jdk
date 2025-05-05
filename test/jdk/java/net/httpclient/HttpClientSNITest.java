/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.common.ServerNameMatcher;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8346705
 * @summary verify the behaviour of java.net.http.HttpClient
 *          when sending a Server Name Indication in the TLS
 *          connections that it establishes for the requests
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.net.URIBuilder
 * @run junit HttpClientSNITest
 */
public class HttpClientSNITest {
    private static final String RESP_BODY_TEXT = "hello world";

    private static final class Handler implements HttpTestHandler {

        @Override
        public void handle(final HttpTestExchange exch) throws IOException {
            System.out.println("handling request " + exch.getRequestURI());
            final byte[] respBody = RESP_BODY_TEXT.getBytes(US_ASCII);
            exch.sendResponseHeaders(200, respBody.length);
            try (final OutputStream os = exch.getResponseBody()) {
                os.write(respBody);
            }
        }
    }

    /*
     * Creates and configures a HTTPS server with a SNIMatcher that
     * expects a specific SNI name to be sent by the connection client.
     * The test uses a HttpClient to issue a couple of requests with the URI having
     * a IP address literal as the host. For one of the request, the HttpClient
     * is configured with specific ServerName(s) through HttpClient.sslParameters()
     * and for the other request, it isn't.
     * The test then verifies that for such requests with a IP address literal as the host,
     * the HttpClient sends across the ServerName(s) if any has been configured on the client.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRequestToIPLiteralHost(final boolean sniConfiguredOnClient) throws Exception {
        final SSLContext sslContext = new SimpleSSLContext().get();
        assertNotNull(sslContext, "could not create a SSLContext");
        final String expectedSNI = "non-dns-resolvable.foo.bar.localhost";
        final ServerNameMatcher matcher = new ServerNameMatcher(expectedSNI);
        final HttpTestServer server = createServer(matcher, sslContext);
        try {
            final HttpClient.Builder builder = HttpClient.newBuilder().sslContext(sslContext);
            if (sniConfiguredOnClient) {
                final SSLParameters clientConfiguredSSLParams = new SSLParameters();
                clientConfiguredSSLParams.setServerNames(List.of(new SNIHostName(expectedSNI)));
                builder.sslParameters(clientConfiguredSSLParams);
            }
            try (final HttpClient client = builder.build()) {
                final String ipLiteral = InetAddress.getLoopbackAddress().getHostAddress();
                final URI reqURI = URIBuilder.newBuilder()
                        .host(ipLiteral)
                        .port(server.getAddress().getPort())
                        .scheme("https")
                        .path("/")
                        .build();
                final HttpRequest req = HttpRequest.newBuilder(reqURI).build();
                System.out.println("issuing request " + reqURI);
                final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString(US_ASCII));
                assertEquals(200, resp.statusCode(), "unexpected response status code");
                assertEquals(RESP_BODY_TEXT, resp.body(), "unexpected response body");
                if (sniConfiguredOnClient) {
                    assertTrue(matcher.wasInvoked(), "SNIMatcher wasn't invoked on the server");
                } else {
                    assertFalse(matcher.wasInvoked(), "SNIMatcher was unexpectedly invoked" +
                            " on the server");
                }
            }
        } finally {
            System.out.println("stopping server " + server.getAddress());
            server.stop();
        }
    }

    /*
     * Creates and configures a HTTPS server with a SNIMatcher that
     * expects a specific SNI name to be sent by the connection client.
     * The test uses a HttpClient to issue a couple of requests with the URI having
     * a hostname (i.e. not a IP address literal) as the host. For one of the request,
     * the HttpClient is configured with specific ServerName(s) through
     * HttpClient.sslParameters() and for the other request, it isn't.
     * The test then verifies that for such requests with a hostname
     * (i.e. not a IP address literal) in the request URI,
     * the HttpClient never sends ServerName(s) that may have been configured on the
     * client and instead it sends the hostname (from the request URI) as the ServerName
     * for each of the request.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRequestResolvedHostName(final boolean sniConfiguredOnClient) throws Exception {
        final SSLContext sslContext = new SimpleSSLContext().get();
        assertNotNull(sslContext, "could not create a SSLContext");
        final String resolvedHostName = InetAddress.getLoopbackAddress().getHostName();
        final String expectedSNI = resolvedHostName;
        final ServerNameMatcher matcher = new ServerNameMatcher(expectedSNI);
        final HttpTestServer server = createServer(matcher, sslContext);
        try {
            final HttpClient.Builder builder = HttpClient.newBuilder().sslContext(sslContext);
            if (sniConfiguredOnClient) {
                final SSLParameters clientConfiguredSSLParams = new SSLParameters();
                clientConfiguredSSLParams.setServerNames(List.of(new SNIHostName("does-not-matter")));
                builder.sslParameters(clientConfiguredSSLParams);
            }
            try (final HttpClient client = builder.build()) {
                final URI reqURI = URIBuilder.newBuilder()
                        .host(resolvedHostName)
                        .port(server.getAddress().getPort())
                        .scheme("https")
                        .path("/")
                        .build();
                final HttpRequest req = HttpRequest.newBuilder(reqURI).build();
                System.out.println("issuing request " + reqURI);
                final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString(US_ASCII));
                assertEquals(200, resp.statusCode(), "unexpected response status code");
                assertEquals(RESP_BODY_TEXT, resp.body(), "unexpected response body");
                assertTrue(matcher.wasInvoked(), "SNIMatcher wasn't invoked on the server");
            }
        } finally {
            System.out.println("stopping server " + server.getAddress());
            server.stop();
        }
    }

    /*
     * Creates a HttpsServer configured to use the given SNIMatcher
     */
    private static HttpTestServer createServer(final SNIMatcher matcher,
                                               final SSLContext sslContext) throws Exception {
        final InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        final int backlog = 0;
        final HttpsServer httpsServer = HttpsServer.create(addr, backlog);
        final HttpsConfigurator configurator = new HttpsConfigurator(sslContext) {
            @Override
            public void configure(final HttpsParameters params) {
                final SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
                // add the SNIMatcher
                sslParameters.setSNIMatchers(List.of(matcher));
                params.setSSLParameters(sslParameters);
                System.out.println("configured HttpsServer with SNIMatcher: " + matcher);
            }
        };
        httpsServer.setHttpsConfigurator(configurator);
        final HttpTestServer server = HttpTestServer.of(httpsServer);
        server.addHandler(new Handler(), "/");
        server.start();
        System.out.println("server started at " + server.getAddress());
        return server;
    }
}
