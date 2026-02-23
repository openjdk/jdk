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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test id
 * @bug 8367976
 * @summary Verifies that setting the `jdk.httpclient.bufsize` system property
 *          to its lowest possible value, 1, does not wedge the client
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @run junit/othervm -Djdk.httpclient.bufsize=1 BufferSize1Test
 */

class BufferSize1Test implements HttpServerAdapters {

    @BeforeAll
    static void verifyBufferSize() {
        assertEquals(1, Utils.BUFSIZE);
    }

    static Object[][] testArgs() {
        return new Object[][]{
                {HTTP_1_1, false},
                {HTTP_1_1, true},
                {HTTP_2, false},
                {HTTP_2, true},
                {HTTP_3, true}
        };
    }

    @ParameterizedTest
    @MethodSource("testArgs")
    void test(Version version, boolean secure) throws Exception {

        // Create the server
        var sslContext = secure || HTTP_3.equals(version) ? SimpleSSLContext.findSSLContext() : null;
        try (var server = switch (version) {
            case HTTP_1_1, HTTP_2 -> HttpTestServer.create(version, sslContext);
            case HTTP_3 -> HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        }) {

            // Add the handler and start the server
            var serverHandlerPath = "/" + BufferSize1Test.class.getSimpleName();
            server.addHandler(new HttpTestEchoHandler(), serverHandlerPath);
            server.start();

            // Create the client
            try (var client = createClient(version, sslContext)) {

                // Create the request with body to ensure that `ByteBuffer`s
                // will be used throughout the entire end-to-end interaction.
                byte[] requestBodyBytes = "body".repeat(1000).getBytes(StandardCharsets.US_ASCII);
                var request = createRequest(sslContext, server, serverHandlerPath, version, requestBodyBytes);

                // Execute and verify the request.
                // Do it twice to cover code paths before and after a protocol upgrade.
                requestAndVerify(client, request, requestBodyBytes);
                requestAndVerify(client, request, requestBodyBytes);

            }

        }

    }

    private HttpClient createClient(Version version, SSLContext sslContext) {
        var clientBuilder = newClientBuilderForH3()
                .proxy(NO_PROXY)
                .version(version);
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        return clientBuilder.build();
    }

    private static HttpRequest createRequest(
            SSLContext sslContext,
            HttpTestServer server,
            String serverHandlerPath,
            Version version,
            byte[] requestBodyBytes) {
        var requestUri = URI.create(String.format(
                "%s://%s%s/x",
                sslContext == null ? "http" : "https",
                server.serverAuthority(),
                serverHandlerPath));
        var requestBuilder = HttpRequest
                .newBuilder(requestUri)
                .version(version)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBodyBytes));
        if (HTTP_3.equals(version)) {
            requestBuilder.setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        }
        return requestBuilder.build();
    }

    private static void requestAndVerify(HttpClient client, HttpRequest request, byte[] requestBodyBytes)
            throws IOException, InterruptedException {
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new AssertionError("Was expecting status code 200, found: " + response.statusCode());
        }
        byte[] responseBodyBytes = response.body();
        int mismatchIndex = Arrays.mismatch(requestBodyBytes, responseBodyBytes);
        assertTrue(
                mismatchIndex < 0,
                String.format(
                        "Response body (%s bytes) mismatches the request body (%s bytes) at index %s!",
                        responseBodyBytes.length, requestBodyBytes.length, mismatchIndex));
    }

}
