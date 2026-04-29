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
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.util.Map;
import static java.net.http.HttpClient.Version.HTTP_2;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.test.lib.net.URIBuilder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @summary Verifies that Http2Connection.cachedHeaderBuffer is reused
 *          across multiple requests on the same connection.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *      --add-opens java.net.http/jdk.internal.net.http=ALL-UNNAMED
 *      ${test.main.class}
 */

public class HeaderEncodingBufferReuseTest implements HttpServerAdapters {

    static String httpUri;
    static HttpTestServer testServer;

    @BeforeAll
    static void init() throws Exception {
        testServer = HttpTestServer.create(HTTP_2);
        testServer.addHandler(new OkHandler(), "/test");
        testServer.start();
        httpUri = URIBuilder.newBuilder()
                            .scheme("http")
                            .host("localhost")
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
                .build()) {

            // Force a large cached header buffer by sending 300 headers.
            assertEquals(200, send(client, httpUri, 300).statusCode());

            Object conn = getHttp2Connection(client);
            assertEquals(200, send(client, httpUri, 2).statusCode());
            ByteBuffer cached = (ByteBuffer) getField(conn, "cachedHeaderBuffer");
            assertNotNull(cached);
            assertSame(conn, getHttp2Connection(client));

            assertEquals(200, send(client, httpUri, 2).statusCode());
            assertSame(cached, getField(conn, "cachedHeaderBuffer"));
            assertSame(conn, getHttp2Connection(client));

            assertEquals(200, send(client, httpUri, 300).statusCode());
            assertSame(cached, getField(conn, "cachedHeaderBuffer"));
            assertSame(conn, getHttp2Connection(client));

            assertEquals(200, send(client, httpUri, 2).statusCode());
            assertSame(cached, getField(conn, "cachedHeaderBuffer"));
            assertSame(conn, getHttp2Connection(client));
        }
    }

    static Object getField(Object obj, String name) throws Exception {
        Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(obj);
    }

    static Object getHttp2Connection(HttpClient client) throws Exception {
        Object clientImpl = getField(client, "impl");

        var method = clientImpl.getClass().getDeclaredMethod("client2");
        method.setAccessible(true);
        Object client2 = method.invoke(clientImpl);

        Object conns = getField(client2, "connections");
        Map<String, ?> connections = (Map<String, ?>) conns;
        assertEquals(1, connections.size());
        return connections.values().iterator().next();
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
