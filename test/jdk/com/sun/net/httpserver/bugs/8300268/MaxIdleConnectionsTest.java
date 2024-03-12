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
 * @bug 8300268
 * @library /test/lib
 * @modules jdk.httpserver/sun.net.httpserver
 * @build jdk.httpserver/sun.net.httpserver.HttpServerAccess MaxIdleConnectionsTest
 * @run junit/othervm -Dsun.net.httpserver.maxIdleConnections=4 MaxIdleConnectionsTest
 */

import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import sun.net.httpserver.HttpServerAccess;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MaxIdleConnectionsTest {

    HttpServer server;
    int maxIdleConnections, totalConnections;
    CountDownLatch reqFinishedProcessing;

    @BeforeAll
    void before() throws Exception {
        maxIdleConnections = Integer.getInteger("sun.net.httpserver.maxIdleConnections");
        totalConnections = maxIdleConnections + 1;
        reqFinishedProcessing = new CountDownLatch(totalConnections);
        server = startServer(reqFinishedProcessing);
    }

    @AfterAll
    void after() throws Exception {
        server.stop(0);
    }

    // Issue one too many requests and assert that the idle connection pool doesn't
    // exceed maxIdleConnections
    @Test
    public void test() throws Exception {
        final int port = server.getAddress().getPort();

        final List<Future<Void>> responses = new ArrayList<>();
        try (final ExecutorService requestIssuer = Executors.newFixedThreadPool(totalConnections)) {
            for (int i = 1; i <= totalConnections; i++) {
                URL requestURL = URIBuilder.newBuilder()
                        .scheme("http")
                        .loopback()
                        .port(port)
                        .path("/MaxIdleConnectionTest/" + i)
                        .toURL();
                final Future<Void> result = requestIssuer.submit(() -> {
                    System.out.println("Issuing request " + requestURL);
                    final URLConnection conn = requestURL.openConnection();
                    try (final InputStream is = conn.getInputStream()) {
                        is.readAllBytes();
                    }
                    return null;
                });
                responses.add(result);
            }
            // wait for all the requests to reach each of the handlers
            System.out.println("Waiting for all " + totalConnections + " requests to reach" +
                    " the server side request handler");
            reqFinishedProcessing.await();
        }

        // verify every request got served before checking idle count
        for (int i = 0; i < totalConnections; i++) {
            responses.get(i).get();
            System.out.println("Received successful response for request " + i);
        }

        // assert that the limit set by maxIdleConnections was not exceeded
        int idleConnectionCount = HttpServerAccess.getIdleConnectionCount(server);
        System.out.println("count " + idleConnectionCount);
        assertTrue(maxIdleConnections >= idleConnectionCount,
                String.format("Too many idle connections: %d, limit: %d", idleConnectionCount, maxIdleConnections));
    }

    // Create HttpServer that will handle requests with multiple threads
    private static HttpServer startServer(final CountDownLatch reqFinishedProcessing) throws IOException {
        final var bindAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        final HttpServer server = HttpServer.create(bindAddr, 0);

        final AtomicInteger threadId = new AtomicInteger();
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            final Thread t = new Thread(r);
            t.setName("http-request-handler-" + threadId.incrementAndGet());
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/MaxIdleConnectionTest/", (exchange) -> {
            System.out.println("Request " + exchange.getRequestURI() + " received");
            System.out.println("Sending response for request " + exchange.getRequestURI() + " from " + exchange.getRemoteAddress());
            reqFinishedProcessing.countDown();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Server started at address " + server.getAddress());
        return server;
    }
}
