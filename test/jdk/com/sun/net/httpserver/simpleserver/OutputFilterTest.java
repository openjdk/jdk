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
 * @summary Tests for OutputFilter
 * @modules java.base/sun.net.www:+open
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run testng/othervm -Djdk.httpclient.redirects.retrylimit=1 OutputFilterTest
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.*;
import static com.sun.net.httpserver.SimpleFileServer.OutputLevel.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class OutputFilterTest {
    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<IOException> IOE = IOException.class;

    static final OutputStream OUT = new ByteArrayOutputStream();
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger logger = Logger.getLogger("com.sun.net.httpserver");

    @BeforeTest
    public void setup() {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
    }

    @Test
    public void testNull() {
        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(null, null));
        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(null, VERBOSE));
        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(OUT, null));
    }

    @Test
    public void testDescription() {
        var filter = SimpleFileServer.createOutputFilter(OUT, VERBOSE);
        assertEquals(filter.description(), "HttpExchange OutputFilter (outputLevel: VERBOSE)");

        filter = SimpleFileServer.createOutputFilter(OUT, INFO);
        assertEquals(filter.description(), "HttpExchange OutputFilter (outputLevel: INFO)");
    }

    @Test
    public void testNONE() {
        assertThrows(IAE, () -> SimpleFileServer.createOutputFilter(OUT, NONE));
    }

    /**
     * Confirms that the output filter produces the expected output for a
     * successful exchange (with the request-path attribute set.)
     */
    @Test
    public void testExchange() throws Exception {
        var baos = new ByteArrayOutputStream();
        var handler = new RequestPathHandler();
        var filter = SimpleFileServer.createOutputFilter(baos, VERBOSE);
        var server = HttpServer.create(LOOPBACK_ADDR, 10, "/", handler, filter);
        server.start();
        try (baos) {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().map().size(), 3);
            assertEquals(response.body(), "hello world");
        } finally {
            server.stop(0);
            baos.flush();
            var filterOutput = baos.toString(UTF_8);
            var pattern = Pattern.compile("""
                    127\\.0\\.0\\.1 - - \\[[\\s\\S]+] "GET / HTTP/1\\.1" 200 -
                    Resource requested: /foo/bar
                    (>[\\s\\S]+:[\\s\\S]+)+
                    >
                    (<[\\s\\S]+:[\\s\\S]+)+
                    <
                    """.replaceAll("\n", System.lineSeparator()));
            assertTrue(pattern.matcher(filterOutput).matches());

            /*
             * Expected output format:
             *     """
             *     127.0.0.1 - - [06/Jul/2021:12:56:47 +0100] "GET / HTTP/1.1" 200 -
             *     Resource requested: /foo/bar
             *     > Connection: Upgrade, HTTP2-Settings
             *     > Http2-settings: AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA
             *     > Host: localhost:59146
             *     > Upgrade: h2c
             *     > User-agent: Java-http-client/18-internal
             *     > Content-length: 0
             *     >
             *     < Date: Tue, 06 Jul 2021 11:56:47 GMT
             *     < Content-length: 11
             *     <
             *     """;
             */
        }
    }

    /**
     * Confirms that the output filter produces the expected output for
     * a successful exchange (without the request-path attribute set.)
     */
    @Test
    public void testExchangeWithoutRequestPath() throws Exception {
        var baos = new ByteArrayOutputStream();
        var handler = new NoRequestPathHandler();
        var filter = SimpleFileServer.createOutputFilter(baos, VERBOSE);
        var server = HttpServer.create(LOOPBACK_ADDR, 10, "/", handler, filter);
        server.start();
        try (baos) {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().map().size(), 2);
            assertEquals(response.body(), "hello world");
        } finally {
            server.stop(0);
            baos.flush();
            var filterOutput = baos.toString(UTF_8);
            var pattern = Pattern.compile("""
                    127\\.0\\.0\\.1 - - \\[[\\s\\S]+] "GET / HTTP/1\\.1" 200 -
                    (>[\\s\\S]+:[\\s\\S]+)+
                    >
                    (<[\\s\\S]+:[\\s\\S]+)+
                    <
                    """.replaceAll("\n", System.lineSeparator()));
            assertTrue(pattern.matcher(filterOutput).matches());

            /*
             * Expected output:
             *     """
             *     127.0.0.1 - - [12/Jul/2021:10:05:10 +0000] "GET / HTTP/1.1" 200 -
             *     > Connection: Upgrade, HTTP2-Settings
             *     > Http2-settings: AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA
             *     > Host: localhost:57931
             *     > Upgrade: h2c
             *     > User-agent: Java-http-client/18-internal
             *     > Content-length: 0
             *     >
             *     < Date: Mon, 12 Jul 2021 10:05:10 GMT
             *     < Content-length: 11
             *     <
             *     """;
             */
        }
    }

    @DataProvider
    public Object[][] throwingHandler() {
        return new Object[][] {
                {VERBOSE, "Error: server exchange handling failed: IOE ThrowingHandler" + System.lineSeparator()},
                {INFO, "Error: server exchange handling failed: IOE ThrowingHandler" + System.lineSeparator()},
                {NONE, ""}
        };
    }

    /**
     * Confirms that the output filter captures a throwable that is thrown
     * during the exchange handling and prints the expected error message.
     * The "httpclient.redirects.retrylimit" system property is set to 1 to
     * prevent retries on the client side, which would result in more than one
     * error message.
     */
    @Test(dataProvider = "throwingHandler")
    public void testExchangeThrowingHandler(OutputLevel level,
                                            String expectedOutput) throws Exception {
        var baos = new ByteArrayOutputStream();
        var handler = new ThrowingHandler();
        HttpServer server;
        if (level.equals(NONE)) {
            server = HttpServer.create(LOOPBACK_ADDR, 10, "/", handler);
        } else {
            var filter = SimpleFileServer.createOutputFilter(baos, level);
            server = HttpServer.create(LOOPBACK_ADDR, 10, "/", handler, filter);
        }
        server.start();
        try (baos) {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            assertThrows(IOE, () -> client.send(request, HttpResponse.BodyHandlers.ofString()));
        } finally {
            server.stop(0);
            baos.flush();
            assertEquals(baos.toString(UTF_8), expectedOutput);
        }
    }

    /**
     * Confirms that the output filter prints the expected message if the request
     * URI cannot be resolved. This only applies if the filter is used in
     * combination with the SimpleFileServer file-handler, which sets the
     * necessary request-path attribute.
     */
    @Test
    public void testCannotResolveRequestURI() throws Exception {
        var baos = new ByteArrayOutputStream();
        var handler = SimpleFileServer.createFileHandler(Path.of(".").toAbsolutePath());
        var filter = SimpleFileServer.createOutputFilter(baos, VERBOSE);
        var server = HttpServer.create(LOOPBACK_ADDR, 0, "/", handler, filter);
        server.start();
        try (baos) {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile\u0000.txt")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().map().size(), 3);
        } finally {
            server.stop(0);
            baos.flush();
            var filterOutput = baos.toString(UTF_8);
            var pattern = Pattern.compile("""
                    127\\.0\\.0\\.1 - - \\[[\\s\\S]+] "GET /aFile%00\\.txt HTTP/1\\.1" 404 -
                    Resource requested: could not resolve request URI path
                    (>[\\s\\S]+:[\\s\\S]+)+
                    >
                    (<[\\s\\S]+:[\\s\\S]+)+
                    <
                    """.replaceAll("\n", System.lineSeparator()));
            assertTrue(pattern.matcher(filterOutput).matches());
        }
    }

    // --- infra ---

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    /**
     * A handler that sets the request-path attribute and a custom header
     * and sends a response.
     */
    static class RequestPathHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {
                is.readAllBytes();
                exchange.setAttribute("request-path", "/foo/bar");
                exchange.getResponseHeaders().put("Foo", List.of("bar", "bar"));
                var resp = "hello world".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                os.write(resp);
            }
        }
    }

    /**
     * A handler that sets no request-path attribute and sends a response.
     */
    static class NoRequestPathHandler implements HttpHandler {
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
     * A handler that throws an IOException.
     */
    static class ThrowingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                throw new IOException("IOE ThrowingHandler");
            }
        }
    }
}
