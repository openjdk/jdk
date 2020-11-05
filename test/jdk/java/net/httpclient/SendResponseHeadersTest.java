/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8253005
 * @summary Check that sendResponseHeaders throws an IOException when headers
 *  have already been sent
 * @run testng/othervm SendResponseHeadersTest
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;


public class SendResponseHeadersTest {

    @Test
    public void testSend() throws Exception {
        var addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        var server = HttpServer.create(addr, 0);
        var path = "/test/foo.html";
        server.createContext("/test", new TestHandler());
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start();

        URI uri = new URI("http", null,
                server.getAddress().getHostString(),
                server.getAddress().getPort(),
                path, null, null);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // verify empty response received, otherwise an error has occurred
            if (!response.body().isEmpty())
                fail(response.body());
        } finally {
            server.stop(2);
            executor.shutdown();
        }
    }

    static class TestHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = exchange.getResponseBody()) {

                is.readAllBytes();
                exchange.sendResponseHeaders(200, 0);
                try {
                    IOException io = expectThrows(IOException.class,
                            () -> exchange.sendResponseHeaders(200, "failMsg".getBytes().length));
                    System.out.println("Got expected exception: " + io);
                } catch (Throwable t) {
                    // unexpected exception thrown, return error to client
                    t.printStackTrace();
                    os.write(("Unexpected error: " + t).getBytes());
                }
            }
        }
    }
}
