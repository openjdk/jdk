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
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;

import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

/*
 * @test
 * @bug 8367976
 * @summary Verifies that setting the `jdk.httpclient.bufsize` system property
 *          to its lowest possible value, 1, does not wedge the client
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @run main/othervm -Djdk.httpclient.bufsize=1 -Dtest.httpVersion=HTTP_1_1 BufferSize1Test
 * @run main/othervm -Djdk.httpclient.bufsize=1 -Dtest.httpVersion=HTTP_1_1 -Dtest.sslEnabled BufferSize1Test
 * @run main/othervm -Djdk.httpclient.bufsize=1 -Dtest.httpVersion=HTTP_2 BufferSize1Test
 * @run main/othervm -Djdk.httpclient.bufsize=1 -Dtest.httpVersion=HTTP_2 -Dtest.sslEnabled BufferSize1Test
 * @run main/othervm -Djdk.httpclient.bufsize=1 -Dtest.httpVersion=HTTP_3 BufferSize1Test
 */

public class BufferSize1Test {

    public static void main(String[] args) throws Exception {

        // Verify `Utils.BUFSIZE`
        if (Utils.BUFSIZE != 1) {
            throw new AssertionError("Unexpected `Utils.BUFSIZE`: " + Utils.BUFSIZE);
        }

        // Create the server
        var version = Version.valueOf(System.getProperty("test.httpVersion"));
        var sslContext = System.getProperty("test.sslEnabled") != null || HTTP_3.equals(version)
                ? new SimpleSSLContext().get()
                : null;
        try (var server = switch (version) {
            case HTTP_1_1, HTTP_2 -> HttpTestServer.create(version, sslContext);
            case HTTP_3 -> HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        }) {

            // Add the handler and start the server
            var serverHandlerPath = "/" + BufferSize1Test.class.getSimpleName();
            HttpServerAdapters.HttpTestHandler serverHandler = exchange -> {
                try (exchange) {
                    exchange.sendResponseHeaders(200, 0);
                }
            };
            server.addHandler(serverHandler, serverHandlerPath);
            server.start();

            // Create the client
            var clientBuilder = HttpServerAdapters
                    .createClientBuilderFor(version)
                    .proxy(NO_PROXY)
                    .version(version);
            if (sslContext != null) {
                clientBuilder.sslContext(sslContext);
            }
            try (var client = clientBuilder.build()) {

                // Create the request
                var requestUri = URI.create(String.format(
                        "%s://%s%s/x",
                        sslContext == null ? "http" : "https",
                        server.serverAuthority(),
                        serverHandlerPath));
                var requestBuilder = HttpRequest.newBuilder(requestUri).version(version).HEAD();
                if (HTTP_3.equals(version)) {
                    requestBuilder.setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
                }
                var request = requestBuilder.build();

                // Execute and verify the request
                var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() != 200) {
                    throw new AssertionError("Was expecting status code 200, found: " + response.statusCode());
                }

            }

        }

    }

}
