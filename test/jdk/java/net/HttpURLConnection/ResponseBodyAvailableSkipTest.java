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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8393415
 * @summary Stress `available()` and `skip()` while reading response body
 * @library /test/lib
 * @run junit ${test.main.class}
 */

class ResponseBodyAvailableSkipTest {

    private static final Charset CHARSET = US_ASCII;

    @Test
    void test() throws Exception {
        try (var serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {

            // Establish the connection and issue the request
            var connection = (HttpURLConnection) URIBuilder
                    .newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(serverSocket.getLocalPort())
                    .toURL()
                    .openConnection(Proxy.NO_PROXY);
            var responseCodeFuture = executor.submit(connection::getResponseCode);

            // Accept the client socket
            try (var clientSocket = serverSocket.accept()) {

                // Make the server tick
                var responseBody = readRequestAndWriteResponse(clientSocket);

                // Verify the response body
                int responseCode = responseCodeFuture.get();
                verifyResponse(responseCode, connection, responseBody);

            }

        }

    }

    private static String readRequestAndWriteResponse(Socket clientSocket)
            throws IOException {

        // Consume the request
        readUntilEmptyLine(clientSocket.getInputStream());

        // Write the response
        var contentLength = 8192;
        var responseBody = "x".repeat(contentLength);
        var response =
                "HTTP/1.1 200 OK\r\n" +
                        "Connection: close\r\n" +
                        "Content-Length: %s\r\n".formatted(8192) +
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

    /** Verifies the response by using `available()` and `skip()` on it. */
    private static void verifyResponse(
            int responseCode,
            HttpURLConnection connection,
            String expectedResponseBody)
            throws Exception {

        // Verify the received response code
        assertEquals(200, responseCode);

        // Read one byte
        var actualResponseBody = new StringBuilder();
        var responseInputStream = connection.getInputStream();
        var c1 = responseInputStream.read();
        assertNotEquals(-1, c1, "Unexpected response body EOF");
        actualResponseBody.append((char) c1);

        // Verify `available()`
        var available1 = responseInputStream.available();
        var maxAvailable1 = expectedResponseBody.length() - actualResponseBody.length();
        assertTrue(
                0 <= available1 && available1 <= maxAvailable1,
                "Unexpected available(): 0 <= available (%s) <= %s"
                        .formatted(available1, maxAvailable1));

        // Skip some
        var skipped1 = Math.toIntExact(responseInputStream.skip(42));
        assertTrue(
                0 <= skipped1 && skipped1 <= maxAvailable1,
                "Unexpected skip(): 0 <= skip (%s) <= %s"
                        .formatted(skipped1, maxAvailable1));
        actualResponseBody.append(
                expectedResponseBody.subSequence(
                        actualResponseBody.length(),
                        actualResponseBody.length() + skipped1));

        // Verify `available()` (2nd time)
        var available2 = responseInputStream.available();
        var maxAvailable2 = expectedResponseBody.length() - actualResponseBody.length();
        assertTrue(
                0 <= available2 && available2 <= maxAvailable2,
                "Unexpected available(): 0 <= available (%s) <= %s"
                        .formatted(available2, maxAvailable2));

        // Read one byte (2nd time)
        var c2 = responseInputStream.read();
        assertNotEquals(-1, c2, "Unexpected response body EOF");
        actualResponseBody.append((char) c2);

        // Skip maximum
        var skipped2 = Math.toIntExact(responseInputStream.skip(Long.MAX_VALUE));
        var maxSkipped2 = expectedResponseBody.length() - actualResponseBody.length();
        assertTrue(
                0 <= skipped2 && skipped2 <= maxSkipped2,
                "Unexpected skip(): 0 <= skip (%s) <= %s"
                        .formatted(skipped2, maxAvailable2));
        actualResponseBody.append(
                expectedResponseBody.subSequence(
                        actualResponseBody.length(),
                        actualResponseBody.length() + skipped2));

        // Read the rest
        var buffer = new byte[expectedResponseBody.length() - actualResponseBody.length()];
        for (int readLength;
             0 < (readLength = responseInputStream.read(buffer, 0, buffer.length));) {
            actualResponseBody.append(new String(buffer, 0, readLength, CHARSET));
        }

        // Confirm EOF
        assertEquals(0, responseInputStream.available());
        assertEquals(0, responseInputStream.skip(1));
        assertEquals(-1, responseInputStream.read());

        // Verify the read response body
        assertEquals(expectedResponseBody, actualResponseBody.toString());

    }

}
