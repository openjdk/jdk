/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8306040
 * @summary HttpResponseInputStream.available() returns 1 on empty stream
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @run junit/othervm HttpInputStreamAvailableTest
 *
 */
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HttpInputStreamAvailableTest {

    private HttpServer server;
    private int port;
    static final String TEST_MESSAGE = "This is test message";
    static final int ZERO = 0;

    @BeforeAll
    void setup() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        InetSocketAddress addr = new InetSocketAddress(loopback, 0);
        server = HttpServer.create(addr, 0);
        port = server.getAddress().getPort();
        FirstHandler fHandler = new FirstHandler();
        server.createContext("/NonZeroResponse/", fHandler);
        SecondHandler sHandler = new SecondHandler();
        server.createContext("/ZeroResponse/", sHandler);
        server.start();
    }

    @AfterAll
    void teardown() throws Exception {
        server.stop(0);
    }

    @Test
    public void test() throws Exception {
        HttpClient client = HttpClient
                .newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();

        URI uri = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path("/NonZeroResponse/")
                .build();

        HttpRequest request = HttpRequest
                .newBuilder(uri)
                .GET()
                .build();

        // Send a httpRequest and assert the bytes available
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        try ( InputStream in = response.body()) {
            in.readNBytes(2);
            // this is not guaranteed, but a failure here would be surprising
            assertEquals(TEST_MESSAGE.length() - 2, in.available());
            //read the remaining data
            in.readAllBytes();
            //available should return 0
            assertEquals(ZERO, in.available());
        }
    }

    @Test
    public void test1() throws Exception {
        HttpClient client = HttpClient
                .newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();

        URI uri = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .path("/ZeroResponse/")
                .build();

        HttpRequest request = HttpRequest
                .newBuilder(uri)
                .GET()
                .build();

        // Send a httpRequest and assert the bytes available
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        try ( InputStream in = response.body()) {
            assertEquals(ZERO, in.available());
            in.readAllBytes();
            assertEquals(ZERO, in.available());
        }
    }

    static class FirstHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try ( OutputStream os = exchange.getResponseBody()) {
                byte[] workingResponse = TEST_MESSAGE.getBytes();
                exchange.sendResponseHeaders(200, workingResponse.length);
                os.write(workingResponse);
                os.flush();
            }
        }
    }

    static class SecondHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(204, -1);
        }
    }
}
