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

/*
 * @test
 * @summary Verifies that Http2Connection.cachedHeaderBuffer is reused
 *          across multiple requests on the same connection.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 * @run main/othervm
 *      --add-opens java.net.http/jdk.internal.net.http=ALL-UNNAMED
 *      HeaderEncodingBufferReuseTest
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

import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.test.lib.Asserts;

public class HeaderEncodingBufferReuseTest {
    public static void main(String[] args) throws Exception {
        Http2TestServer server = new Http2TestServer("localhost", false, 0);
        server.addHandler(new OkHandler(), "/test");
        server.start();
        String uri = "http://localhost:" + server.getAddress().getPort() + "/test";

        try (HttpClient client = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(HttpClient.Version.HTTP_2)
                .build()) {

            HttpResponse<Void> warmup = send(client, uri, 2);
            Asserts.assertEquals(warmup.version(), HttpClient.Version.HTTP_2);

            Object conn = getHttp2Connection(client);
            Asserts.assertEquals(send(client, uri, 2).statusCode(), 200);
            ByteBuffer cached = (ByteBuffer) getField(conn, "cachedHeaderBuffer");
            Asserts.assertNotNull(cached);

            Asserts.assertEquals(send(client, uri, 2).statusCode(), 200);
            Asserts.assertEquals(cached, getField(conn, "cachedHeaderBuffer"));

            Asserts.assertEquals(send(client, uri, 300).statusCode(), 200);
            Asserts.assertEquals(cached, getField(conn, "cachedHeaderBuffer"));

            Asserts.assertEquals(send(client, uri, 2).statusCode(), 200);
            Asserts.assertEquals(cached, getField(conn, "cachedHeaderBuffer"));
        } finally {
            server.stop();
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
        Asserts.assertEquals(1, connections.size());
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

    static class OkHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }
}
