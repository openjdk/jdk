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

/**
 * @test id=default
 * @bug 8368695
 * @summary Test 101 switching protocal response handling
 * @run junit/othervm SwitchingProtocolTest
 */
/**
 * @test id=preferIPv6
 * @bug 8368695
 * @summary Test 101 switching protocal response handling ipv6
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true SwitchingProtocolTest
 */

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

public class SwitchingProtocolTest {

    private static final String RESPONSE_BODY = "Here is my reply!";
    private static final String REQUEST_BODY = "I will send all the data.";
    private static final int REQUEST_LENGTH = REQUEST_BODY.getBytes().length;
    private static final int msgCode = 101;
    private static final String someContext = "/context";

    static {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
    }

    @Test
    public void testSendResponse() throws Exception {
        System.out.println("testSendResponse()");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        try {
            server.createContext(
                someContext,
                msg -> {
                    byte[] reply = RESPONSE_BODY.getBytes(UTF_8);
                    System.err.println("Handling request: " + msg.getRequestURI());
                    try {
                        assertEquals(-1, msg.getRequestBody().read());
                        assertEquals(0, msg.getRequestBody().readAllBytes().length);
                        msg.sendResponseHeaders(msgCode, -1);
                        // Read and assert request body
                        byte[] requestBytes = msg.getRequestBody().readNBytes(REQUEST_LENGTH);
                        String requestBody = new String(requestBytes, UTF_8);
                        assertEquals(REQUEST_BODY, requestBody);
                        msg.getResponseBody().write(reply);
                        msg.getResponseBody().flush();
                    } finally {
                        // don't close the exchange and don't close any stream
                        // to trigger the assertion.
                        System.err.println("Request handled: " + msg.getRequestURI());
                    }
                });
            server.start();
            System.out.println("Server started at port " + server.getAddress().getPort());

            runRawSocketHttpClient(loopback, server.getAddress().getPort());
        } finally {
            System.out.println("shutting server down");
            executor.shutdown();
            server.stop(0);
        }
        System.out.println("Server finished.");
    }

    @Test
    public void testCloseOutputStream() throws Exception {
        System.out.println("testCloseOutputStream()");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        try {
            server.createContext(
                someContext,
                msg -> {
                    System.err.println("Handling request: " + msg.getRequestURI());
                    byte[] reply = RESPONSE_BODY.getBytes(UTF_8);
                    try {
                        try {
                            msg.sendResponseHeaders(msgCode, -1);
                            msg.getResponseBody().write(reply);
                            msg.getResponseBody().flush();
                            // Read and assert request body
                            byte[] requestBytes = msg.getRequestBody().readNBytes(REQUEST_LENGTH);
                            String requestBody = new String(requestBytes, UTF_8);
                            assertEquals(REQUEST_BODY, requestBody);
                            msg.getResponseBody().close();
                            Thread.sleep(50);
                        } catch (IOException | InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    } finally {
                        System.err.println("Request handled: " + msg.getRequestURI());
                    }
                });
            server.start();
            System.out.println("Server started at port " + server.getAddress().getPort());

            runRawSocketHttpClient(loopback, server.getAddress().getPort());
        } finally {
            System.out.println("shutting server down");
            executor.shutdown();
            server.stop(0);
        }
        System.out.println("Server finished.");
    }


    @Test
    public void testException() throws Exception {
        System.out.println("testException()");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        try {
            server.createContext(
                someContext,
                msg -> {
                    msg.sendResponseHeaders(msgCode, -1);
                    throw new RuntimeException("Simulated exception");
                });
            server.start();
            System.out.println("Server started at port " + server.getAddress().getPort());

            runRawSocketHttpClient(loopback, server.getAddress().getPort(), true);
        } finally {
            System.out.println("shutting server down");
            executor.shutdown();
            server.stop(0);
        }
        System.out.println("Server finished.");
    }

    static void runRawSocketHttpClient(InetAddress address, int port) throws Exception {
        runRawSocketHttpClient(address, port, false);
    }

    static void runRawSocketHttpClient(InetAddress address, int port, boolean exception)
        throws Exception {
        Socket socket = null;
        PrintWriter writer = null;
        BufferedReader reader = null;
        final String CRLF = "\r\n";
        try {
            socket = new Socket(address, port);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            System.out.println("Client connected by socket: " + socket);
            String body = REQUEST_BODY;
            var contentLength = body.getBytes(UTF_8).length;

            writer.print("GET " + someContext + "/ HTTP/1.1" + CRLF);
            writer.print("User-Agent: Java/" + System.getProperty("java.version") + CRLF);
            writer.print("Host: " + address.getHostName() + CRLF);
            writer.print("Accept: */*" + CRLF);
            writer.print("Content-Length: " + contentLength + CRLF);
            writer.print("Connection: keep-alive" + CRLF);
            writer.print("Connection: Upgrade" + CRLF);
            writer.print("Upgrade: custom" + CRLF);
            writer.print(CRLF); // Important, else the server will expect that
            // there's more into the request.
            writer.flush();
            System.out.println("Client wrote request to socket: " + socket);

            System.out.println("Client wrote body to socket: " + socket);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Client start reading from server:");
            String line = reader.readLine();
            StringBuilder responseBody = new StringBuilder();
            for (; line != null; line = reader.readLine()) {
                if (line.isEmpty()) {
                    break;
                }
                System.out.println("\"" + line + "\"");
            }
            // Write request body after headers
            writer.print(body);
            writer.flush();
            // Read response body
            char[] buf = new char[RESPONSE_BODY.length()];
            int read = reader.read(buf);
            if (read > 0) {
                responseBody.append(buf, 0, read);
            }
            String actualResponse = responseBody.toString();
            assertEquals(RESPONSE_BODY, actualResponse, "Response body does not match");
            System.out.println("Client finished reading from server");
        } catch (SocketException se) {
            if (!exception) {
                fail("Unexpected exception: " + se);
            }
            assertEquals("Connection reset", se.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException logOrIgnore) {
                    logOrIgnore.printStackTrace();
                }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException logOrIgnore) {
                    logOrIgnore.printStackTrace();
                }
            }
        }
        System.out.println("Client finished.");
    }
}
