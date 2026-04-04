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

/*
 * @test
 * @bug 8373677
 * @summary Tests for verifying that a non-SSL server can detect
 *          when a client attempts to use SSL.
 * @library /test/lib
 * @run junit/othervm ${test.main.class}
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import static com.sun.net.httpserver.HttpExchange.RSPBODY_EMPTY;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.junit.jupiter.api.Assertions.*;

public class ClearTextServerSSL {

    static final InetAddress LOOPBACK_ADDR = InetAddress.getLoopbackAddress();
    static final boolean ENABLE_LOGGING = true;
    static final Logger logger = Logger.getLogger("com.sun.net.httpserver");

    static final String CTXT_PATH = "/ClearTextServerSSL";

    @BeforeAll
    public static void setup() {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
    }

    @Test
    public void test() throws Exception {
        var sslContext = SimpleSSLContext.findSSLContext();
        var handler = new TestHandler();
        var server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDR, 0), 0);
        server.createContext(path(""), handler);
        server.start();
        try (var client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .proxy(NO_PROXY)
                    .build()) {
            var request = HttpRequest.newBuilder()
                    .uri(uri("http", server, path("/clear")))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            var sslRequest = HttpRequest.newBuilder()
                    .uri(uri("https", server, path("/ssl")))
                    .build();
            Assertions.assertThrows(SSLException.class, () -> {
                client.send(sslRequest, HttpResponse.BodyHandlers.ofString());
            });
            try (var socket = new Socket()) {
                socket.connect(server.getAddress());
                byte[] badRequest = {
                        22, 'B', 'A', 'D', ' ',
                        '/', ' ' ,
                        'H', 'T', 'T', 'P', '/', '1', '.', '1' };
                socket.getOutputStream().write(badRequest);
                socket.getOutputStream().flush();
                var reader = new InputStreamReader(socket.getInputStream());
                var line = reader.readAllLines();
                Assertions.assertEquals("HTTP/1.1 400 Bad Request", line.get(0));
                System.out.println("Got expected response:");
                line.stream().map(l -> "\t" + l).forEach(System.out::println);
            }

        } finally {
            server.stop(0);
        }
    }

    // --- infra ---

    static String path(String path) {
        assert CTXT_PATH.startsWith("/");
        assert !CTXT_PATH.endsWith("/");
        if (path.startsWith("/")) {
            return CTXT_PATH + path;
        } else {
            return CTXT_PATH + "/" + path;
        }
    }

    static URI uri(String scheme, HttpServer server, String path) throws URISyntaxException {
        return URIBuilder.newBuilder()
                .scheme(scheme)
                .loopback()
                .port(server.getAddress().getPort())
                .path(path)
                .build();
    }

    /**
     * A test handler that reads any request bytes and sends
     * an empty 200 response
     */
    static class TestHandler implements HttpHandler {
        @java.lang.Override
        public void handle(HttpExchange exchange) throws IOException {
            try (var reqBody = exchange.getRequestBody()) {
                reqBody.readAllBytes();
                exchange.sendResponseHeaders(200, RSPBODY_EMPTY);
            } catch (Throwable t) {
                t.printStackTrace();
                exchange.sendResponseHeaders(500, RSPBODY_EMPTY);
            }
        }
    }
}
