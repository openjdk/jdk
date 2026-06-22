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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxReqTime` is configured to 3 seconds.
 *
 * @comment `sun.net.httpserver.timerMillis` determines the frequency the
 *          timeout check mechanism is triggered. Up its pace to increase
 *          the timeout check responsiveness.
 * @run junit/othervm
 *      -Dsun.net.httpserver.timerMillis=200
 *      -Dsun.net.httpserver.maxReqTime=3
 *      -Dtest.tolaratedPauseDurationMillis=1000
 *      -Dtest.excessivePauseDurationMillis=5000
 *      ${test.main.class}
 */

class MaxReqTimePropertyTest {

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
            Duration.ofMillis(Integer.getInteger("test.tolaratedPauseDurationMillis"));

    /**
     * The wait duration that will not be tolerated by the server and cause termination of the connection.
     */
    private static final Duration EXCESSIVE_PAUSE_DURATION =
            Duration.ofMillis(Integer.getInteger("test.excessivePauseDurationMillis"));

    private static final HttpServer SERVER = createServer();

    private static HttpServer createServer() {
        final HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        server.createContext("/", NoContentReturningHandler.INSTANCE);
        server.start();
        return server;
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    private static final char PAUSE_DIRECTIVE = '!';

    private Socket clientSocket;

    @BeforeEach
    void openClientSocket() throws IOException {
        var serverAddress = SERVER.getAddress();
        this.clientSocket = new Socket(serverAddress.getAddress(), serverAddress.getPort());
    }

    @AfterEach
    void closeClientSocket() throws IOException {
        clientSocket.close();
    }

    static Object[][] args() {
        var p = PAUSE_DIRECTIVE;
        return new Object[][]{
                {
                        "pause at the beginning of the request line",
                        // Request timeout mechanism is armed right after the
                        // first socket state change, e.g., write/close by the
                        // client.
                        false,
                        p + "GET / HTTP/1.1\r\n\r\n"
                },
                {
                        "pause in the middle of the request line", true,
                        "GET " + p + "/ HTTP/1.1\r\n\r\n"
                },
                {
                        "pause right after the request line", true,
                        "GET / HTTP/1.1\r\n" + p + "\r\n"
                },
                {
                        "pause at a header field", true,
                        "GET / HTTP/1.1\r\n" +
                                "w: x\r\n" +
                                p + "y: z\r\n" +
                                "\r\n"
                },
                {
                        "pause at the beginning of the fixed body", true,
                        "POST / HTTP/1.1\r\n" +
                                "Content-Length: 1\r\n" +
                                "\r\n" +
                                p + "x"
                },
                {
                        "pause in the middle of the fixed body", true,
                        "POST / HTTP/1.1\r\n" +
                                "Content-Length: 2\r\n" +
                                "\r\n" +
                                "x" + p + "y"
                },
                {
                        "pause at the end of the fixed body",
                        // An excessive pause at the end of the body does not
                        // trip the timeout mechanism. Seeing the
                        // `Content-Length` header and the promised payload
                        // delivered, server marks the request processed.
                        false,
                        "POST / HTTP/1.1\r\n" +
                                "Content-Length: 1\r\n" +
                                "\r\n" +
                                "x" + p
                },
                {
                        "pause at the chunked body header line", true,
                        "POST / HTTP/1.1\r\n" +
                                "Transfer-Encoding: chunked\r\n" +
                                "\r\n" +
                                p + "3\r\n" +
                                "abc\r\n" +
                                "0\r\n\r\n"
                },
                {
                        "pause at the chunked body data line", true,
                        "POST / HTTP/1.1\r\n" +
                                "Transfer-Encoding: chunked\r\n" +
                                "\r\n" +
                                "3\r\n" +
                                p + "abc\r\n" +
                                "0\r\n\r\n"
                },
                {
                        "pause at the end of the chunked body zero chunk",
                        // An excessive pause at the end of the body does not
                        // trip the timeout mechanism. Seeing the zero chunk,
                        // server marks the request processed.
                        false,
                        "POST / HTTP/1.1\r\n" +
                                "Transfer-Encoding: chunked\r\n" +
                                "\r\n" +
                                "3\r\n" +
                                "abc\r\n" +
                                "0\r\n\r\n" + p
                },
        };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("args")
    void testToleratedPause(String description, boolean excessivePauseFails, String request)
            throws IOException, InterruptedException {
        sendRequest(clientSocket, request, TOLERATED_PAUSE_DURATION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("args")
    void testExcessivePause(String description, boolean excessivePauseFails, String request)
            throws IOException, InterruptedException {
        if (excessivePauseFails) {
            assertThrows(
                    SocketException.class,
                    () -> sendRequest(clientSocket, request, EXCESSIVE_PAUSE_DURATION));
        } else {
            sendRequest(clientSocket, request, EXCESSIVE_PAUSE_DURATION);
        }
    }

    private static void sendRequest(
            Socket clientSocket, String request, Duration pauseDuration)
            throws IOException, InterruptedException {
        LOGGER.info("Sending request");
        var socketOutput = clientSocket.getOutputStream();
        for (int i = 0; i < request.length(); i++) {
            var c = request.charAt(i);
            if (c == PAUSE_DIRECTIVE) {
                Thread.sleep(pauseDuration);
            } else {
                socketOutput.write(c);
            }
        }
        socketOutput.flush();
        var inputStream = clientSocket.getInputStream();
        assertEquals("HTTP/1.1 204 No Content", readUntilCrLf(inputStream));
        while (!readUntilCrLf(inputStream).isEmpty());
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

    private enum NoContentReturningHandler implements HttpHandler { INSTANCE;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LOGGER.info("Received request (target=%s)".formatted(exchange.getRequestURI()));
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(204, HttpExchange.RSPBODY_EMPTY);
            }
        }

    }

}
