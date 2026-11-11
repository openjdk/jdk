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

import jdk.test.lib.format.Format;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/*
 * @test
 * @bug 8393415
 * @summary Verify `getInputStream` on fixed content
 * @library /test/lib
 * @run junit ${test.main.class}
 */

class ResponseStreamFixed {

    private static final Charset CHARSET = US_ASCII;

    static Stream<Arguments> args() {
        return Stream
                .of("GET", "POST").flatMap(method -> Stream
                        .of(0, 42).flatMap(contentLength -> Stream
                                .of(false, true).map(keepAlive ->
                                        Arguments.of(method, contentLength, keepAlive))));
    }

    @ParameterizedTest
    @MethodSource("args")
    void test(String method, int contentLength, boolean keepAlive) throws Exception {
        try (var serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {

            // Connect to the server
            var connection1 = connect(method, "/connection1", serverSocket);

            // Accept the client socket
            try (var client1Socket = serverSocket.accept();
                 var executor = Executors.newSingleThreadExecutor()) {

                // Kick-start the request #1
                var response1CodeFuture = executor.submit(connection1::getResponseCode);

                // Make the server tick
                var response1Body =
                        readRequestAndWriteResponse(
                                contentLength, keepAlive, client1Socket, 'a');

                // Verify the response #1 body
                int response1Code = response1CodeFuture.get();
                verifyResponse(contentLength, response1Code, connection1, response1Body);

                // Connect to the server a 2nd time.
                //
                // Depending on the configured `keepAlive`, this should either
                // reuse the earlier connection that should have been pooled, or
                // create a new one.
                var connection2 = connect(method, "/connection2", serverSocket);

                // Kick-start the request #2
                var response2CodeFuture = executor.submit(connection2::getResponseCode);

                // Determine where shall the request #2 is served from
                try (var client2Socket = keepAlive ? client1Socket : serverSocket.accept()) {

                    // Make the server tick
                    var response2Body =
                            readRequestAndWriteResponse(
                                    contentLength, keepAlive, client2Socket, 'b');

                    // Verify the response #2 body
                    int response2Code = response2CodeFuture.get();
                    verifyResponse(contentLength, response2Code, connection2, response2Body);

                }

            }

        }

    }

    private static HttpURLConnection connect(
            String method, String path, ServerSocket serverSocket)
            throws Exception {
        var connection = (HttpURLConnection) URIBuilder
                .newBuilder()
                .scheme("http")
                .loopback()
                .port(serverSocket.getLocalPort())
                .path(path)
                .toURL()
                .openConnection(Proxy.NO_PROXY);
        connection.setRequestMethod(method);
        connection.connect();
        return connection;
    }

    private static String readRequestAndWriteResponse(
            int contentLength, boolean keepAlive, Socket clientSocket, char distinctChar)
            throws IOException {

        // Consume the request
        readUntilEmptyLine(clientSocket.getInputStream());

        // Create the response body
        var paddingChar = 'x';
        assertNotEquals(paddingChar, distinctChar);
        var responseBody = (distinctChar +                                   // A distinct character for `read()`
                ("" + paddingChar).repeat(Math.max(0, contentLength - 3)) +  // The sequence for `read(byte[], int, int)`
                "\r\n")                                                      // Aid for human eyes
                .substring(0, contentLength);

        // Write the response
        var response =
                "HTTP/1.1 200 OK\r\n" +
                        "Connection: %s\r\n".formatted(keepAlive ? "keep-alive" : "close") +
                        "Content-Length: %s\r\n".formatted(contentLength) +
                        "\r\n" +
                        responseBody +
                        "\r\nTHESE EXTRA BYTES SHOULD NEVER BE READ!\r\n";
        clientSocket.getOutputStream().write(response.getBytes(CHARSET));
        return responseBody;

    }

    private static void readUntilEmptyLine(InputStream stream) throws IOException {
        var buffer = new StringBuilder();
        do {
            var c = stream.read();
            assertNotEquals(-1, c, "EOF after reading: " + Format.asLiteral(buffer));
            buffer.append((char) c);
        } while (!endsWithEmptyLine(buffer));
    }

    private static boolean endsWithEmptyLine(CharSequence cs) {
        var l = cs.length();
        return l > 3 &&
               cs.charAt(l - 4) == '\r' && cs.charAt(l - 3) == '\n' &&
               cs.charAt(l - 2) == '\r' && cs.charAt(l - 1) == '\n';
    }

    private static void verifyResponse(
            int contentLength,
            int responseCode,
            HttpURLConnection connection,
            String expectedResponseBody)
            throws Exception {

        // Verify the received response code
        assertEquals(200, responseCode);

        // Read the first byte of the response body.
        //
        // Why don't we simply use `read(byte[], int, int)` to read all? Because
        // this is a very modest fuzzing attempt to combine both `read()` and
        // `read(byte[], int, int)`, since each can implement byte counting
        // differently.
        var actualResponseBody = new StringBuilder();
        var responseInputStream = connection.getInputStream();
        var c = responseInputStream.read();
        if (c == -1) {
            assertEquals(0, contentLength, "Unexpected response body EOF");
        } else {
            actualResponseBody.append((char) c);
        }

        // Read the rest of the response body.
        // `+ 8192` slack to trigger excessive read, which should not be possible.
        var buffer = new byte[contentLength + 8192];
        for (int readLength;
             0 < (readLength = responseInputStream.read(buffer, 0, buffer.length));) {
            actualResponseBody.append(new String(buffer, 0, readLength, CHARSET));
        }

        // Confirm EOF
        assertEquals(-1, responseInputStream.read());

        // Verify the read response body
        assertEquals(expectedResponseBody, actualResponseBody.toString());

    }

}
