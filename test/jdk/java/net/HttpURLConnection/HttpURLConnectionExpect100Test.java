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
 * @bug 8314978
 * @summary Multiple server call from connection failing with expect100 in
 * getOutputStream
 * @library /test/lib
 * @run junit/othervm HttpURLConnectionExpect100Test
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;

import jdk.test.lib.net.URIBuilder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HttpURLConnectionExpect100Test {

    private HttpServer server;
    private int port;
    static final String RESPONSE = "This is default response.";

    @BeforeAll
    void setup() throws Exception {
        server = HttpServer.create();
        port = server.getPort();
    }

    @AfterAll
    void teardown() throws Exception {
        server.close();
    }

    @Test
    public void expect100ContinueHitCountTest() throws Exception {
        server.resetHitCount();
        URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        //send expect continue
        conn.setRequestProperty("Expect", "100-continue");
        sendRequest(conn);
        getHeaderField(conn);
        assertEquals(1, server.getServerHitCount());
        // Server rejects the expect 100-continue request with 417 response
        assertEquals(417, conn.getResponseCode());
    }

    @Test
    public void defaultRequestHitCountTest() throws Exception {
        server.resetHitCount();
        URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(port)
                .toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        sendRequest(conn);
        getHeaderField(conn);
        assertEquals(1, server.getServerHitCount());
        assertEquals(200, conn.getResponseCode());
        try ( InputStream in = conn.getInputStream()) {
            byte[] data = in.readAllBytes();
            assertEquals(RESPONSE.length(), data.length);
        }
    }

    private void sendRequest(final HttpURLConnection conn) throws Exception {
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(10);
        byte[] payload = new byte[10];
        try ( OutputStream os = conn.getOutputStream()) {
            os.write(payload);
            os.flush();
        } catch (IOException e) {
            // intentional, server will reject the expect 100
        }
    }

    private void getHeaderField(final HttpURLConnection conn) {
        // Call getHeaderFiels in loop, this should not hit server.
        for (int i = 0; i < 5; i++) {
            System.out.println("Getting: field" + i);
            conn.getHeaderField("field" + i);
        }
    }

    static class HttpServer extends Thread {

        private final ServerSocket ss;
        private static HttpServer inst;
        private volatile int hitCount;
        private volatile boolean isRunning;
        private final int port;

        private HttpServer() throws IOException {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            ss = new ServerSocket();
            ss.bind(new InetSocketAddress(loopback, 0));
            port = ss.getLocalPort();
            isRunning = true;
        }

        static HttpServer create() throws IOException {
            if (inst != null) {
                return inst;
            } else {
                inst = new HttpServer();
                inst.setDaemon(true);
                inst.start();
                return inst;
            }
        }

        int getServerHitCount() {
            return hitCount;
        }

        void resetHitCount() {
            hitCount = 0;
        }

        int getPort() {
            return port;
        }

        void close() {
            isRunning = false;
            if (ss != null && !ss.isClosed()) {
                try {
                    ss.close();
                } catch (IOException ex) {
                }
            }
        }

        @Override
        public void run() {
            Socket client;
            try {
                while (isRunning) {
                    client = ss.accept();
                    System.out.println(client.getRemoteSocketAddress().toString());
                    hitCount++;
                    handleConnection(client);
                }
            } catch (IOException ex) {
                // throw exception only if isRunning is true
                if (isRunning) {
                    throw new RuntimeException(ex);
                }
            } finally {
                if (ss != null && !ss.isClosed()) {
                    try {
                        ss.close();
                    } catch (IOException ex) {
                        //ignore
                    }
                }
            }
        }

        private void handleConnection(Socket client) throws IOException {
            try ( BufferedReader in = new BufferedReader(
                new InputStreamReader(client.getInputStream()));
                PrintStream out = new PrintStream(client.getOutputStream())) {
                handle_connection(in, out);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    client.close();
                } catch (IOException e) {
                }
            }
        }

        private void handle_connection(BufferedReader in, PrintStream out)
                throws IOException, InterruptedException {
            StringBuilder clientRequest = new StringBuilder();
            String line = null;
            do {
                line = in.readLine();
                clientRequest.append(line);
            } while (line != null && line.length() != 0);
            if (clientRequest.toString().contains("100-continue")) {
                rejectExpect100Continue(out);
            } else {
                defaultResponse(out);
            }
        }

        private void rejectExpect100Continue(PrintStream out) {
            out.print("HTTP/1.1 417 Expectation Failed\r\n");
            out.print("Server: Test-Server\r\n");
            out.print("Connection: close\r\n");
            out.print("Content-Length: 0\r\n");
            out.print("\r\n");
            out.flush();
        }

        private void defaultResponse(PrintStream out) {
            // send the 200 OK
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Server: Test-Server\r\n");
            out.print("Connection: close\r\n");
            out.print("Content-Length: " + RESPONSE.length() + "\r\n\r\n");
            out.print(RESPONSE);
            out.flush();
        }
    }
}
