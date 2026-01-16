/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2Handler;


import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @test
 * @bug 8292876
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.http2.Http2TestExchange
 * @compile ../ReferenceTracker.java
 * @run junit UserInfoTest
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserInfoTest {

    static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    Http2TestServer server;
    int port;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();

    @BeforeAll
    void before() throws Exception {
        server = createServer(sslContext);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterAll
    void after() throws Exception {
        server.close();
    }

    static class Http2TestHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange e) throws IOException {
            String authorityHeader = e.getRequestHeaders().firstValue(":authority").orElse(null);
            if (authorityHeader == null || authorityHeader.contains("user@")) {
                e.sendResponseHeaders(500, -1);
            } else {
                e.sendResponseHeaders(200, -1);
            }
        }
    }

    private static Http2TestServer createServer(SSLContext sslContext) throws Exception {
        Http2TestServer http2TestServer = new Http2TestServer("localhost", true, sslContext);
        Http2TestHandler handler = new Http2TestHandler();
        http2TestServer.addHandler(handler, "/");
        return http2TestServer;
    }

    @Test
    public void testAuthorityHeader() throws Exception {
        HttpClient client = HttpClient
                .newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .build();
        TRACKER.track(client);

        URI uri = URIBuilder.newBuilder()
                .scheme("https")
                .userInfo("user")
                .loopback()
                .port(port)
                .build();

        HttpRequest request = HttpRequest
                .newBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Test Failed : " + response.uri().getAuthority());

        client = null;
        System.gc();
        var error = TRACKER.check(500);
        if (error != null) throw error;
    }
}
