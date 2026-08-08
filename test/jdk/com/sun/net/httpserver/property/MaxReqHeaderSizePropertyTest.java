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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test id=default
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxReqHeaderSize` is not configured, and left to
 *          its default, 389120.
 *
 * @run junit/othervm ${test.main.class}
 */

/*
 * @test id=200
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxReqHeaderSize` is configured to 200.
 *
 * @run junit/othervm -Dsun.net.httpserver.maxReqHeaderSize=200 ${test.main.class}
 */

class MaxReqHeaderSizePropertyTest {

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

    private static final int MAX_REQUEST_HEADER_SIZE = effectiveMaxRequestHeaderSize();

    private static int effectiveMaxRequestHeaderSize() {
        var count = Integer.getInteger("sun.net.httpserver.maxReqHeaderSize");
        return count != null && count > 0 ? count : 389120;
    }

    private static final String REQUEST_LINE = "GET / HTTP/1.1";

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

    static List<Arguments> args() {

        List<Arguments> args = new ArrayList<>();

        args.add(Arguments.of(
                "at limit", true,
                createRequestHeaderLinesLeavingRoomFor100MoreCharacters(lines -> {
                    int valueLength = 100
                            - 1     // "k" (key)
                            - 2     // CRLF
                            - 32;
                    lines.add("k:" + "v".repeat(valueLength));
                })));

        var S = " ";
        args.add(Arguments.of(
                "at limit with extra whitespace before the key", true,
                createRequestHeaderLinesLeavingRoomFor100MoreCharacters(lines -> {
                    int valueLength = 100
                            - 1     // "k" (key)
                            - 2     // CRLF
                            - 32;
                    lines.add(S + "k:" + "v".repeat(valueLength));
                })));

        args.add(Arguments.of(
                "at limit with extra whitespace after the key",
                // No whitespace is allowed between the field name and colon (RFC 9112)
                false,
                createRequestHeaderLinesLeavingRoomFor100MoreCharacters(lines -> {
                    int valueLength = 100
                            - 1     // "k" (key)
                            - 2     // CRLF
                            - 32;
                    lines.add("k" + S + ":" + "v".repeat(valueLength));
                })));

        args.add(Arguments.of(
                "at limit with extra whitespace before the value",
                // Leading whitespace is taken into account while calculating the "size" of the value.
                false,
                createRequestHeaderLinesLeavingRoomFor100MoreCharacters(lines -> {
                    int valueLength = 100
                            - 1     // "k" (key)
                            - 2     // CRLF
                            - 32;
                    lines.add("k" + ":" + S + "v".repeat(valueLength));
                })));

        args.add(Arguments.of(
                "at limit with extra whitespace after the value",
                // Trailing whitespace is taken into account while calculating the "size" of the value.
                false,
                createRequestHeaderLinesLeavingRoomFor100MoreCharacters(lines -> {
                    int valueLength = 100
                            - 1     // "k" (key)
                            - 2     // CRLF
                            - 32;
                    lines.add("k" + ":" + "v".repeat(valueLength) + S);
                })));

        args.add(Arguments.of(
                "off limit due to excessive key", false,
                createRequestHeaderLinesLeavingRoomFor100MoreCharacters(lines -> {
                    int keyLength = 100
                            - 1     // "v" (value)
                            - 2     // CRLF
                            - 32
                            + 1;    // excess
                    lines.add("x".repeat(keyLength) + ":v");
                })));

        args.add(Arguments.of(
                "off limit due to excessive value", false,
                createRequestHeaderLinesLeavingRoomFor100MoreCharacters(lines -> {
                    int valueLength = 100
                            - 1     // "k" (key)
                            - 2     // CRLF
                            - 32
                            + 1;    // excess
                    lines.add("k:" + "v".repeat(valueLength));
                })));

        return args;

    }

    private static List<String> createRequestHeaderLinesLeavingRoomFor100MoreCharacters(
            Consumer<List<String>> consumer) {
        var lines = new ArrayList<String>();
        int valueLength = MAX_REQUEST_HEADER_SIZE
                // Request line + 32
                - REQUEST_LINE.length() - 32
                // "a" + CRLF + 32 (for this header)
                - 1 - 2 - 32
                // 100 (for `consumer` to inject)
                - 100;
        lines.add("a:" + "b".repeat(valueLength));
        consumer.accept(lines);
        return lines;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("args")
    void test(String description, boolean shouldSucceed, List<String> requestHeaderLines) throws IOException {
        if (shouldSucceed) {
            sendRequest(clientSocket, requestHeaderLines);
        } else {
            assertThrows(SocketException.class, () -> sendRequest(clientSocket, requestHeaderLines));
        }
    }

    private static void sendRequest(
            Socket clientSocket, List<String> requestHeaderLines)
            throws IOException {
        LOGGER.info("Sending request");
        var socketOutput = clientSocket.getOutputStream();
        List<String> requestLines = new ArrayList<>();
        requestLines.add(REQUEST_LINE);
        requestLines.addAll(requestHeaderLines);
        var request = String.join("\r\n", requestLines);
        socketOutput.write(request.getBytes(US_ASCII));
        socketOutput.write("\r\n\r\n".getBytes(US_ASCII));
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
                exchange.sendResponseHeaders(204, HttpExchange.RSPBODY_EMPTY);
            }
        }

    }

}
