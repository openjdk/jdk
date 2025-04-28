/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8263899
 * @summary Verifies that empty `WWW-Authenticate` header is correctly parsed
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit EmptyAuthenticate
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmptyAuthenticate {

    private static final SSLContext SSL_CONTEXT = createSslContext();

    private static final String WWW_AUTH_HEADER_NAME = "WWW-Authenticate";

    private static SSLContext createSslContext() {
        try {
            return new SimpleSSLContext().get();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @ParameterizedTest
    @MethodSource("args")
    void test(Version version, boolean secure) throws Exception {
        String handlerPath = "/%s/%s/".formatted(EmptyAuthenticate.class.getSimpleName(), version);
        String uriPath = handlerPath + (secure ? 's' : 'c');
        HttpTestServer server = createServer(version, secure, handlerPath);
        try (HttpClient client = createClient(version, secure)) {
            HttpRequest request = createRequest(server, secure, uriPath);
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            HttpHeaders responseHeaders = response.headers();
            assertEquals(
                    "",
                    responseHeaders.firstValue(WWW_AUTH_HEADER_NAME).orElse(null),
                    () -> "was expecting empty `%s` header in: %s".formatted(
                            WWW_AUTH_HEADER_NAME, responseHeaders.map()));
        } finally {
            server.stop();
        }
    }

    static Stream<Arguments> args() {
        return Stream
                .of(Version.HTTP_1_1, Version.HTTP_2)
                .flatMap(version -> Stream
                        .of(true, false)
                        .map(secure -> Arguments.of(version, secure)));
    }

    private static HttpTestServer createServer(Version version, boolean secure, String uriPath)
            throws IOException {
        HttpTestServer server = secure
                ? HttpTestServer.create(version, SSL_CONTEXT)
                : HttpTestServer.create(version);
        HttpTestHandler handler = new ServerHandlerRespondingWithEmptyWwwAuthHeader();
        server.addHandler(handler, uriPath);
        server.start();
        return server;
    }

    private static final class ServerHandlerRespondingWithEmptyWwwAuthHeader implements HttpTestHandler {

        private int responseIndex = 0;

        @Override
        public synchronized void handle(HttpServerAdapters.HttpTestExchange exchange) throws IOException {
            try (exchange) {
                exchange.getResponseHeaders().addHeader(WWW_AUTH_HEADER_NAME, "");
                byte[] responseBodyBytes = "test body %d"
                        .formatted(responseIndex)
                        .getBytes(StandardCharsets.US_ASCII);
                exchange.sendResponseHeaders(401, responseBodyBytes.length);
                exchange.getResponseBody().write(responseBodyBytes);
            } finally {
                responseIndex++;
            }
        }

    }

    private static HttpClient createClient(Version version, boolean secure) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().version(version).proxy(NO_PROXY);
        if (secure) {
            clientBuilder.sslContext(SSL_CONTEXT);
        }
        return clientBuilder.build();
    }

    private static HttpRequest createRequest(HttpTestServer server, boolean secure, String uriPath) {
        URI uri = URI.create("%s://%s%s".formatted(secure ? "https" : "http", server.serverAuthority(), uriPath));
        return HttpRequest.newBuilder(uri).version(server.getVersion()).GET().build();
    }

}
