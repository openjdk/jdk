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


import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.Utils;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8304065
 * @summary HttpServer.stop() should terminate immediately if no exchanges are in progress,
 *          else terminate after a timeout
 * @library /test/lib
 * @run junit/othervm -Djdk.internal.httpclient.debug=err ServerStopTerminationTest
 */

public class ServerStopTerminationTest {

    // enabling logging for the http server
    private final static Logger logger = Logger.getLogger("com.sun.net.httpserver");

    static {
        logger.setLevel(Level.FINEST);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.FINEST);
    }

    // The server instance used to test shutdown timing
    private HttpServer server;
    // Client for initiating exchanges
    private HttpClient client;
    // Allows test to await the start of the exchange
    private final CountDownLatch start = new CountDownLatch(1);
    // Allows test to signal when exchange should complete
    private final CountDownLatch complete = new CountDownLatch(1);

    @BeforeEach
    public void setup() throws IOException {

        // Create an HttpServer binding to the loopback address with an ephemeral port
        server = HttpServer.create();
        final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        server.bind(
                new InetSocketAddress(loopbackAddress, 0),
                0);

        // A handler with completion timing controlled by tests
        server.createContext("/", exchange -> {
            // Let the test know the exchange is started
            start.countDown();
            try {
                // Wait for test to signal that we can complete the exchange
                complete.await();
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } catch (final InterruptedException e) {
                throw new IOException(e);
            }
        });

        // Start server and client
        server.start();
        client = HttpClient.newBuilder().build();
    }

    /**
     * Clean up resources used by this test
     */
    @AfterEach
    public void cleanup() {
        client.shutdown();
    }

    /**
     * Verify that a stop operation with a 1-second exchange and a 2-second delay
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
        log("Exchange started");

        // Complete the exchange one second into the future
        final Duration exchangeDuration = Duration.ofSeconds(1);
        completeExchange(exchangeDuration);
        log("Complete Exchange triggered");

        // Time the shutdown sequence
        final Duration delayDuration = Duration.ofSeconds(Utils.adjustTimeout(5));
        log("Shutdown triggered with the delay of " + delayDuration.getSeconds());
        final long elapsed = timeShutdown(delayDuration);
        log("Shutdown complete");

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
     * Verify that a stop operation with a 1-second delay and a 10-second exchange
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
        log("Exchange started");

        // Complete the exchange 10 second into the future.
        // Runs in parallel, so won't block the server stop
        final Duration exchangeDuration = Duration.ofSeconds(Utils.adjustTimeout(10));
        completeExchange(exchangeDuration);
        log("Complete Exchange triggered");


        // Time the shutdown sequence
        final Duration delayDuration = Duration.ofSeconds(1);
        log("Shutdown triggered with the delay of " + delayDuration.getSeconds());
        final long elapsed = timeShutdown(delayDuration);
        log("Shutdown complete");


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
     * Verify that a stop operation with a 1-second delay and a 20-second executor
     * completes after the delay expires.
     *
     * @throws InterruptedException if an unexpected interruption occurs
     */
    @Test
    public void shouldCompeteAfterDelayCustomHandler() throws IOException, InterruptedException {

        // Custom server setup to include the executor
        log("Changing the server to the server with a custom executor");
        // Create an HttpServer binding
        final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopbackAddress, 0),
                0,
                "/", exchange -> {
                    exchange.sendResponseHeaders(200, 0);
                    exchange.close();
                });
        final Duration executorSleepTime = Duration.ofSeconds(Utils.adjustTimeout(20));
        server.setExecutor(command -> Thread.ofVirtual().start(() -> {
            try {
                // Let the test know the executor was triggered
                start.countDown();
                log("Custom executor started, sleeping");
                // waiting in the executor stage before running the exchange
                Thread.sleep(executorSleepTime);
                log("Custom executor sleep complete");
                command.run();// start the exchange

            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
        // Start server and client
        server.start();
        log("Custom setup complete");

        // Initiate an exchange
        startExchange();
        // Wait for the server to start the executor
        start.await();
        log("Exchange (Executor) started");

        // Time the shutdown sequence
        final Duration delayDuration = Duration.ofSeconds(1);
        log("Shutdown triggered with the delay of " + delayDuration.getSeconds());
        final long elapsed = timeShutdown(delayDuration);
        log("Shutdown complete");

        // The shutdown should not await the exchange to complete
        if (elapsed >= executorSleepTime.toNanos()) {
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
        final Duration delayDuration = Duration.ofSeconds(Utils.adjustTimeout(5));
        final long elapsed = timeShutdown(delayDuration);
        log("Shutting down the server with no exchanges");
        if (elapsed >= delayDuration.toNanos()) {
            fail("Expected HttpServer.stop to terminate immediately with no active exchanges");
        }
    }

    /**
     * Verify that an already stopped HttpServer can be stopped
     */
    @Test
    public void shouldAllowRepeatedStop() {
        final Duration delayDuration = Duration.ofSeconds(1);
        log("Shutting down the server the first time");
        timeShutdown(delayDuration);
        log("Shutting down the server the second time");
        timeShutdown(delayDuration);
    }

    /**
     * Run HttpServer::stop with the given delay, returning the
     * elapsed time in nanoseconds for the shutdown to complete
     */
    private long timeShutdown(Duration delayDuration) {
        final long startTime = System.nanoTime();

        server.stop((int) delayDuration.toSeconds());
        return System.nanoTime() - startTime;
    }

    /**
     * Initiate an exchange asynchronously
     */
    private void startExchange() {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URIBuilder.newBuilder()
                            .scheme("http")
                            .loopback()
                            .port(server.getAddress().getPort())
                            .build())
                    // We need to use POST to prevent retries
                    .POST(HttpRequest.BodyPublishers.ofString(""))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenCompleteAsync((r, t) -> {
                        System.out.println("request completed (" + r + ", " + t + ")");
                        // count the latch down to allow the handler to complete
                        // and the server's dispatcher thread to proceed; The handler
                        // is called within the dispatcher thread since we haven't
                        // set any executor on the server side
                        complete.countDown();
                    });
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
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

    /**
     * This logging method will log the name of the method which called the log
     * for easier debug
     *
     * @param message message to include in the log
     */
    private void log(final String message) {

        String filename = "";
        try {
            filename = Thread.currentThread()
                    .getStackTrace()[2].getMethodName();
        } finally {
            System.out.printf("{%s}: %s%n", filename, message);
        }
    }
}
