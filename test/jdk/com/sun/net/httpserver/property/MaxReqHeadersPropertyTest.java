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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test id=default
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxReqHeaders` is not configured, and hence,
 *          falls back to its default, 200.
 *
 * @run junit/othervm ${test.main.class}
 */

/*
 * @test id=zero
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxReqHeaders` is configured to 0, which gets
 *          replaced with the default, 200.
 *
 * @run junit/othervm -Dsun.net.httpserver.maxReqHeaders=0 ${test.main.class}
 */

/*
 * @test id=negative
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxReqHeaders` is configured to a negative value
 *          (-1, in this case), which gets replaced with the default, 200.
 *
 * @run junit/othervm -Dsun.net.httpserver.maxReqHeaders=-1 ${test.main.class}
 */

/*
 * @test id=7
 * @bug 8295785
 * @summary Verifies `HttpServer` behavior when
 *          `sun.net.httpserver.maxReqHeaders` is configured to a non-default
 *          valid value, which is 7 in this case.
 *
 * @run junit/othervm -Dsun.net.httpserver.maxReqHeaders=7 ${test.main.class}
 */

class MaxReqHeadersPropertyTest {

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

    private static final int MAX_REQUEST_HEADER_COUNT = effectiveMaxRequestHeaderCount();

    private static int effectiveMaxRequestHeaderCount() {
        var count = Integer.getInteger("sun.net.httpserver.maxReqHeaders");
        return count != null && count > 0 ? count : 200;
    }

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
        int M = MAX_REQUEST_HEADER_COUNT;

        args.add(Arguments.of(
                "at limit", true,
                // k1:v1, k2:v2, ..., kM:vM
                createRequestHeaderLines(M, null)));

        args.add(Arguments.of(
                "off limit by 1", false,
                // k1:v1, k2:v2, k3:v3, ..., k{M+1}:v{M+1}
                createRequestHeaderLines(M + 1, null)));

        args.add(Arguments.of(
                "at limit with duplicates (stressing order-sensitivity for duplicates)", true,
                // k:w, k:x, k:y, k:z, k5:v5, k6:v6, ..., k{M+3}:v{M+3}
                createRequestHeaderLines(M + 3, lines -> {
                    lines.set(0, "k: w");
                    lines.set(1, "k: x");
                    lines.set(2, "k: y");
                    lines.set(3, "k: z");
                })));

        args.add(Arguments.of(
                "off limit with duplicates (stressing order-sensitivity for duplicates)", false,
                // k:x, k2:v2, k3:v3, ..., kM:vM, k:y
                createRequestHeaderLines(M + 1, lines -> {
                    lines.set(0, "k: x");
                    lines.set(M, "k: y");
                })));

        args.add(Arguments.of(
                "at limit with multi-line value", true,
                // k:x\r\n\ty, k2:v2, k3:v3, ..., kM:vM
                createRequestHeaderLines(M, lines -> {
                    lines.set(0, "k: x\r\n\ty");
                })));

        args.add(Arguments.of(
                "at limit with duplicates and multi-line values", true,
                // k:w\r\n\tx, k:y\r\n\tz, k3:v3, k4:v4, ..., kM:vM
                createRequestHeaderLines(M, lines -> {
                    lines.set(0, "k: w\r\n\tx");
                    lines.set(1, "k: y\r\n\tz");
                })));

        args.add(Arguments.of(
                "at limit with multi-line value containing colon", true,
                // k:x\r\n\ty:Y, k2:v2, k3:v3, ..., kM:vM
                createRequestHeaderLines(M, lines -> {
                    lines.set(0, "k: x\r\n\ty: Y");
                })));

        args.add(Arguments.of(
                "at limit with duplicates and multi-line values containing colon", true,
                // k:w\r\n\tx: X, k:y\r\n\tz: Z, k3:v3, k4:v4, ..., kM:vM
                createRequestHeaderLines(M, lines -> {
                    lines.set(0, "k: w\r\n\tx: X");
                    lines.set(1, "k: y\r\n\tz: Z");
                })));

        return args;

    }

    private static List<String> createRequestHeaderLines(int length, Consumer<List<String>> consumer) {
        var lines = IntStream
                .range(1, length + 1)
                .mapToObj(requestHeaderIndex ->
                        "k%02X: v%02X".formatted(requestHeaderIndex, requestHeaderIndex))
                .collect(Collectors.toList());
        if (consumer != null) {
            consumer.accept(lines);
        }
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
        requestLines.add("GET / HTTP/1.1");
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
