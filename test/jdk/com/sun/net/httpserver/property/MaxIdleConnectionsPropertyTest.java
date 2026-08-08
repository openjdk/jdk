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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test id=default
 * @bug 8295785
 * @summary Tests that the `sun.net.httpserver.maxIdleConnections` default
 *          (i.e., 200) results in all new connections to be closed after use
 *          when there are already 200 idle connections in the server's pool.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      ${test.main.class}
 */

/*
 * @test id=negative
 * @bug 8295785
 * @summary Tests that configuring `sun.net.httpserver.maxIdleConnections` to a
 *          negative value (-1 in this case) results in all connections to be
 *          closed after use.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.maxIdleConnections=-1
 *      ${test.main.class}
 */

/*
 * @test id=0
 * @bug 8295785
 * @summary Tests that configuring `sun.net.httpserver.maxIdleConnections` to 0
 *          results in all connections to be closed after use.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.maxIdleConnections=0
 *      ${test.main.class}
 */

/*
 * @test id=3
 * @bug 8295785
 * @summary Tests that configuring `sun.net.httpserver.maxIdleConnections` to a
 *          value greater than zero (3 in this case) results in all new
 *          connections to be closed after use when the provided capacity is
 *          reached.
 *
 * @library /test/lib
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.maxIdleConnections=3
 *      ${test.main.class}
 */

class MaxIdleConnectionsPropertyTest {

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
        int maxIdleConnectionCount = Integer.getInteger("sun.net.httpserver.maxIdleConnections", 200);

        // Create the HTTP server
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", NoContentReturningHandler.INSTANCE);
        server.start();

        try {

            // Create requestors that will be allowed for the pool admission
            var serverAddress = server.getAddress();
            var allowedRequestors = IntStream
                    .range(0, maxIdleConnectionCount)
                    .mapToObj(requestIndex -> {
                        var target = "/?request=" + requestIndex;
                        return new Requestor(serverAddress, target);
                    })
                    .toList();

            // Create the requestor that will *NOT* be allowed for the pool admission
            var lastRequestor = new Requestor(serverAddress, "/?request=last");

            // Verify that all requestors successfully connect and exchange a
            // request-response.
            for (var requestor : allowedRequestors) {
                requestor.exchange();
            }
            lastRequestor.exchange();

            // Verify that the last request, which should not have admitted to
            // the connection pool, observes the server disconnect.
            //
            // A connection getting marked idle and evaluated for close is an
            // asynchronous task that starts with the exchange sending an
            // `ExchangeFinished` event. Hence, pause a bit to give this
            // asynchronous task some time to take effect.
            Thread.sleep(Utils.adjustTimeout(500));
            assertThrows(SocketException.class, lastRequestor::exchange);

            // Verify that all requestors admitted to the pool can still
            // successfully exchange a request-response
            for (var requestor : allowedRequestors) {
                requestor.exchange();
            }

        } finally {
            Requestor.closeAll();
            server.stop(0);
        }

    }

    private static final class Requestor implements AutoCloseable {

        private static final List<Requestor> INSTANCES =
                Collections.synchronizedList(new ArrayList<>());

        private final Socket socket;

        private final String target;

        private boolean stopped = false;

        private Requestor(InetSocketAddress remoteAddress, String target) {
            try {
                this.socket = new Socket(remoteAddress.getAddress(), remoteAddress.getPort());
            } catch (IOException ioe) {
                var message = "Connection has failed (host=%s, port=%s, target=%s)"
                        .formatted(
                                remoteAddress.getAddress().getHostAddress(),
                                remoteAddress.getPort(),
                                target);
                throw new UncheckedIOException(message, ioe);
            }
            this.target = target;
            INSTANCES.add(this);
        }

        private synchronized void exchange() throws IOException {
            assertFalse(stopped);
            writeRequest();
            readResponse();
        }

        private synchronized void writeRequest() throws IOException {
            assertFalse(stopped);
            var socketOutput = socket.getOutputStream();
            socketOutput.write("GET %s HTTP/1.1\r\n\r\n".formatted(target).getBytes(US_ASCII));
            socketOutput.flush();
        }

        private synchronized void readResponse() throws IOException {
            assertFalse(stopped);
            var socketInput = socket.getInputStream();
            var socketReader = new BufferedReader(new InputStreamReader(socketInput, US_ASCII));
            assertEquals("HTTP/1.1 204 No Content", readLine(socketReader));    // Read the status line
            while (!readLine(socketReader).isEmpty());                          // Read the headers section
        }

        private static String readLine(BufferedReader socketReader) throws IOException {
            var line = socketReader.readLine();
            if (line == null) {
                throw new SocketException("EOF");                               // Use `SocketException` to signal EOF
            }
            return line;
        }

        @Override
        public synchronized void close() {
            if (!stopped) {
                stopped = true;
                try {
                    socket.close();
                } catch (IOException _) {
                    // Do nothing
                }
            }
        }

        private static void closeAll() {
            INSTANCES.forEach(Requestor::close);
        }

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
