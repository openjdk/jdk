/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test for HttpsServer::create
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.URIBuilder
 * @run testng/othervm HttpsServerTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.SSLContext;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

public class HttpsServerTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    SSLContext sslContext;

    @BeforeTest
    public void setup() throws IOException {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
        sslContext = new SimpleSSLContext().get();
        SSLContext.setDefault(sslContext);
    }

    @Test
    public void testNull() {
        assertThrows(NPE, () -> HttpsServer.create(null, 0, null, new Handler()));
        assertThrows(NPE, () -> HttpsServer.create(null, 0, "/", null));
        assertThrows(NPE, () -> HttpsServer.create(null, 0, "/", new Handler(), (Filter)null));
        assertThrows(NPE, () -> HttpsServer.create(null, 0, "/", new Handler(), new Filter[]{null}));
    }

    @Test
    public void testCreate() throws IOException {
        assertNull(HttpsServer.create().getAddress());

        final var s1 = HttpsServer.create(null, 0);
        assertNull(s1.getAddress());
        s1.bind((LOOPBACK_ADDR), 0);
        assertEquals(s1.getAddress().getAddress(), LOOPBACK_ADDR.getAddress());

        final var s2 = HttpsServer.create(null, 0, "/foo/", new Handler());
        assertNull(s2.getAddress());
        s2.bind(LOOPBACK_ADDR, 0);
        assertEquals(s2.getAddress().getAddress(), LOOPBACK_ADDR.getAddress());
        s2.removeContext("/foo/");  // throws if context doesn't exist
    }

    @Test
    public void testExchange() throws Exception {
        var filter = new Filter();
        var server = HttpsServer.create(LOOPBACK_ADDR, 0, "/test", new Handler(), filter);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.start();
        try {
            var client = HttpClient.newBuilder()
                    .proxy(NO_PROXY)
                    .sslContext(sslContext)
                    .build();
            var request = HttpRequest.newBuilder(uri(server, "/test")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "hello world");
            assertEquals(response.headers().firstValue("content-length").get(),
                    Integer.toString("hello world".length()));
            assertEquals(response.statusCode(), filter.responseCode.get().intValue());
        } finally {
            server.stop(0);
        }
    }

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .scheme("https")
                .host("localhost")
                .port(server.getAddress().getPort())
                .path(path)
                .buildUnchecked();
    }

    /**
     * A test handler that discards the request and sends a response
     */
    static class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                is.readAllBytes();
                var resp = "hello world".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                os.write(resp);
            }
        }
    }

    /**
     * A test post-processing filter that captures the response code
     */
    static class Filter extends com.sun.net.httpserver.Filter {
        final CompletableFuture<Integer> responseCode = new CompletableFuture<>();

        @Override
        public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
            chain.doFilter(exchange);
            responseCode.complete(exchange.getResponseCode());
        }

        @Override
        public String description() {
            return "HttpsServerTest Filter";
        }
    }
}
