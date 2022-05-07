/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress test the HTTP protocol handler and HTTP server
 * @requires vm.debug != true
 * @modules jdk.httpserver
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} HttpALot.java
 * @run main/othervm/timeout=600
 *     --enable-preview
 *     -Dsun.net.client.defaultConnectTimeout=5000
 *     -Dsun.net.client.defaultReadTimeout=5000
 *     HttpALot
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;

public class HttpALot {

    public static void main(String[] args) throws Exception {
        int requests = 25_000;
        if (args.length > 0) {
            requests = Integer.parseInt(args[0]);
        }

        AtomicInteger requestsHandled = new AtomicInteger();

        // Create HTTP on the loopback address. The server will reply to
        // the requests to GET /hello. It uses virtual threads.
        InetAddress lb = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(lb, 0), 1024);
        ThreadFactory factory = Thread.ofVirtual().factory();
        server.setExecutor(Executors.newThreadPerTaskExecutor(factory));
        server.createContext("/hello", e -> {
            byte[] response = "Hello".getBytes("UTF-8");
            e.sendResponseHeaders(200, response.length);
            try (OutputStream out = e.getResponseBody()) {
                out.write(response);
            }
            requestsHandled.incrementAndGet();
        });

        // URL for hello service
        URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path("/hello")
                .toURL();

        // go
        server.start();
        try {
            factory = Thread.ofVirtual().name("fetcher-", 0).factory();
            try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
                for (int i = 1; i <= requests; i++) {
                    executor.submit(() -> fetch(url)).get();
                }
            }
        } finally {
            server.stop(1);
        }

        if (requestsHandled.get() < requests) {
            throw new RuntimeException(requestsHandled.get() + " handled, expected " + requests);
        }
    }

    private static String fetch(URL url) throws IOException {
        try (InputStream in = url.openConnection(Proxy.NO_PROXY).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            return new String(bytes, "UTF-8");
        }
    }
}
