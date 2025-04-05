/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @test
 * @bug 8304065
 * @summary HttpServer.stop() should terminate immediately if no exchanges are in progress
 * @run junit ServerStopTermination
 */

public class ServerStopTermination {

    // The server instance used to test shutdown timing
    private HttpServer server;
    // Client for initiating exchanges
    private HttpClient client;
    // Allows test to await the start of the exchange
    private CountDownLatch start = new CountDownLatch(1);
    // Allows test to signal when exchange should complete
    private CountDownLatch complete = new CountDownLatch(1);

    @BeforeEach
    public void setup() throws IOException {
        // Create an HttpServer binding to the loopback address with an ephemeral port
        server = HttpServer.create();
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        server.bind(new InetSocketAddress(loopbackAddress, 0), 0);

        // A handler with completion timing controlled by tests
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                // Let the test know the exchange is started
                start.countDown();
                try {
                    // Wait for test to signal that we can complete the exchange
                    complete.await();
                    exchange.sendResponseHeaders(200, 0);
                    exchange.close();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        });

        // Start server and client
        server.start();
        client = HttpClient.newHttpClient();
    }

    /**
     * Clean up resources used by this test
     */
    @AfterEach
    public void cleanup() {
        client.shutdown();
    }

    /**
     * Verify that a stop operation with a 1 second exchange and a 2 second delay
     * completes when the exchange completes.
     *
     * @throws InterruptedException if an unexpected interruption occurs
     */
    @Test
    public void shouldAwaitActiveExchange() throws InterruptedException {
        // Initiate an exchange
        startExchange();
        // Wait for the server to receive the exchange
        start.await();

        // Complete the exchange one second into the future
        Duration exchangeDuration = Duration.ofSeconds(1);
        completeExchange(exchangeDuration);

        // Time the shutdown sequence
        Duration delayDuration = Duration.ofSeconds(2);
        long elapsed = timeShutdown(delayDuration);

        // The shutdown should take at least as long as the exchange duration
        if (elapsed < exchangeDuration.toNanos()) {
            fail("HttpServer.stop terminated before exchange completed");
        }

        // The delay should not have expired
        if (elapsed >= delayDuration.toNanos()) {
            fail("HttpServer.stop terminated after delay expired");
        }
    }

    /**
     * Verify that a stop operation with a 1 second delay and a 2 second exchange
     * completes after the delay expires.
     *
     * @throws InterruptedException if an unexpected interruption occurs
     */
    @Test
    public void shouldCompeteAfterDelay() throws InterruptedException {
        // Initiate an exchange
        startExchange();
        // Wait for the server to receive the exchange
        start.await();

        // Complete the exchange two second into the future
        Duration exchangeDuration = Duration.ofSeconds(10);
        completeExchange(exchangeDuration);

        // Time the shutdown sequence
        Duration delayDuration = Duration.ofSeconds(1);
        long elapsed = timeShutdown(delayDuration);

        // The shutdown should not await the exchange to complete
        if (elapsed >= exchangeDuration.toNanos()) {
            fail("HttpServer.stop terminated too late");
        }

        // The shutdown delay should have expired
        if (elapsed < delayDuration.toNanos()) {
            fail("HttpServer.stop terminated before delay");
        }
    }

    /**
     * Verify that an HttpServer with no active exchanges terminates
     * before the delay timeout occurs.
     */
    @Test
    public void noActiveExchanges() {
        // With no active exchanges, shutdown should complete immediately
        Duration delayDuration = Duration.ofSeconds(2);
        long elapsed = timeShutdown(delayDuration);
        if (elapsed >= delayDuration.toNanos()) {
            fail("Expected HttpServer.stop to terminate immediately with no active exchanges");
        }
    }

    /**
     * Verify that an already stopped HttpServer can be stopped
     */
    @Test
    public void shouldAllowRepeatedStop() {
        Duration delayDuration = Duration.ofSeconds(1);
        long elapsed = timeShutdown(delayDuration);
        long elapsed2 = timeShutdown(delayDuration);
    }

    /**
     * Run HttpServer::stop with the given delay, returning the
     * elapsed time in nanoseconds for the shutdown to complete
     */
    private long timeShutdown(Duration delayDuration) {
        long before = System.nanoTime();
        server.stop((int) delayDuration.toSeconds());
        long elapsed = System.nanoTime() - before;
        return elapsed;
    }

    /**
     * Initiate an exchange asynchronously
     */
    private void startExchange() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + server.getAddress().getAddress().getHostAddress() +":" + server.getAddress().getPort() + "/"))
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * At the specified time into the future, signal to the
     * handler that the exchange can complete.
     *
     * @param exchangeDuration the duration to wait before signaling completion
     */
    private void completeExchange(Duration exchangeDuration) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(exchangeDuration);
                complete.countDown();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
