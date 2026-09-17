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
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.util.Collection;
import javax.net.ssl.SSLContext;
import static java.net.http.HttpClient.Version.HTTP_2;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.internal.net.http.HttpClientImplAccess;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.net.SimpleSSLContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8383248
 * @summary Verifies that Http2Connection.cachedHeaderBuffer is reused
 *          across multiple requests on the same connection.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 *          /test/jdk/java/net/httpclient/access
 * @build java.net.http/jdk.internal.net.http.HttpClientImplAccess
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit/othervm
 *      ${test.main.class}
 */

public class HeaderEncodingBufferReuseTest implements HttpServerAdapters {

    static String httpUri;
    static SSLContext sslContext;
    static HttpTestServer testServer;

    @BeforeAll
    static void init() throws Exception {
        sslContext = SimpleSSLContext.findSSLContext();
        testServer = HttpTestServer.create(HTTP_2, sslContext);
        testServer.addHandler(new OkHandler(), "/test");
        testServer.start();
        httpUri = URIBuilder.newBuilder()
                            .scheme("https")
                            .loopback()
                            .port(testServer.getAddress().getPort())
                            .path("/test")
                            .build()
                            .toString();
    }

    @AfterAll
    static void teardown() {
        testServer.stop();
    }

    @Test
    void test() throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .build()) {

            // Send an initial request to allocate the header encoding buffer.
            assertEquals(200, send(client, httpUri, 2).statusCode());

            Collection<?> connections = HttpClientImplAccess.getHttp2Connections(client);
            assertEquals(1, connections.size());
            Object conn = connections.iterator().next();

            ByteBuffer cached = HttpClientImplAccess.getCachedHeaderBuffer(conn);
            assertNotNull(cached);

            // Send another request and verify the same buffer is reused.
            assertEquals(200, send(client, httpUri, 2).statusCode());
            connections = HttpClientImplAccess.getHttp2Connections(client);
            assertEquals(1, connections.size());
            assertSame(conn, connections.iterator().next());
            assertSame(cached, HttpClientImplAccess.getCachedHeaderBuffer(conn));

            // Verify that the buffer is reused when headers span multiple frames.
            assertEquals(200, send(client, httpUri, 300).statusCode());
            connections = HttpClientImplAccess.getHttp2Connections(client);
            assertEquals(1, connections.size());
            assertSame(conn, connections.iterator().next());
            assertSame(cached, HttpClientImplAccess.getCachedHeaderBuffer(conn));
        }
    }

    static HttpResponse<Void> send(HttpClient client, String uri, int headerCount) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .POST(BodyPublishers.ofString("test"));
        for (int i = 0; i < headerCount; i++) {
            builder.header("X-Header-" + i, "value-" + "x".repeat(50) + "-" + i);
        }
        return client.send(builder.build(), BodyHandlers.discarding());
    }

    static class OkHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }
}
