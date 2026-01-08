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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.internal.net.http.http3.Http3Error;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpRequest.BodyPublishers;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static jdk.httpclient.test.lib.common.HttpServerAdapters.createClientBuilderForH3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8369812
 * @summary verify that when the server sends a request reset with H3_REQUEST_REJECTED as the
 *          error code, then the HttpClient rightly reissues that request afresh
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit ${test.main.class}
 */
class H3RequestRejectedTest {

    private static final String HANDLER_PATH = "/foo";

    private static HttpTestServer server;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();

    @BeforeAll
    static void beforeAll() throws Exception {
        server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        server.addHandler(new Handler(), HANDLER_PATH);
        server.start();
        System.err.println("server started at " + server.getAddress());
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            System.err.println("stopping server: " + server.getAddress());
            server.close();
        }
    }

    /*
     * Issues a HTTP/3 request to a server which is expected to respond with a
     * reset stream with error code H3_REQUEST_REJECTED for the request, every single time.
     * The HTTP/3 specification allows for such requests to be retried by the client,
     * afresh (if necessary on a new connection). This test verifies that the application
     * receives an IOException, due to the server always rejecting the request.
     */
    @Test
    void testAlwaysRejected() throws Exception {
        try (final HttpClient client = createClientBuilderForH3()
                .sslContext(sslContext).proxy(NO_PROXY).version(HTTP_3)
                .build()) {

            final URI reqURI = URIBuilder.newBuilder()
                    .scheme("https")
                    .host(server.getAddress().getAddress())
                    .port(server.getAddress().getPort())
                    .path(HANDLER_PATH)
                    .query("always-reject")
                    .build();
            final HttpRequest req = HttpRequest.newBuilder()
                    .uri(reqURI)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY).build();
            System.err.println("issuing request " + req);
            final IOException thrown = assertThrows(IOException.class,
                    () -> client.send(req, BodyHandlers.ofString(US_ASCII)));
            if (!"request not processed by peer".equals(thrown.getMessage())) {
                // propagate original exception
                fail("missing exception message in IOException", thrown);
            }
        }
    }

    /*
     * Issues several HTTP/3 requests to a server which is expected to respond with a
     * reset stream with error code H3_REQUEST_REJECTED for some of those requests.
     * The HTTP/3 specification allows for such requests to be retried by the client,
     * afresh (if necessary on a new connection). This test verifies that the HttpClient
     * implementation internally retries such requests and thus the application receives
     * a successful response.
     */
    @Test
    void testRejectedRequest() throws Exception {
        try (final HttpClient client = createClientBuilderForH3().sslContext(sslContext)
                .proxy(NO_PROXY).version(HTTP_3)
                .build()) {

            final URI reqURI = URIBuilder.newBuilder()
                    .scheme("https")
                    .host(server.getAddress().getAddress())
                    .port(server.getAddress().getPort())
                    .path(HANDLER_PATH)
                    .build();
            for (int i = 1; i <= 5; i++) {
                final HttpRequest req = HttpRequest.newBuilder()
                        .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                        // using POST introduces additional guarantees that
                        // the request will be retried only because it was
                        // unprocessed and not for some other retryable reasons
                        // which are allowed for idempotent methods like GET
                        .POST(BodyPublishers.noBody())
                        .uri(new URI(reqURI.toString() + "?iteration=" + i))
                        .build();
                System.err.println("iteration " + i + " issuing request " + req);
                final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString(US_ASCII));
                assertEquals(200, resp.statusCode(), "unexpected response status");
                assertEquals(Handler.RESP_BODY, resp.body(), "unexpected response body");
            }
            // verify that some requests were indeed rejected (and reissued internally by the client)
            final Set<URI> rejectedOnes = Handler.rejectedRequests;
            System.err.println("server side handler (intentionally) rejected following "
                    + rejectedOnes.size() + " requests:");
            System.err.println(rejectedOnes);
            assertFalse(rejectedOnes.stream()
                            .filter((uri) -> uri.toString().contains("iteration"))
                            .findAny()
                            .isEmpty(),
                    "server was expected to reject at least one request, but it didn't");
        }
    }

    private static final class Handler implements HttpTestHandler {
        private static final String RESP_BODY = "done";
        private static final Set<URI> rejectedRequests = Collections.synchronizedSet(new HashSet<>());
        private static final AtomicInteger reqsCounter = new AtomicInteger();

        @Override
        public void handle(final HttpTestExchange exch) throws IOException {
            final URI reqURI = exch.getRequestURI();
            final String reqQuery = reqURI.getQuery();
            System.err.println("handling request " + reqURI + " " + reqQuery);
            final int currentReqNum = reqsCounter.incrementAndGet();
            if (currentReqNum % 2 == 0
                    || (reqQuery != null && reqQuery.contains("always-reject"))) {
                // reject it
                rejectedRequests.add(reqURI);
                System.err.println("resetting request with H3_REQUEST_REJECTED code for " + reqURI);
                exch.resetStream(Http3Error.H3_REQUEST_REJECTED.code());
                return;
            }
            // send 200 response
            final byte[] body = RESP_BODY.getBytes(US_ASCII);
            exch.sendResponseHeaders(200, body.length);
            try (final OutputStream os = exch.getResponseBody()) {
                os.write(body);
            }
            System.err.println("responded with 200 status code for " + reqURI);
        }
    }
}
