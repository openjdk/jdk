/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304963
 * @summary Connection should be reusable after HEAD request
 * @library /test/lib
 * @run junit HeadKeepAlive
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;

public class HeadKeepAlive
{

    public static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @Test
    void test() throws IOException, InterruptedException {
        HttpServer httpServer = startHttpServer();
        int port = httpServer.getAddress().getPort();
        try {
            URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path("/firstCall")
                .toURLUnchecked();
            HttpURLConnection uc = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
            uc.setRequestMethod("HEAD");
            int responseCode = uc.getResponseCode();
            assertEquals(200, responseCode, "First request should succeed");

            URL url2 = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(port)
                    .path("/secondCall")
                    .toURLUnchecked();
            HttpURLConnection uc2 = (HttpURLConnection)url2.openConnection(Proxy.NO_PROXY);
            uc2.setRequestMethod("HEAD");
            responseCode = uc2.getResponseCode();
            assertEquals(200, responseCode, "Second request should reuse connection");
        } finally {
            httpServer.stop(0);
        }
    }

    /**
     * Http Server
     */
    HttpServer startHttpServer() throws IOException {
        Handler outHandler = new StreamHandler(System.out,
                                 new SimpleFormatter());
        outHandler.setLevel(Level.FINEST);
        LOGGER.setLevel(Level.FINEST);
        LOGGER.addHandler(outHandler);
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        httpServer.createContext("/", new MyHandler());
        httpServer.start();
        return httpServer;
    }

    class MyHandler implements HttpHandler {

        volatile int port1;
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/firstCall")) {
                port1 = t.getRemoteAddress().getPort();
                System.out.println("First connection on client port = " + port1);
                // send response
                t.sendResponseHeaders(200, -1);
                // the connection should still be reusable
            } else if (path.equals("/secondCall")) {
                int port2 = t.getRemoteAddress().getPort();
                System.out.println("Second connection on client port = " + port2);
                if (port1 == port2) {
                    t.sendResponseHeaders(200, -1);
                } else {
                    t.sendResponseHeaders(500, -1);
                }
            }
            t.close();
        }
    }
}
