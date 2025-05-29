/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Retry limit system property
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 * @run junit HttpClientRetryLimitTest
 * @run junit/othervm -Djdk.httpclient.auth.retrylimit=1 HttpClientRetryLimitTest
 * @run junit/othervm -Djdk.httpclient.auth.retrylimit=0 HttpClientRetryLimitTest
 * @run junit/othervm -Djdk.httpclient.auth.retrylimit=-1 HttpClientRetryLimitTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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


class HttpClientRetryLimitTest implements HttpServerAdapters {

    // This is the system default value for jdk.httpclient.auth.retrylimit
    private static final int DEFAULT_RETRY_LIMIT = 3;
    private static final int RETRY_LIMIT = Integer.getInteger(
            "jdk.httpclient.auth.retrylimit", DEFAULT_RETRY_LIMIT);

    static Stream<HttpClient.Version> args() {
        return Stream.of(
                HttpClient.Version.HTTP_1_1,
                HttpClient.Version.HTTP_2
        );
    }

    @ParameterizedTest
    @MethodSource("args")
    public void testDefaultSystemProperty(HttpClient.Version version) throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);

        try (HttpTestServer httpTestServer = HttpTestServer.create(version)) {

            HttpTestHandler httpTestHandler = t -> {
                t.getResponseHeaders()
                        .addHeader("WWW-Authenticate", "Basic realm=\"Test\"");
                t.sendResponseHeaders(401,0);
            };

            httpTestServer.addHandler(httpTestHandler, "/");
            httpTestServer.start();
            try (
                HttpClient client = HttpClient.newBuilder()
                        .authenticator(new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                requestCount.incrementAndGet();
                                return new PasswordAuthentication("username", "password".toCharArray());
                            }
                        })
                        .build()) {

                HttpRequest request = HttpRequest.newBuilder()
                        .GET()
                        .uri(new URI("http://" + httpTestServer.serverAuthority() + "/" + this.getClass().getSimpleName() + "/"))
                        .build();
                throw assertThrows(IOException.class, () -> client.send(request, HttpResponse.BodyHandlers.discarding()));
            } catch (Exception e) {
                if (RETRY_LIMIT > 0){
                    assertEquals(RETRY_LIMIT, requestCount.get(),
                            "Expected number of request retries was " + RETRY_LIMIT + " but actual was "+requestCount);
                }
                else{
                    requestCount.decrementAndGet();
                    assertEquals(0, requestCount.get(),
                            "Expected number of request retries was 0 but actual was "+requestCount);
                }
                e.printStackTrace();
            }
        }
    }
}
