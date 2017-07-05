/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

/**
 * @test
 * @bug 8153142
 * @run main/othervm HeadersTest1
 * @summary HeadersTest1
 */

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class HeadersTest1 {

    final static String RESPONSE = "Hello world";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 10);
        ExecutorService e = Executors.newCachedThreadPool();
        Handler h = new Handler();
        HttpContext serverContext = server.createContext("/test", h);
        int port = server.getAddress().getPort();
        System.out.println("Server port = " + port);

        server.setExecutor(e);
        server.start();
        HttpClient client = HttpClient.create()
                                      .build();

        try {
            URI uri = new URI("http://127.0.0.1:" + Integer.toString(port) + "/test/foo");
            HttpRequest req = client.request(uri)
                .headers("X-Bar", "foo1")
                .headers("X-Bar", "foo2")
                .GET();

            HttpResponse resp = req.response();
            if (resp.statusCode() != 200)
                throw new RuntimeException("Test failed: status code");
            HttpHeaders hd = resp.headers();
            List<String> v = hd.allValues("X-Foo-Response");
            if (!v.contains("resp1"))
                throw new RuntimeException("Test failed: resp1");
            if (!v.contains("resp2"))
                throw new RuntimeException("Test failed: resp2");

        } finally {
            client.executorService().shutdownNow();
            server.stop(0);
            e.shutdownNow();
        }
        System.out.println("OK");
    }

   static class Handler implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            String method = he.getRequestMethod();
            InputStream is = he.getRequestBody();
            List<String> l = he.getRequestHeaders().get("X-Bar");
            if (!l.contains("foo1") || !l.contains("foo2")) {
                for (String s : l)
                    System.out.println("HH: " + s);
                he.sendResponseHeaders(500, -1);
                he.close();
                return;
            }
            Headers h = he.getResponseHeaders();
            h.add("X-Foo-Response", "resp1");
            h.add("X-Foo-Response", "resp2");
            he.sendResponseHeaders(200, RESPONSE.length());
            OutputStream os = he.getResponseBody();
            os.write(RESPONSE.getBytes(US_ASCII));
            os.close();
        }

   }
}
