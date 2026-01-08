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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.stream.Stream;


import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8292876
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @compile ../ReferenceTracker.java
 * @run junit/othervm -Djdk.httpclient.HttpClient.log=quic,errors
 *              -Djdk.httpclient.http3.maxDirectConnectionTimeout=4000
 *              -Djdk.internal.httpclient.debug=true H3UserInfoTest
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class H3UserInfoTest implements HttpServerAdapters {

    static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    static HttpTestServer server;
    static HttpTestServer server3;
    static String serverURI;
    static String server3URI;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();

    @BeforeAll
    static void before() throws Exception {
        HttpTestHandler handler = new HttpHandler();

        server = HttpTestServer.create(ANY, sslContext);
        server.addHandler(handler, "/");
        serverURI = "https://" + server.serverAuthority() +"/http3-any/";
        server.start();

        server3 = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        server3.addHandler(handler, "/");
        server3URI = "https://" + server3.serverAuthority() +"/http3-only/";
        server3.start();
    }

    @AfterAll
    static void after() throws Exception {
        server.stop();
        server3.stop();
    }

    static class HttpHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange e) throws IOException {
            String authorityHeader = e.getRequestHeaders()
                    .firstValue(":authority")
                    .orElse(null);
            if (authorityHeader == null || authorityHeader.contains("user@")) {
                e.sendResponseHeaders(500, 0);
            } else {
                e.sendResponseHeaders(200, 0);
            }
        }
    }

    public static Stream<Arguments> servers() {
        return Stream.of(
                Arguments.arguments(serverURI, server),
                Arguments.arguments(server3URI, server3)
        );
    }

    @ParameterizedTest
    @MethodSource("servers")
    public void testAuthorityHeader(String serverURI, HttpTestServer server) throws Exception {
        try (HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(HTTP_3)
                .sslContext(sslContext)
                .build()) {
            TRACKER.track(client);

            URI origURI = URI.create(serverURI);
            URI uri = URIBuilder.newBuilder()
                    .scheme("https")
                    .userInfo("user")
                    .host(origURI.getHost())
                    .port(origURI.getPort())
                    .path(origURI.getRawPath())
                    .build();
            var config = server.h3DiscoveryConfig();

            int numRetries = 0;
            while (true) {
                if (config == ALT_SVC) {
                    // send head request
                    System.out.printf("Sending head request (%s) to %s%n", config, origURI);
                    System.err.printf("Sending head request (%s) to %s%n", config, origURI);
                    HttpRequest head = HttpRequest.newBuilder(origURI)
                            .HEAD()
                            .version(HTTP_2)
                            .build();
                    var headResponse = client.send(head, BodyHandlers.ofString());
                    assertEquals(200, headResponse.statusCode());
                    assertEquals(HTTP_2, headResponse.version());
                    assertEquals("", headResponse.body());
                }

                HttpRequest request = HttpRequest
                        .newBuilder(uri)
                        .setOption(H3_DISCOVERY, config)
                        .version(HTTP_3)
                        .GET()
                        .build();

                System.out.printf("Sending GET request (%s) to %s%n", config, origURI);
                System.err.printf("Sending GET request (%s) to %s%n", config, origURI);
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

                assertEquals(200, response.statusCode(),
                        "Test Failed : " + response.uri().getAuthority());
                assertEquals("", response.body());
                if (config != ANY) {
                    assertEquals(HTTP_3, response.version());
                } else if (response.version() != HTTP_3) {
                    // the request went through HTTP/2 - the next
                    // should go through HTTP/3
                    if (numRetries++ < 3) {
                        System.out.printf("Received GET response (%s) to %s with version %s: " +
                                "repeating request once more%n", config, origURI, response.version());
                        System.err.printf("Received GET response (%s) to %s with version %s: " +
                                "repeating request once more%n", config, origURI, response.version());
                        assertEquals(HTTP_2, response.version());
                        continue;
                    } else {
                        fail("Did not receive the expected HTTP3 response");
                    }
                }
                break;
            }
        }
        // the client should already be closed, but its facade ref might
        // not have been cleared by GC yet.
        System.gc();
        var error = TRACKER.checkClosed(1500);
        if (error != null) throw error;
    }
}
