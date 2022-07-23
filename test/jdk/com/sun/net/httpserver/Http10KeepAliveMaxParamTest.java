/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.HttpHeaderParser;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/*
 * @test
 * @bug 8289291
 * @summary Verifies that the HttpServer doesn't add the "max" parameter to the Keep-Alive header
 * that it sets in the response
 * @library /test/lib
 * @run main Http10KeepAliveMaxParamTest
 */
public class Http10KeepAliveMaxParamTest {

    /**
     * Sends a HTTP/1.0 request with "Connection: keep-alive" header to the
     * com.sun.net.httpserver.HttpServer and then verifies that if the server responds back
     * with a "Keep-Alive" header in the response, then the header value doesn't have the
     * "max" parameter.
     */
    public static void main(final String[] args) throws Exception {
        final var bindAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        final int backlog = 0;
        final HttpServer server = HttpServer.create(bindAddr, backlog);
        server.createContext("/", (exchange) -> {
            System.out.println("Sending response for request " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        System.out.println("Server started at address " + server.getAddress());
        try {
            try (final Socket sock = new Socket(bindAddr.getAddress(), server.getAddress().getPort())) {
                // send a HTTP/1.0 request
                final String request = "GET /test/foo HTTP/1.0\r\nConnection: keep-alive\r\n\r\n";
                final OutputStream os = sock.getOutputStream();
                os.write(request.getBytes(StandardCharsets.UTF_8));
                os.flush();
                System.out.println("Sent request to server:");
                System.out.println(request);
                // read the response headers
                final HttpHeaderParser headerParser = new HttpHeaderParser(sock.getInputStream());
                // verify that the response contains 200 status code.
                // method is oddly named, but it returns status line of response
                final String statusLine = headerParser.getRequestDetails();
                System.out.println("Received status line " + statusLine);
                if (statusLine == null || !statusLine.contains("200")) {
                    throw new AssertionError("Unexpected response from server," +
                            " status line = " + statusLine);
                }
                System.out.println("Server responded with headers: " + headerParser.getHeaderMap());
                // spec doesn't mandate the presence of the Keep-Alive header. We skip this test
                // if the server doesn't send one
                final List<String> keepAliveHeader = headerParser.getHeaderValue("keep-alive");
                if (keepAliveHeader == null || keepAliveHeader.isEmpty()) {
                    // skip the test
                    System.out.println("Test SKIPPED since the server didn't return a keep-alive" +
                            " header in response");
                    return;
                }
                // we expect only one keep-alive header and there shouldn't be any "max" parameter
                // in that value
                final String val = keepAliveHeader.get(0);
                if (val.toLowerCase(Locale.ROOT).contains("max")) {
                    throw new AssertionError("Server wasn't supposed to send " +
                            "\"max\" parameter in keep-alive response header. " +
                            "Actual header value = " + val);
                }
            }
        } finally {
            server.stop(0);
        }
    }
}
