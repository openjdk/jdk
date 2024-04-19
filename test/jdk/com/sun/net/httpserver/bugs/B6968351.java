/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @run main B6968351
 * @run main/othervm -Dsun.net.httpserver.nodelay=false -Djdk.httpclient.HttpClient.log=all -Djava.net.preferIPv6Addresses=true -Djavax.net.debug=all B6968351
 */

import com.sun.net.httpserver.*;
import jdk.test.lib.net.URIBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class B6968351 {

    public static final Logger LOGGER = Logger.getLogger("sun.net.www.protocol.http");

    public static void main (String[] args) throws Exception {

        java.util.logging.Handler outHandler = new StreamHandler(System.out,
                new SimpleFormatter());
        outHandler.setLevel(Level.FINEST);
        LOGGER.setLevel(Level.FINEST);
        LOGGER.addHandler(outHandler);

        InetAddress loopback = InetAddress.getLoopbackAddress();
        InetSocketAddress addr = new InetSocketAddress (loopback, 0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpHandler handler = new Handler();
        HttpContext ctx = server.createContext ("/test", handler);
        HttpContext ctx2 = server.createContext ("/chunked", handler);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();

        HttpClient client = HttpClient.newBuilder().build();

        long start = System.currentTimeMillis();
        for(int i=0;i<1000;i++) {
            var uri = URIBuilder.newBuilder().scheme("http").port(server.getAddress().getPort()).path("/test").build();
            var response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            if(!response.body().equals("hello")) throw new IllegalStateException("incorrect body");
        }
        for(int i=0;i<1000;i++) {
            var uri = URIBuilder.newBuilder().scheme("http").port(server.getAddress().getPort()).path("/chunked").build();
            var response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            if(!response.body().equals("hello")) throw new IllegalStateException("incorrect body");
        }
        long time = System.currentTimeMillis()-start;
        System.out.println("time "+time);
        if(time>5000) throw new IllegalStateException("took too long");
        server.stop(0);
        executor.shutdown();
    }

    static class Handler implements HttpHandler {
        public void handle (HttpExchange t)
                throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            while (is.read () != -1) ;
            is.close();
            rmap.add("content-type","text/plain");
            t.sendResponseHeaders(200,5);
            t.getResponseBody().write("hello".getBytes(StandardCharsets.ISO_8859_1));
            t.getResponseBody().close();
        }
    }
    static class ChunkedHandler implements HttpHandler {
        public void handle (HttpExchange t)
                throws IOException
        {
            InputStream is = t.getRequestBody();
            Headers map = t.getRequestHeaders();
            Headers rmap = t.getResponseHeaders();
            while (is.read () != -1) ;
            is.close();
            rmap.add("content-type","text/plain");
            t.sendResponseHeaders(200,0);
            t.getResponseBody().write("hello".getBytes(StandardCharsets.ISO_8859_1));
            t.getResponseBody().close();
        }
    }
}
