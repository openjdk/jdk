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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8170305
 * @summary Tests behaviour of HttpURLConnection when server responds with 1xx interim response status codes
 * @library /test/lib
 * @run testng Response1xxTest
 */
public class Response1xxTest {
    private static final String EXPECTED_RSP_BODY = "Hello World";

    private ServerSocket serverSocket;
    private Http11Server server;
    private String requestURIBase;


    @BeforeClass
    public void setup() throws Exception {
        serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        server = new Http11Server(serverSocket);
        new Thread(server).start();
        requestURIBase = URIBuilder.newBuilder().scheme("http").loopback()
                .port(serverSocket.getLocalPort()).build().toString();
    }

    @AfterClass
    public void teardown() throws Exception {
        if (server != null) {
            server.stop = true;
            System.out.println("(HTTP 1.1) Server stop requested");
        }
        if (serverSocket != null) {
            serverSocket.close();
            System.out.println("Closed (HTTP 1.1) server socket");
        }
    }

    private static final class Http11Server implements Runnable {
        private static final int CONTENT_LENGTH = EXPECTED_RSP_BODY.getBytes(StandardCharsets.UTF_8).length;

        private static final String HTTP_1_1_RSP_200 = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + CONTENT_LENGTH + "\r\n\r\n" +
                EXPECTED_RSP_BODY;

        private static final String REQ_LINE_FOO = "GET /test/foo HTTP/1.1\r\n";
        private static final String REQ_LINE_BAR = "GET /test/bar HTTP/1.1\r\n";
        private static final String REQ_LINE_HELLO = "GET /test/hello HTTP/1.1\r\n";
        private static final String REQ_LINE_BYE = "GET /test/bye HTTP/1.1\r\n";


        private final ServerSocket serverSocket;
        private volatile boolean stop;

        private Http11Server(final ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            System.out.println("Server running at " + serverSocket);
            while (!stop) {
                Socket socket = null;
                try {
                    // accept a connection
                    socket = serverSocket.accept();
                    System.out.println("Accepted connection from client " + socket);
                    // read request
                    final String requestLine;
                    try {
                        requestLine = readRequestLine(socket);
                    } catch (Throwable t) {
                        // ignore connections from potential rogue client
                        System.err.println("Ignoring connection/request from client " + socket
                                + " due to exception:");
                        t.printStackTrace();
                        // close the socket
                        safeClose(socket);
                        continue;
                    }
                    System.out.println("Received following request line from client " + socket
                            + " :\n" + requestLine);
                    final int informationalResponseCode;
                    if (requestLine.startsWith(REQ_LINE_FOO)) {
                        // we will send intermediate/informational 102 response
                        informationalResponseCode = 102;
                    } else if (requestLine.startsWith(REQ_LINE_BAR)) {
                        // we will send intermediate/informational 103 response
                        informationalResponseCode = 103;
                    } else if (requestLine.startsWith(REQ_LINE_HELLO)) {
                        // we will send intermediate/informational 100 response
                        informationalResponseCode = 100;
                    } else if (requestLine.startsWith(REQ_LINE_BYE)) {
                        // we will send intermediate/informational 101 response
                        informationalResponseCode = 101;
                    } else {
                        // unexpected client. ignore and close the client
                        System.err.println("Ignoring unexpected request from client " + socket);
                        safeClose(socket);
                        continue;
                    }
                    try (final OutputStream os = socket.getOutputStream()) {
                        // send informational response headers a few times (spec allows them to
                        // be sent multiple times)
                        for (int i = 0; i < 3; i++) {
                            // send 1xx response header
                            os.write(("HTTP/1.1 " + informationalResponseCode + "\r\n\r\n")
                                    .getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            System.out.println("Sent response code " + informationalResponseCode
                                    + " to client " + socket);
                        }
                        // now send a final response
                        System.out.println("Now sending 200 response code to client " + socket);
                        os.write(HTTP_1_1_RSP_200.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        System.out.println("Sent 200 response code to client " + socket);
                    }
                } catch (Throwable t) {
                    // close the client connection
                    safeClose(socket);
                    // continue accepting any other client connections until we are asked to stop
                    System.err.println("Ignoring exception in server:");
                    t.printStackTrace();
                }
            }
        }

        static String readRequestLine(final Socket sock) throws IOException {
            final InputStream is = sock.getInputStream();
            final StringBuilder sb = new StringBuilder("");
            byte[] buf = new byte[1024];
            while (!sb.toString().endsWith("\r\n\r\n")) {
                final int numRead = is.read(buf);
                if (numRead == -1) {
                    return sb.toString();
                }
                final String part = new String(buf, 0, numRead, StandardCharsets.ISO_8859_1);
                sb.append(part);
            }
            return sb.toString();
        }

        private static void safeClose(final Socket socket) {
            try {
                socket.close();
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    /**
     * Tests that when a HTTP/1.1 server sends intermediate 1xx response codes and then the final
     * response, the client (internally) will ignore those intermediate informational response codes
     * and only return the final response to the application
     */
    @Test
    public void test1xx() throws Exception {
        final URI[] requestURIs = new URI[]{
                new URI(requestURIBase + "/test/foo"),
                new URI(requestURIBase + "/test/bar"),
                new URI(requestURIBase + "/test/hello")};
        for (final URI requestURI : requestURIs) {
            System.out.println("Issuing request to " + requestURI);
            final HttpURLConnection urlConnection = (HttpURLConnection) requestURI.toURL().openConnection();
            final int responseCode = urlConnection.getResponseCode();
            Assert.assertEquals(responseCode, 200, "Unexpected response code");
            final String body;
            try (final InputStream is = urlConnection.getInputStream()) {
                final byte[] bytes = is.readAllBytes();
                body = new String(bytes, StandardCharsets.UTF_8);
            }
            Assert.assertEquals(body, EXPECTED_RSP_BODY, "Unexpected response body");
        }
    }

    /**
     * Tests that when a HTTP/1.1 server sends 101 response code, when the client
     * didn't ask for a connection upgrade, then the request fails with an exception.
     */
    @Test
    public void test101CausesRequestFailure() throws Exception {
        final URI requestURI = new URI(requestURIBase + "/test/bye");
        System.out.println("Issuing request to " + requestURI);
        final HttpURLConnection urlConnection = (HttpURLConnection) requestURI.toURL().openConnection();
        // we expect the request to fail because the server unexpectedly sends a 101 response
        Assert.assertThrows(ProtocolException.class, () -> urlConnection.getResponseCode());
    }
}
