/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8295785
 * @summary Verifies that configuring `jdk.httpserver.maxConnections` to a value
 *          greater than 0 (5 in this case) results in server rejecting the
 *          new incoming connections when this limit is exceeded.
 *
 * @run junit/othervm
 *      -Djdk.httpserver.maxConnections=5
 *      ${test.main.class}
 */

class MaxConnectionsPropertyTest {

    /**
     * The {@code com.sun.net.httpserver} logger anchor to avoid getting it garbage-collected.
     */
    private static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    static {
        boolean enableLogging = System.getProperty("test.enableLogging") != null;
        if (enableLogging) {
            LOGGER.setLevel(Level.ALL);     // 0. Set `HttpServer`'s logger to `ALL`
            Logger.getLogger("")            // 1. Get the root logger
                    .getHandlers()[0]       // 2. Get its first handler (by default it's a `ConsoleHandler`)
                    .setLevel(Level.ALL);   // 3. Sets its level to `ALL` (by default it's `INFO`)
        }
    }

    @Test
    void test() throws Exception {

        // Read the expected idle interval
        int maxConnectionCount = Integer.getInteger("jdk.httpserver.maxConnections");

        // Create the executor that will be used to execute (blocking!) requests
        var threadFactory = Thread.ofPlatform()
                .name(MaxConnectionsPropertyTest.class.getSimpleName() + '-', 0)
                .factory();
        try (var executor = Executors.newThreadPerTaskExecutor(threadFactory)) {

            // Create the HTTP server
            var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            var serverHandlerIncomingRequestRegistry = new LinkedBlockingQueue<>();
            var serverHandlerUnblockSignal = new CountDownLatch(1);
            var serverHandler = new ActionRunningHandler(
                    () -> serverHandlerIncomingRequestRegistry.add("new-request"),
                    serverHandlerUnblockSignal::await);
            server.createContext("/", serverHandler);
            // The default server executor runs inline on the dispatcher thread.
            // So the first handled request can block the accept loop before the
            // test reliably reaches MAX+1 accepted connections. Use an external
            // multithreaded executor to avoid that.
            server.setExecutor(executor);
            server.start();

            try {

                // Create MAX+1 sockets
                var sockets = IntStream
                        .range(0, maxConnectionCount + 1)
                        .mapToObj(_ -> new Socket())
                        .toList();
                try {

                    // Create MAX connections and exhaust the server's connection pool capacity
                    LOGGER.info("Creating MAX connections");
                    for (int socketIndex = 0; socketIndex < maxConnectionCount; socketIndex++) {
                        connectAndWriteRequest(
                                server.getAddress(),
                                sockets.get(socketIndex),
                                "/?socket=" + socketIndex);
                    }

                    // Verify that MAX connections are handled by the server
                    LOGGER.info("Waiting for handler to receive MAX connections");
                    for (int socketIndex = 0; socketIndex < maxConnectionCount; socketIndex++) {
                        serverHandlerIncomingRequestRegistry.take();
                    }

                    // Verify that the next request attempt fails
                    LOGGER.info("Issuing one last request exceeding the max. connections limit");
                    var lastSocket = sockets.get(maxConnectionCount);
                    assertThrows(SocketException.class, () -> {
                        connectAndWriteRequest(server.getAddress(), lastSocket, "/?socket=last");
                        if (lastSocket.getInputStream().read() == -1) {
                            // `assertThrows` expects `SocketException` to verify disconnect.
                            // Use `SocketException` to signal EOF.
                            throw new SocketException("EOF");
                        }
                    });

                } finally {

                    // Close client sockets
                    for (Socket socket : sockets) {
                        try {
                            socket.close();
                        } catch (IOException _) {
                            // Do nothing
                        }
                    }

                    // Unblock the server handler
                    serverHandlerUnblockSignal.countDown();

                }

            } finally {
                server.stop(0);
            }

        }

    }

    private static void connectAndWriteRequest(
            InetSocketAddress serverAddress, Socket socket, String requestTarget)
            throws IOException {
        socket.connect(serverAddress);
        var socketOutput = socket.getOutputStream();
        socketOutput.write("GET %s HTTP/1.1\r\n\r\n".formatted(requestTarget).getBytes(US_ASCII));
        socketOutput.flush();
    }

    private static final class ActionRunningHandler implements HttpHandler {

        private final ThrowingRunnable[] handlerActions;

        private ActionRunningHandler(ThrowingRunnable... handlerActions) {
            this.handlerActions = handlerActions;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LOGGER.info("Received request (target=%s)".formatted(exchange.getRequestURI()));
            try (exchange) {
                try {
                    for (var handlerAction : handlerActions) {
                        handlerAction.run();
                    }
                } catch (Exception exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new IOException(exception);
                }
            }
        }

    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;

    }

}
