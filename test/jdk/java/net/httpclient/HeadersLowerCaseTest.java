/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @summary Verify that the request/response headers of HTTP/2 and HTTP/3
 *          are sent and received in lower case
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm -Djdk.internal.httpclient.debug=true HeadersLowerCaseTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HeadersLowerCaseTest implements HttpServerAdapters {

    private static Set<String> REQUEST_HEADERS;

    private HttpTestServer h2server;
    private HttpTestServer h3server;
    private String h2ReqURIBase;
    private String h3ReqURIBase;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();

    @BeforeAll
    public void beforeAll() throws Exception {
        h2server = HttpTestServer.create(HTTP_2, sslContext);
        h2server.start();
        h2ReqURIBase = "https://" + h2server.serverAuthority();
        h2server.addHandler(new ReqHeadersVerifier(), "/h2verifyReqHeaders");
        System.out.println("HTTP/2 server listening on " + h2server.getAddress());


        h3server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3server.start();
        h3ReqURIBase = "https://" + h3server.serverAuthority();
        h3server.addHandler(new ReqHeadersVerifier(), "/h3verifyReqHeaders");
        System.out.println("HTTP/3 server listening on " + h3server.getAddress());

        REQUEST_HEADERS = new HashSet<>();
        REQUEST_HEADERS.add("AbCdeFgh");
        REQUEST_HEADERS.add("PQRSTU");
        REQUEST_HEADERS.add("xyz");
        REQUEST_HEADERS.add("A1243Bde2");
        REQUEST_HEADERS.add("123243");
        REQUEST_HEADERS.add("&1bacd*^d");
        REQUEST_HEADERS.add("~!#$%^&*_+");
    }

    @AfterAll
    public void afterAll() throws Exception {
        if (h2server != null) {
            h2server.stop();
        }
        if (h3server != null) {
            h3server.stop();
        }
    }

    /**
     * Handler which verifies that the request header names are lowercase (as mandated by the spec)
     */
    private static final class ReqHeadersVerifier implements HttpTestHandler {

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            System.out.println("Verifying request headers for " + exchange.getRequestURI());
            final Set<String> missing = new HashSet<>(REQUEST_HEADERS);
            final HttpTestRequestHeaders headers = exchange.getRequestHeaders();
            for (final Map.Entry<String, List<String>> e : headers.entrySet()) {
                final String header = e.getKey();
                // check validity of (non-pseudo) header names
                if (!header.startsWith(":") && !Utils.isValidLowerCaseName(header)) {
                    System.err.println("Header name " + header + " is not valid");
                    sendResponse(exchange, 500);
                    return;
                }
                final List<String> headerVals = e.getValue();
                if (headerVals.isEmpty()) {
                    System.err.println("Header " + header + " is missing value");
                    sendResponse(exchange, 500);
                    return;
                }
                // the header value represents the original form of the header key held in the
                // REQUEST_HEADERS set
                final String originalForm = headerVals.get(0);
                missing.remove(originalForm);
            }
            if (!missing.isEmpty()) {
                System.err.println("Missing headers in request: " + missing);
                sendResponse(exchange, 500);
                return;
            }
            System.out.println("All expected headers received in lower case for " + exchange.getRequestURI());
            sendResponse(exchange, 200);
        }

        private static void sendResponse(final HttpTestExchange exchange, final int statusCode) throws IOException {
            final HttpTestResponseHeaders respHeaders = exchange.getResponseHeaders();
            // we just send the pre-defined (request) headers back as the response headers
            for (final String k : REQUEST_HEADERS) {
                respHeaders.addHeader(k, k);
            }
            exchange.sendResponseHeaders(statusCode, 0);
        }
    }

    private Stream<Arguments> params() throws Exception {
        return Stream.of(
                Arguments.of(HTTP_2, new URI(h2ReqURIBase + "/h2verifyReqHeaders")),
                Arguments.of(Version.HTTP_3, new URI(h3ReqURIBase + "/h3verifyReqHeaders")));
    }

    /**
     * Issues a HTTP/2 or HTTP/3 request with header names of varying case (some in lower,
     * some mixed, some upper case) and expects that the client internally converts them
     * to lower case before encoding and sending to the server. The server side handler verifies
     * that it receives the header names in lower case and if it doesn't then it returns a
     * non-200 response
     */
    @ParameterizedTest
    @MethodSource("params")
    public void testRequestHeaders(final Version version, final URI requestURI) throws Exception {
        try (final HttpClient client = newClientBuilderForH3()
                .version(version)
                .sslContext(sslContext)
                .proxy(HttpClient.Builder.NO_PROXY).build()) {
            Http3DiscoveryMode config = switch (version) {
                case HTTP_3 -> HTTP_3_URI_ONLY;
                default -> ALT_SVC;
            };
            final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(requestURI)
                    .setOption(H3_DISCOVERY, config)
                    .version(version);
            for (final String k : REQUEST_HEADERS) {
                reqBuilder.header(k, k);
            }
            final HttpRequest req = reqBuilder.build();
            System.out.println("Issuing " + version + " request to " + requestURI);
            final HttpResponse<Void> resp = client.send(req, BodyHandlers.discarding());
            assertEquals(resp.version(), version, "Unexpected HTTP version in response");
            assertEquals(resp.statusCode(), 200, "Unexpected response code");
            // now try with async
            System.out.println("Issuing (async) request to " + requestURI);
            final CompletableFuture<HttpResponse<Void>> futureResp = client.sendAsync(req,
                    BodyHandlers.discarding());
            final HttpResponse<Void> asyncResp = futureResp.get();
            assertEquals(asyncResp.version(), version, "Unexpected HTTP version in response");
            assertEquals(asyncResp.statusCode(), 200, "Unexpected response code");
        }
    }

    /**
     * Verifies that when a HTTP/2 or HTTP/3 request is being built using
     * {@link HttpRequest.Builder}, only valid header names are allowed to be added to the request
     */
    @ParameterizedTest
    @MethodSource("params")
    public void testInvalidHeaderName(final Version version, final URI requestURI) throws Exception {
        Http3DiscoveryMode config = switch (version) {
            case HTTP_3 -> HTTP_3_URI_ONLY;
            default -> ALT_SVC;
        };
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(requestURI)
                .setOption(H3_DISCOVERY, config)
                .version(version);
        final String copyrightSign = new String(Character.toChars(0x00A9)); // copyright sign
        final String invalidHeaderName = "abcd" + copyrightSign;
        System.out.println("Adding header name " + invalidHeaderName + " to " + version + " request");
        // Field names are strings containing a subset of ASCII characters.
        // This header name contains a unicode character, so it should fail
        assertThrows(IllegalArgumentException.class,
                () -> reqBuilder.header(invalidHeaderName, "something"));
    }
}
