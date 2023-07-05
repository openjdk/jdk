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
 * @bug 8054022
 * @summary Verify that expect 100-continue doesn't hang
 * @library /test/lib
 * @run junit/othervm HttpURLConnectionExpectContinueTest
 * @run junit/othervm -Djava.net.preferIPv4Stack=true HttpURLConnectionExpectContinueTest
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true HttpURLConnectionExpectContinueTest
 */

import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HttpURLConnectionExpectContinueTest {

    class Control {
        volatile ServerSocket serverSocket = null;
        volatile boolean stop = false;
        volatile boolean respondWith100Continue = false;
        volatile boolean write100ContinueTwice = false;
        volatile String response = null;
    }

    private Thread serverThread = null;
    private volatile Control control;
    static final Logger logger;

    static {
        logger = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");
        logger.setLevel(Level.ALL);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
    }

    @BeforeAll
    public void startServerSocket() throws Exception {
        Control control = this.control = new Control();

        control.serverSocket = new ServerSocket();
        control.serverSocket.setReuseAddress(true);
        control.serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        Runnable runnable = () -> {
            while (!control.stop) {
                try {
                    Socket socket = control.serverSocket.accept();
                    InputStream inputStream = socket.getInputStream();
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                    StringBuilder stringBuilder = new StringBuilder();

                    // Read initial request
                    byte b;
                    while (true) {
                        b = (byte) inputStreamReader.read();
                        stringBuilder.append((char) b);

                        if (stringBuilder.length() >= 4) {
                            char[] lastBytes = new char[4];
                            stringBuilder.getChars(
                                    stringBuilder.length() - 4,
                                    stringBuilder.length(), lastBytes, 0);
                            if (Arrays.equals(lastBytes, new char[]{'\r', '\n', '\r', '\n'})) {
                                break;
                            }
                        }
                    }

                    OutputStream outputStream = socket.getOutputStream();

                    String header = stringBuilder.toString();
                    String contentLengthString = "Content-Length:";

                    // send 100 continue responses if set by test
                    if (control.respondWith100Continue) {
                        outputStream.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes());
                        outputStream.flush();
                        if (control.write100ContinueTwice) {
                            outputStream.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes());
                            outputStream.flush();
                        }
                    }

                    // expect main request to be received
                    int idx = header.indexOf(contentLengthString);

                    if (idx >= 0) {
                        String substr = header.substring(idx + contentLengthString.length());
                        idx = substr.indexOf('\r');
                        substr = substr.substring(0, idx);
                        int contentLength = Integer.parseInt(substr.trim());

                        StringBuilder contentLengthBuilder = new StringBuilder();
                        for (int i = 0; i < contentLength; i++) {
                            b = (byte) inputStreamReader.read();
                            contentLengthBuilder.append((char) b);
                        }

                    } else {
                        StringBuilder contentLengthBuilder = new StringBuilder();
                        while (true) {
                            b = (byte) inputStreamReader.read();
                            contentLengthBuilder.append((char) b);

                            if (contentLengthBuilder.length() >= 2) {
                                char[] lastBytes = new char[2];
                                contentLengthBuilder.getChars(
                                        contentLengthBuilder.length() - 2,
                                        contentLengthBuilder.length(), lastBytes, 0);
                                if (Arrays.equals(lastBytes, new char[]{'\r', '\n'})) {
                                    String lengthInHex =
                                            contentLengthBuilder.substring(0, contentLengthBuilder.length() - 2);

                                    int contentLength = Integer.parseInt(lengthInHex, 16);
                                    char[] body = new char[contentLength];
                                    inputStreamReader.read(body);
                                    break;
                                    // normally we have to parse more data,
                                    // but for simplicity we expect no more chunks...
                                }
                            }
                        }
                    }

                    // send response
                    outputStream.write(control.response.getBytes());
                    outputStream.flush();
                } catch (SocketException e) {
                    // ignore
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        serverThread = new Thread(runnable);
        serverThread.start();
    }

    @AfterAll
    public void stopServerSocket() throws Exception {
        Control control = this.control;
        control.stop = true;
        control.serverSocket.close();
        serverThread.join();
    }

    @Test
    public void testNonChunkedRequestAndNoExpect100ContinueResponse() throws Exception {
        String body = "testNonChunkedRequestAndNoExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = false;
        control.write100ContinueTwice = false;

        HttpURLConnection connection = createConnection();
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testNonChunkedRequestWithExpect100ContinueResponse() throws Exception {
        String body = "testNonChunkedRequestWithExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = true;
        control.write100ContinueTwice = false;

        HttpURLConnection connection = createConnection();
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testNonChunkedRequestWithDoubleExpect100ContinueResponse() throws Exception {
        String body = "testNonChunkedRequestWithDoubleExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = true;
        control.write100ContinueTwice = true;

        HttpURLConnection connection = createConnection();
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testChunkedRequestAndNoExpect100ContinueResponse() throws Exception {
        String body = "testChunkedRequestAndNoExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = false;
        control.write100ContinueTwice = false;

        HttpURLConnection connection = createConnection();
        connection.setChunkedStreamingMode(body.length() / 2);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testChunkedRequestWithExpect100ContinueResponse() throws Exception {
        String body = "testChunkedRequestWithExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = true;
        control.write100ContinueTwice = false;

        HttpURLConnection connection = createConnection();
        connection.setChunkedStreamingMode(body.length() / 2);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testChunkedRequestWithDoubleExpect100ContinueResponse() throws Exception {
        String body = "testChunkedRequestWithDoubleExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = true;
        control.write100ContinueTwice = true;

        HttpURLConnection connection = createConnection();
        connection.setChunkedStreamingMode(body.length() / 2);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testFixedLengthRequestAndNoExpect100ContinueResponse() throws Exception {
        String body = "testFixedLengthRequestAndNoExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = false;
        control.write100ContinueTwice = false;

        HttpURLConnection connection = createConnection();
        connection.setFixedLengthStreamingMode(body.length());
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testFixedLengthRequestWithExpect100ContinueResponse() throws Exception {
        String body = "testFixedLengthRequestWithExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = true;
        control.write100ContinueTwice = false;

        HttpURLConnection connection = createConnection();
        connection.setFixedLengthStreamingMode(body.getBytes().length);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    @Test
    public void testFixedLengthRequestWithDoubleExpect100ContinueResponse() throws Exception {
        String body = "testFixedLengthRequestWithDoubleExpect100ContinueResponse";
        Control control = this.control;
        control.response = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body + "\r\n";
        control.respondWith100Continue = true;
        control.write100ContinueTwice = true;

        HttpURLConnection connection = createConnection();
        connection.setFixedLengthStreamingMode(body.getBytes().length);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(body.getBytes());
        outputStream.close();

        int responseCode = connection.getResponseCode();
        String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        System.err.println("response body: " + responseBody);
        assertTrue(responseCode == 200,
                String.format("Expected 200 response, instead received %s", responseCode));
        assertTrue(body.equals(responseBody),
                String.format("Expected response %s, instead received %s", body, responseBody));
    }

    // Creates a connection with all the common settings used in each test
    private HttpURLConnection createConnection() throws Exception {
        URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(control.serverSocket.getLocalPort())
                .toURL();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(5000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Close");
        connection.setRequestProperty("Expect", "100-Continue");

        return connection;
    }
}
