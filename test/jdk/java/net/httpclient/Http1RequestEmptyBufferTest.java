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

import jdk.test.lib.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @test
 * @bug 8308024
 * @summary Verify that the server observes the terminal chunk exactly once
 *          when the HTTP/1.1 client request body publisher supplies an empty buffer.
 * @library /test/lib
 * @run junit/othervm ${test.main.class}
 */
public class Http1RequestEmptyBufferTest {

    static final byte[] HEADER_END = new byte[] {'\r', '\n', '\r', '\n'};

    static byte[] readRequestHeaders(InputStream input) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int headerEndMatch = 0, nextByte;
        while ((nextByte = input.read()) != -1) {
            headerBytes.write(nextByte);
            if (nextByte == HEADER_END[headerEndMatch]) {
                headerEndMatch++;
                if (headerEndMatch == 4) {
                    return headerBytes.toByteArray();
                }
            } else {
                headerEndMatch = 0;
            }
        }
        throw new IOException("EOF reached before reaching end of headers");
    }

    static final String TERMINAL_CHUNK = "0\r\n\r\n";

    static final String RESPONSE_HEADERS = "HTTP/1.1 200 OK\r\n" +
            "Content-Length: 0\r\n" +
            "Connection: close\r\n\r\n";

    static String escape(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    @Test
    void test() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            String path = "/testChunkEmptyBuffer/";
            URI uri = new URI("http",
                    null,
                    server.getInetAddress().getHostAddress(),
                    server.getLocalPort(),
                    path,
                    null,
                    null);
            try (HttpClient client = HttpClient.newBuilder()
                    .proxy(HttpClient.Builder.NO_PROXY)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .PUT(HttpRequest.BodyPublishers.ofByteArrays(List.of(new byte[0])))
                        .build();
                CompletableFuture<HttpResponse<Void>> responseFuture =
                        client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
                try (Socket connection = server.accept()) {
                    connection.setSoTimeout((int)Utils.adjustTimeout(1000));
                    InputStream input = connection.getInputStream();
                    byte[] headerBytes = readRequestHeaders(input);
                    String headerText = new String(headerBytes, StandardCharsets.US_ASCII)
                            .toLowerCase(Locale.ROOT);
                    assertTrue(headerText.contains("transfer-encoding: chunked"),
                            "Expected Transfer-Encoding: chunked header, got: "
                            + headerText);
                    byte[] firstChunkBytes = input.readNBytes(TERMINAL_CHUNK.length());
                    OutputStream os = connection.getOutputStream();
                    os.write(RESPONSE_HEADERS.getBytes(StandardCharsets.US_ASCII));
                    os.flush();
                    String requestBody = new String(firstChunkBytes, StandardCharsets.US_ASCII)
                            + new String(input.readAllBytes(), StandardCharsets.US_ASCII);
                    assertEquals(escape(TERMINAL_CHUNK), escape(requestBody));
                    HttpResponse<Void> response = responseFuture.join();
                    assertEquals(200, response.statusCode());
                }
            }
        }
    }
}
