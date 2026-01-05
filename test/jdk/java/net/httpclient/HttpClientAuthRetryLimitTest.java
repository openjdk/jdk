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


/*
 * @test
 * @bug 8340182
 * @summary Auth retry limit system property
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 * @run junit HttpClientAuthRetryLimitTest
 * @run junit/othervm -Djdk.httpclient.auth.retrylimit=1 HttpClientAuthRetryLimitTest
 * @run junit/othervm -Djdk.httpclient.auth.retrylimit=0 HttpClientAuthRetryLimitTest
 * @run junit/othervm -Djdk.httpclient.auth.retrylimit=-1 HttpClientAuthRetryLimitTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientAuthRetryLimitTest implements HttpServerAdapters {

    private static final SSLContext SSL_CONTEXT = createSslContext();

    private static SSLContext createSslContext() {
        try {
            return new SimpleSSLContext().get();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    // This is the system default value for jdk.httpclient.auth.retrylimit
    private static final int DEFAULT_RETRY_LIMIT = 3;
    private static final int RETRY_LIMIT = Integer.getInteger(
            "jdk.httpclient.auth.retrylimit", DEFAULT_RETRY_LIMIT);

    private static Stream<Object> args() {
        return Stream.of(
                HttpClient.Version.HTTP_1_1,
                HttpClient.Version.HTTP_2)
                .flatMap(version -> Stream
                        .of(false, true)
                        .map(secure -> Arguments.of(version, secure)));
    }

    private static HttpClient.Builder createClient(boolean secure) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (secure) {
            builder.sslContext(SSL_CONTEXT);
        }
        return builder;
    }

    @ParameterizedTest
    @MethodSource("args")
    void testDefaultSystemProperty(HttpClient.Version version, boolean secure) throws Exception {

        AtomicInteger requestCount = new AtomicInteger(0);

        try (HttpTestServer httpTestServer = ((secure)? HttpTestServer.create(
                version, SSL_CONTEXT): HttpTestServer.create(version))) {
            final String requestUriScheme = secure ? "https" : "http";
            final String requestPath = "/" + this.getClass().getSimpleName() + "/";
            final String uriString = "://" + httpTestServer.serverAuthority() + requestPath;
            final URI requestUri = URI.create(requestUriScheme + uriString);

            HttpTestHandler httpTestHandler = t -> {
                t.getResponseHeaders()
                        .addHeader("WWW-Authenticate", "Basic realm=\"Test\"");
                t.sendResponseHeaders(401,0);
            };

            httpTestServer.addHandler(httpTestHandler, requestPath);
            httpTestServer.start();
            try (
                HttpClient client = createClient(secure)
                        .authenticator(new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                requestCount.incrementAndGet();
                                return new PasswordAuthentication("username", "password".toCharArray());
                            }
                        })
                        .build()) {
                HttpRequest request = HttpRequest.newBuilder().version(version)
                        .GET()
                        .uri(requestUri)
                        .build();
                IOException exception = assertThrows(IOException.class, () -> client.send(
                        request, HttpResponse.BodyHandlers.discarding()));
                assertEquals("too many authentication attempts. Limit: " + RETRY_LIMIT, exception.getMessage());
                int totalRequestCount = requestCount.get();
                assertEquals(totalRequestCount, Math.max(RETRY_LIMIT, 0) + 1);
            }
        }
    }
}
