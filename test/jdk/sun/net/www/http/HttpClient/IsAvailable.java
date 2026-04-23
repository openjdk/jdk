/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8009650 8180483
 * @summary Verifies the `HttpClient::available` behavior against several socket
 *          states.
 *
 *          A "streaming POST" is controlled by `HttpURLConnection::streaming`,
 *          which becomes `true` when the caller uses either
 *          `setFixedLengthStreamingMode()` or `setChunkedStreamingMode()`, and
 *          *then writes the request body*. Non-streaming mode uses
 *          `PosterOutputStream.java`, which buffers the body and makes replay
 *          possible. Whereas, streaming mode allows user to directly write to
 *          the socket, so the body is not safely replayable. For many ordinary
 *          (i.e., non-streaming) requests, if a connection reuse fails, the
 *          stack can often recover by opening a new connection and retrying;
 *          see the retry logic in `HttpClient`. OTOH, for a "streaming POST",
 *          retry is dangerous or impossible because the body may already be in
 *          flight and is not buffered for replay. Hence,
 *          `HttpClient::available` tries to check whether the cached socket is
 *          still usable by doing a 1ms `read()`. Though that probe is
 *          destructive: it can consume and strand one byte from the socket
 *          input stream. Hence, probe failures should result in removal of the
 *          connection.
 *
 * @modules java.base/sun.net
 *          java.base/sun.net.www.http:+open
 * @library /test/lib
 *
 * @comment `othervm` is required since the `HttpURLConnection` logger state is tainted.
 * @run junit/othervm ${test.main.class}
 */

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.ServerSocket;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.net.www.http.HttpClient;

import java.util.function.Predicate;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import jdk.test.lib.net.URIBuilder;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static jdk.test.lib.Utils.adjustTimeout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsAvailable {

    private static final Logger LOGGER =
            Logger.getLogger(IsAvailable.class.getCanonicalName());

    // Class-level anchor to avoid the `HttpURLConnection` logger getting GC'ed
    private static final Logger HUC_LOGGER =
            Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");

    @BeforeAll
    static void init() {
        increaseLoggerVerbosity(HUC_LOGGER);
    }

    private static void increaseLoggerVerbosity(Logger logger) {
        logger.setLevel(Level.FINEST);
        var handler = new ConsoleHandler();
        handler.setLevel(Level.FINEST);
        logger.addHandler(handler);
    }

    @Test
    void testClosedSocket() throws Exception {
        try (var infra = new Infra()) {

            // Obtain the initial read timeout
            int readTimeout = infra.readTimeout();

            // Verify that the just established connection is available
            LOGGER.info("Checking the connection (#1)...");
            assertTrue(infra.available(), "Freshly established connection should be available");
            assertEquals(readTimeout, infra.httpClient.getReadTimeout(), "Read-timeout should be restored");

            // Verify that closing the socket removes the availability
            LOGGER.info("Closing the socket...");
            infra.clientSocket.close();
            LOGGER.info("Checking the connection (#2)...");
            assertFalse(infra.available(), "Connection over closed socket should not be available");
            assertEquals(readTimeout, infra.httpClient.getReadTimeout(), "Read-timeout should be restored");

        }
    }

    @Test
    void testSocketWithUnconsumedData() throws Exception {
        try (var infra = new Infra()) {

            // Obtain the initial read timeout
            int readTimeout = infra.readTimeout();

            // Verify that the just established connection is available
            LOGGER.info("Checking the connection (#1)...");
            assertTrue(infra.available(), "Freshly established connection should be available");
            assertEquals(readTimeout, infra.httpClient.getReadTimeout(), "Read-timeout should be restored");

            // Write (unexpected) data to the socket
            LOGGER.info("Writing data to the socket...");
            try (var clientSocketOutputStream = infra.clientSocket.getOutputStream()) {
                clientSocketOutputStream.write("unexpected data".getBytes(US_ASCII));
            }

            // Writing to the socket on the server side may not make the data
            // immediately visible to the client side. Make sure we wait long
            // enough for the data to get delivered.
            Thread.sleep(adjustTimeout(500));

            // Verify that the presence of stale data on the socket removes the availability
            LOGGER.info("Checking the connection (#2)...");
            assertFalse(infra.available(), "available should be false if it managed to read some data from the socket");
            assertEquals(readTimeout, infra.httpClient.getReadTimeout(), "Read-timeout should be restored");

        }
    }

    private static final class Infra implements Closeable {

        private static final InetAddress LOOPBACK_ADDRESS = InetAddress.getLoopbackAddress();

        private static final Predicate<HttpClient> AVAILABLE_ACCESSOR = findAvailableAccessor();

        private static Predicate<HttpClient> findAvailableAccessor() {
            final MethodHandle availableMH;
            try {
                availableMH = MethodHandles
                        .privateLookupIn(HttpClient.class, MethodHandles.lookup())
                        .findVirtual(HttpClient.class, "available", MethodType.methodType(boolean.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            return httpClient -> {
                try {
                    return (boolean) availableMH.invoke(httpClient);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            };
        }

        private final ServerSocket serverSocket;

        private final HttpClient httpClient;

        private final Socket clientSocket;

        private Infra() throws Exception {
            this.serverSocket = new ServerSocket(0, 0, LOOPBACK_ADDRESS);
            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(serverSocket.getLocalPort())
                    .toURL();
            this.httpClient = HttpClient.New(url);
            this.clientSocket = serverSocket.accept();
        }

        private int readTimeout() {
            int readTimeout = httpClient.getReadTimeout();
            assertNotEquals(
                    1, readTimeout,
                    "When the read timeout is 1, " +
                            "we cannot validate whether \"available()\" has restored that value or not, " +
                            "since \"available()\" temporarily sets it to 1 as well");
            return readTimeout;
        }

        private boolean available() {
            return AVAILABLE_ACCESSOR.test(httpClient);
        }

        @Override
        public void close() {
            closeQuietly("client socket", clientSocket);
            closeQuietly("HttpClient", httpClient::closeServer);
            closeQuietly("server socket", serverSocket);
        }

        private static void closeQuietly(String name, Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    LOGGER.warning("Failed closing " + name + ": " + e);
                }
            }
        }

    }

}
