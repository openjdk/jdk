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
import jdk.test.lib.Utils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test id=default
 * @bug 8295785
 * @summary Tests the `sun.net.httpserver.idleInterval` system property, which
 *          defaults to 30s. The background task evicting idle connections is
 *          scheduled to run every `sun.net.httpserver.clockTick`
 *          milliseconds, which defaults to 10000. We need to wait for at least
 *          `30+clockTick` seconds, that is, 40.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dtest.evictionWaitDuration=40
 *      ${test.main.class}
 */

/*
 * @test id=0
 * @bug 8295785
 * @summary Tests the `sun.net.httpserver.idleInterval` system property
 *          configured to 0, and expects it to be overridden by the default,
 *          i.e., 30. The background task evicting idle connections is scheduled
 *          to run every `sun.net.httpserver.clockTick` milliseconds, which
 *          defaults to 10000. We need to wait for at least `30+clockTick`
 *          seconds, that is, 40.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.idleInterval=0
 *      -Dtest.evictionWaitDuration=40
 *      ${test.main.class}
 */

/*
 * @test id=negative
 * @bug 8295785
 * @summary Tests the `sun.net.httpserver.idleInterval` system property
 *          configured to -1, and expects it to be overridden by the default,
 *          i.e., 30. The background task evicting idle connections is scheduled
 *          to run every `sun.net.httpserver.clockTick` milliseconds, which
 *          defaults to 10000. We need to wait for at least `30+clockTick`
 *          seconds, that is, 40.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.idleInterval=-1
 *      -Dtest.evictionWaitDuration=40
 *      ${test.main.class}
 */

/*
 * @test id=1
 * @bug 8295785
 * @summary Tests the `sun.net.httpserver.idleInterval` system property
 *          configured to 1. The background task evicting idle connections
 *          is scheduled to run every `sun.net.httpserver.clockTick`
 *          milliseconds, which defaults to 10000. Hence, without configuring
 *          `clockTick`, for values less than 10000, we need to wait at least
 *          for 10 seconds.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.idleInterval=1
 *      -Dtest.evictionWaitDuration=10
 *      ${test.main.class}
 */

/*
 * @test id=1-with-clockTick
 * @bug 8295785
 * @summary Tests the `sun.net.httpserver.idleInterval`  and
 *          `sun.net.httpserver.clockTick` system properties configured to
 *          1 and 100, respectively. `clockTick` determines the frequency of the
 *          background task evicting idle connections, and it defaults to 10000
 *          milliseconds.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.idleInterval=1
 *      -Dsun.net.httpserver.clockTick=100
 *      -Dtest.evictionWaitDuration=1
 *      ${test.main.class}
 */

class IdleIntervalPropertyTest {

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
        int evictionWaitDurationSecs = Integer.getInteger("test.evictionWaitDuration");

        // Create the HTTP server
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", NoContentReturningHandler.INSTANCE);
        server.start();

        // Create the client
        var serverAddress = server.getAddress();
        try (var clientSocket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {

            // Verify two consecutive requests can proceed without tripping any timeouts
            sendRequest(clientSocket, "/?request=1");
            sendRequest(clientSocket, "/?request=2");

            // Pause to let the idle interval mechanism kick in and evict the connection from the pool
            var pauseMillis = Duration.ofSeconds(evictionWaitDurationSecs)
                    // Add an extra slack for the asynchronous mechanism to kick
                    .plus(Duration.ofSeconds(Utils.adjustTimeout(1)))
                    .toMillis();
            Thread.sleep(pauseMillis);

            // Verify that a 3rd request over the same socket will observe the server disconnect
            assertThrows(SocketException.class, () -> sendRequest(clientSocket, "/?request=3"));

        } finally {
            server.stop(0);
        }

    }

    private static void sendRequest(
            Socket clientSocket, String requestTarget)
            throws IOException {
        var socketOutput = clientSocket.getOutputStream();
        socketOutput.write("GET %s HTTP/1.1\r\n\r\n".formatted(requestTarget).getBytes(US_ASCII));
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
            try (exchange) {
                exchange.sendResponseHeaders(204, HttpExchange.RSPBODY_EMPTY);
            }
        }

    }

}
