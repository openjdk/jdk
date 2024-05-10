/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6968351
 * @summary  tcp no delay not required for small payloads
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib
 * @run main/othervm/timeout=5 -Dsun.net.httpserver.nodelay=false  TcpNoDelayNotRequired
 */

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public class TcpNoDelayNotRequired {

    public static final Logger LOGGER = Logger.getLogger("sun.net.www.protocol.http");

    public static void main (String[] args) throws Exception {

        java.util.logging.Handler outHandler = new StreamHandler(System.out,
                new SimpleFormatter());
        outHandler.setLevel(Level.FINEST);
        LOGGER.setLevel(Level.FINEST);
        LOGGER.addHandler(outHandler);

        InetAddress loopback = InetAddress.getLoopbackAddress();
        InetSocketAddress addr = new InetSocketAddress (loopback, 0);

        SSLContext sslContext = new SimpleSSLContext().get();

        HttpServer httpServer = HttpServer.create (addr, 0);
        testHttpServer("http",httpServer,sslContext);

        HttpsServer httpsServer = HttpsServer.create (addr, 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));

        testHttpServer("https",httpsServer,sslContext);
    }

    private static void testHttpServer(String scheme,HttpServer server,SSLContext sslContext) throws Exception {
        HttpContext ctx = server.createContext ("/test", new Handler());
        HttpContext ctx2 = server.createContext ("/chunked", new ChunkedHandler());
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();
        try {
            try (HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build()) {
                long start = System.currentTimeMillis();
                for (int i = 0; i < 1000; i++) {
                    var uri = URIBuilder.newBuilder().scheme(scheme).loopback().port(server.getAddress().getPort()).path("/test").build();
                    var response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
                    if (!response.body().equals("hello"))
                        throw new IllegalStateException("incorrect body " + response.body());
                }
                for (int i = 0; i < 1000; i++) {
                    var uri = URIBuilder.newBuilder().scheme(scheme).loopback().port(server.getAddress().getPort()).path("/chunked").build();
                    var response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
                    if (!response.body().equals("hello"))
                        throw new IllegalStateException("incorrect body " + response.body());
                }
                long time = System.currentTimeMillis() - start;
                System.out.println("time " + time);
            }
        } finally {
            server.stop(0);
        }
        executor.shutdown();
    }

    static class Handler implements HttpHandler {
        public void handle (HttpExchange t)
                throws IOException
        {
            Headers rmap = t.getResponseHeaders();
            try (var is = t.getRequestBody()) {
                is.readAllBytes();
            }
            rmap.add("content-type","text/plain");
            t.sendResponseHeaders(200,5);
            try (var os = t.getResponseBody()) {
                os.write("hello".getBytes(StandardCharsets.ISO_8859_1));
            }
        }
    }
    static class ChunkedHandler implements HttpHandler {
        public void handle (HttpExchange t)
                throws IOException
        {
            Headers rmap = t.getResponseHeaders();
            try (var is = t.getRequestBody()) {
                is.readAllBytes();
            }
            rmap.add("content-type","text/plain");
            t.sendResponseHeaders(200,0);
            try (var os = t.getResponseBody()) {
                os.write("hello".getBytes(StandardCharsets.ISO_8859_1));
            }
        }
    }
}
