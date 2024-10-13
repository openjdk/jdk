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
 * @bug 8219083
 * @summary HttpExchange.getResponseBody write and close should not throw
 *          even when response length is zero
 * @library /test/lib
 * @run junit ZeroLengthOutputStream
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;

public class ZeroLengthOutputStream
{

    public static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");
    public volatile boolean closed;
    public CountDownLatch cdl = new CountDownLatch(1);

    @Test
    void test() throws IOException, InterruptedException {
        HttpServer httpServer = startHttpServer();
        int port = httpServer.getAddress().getPort();
        try {
            URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path("/flis/")
                .toURLUnchecked();
            HttpURLConnection uc = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
            uc.getResponseCode();
            cdl.await();
            assertTrue(closed, "OutputStream close did not complete");
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
        httpServer.createContext("/flis/", new MyHandler());
        httpServer.start();
        return httpServer;
    }

    class MyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                OutputStream os = t.getResponseBody();
                t.sendResponseHeaders(200, -1);
                os.write(new byte[0]);
                os.close();
                System.out.println("Output stream closed");
                closed = true;
            } finally {
                cdl.countDown();
            }
        }
    }
}
