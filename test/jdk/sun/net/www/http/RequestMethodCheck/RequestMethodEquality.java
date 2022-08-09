/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary This test checks that a broken HttpClient is not returned from the KeepAliveCache
 *          when the intial HttpURLConnection.setRequest method is passed a 'new String("POST")'
 *          rather than the "POST" String literal
 * @bug 8274779
 * @library /test/lib
 * @modules java.base/sun.net.www.http
 *          java.base/sun.net.www.protocol.http
 * @build java.base/sun.net.www.http.HttpClientAccess
 * @run testng/othervm RequestMethodEquality
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import sun.net.www.http.HttpClient;
import sun.net.www.http.HttpClientAccess;
import sun.net.www.http.KeepAliveCache;
import sun.net.www.protocol.http.HttpURLConnection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

public class RequestMethodEquality {
    private static final String TEST_CONTEXT = "/reqmethodtest";
    private HttpServer server;
    private CustomHandler handler;
    private HttpClientAccess httpClientAccess;

    @BeforeTest
    public void setup() throws Exception {
        handler = new CustomHandler();
        server = createServer(handler);
        httpClientAccess = new HttpClientAccess();
    }

    @AfterTest
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testHttpClient() throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .host(server.getAddress().getAddress())
                    .port(server.getAddress().getPort())
                    .path(TEST_CONTEXT)
                    .toURL();

            conn = (HttpURLConnection) url.openConnection();
            conn.setChunkedStreamingMode(8); // ensures the call to HttpURLConnection.streaming() passes

            int firstConnectTimeout = 1234;
            HttpClient freshClient = HttpClient.New(url, Proxy.NO_PROXY, firstConnectTimeout, true, conn);
            freshClient.closeServer(); // ensures that the call to HttpClient.available() fails

            httpClientAccess.setInCache(freshClient, true); // allows the assertion in HttpClient.New to pass

            // Injecting a mock KeepAliveCache that the HttpClient can use
            KeepAliveCache kac = httpClientAccess.getKeepAliveCache();
            kac.put(url, null, freshClient);

            // The 'new' keyword is important here as the original code
            // used '==' rather than String.equals to compare request methods
            conn.setRequestMethod(new String("POST"));

            // Before the fix, the value returned to 'cachedClient' would have been the (broken) cached
            // 'freshClient' as HttpClient.available() could never be checked
            int secondConnectTimeout = 4321;
            HttpClient cachedClient = HttpClient.New(url, Proxy.NO_PROXY, secondConnectTimeout, true, conn);
            cachedClient.closeServer();

            int originalConnectTimeout = freshClient.getConnectTimeout();
            int cachedConnectTimeout = cachedClient.getConnectTimeout();

            // If both connectTimeout values are equal, it means the test retrieved the same broken
            // HttpClient from the cache and is trying to re-use it.
            Assert.assertNotEquals(originalConnectTimeout, cachedConnectTimeout, "Both connectTimeout values are equal.\nThis means the test is reusing a broken HttpClient rather than creating a new one.");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpServer createServer(final HttpHandler handler) throws IOException {
        final InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        final int backlog = -1;
        final HttpServer server = HttpServer.create(serverAddress, backlog);
        server.createContext(TEST_CONTEXT, handler);
        server.start();
        System.out.println("Server started on " + server.getAddress());
        return server;
    }

    private static class CustomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // We'll always send 200 OK - We don't care about the server logic
            exchange.sendResponseHeaders(200, 1);
            exchange.close();
        }
    }
}
