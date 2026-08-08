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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxRspTime` is configured to 3 seconds.
 *
 * @comment `sun.net.httpserver.timerMillis` determines the frequency the
 *          timeout check mechanism is triggered. Up its pace to increase
 *          the timeout check responsiveness.
 * @run junit/othervm
 *      -Dsun.net.httpserver.timerMillis=200
 *      -Dsun.net.httpserver.maxRspTime=3
 *      -Dtest.toleratedPauseDurationMillis=1000
 *      -Dtest.excessivePauseDurationMillis=5000
 *      ${test.main.class}
 */

class MaxRspTimePropertyTest {

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

    /**
     * The wait duration that will be tolerated by the server.
     */
    private static final Duration TOLERATED_PAUSE_DURATION =
            Duration.ofMillis(Integer.getInteger("test.toleratedPauseDurationMillis"));

    /**
     * The wait duration that will not be tolerated by the server and cause termination of the connection.
     */
    private static final Duration EXCESSIVE_PAUSE_DURATION =
            Duration.ofMillis(Integer.getInteger("test.excessivePauseDurationMillis"));

    private static final Charset CHARSET = StandardCharsets.US_ASCII;

    @ParameterizedTest
    @EnumSource(PauseLocation.class)
    void testToleratedPause(PauseLocation pauseLocation) throws Exception {
        var server = createServer(pauseLocation, TOLERATED_PAUSE_DURATION);
        try {
            sendRequest(server.getAddress());
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @EnumSource(PauseLocation.class)
    void testExcessivePause(PauseLocation pauseLocation) throws IOException {
        var server = createServer(pauseLocation, EXCESSIVE_PAUSE_DURATION);
        try {
            assertThrows(SocketException.class, () -> sendRequest(server.getAddress()));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer createServer(
            PauseLocation pauseLocation, Duration pauseDuration)
            throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", new PausingHandler(pauseLocation, pauseDuration));
        server.start();
        return server;
    }

    private static void sendRequest(InetSocketAddress serverAddress) throws IOException {
        try (var clientSocket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {

            // Send the request
            LOGGER.info("Sending request");
            var socketOutput = clientSocket.getOutputStream();
            socketOutput.write("GET / HTTP/1.1\r\n\r\n".getBytes(CHARSET));
            socketOutput.flush();

            // Read the response status line
            LOGGER.info("Reading the response status line");
            var inputStream = clientSocket.getInputStream();
            assertEquals("HTTP/1.1 200 OK", readUntilCrLf(inputStream));

            // Read the response headers
            LOGGER.info("Reading the response headers");
            Map<String, String> actualHeaders = new HashMap<>();
            for (String headerLine; !(headerLine = readUntilCrLf(inputStream)).isEmpty(); ) {
                var headerLineFields = headerLine.split(":", 2);
                var headerName = headerLineFields[0].trim();
                // Skip `Date`, unnecessary and difficult to compare
                if ("Date".equals(headerName)) {
                    continue;
                }
                var headerValue = headerLineFields[1].trim();
                actualHeaders.put(headerName, headerValue);
            }

            // Verify the response headers
            Map<String, String> expectedHeaders = new HashMap<>(PausingHandler.HEADERS);
            var expectedBodyBytes = PausingHandler.BODY_BYTES;
            expectedHeaders.put("Content-length", "" + expectedBodyBytes.length);
            assertEquals(expectedHeaders, actualHeaders);

            // Read the response body
            LOGGER.info("Reading the response body");
            var actualBodyBytes = new byte[expectedBodyBytes.length];
            for (int bodyByteIndex = 0; bodyByteIndex < actualBodyBytes.length; bodyByteIndex++) {
                int expectedBodyByte = expectedBodyBytes[bodyByteIndex] & 0xFF;
                int actualBodyByte = inputStream.read();
                if (actualBodyByte == -1) {
                    // `SocketException` is caught to detect the peer disconnect
                    throw new SocketException("EOF");
                }
                assertEquals(
                        expectedBodyByte, actualBodyByte,
                        "Body byte mismatch (byteIndex=%s)".formatted(bodyByteIndex));
            }

        }
    }

    private static String readUntilCrLf(InputStream inputStream) throws IOException {
        var buffer = new StringBuilder();
        var prevChar = -1;
        while (true) {
            int nextChar = inputStream.read();
            if (nextChar < 0) {
                // `SocketException` is caught to detect the peer disconnect
                throw new SocketException("EOF");
            }
            buffer.append((char) nextChar);
            if (prevChar == '\r' && nextChar == '\n') {
                break;
            }
            prevChar = nextChar;
        }
        // Drop CRLF
        buffer.setLength(buffer.length() - 2);
        return buffer.toString();
    }

    private static final class PausingHandler implements HttpHandler {

        private static final byte[] BODY_BYTES = """
                His mind was empty, but somehow that void demanded all his
                attention. Emptiness, he discovered, wants everything for
                itself — it takes the fraction of an atom (or the flicker
                of a thought) to put an end to a universal void.
                """.getBytes(CHARSET);

        private static final Map<String, String> HEADERS = Map.of(
                "X-title", "In the Distance",
                "X-author", "Hernan Diaz");

        private final PauseLocation pauseLocation;

        private final Duration pauseDuration;

        private PausingHandler(PauseLocation pauseLocation, Duration pauseDuration) {
            this.pauseLocation = pauseLocation;
            this.pauseDuration = pauseDuration;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LOGGER.info("Received request");
            try (exchange) {
                HEADERS.forEach(exchange.getResponseHeaders()::add);
                pauseIfLocationMatches(PauseLocation.BEFORE_SENDING_HEADERS);
                exchange.sendResponseHeaders(200, BODY_BYTES.length);
                pauseIfLocationMatches(PauseLocation.AFTER_SENDING_HEADERS);
                var responseBody = exchange.getResponseBody();
                int pauseIndex = BODY_BYTES.length / 2;
                assertTrue(pauseIndex > 0 && pauseIndex < BODY_BYTES.length);
                for (int bodyByteIndex = 0; bodyByteIndex < BODY_BYTES.length; bodyByteIndex++) {
                    responseBody.write(BODY_BYTES[bodyByteIndex] & 0xFF);
                    if (bodyByteIndex == pauseIndex) {
                        pauseIfLocationMatches(PauseLocation.WHILE_SENDING_BODY);
                    }
                }
            }
            LOGGER.info("Served request");
        }

        private void pauseIfLocationMatches(PauseLocation location) {
            if (pauseLocation.equals(location)) {
                try {
                    Thread.sleep(pauseDuration);
                } catch (InterruptedException ie) {
                    // Restore the interrupt
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }

    }

    private enum PauseLocation {

        BEFORE_SENDING_HEADERS,

        AFTER_SENDING_HEADERS,

        WHILE_SENDING_BODY

    }

}
