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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test id=default
 * @bug 8295785
 * @summary Tests the default value of `sun.net.httpserver.drainAmount`,
 *          which is 65,536.
 *
 * @run junit/othervm
 *      ${test.main.class}
 */

/*
 * @test id=negative
 * @bug 8295785
 * @summary Verifies that configuring `sun.net.httpserver.drainAmount` to a
 *          negative value (-1, in this case) results in server closing the
 *          connection if the handler leaves behind an unconsumed request body
 *          of length bigger than 0.
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.drainAmount=-1
 *      ${test.main.class}
 */

/*
 * @test id=0
 * @bug 8295785
 * @summary Verifies that configuring `sun.net.httpserver.drainAmount` to 0
 *          results in server closing the connection if the handler leaves
 *          behind an unconsumed request body of length bigger than 0.
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.drainAmount=0
 *      ${test.main.class}
 */

/*
 * @test id=1
 * @bug 8295785
 * @summary Verifies that configuring `sun.net.httpserver.drainAmount` to 1
 *          results in server closing the connection if the handler leaves
 *          behind an unconsumed request body of length greater than or equal
 *          to 1.
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.drainAmount=1
 *      ${test.main.class}
 */

class DrainAmountPropertyTest {

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

        // Read the drain amount (i.e., the maximum allowed unconsumed request body length)
        var drainAmount = Integer.getInteger("sun.net.httpserver.drainAmount", 65536);

        // Create the HTTP server
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", NoContentReturningHandler.INSTANCE);
        server.start();

        // Create the client
        var serverAddress = server.getAddress();
        try (var clientSocket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {

            // When the drain amount is less than 1, `LeftOverInputStream::drain` gets skipped.
            // As a result, `eof` flag stays `false`, `ServerImpl` observing this closes the connection.
            // Hence, branch for drain amounts greater than 0.
            var effectiveDrainAmount = Math.max(0, drainAmount);
            if (effectiveDrainAmount > 0) {

                // Send the 1st request containing a body matching the drain amount, that is, no excess.
                // This should leave the connection in a reusable state.
                sendRequest(clientSocket, "/?request=reusable", effectiveDrainAmount - 1);

                // Send the 2nd (empty) request and verify that the connection is still usable
                sendRequest(clientSocket, "/?request=still-usable", 0);

            }

            // Send the 3rd request containing a body exceeding the drain amount and verify the server disconnect
            assertThrows(SocketException.class, () -> {
                sendRequest(clientSocket, "/?request=closing", effectiveDrainAmount);
                // Above request might still successfully consume the response before reading the server disconnect.
                // Hence, send a 4th (empty) request to ensure to observe the socket close.
                sendRequest(clientSocket, "/?request=after-close", 0);
            });

        } finally {
            server.stop(0);
        }

    }

    private static void sendRequest(
            Socket clientSocket, String requestTarget, int requestBodyLength)
            throws IOException {
        LOGGER.info("Sending request (target=%s, bodyLength=%s)".formatted(requestTarget, requestBodyLength));
        var socketOutput = clientSocket.getOutputStream();
        socketOutput.write(String
                .join(
                        "\r\n",
                        "POST %s HTTP/1.1".formatted(requestTarget),
                        "Content-Length: %s".formatted(requestBodyLength),
                        "\r\n")
                .getBytes(US_ASCII));
        socketOutput.write(new byte[requestBodyLength]);
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
