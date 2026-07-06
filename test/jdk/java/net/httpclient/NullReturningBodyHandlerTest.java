/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestEchoHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8377796
 * @summary Verify that HttpClient.send()/sendAsync() complete exceptionally
 *          when BodyHandler.apply() returns null
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.net.URIBuilder
 * @run junit ${test.main.class}
 */
class NullReturningBodyHandlerTest {
    private static final String CTX_PATH = "/" + NullReturningBodyHandlerTest.class.getName();

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpClient client;

    private static HttpTestServer h1HttpServer;
    private static HttpTestServer h1HttpsServer;

    private static HttpTestServer h2HttpServer;
    private static HttpTestServer h2HttpsServer;

    private static HttpTestServer h3Server;

    @BeforeAll
    static void beforeAll() throws Exception {
        h1HttpServer = HttpTestServer.create(Version.HTTP_1_1);
        h1HttpServer.addHandler(new HttpTestEchoHandler(false), CTX_PATH);
        h1HttpServer.start();
        System.err.println("HTTP/1.1 http server started at " + h1HttpServer.getAddress());

        h1HttpsServer = HttpTestServer.create(Version.HTTP_1_1, sslContext);
        h1HttpsServer.addHandler(new HttpTestEchoHandler(false), CTX_PATH);
        h1HttpsServer.start();
        System.err.println("HTTP/1.1 https server started at " + h1HttpsServer.getAddress());

        h2HttpServer = HttpTestServer.create(Version.HTTP_2);
        h2HttpServer.addHandler(new HttpTestEchoHandler(false), CTX_PATH);
        h2HttpServer.start();
        System.err.println("HTTP/2 http server started at " + h2HttpServer.getAddress());

        h2HttpsServer = HttpTestServer.create(Version.HTTP_2, sslContext);
        h2HttpsServer.addHandler(new HttpTestEchoHandler(false), CTX_PATH);
        h2HttpsServer.start();
        System.err.println("HTTP/2 https server started at " + h2HttpsServer.getAddress());

        h3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3Server.addHandler(new HttpTestEchoHandler(false), CTX_PATH);
        h3Server.start();
        System.err.println("HTTP/3 server started at " + h3Server.getAddress());

        client = HttpServerAdapters.createClientBuilderForH3()
                .sslContext(sslContext)
                .proxy(NO_PROXY)
                .build();
    }

    @AfterAll
    static void afterAll() throws Exception {
        close(h1HttpServer);
        close(h1HttpsServer);

        close(h2HttpServer);
        close(h2HttpsServer);

        close(h3Server);

        close(client);
    }

    private static void close(final AutoCloseable resource) throws Exception {
        if (resource == null) {
            return;
        }
        System.err.println("closing " + resource);
        resource.close();
    }

    static List<Arguments> params() throws Exception {
        final List<Arguments> args = new ArrayList<>();

        final URI h1HttpsReq = URIBuilder.newBuilder()
                .scheme("https")
                .host(h1HttpsServer.getAddress().getAddress())
                .port(h1HttpsServer.getAddress().getPort())
                .path(CTX_PATH)
                .query("reqVersion=h1")
                .build();
        args.add(Arguments.of(h1HttpsReq, Version.HTTP_1_1, false));

        final URI h1HttpReq = URIBuilder.newBuilder()
                .scheme("http")
                .host(h1HttpServer.getAddress().getAddress())
                .port(h1HttpServer.getAddress().getPort())
                .path(CTX_PATH)
                .query("reqVersion=h1&scheme=http")
                .build();
        args.add(Arguments.of(h1HttpReq, Version.HTTP_1_1, false));

        final URI h2Req = URIBuilder.newBuilder()
                .scheme("https")
                .host(h2HttpsServer.getAddress().getAddress())
                .port(h2HttpsServer.getAddress().getPort())
                .path(CTX_PATH)
                .query("reqVersion=h2")
                .build();
        args.add(Arguments.of(h2Req, Version.HTTP_2, false));

        final URI h2HttpReq = URIBuilder.newBuilder()
                .scheme("http")
                .host(h2HttpServer.getAddress().getAddress())
                .port(h2HttpServer.getAddress().getPort())
                .path(CTX_PATH)
                .query("reqVersion=h2&scheme=http")
                .build();
        // test for HTTP/2 upgrade when there are no already established connections
        args.add(Arguments.of(h2HttpReq, Version.HTTP_2, false));
        // test for HTTP/2 upgrade when there is an established connection
        args.add(Arguments.of(h2HttpReq, Version.HTTP_2, true));

        final URI h3Req = URIBuilder.newBuilder()
                .scheme("https")
                .host(h3Server.getAddress().getAddress())
                .port(h3Server.getAddress().getPort())
                .path(CTX_PATH)
                .query("reqVersion=h3")
                .build();
        args.add(Arguments.of(h3Req, Version.HTTP_3, false));
        return args;
    }

    /*
     * Issues a HTTP request with a BodyHandler implementation that returns a null
     * BodySubscriber. The test then verifies that the request fails and the exception
     * that's raised contains the expected NullPointerException (raised due to
     * BodyHandler.apply(...) returning null).
     */
    @ParameterizedTest
    @MethodSource("params")
    void test(final URI reqURI, final Version version,
              final boolean requiresWarmupHEADRequest) throws Exception {
        if (requiresWarmupHEADRequest) {
            // the test only issues a warmup request for HTTP/2 requests
            assertEquals(Version.HTTP_2, version, "unexpected HTTP version");
            final HttpRequest head = HttpRequest.newBuilder().HEAD()
                    .version(version)
                    .uri(reqURI)
                    .build();
            System.err.println("issuing warmup head request " + head);
            HttpResponse<Void> headResp = client.send(head, HttpResponse.BodyHandlers.discarding());
            assertEquals(200, headResp.statusCode(), "unexpected status code for HEAD request");
        }
        // now run the actual test
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .version(version)
                .uri(reqURI);
        if (version == Version.HTTP_3) {
            builder.setOption(HttpOption.H3_DISCOVERY, HTTP_3_URI_ONLY);
        }
        final HttpRequest req = builder.build();
        // test synchronous send()
        System.err.println("issuing request " + reqURI);
        final IOException ioe = assertThrows(IOException.class,
                () -> client.send(req, new AlwaysReturnsNull()));
        if (!(ioe.getCause() instanceof NullPointerException)) {
            throw ioe; // propagate the original exception
        }
        // now test with sendAsync()
        System.err.println("issuing async request " + reqURI);
        final Future<HttpResponse<String>> f = client.sendAsync(req, new AlwaysReturnsNull());
        final ExecutionException ee = assertThrows(ExecutionException.class, f::get);
        if (!(ee.getCause() instanceof IOException cause)
                || !(cause.getCause() instanceof NullPointerException)) {
            throw ee; // propagate the original exception
        }
    }

    private static final class AlwaysReturnsNull implements BodyHandler<String> {
        @Override
        public BodySubscriber<String> apply(final ResponseInfo responseInfo) {
            return null;
        }
    }
}
