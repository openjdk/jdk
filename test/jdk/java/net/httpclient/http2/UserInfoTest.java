/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


/**
 * @test
 * @bug 8292876
 * @library /test/lib server
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 * @run junit UserInfoTest
 */

public class UserInfoTest {

    static class Http2TestHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange e) throws IOException {
            InputStream is = e.getRequestBody();
            StringBuilder sb = new StringBuilder();
            for (int ch; (ch = is.read()) != -1; ) {
                sb.append((char) ch);
            }
            is.readAllBytes();
            is.close();
            if (e.getRequestURI().getAuthority().contains("user@")) {
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

    public void main() throws Exception {
        SSLContext sslContext = new SimpleSSLContext().get();
        Http2TestServer server = createServer(sslContext);
        int port = server.getAddress().getPort();

        server.start();

        HttpClient client = HttpClient
                .newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .build();

        URI uri = URIBuilder.newBuilder()
                .userInfo("user")
                .loopback()
                .port(port)
                .buildUnchecked();

        HttpRequest request = HttpRequest
                .newBuilder(uri)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Test Failed : " + response.uri().getAuthority());
            }
        } finally {
            server.stop();
        }
    }
}
