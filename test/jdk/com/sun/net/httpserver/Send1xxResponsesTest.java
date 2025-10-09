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
 * @bug 8349670
 * @summary Test 100 continue response handling
 * @run junit/othervm InputRead100Test
 */
/**
 * @test id=preferIPv6
 * @bug 8349670
 * @summary Test 100 continue response handling ipv6
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true Send1xxResponsesTest
 */
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;


public class Send1xxResponsesTest {
    private static final String someContext = "/context";
    static {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
    }

    @Test
    public void testAutoContinue() throws Exception {
        System.out.println("testAutoContinue()");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        String replyMsg = "Here is my reply!";
        try {
            server.createContext(
                someContext,
                msg -> {
                    System.err.println("Handling request: " + msg.getRequestURI());
                    byte[] reply = "Here is my reply!".getBytes(UTF_8);
                    try {
                        msg.getRequestBody().readAllBytes();
                        msg.sendResponseHeaders(200, reply.length);
                        msg.getResponseBody().write(reply);
                        msg.getResponseBody().close();
                    } finally {
                        System.err.println("Request handled: " + msg.getRequestURI());
                    }
                });
            server.start();
            System.out.println("Server started at port " + server.getAddress().getPort());

            runRawSocketHttpClient(loopback, server.getAddress().getPort(), true, replyMsg, 100, 100, 200);
        } finally {
            System.out.println("shutting server down");
            server.stop(0);
        }
        System.out.println("Server finished.");
    }

    @Test
    public void testManualContinue() throws Exception {
        System.out.println("testAutoContinue()");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        String replyMsg = "Here is my reply!";
        try {
            server.createContext(
                someContext,
                msg -> {
                    System.err.println("Handling request: " + msg.getRequestURI());
                    byte[] reply = replyMsg.getBytes(UTF_8);
                    try {
                        msg.sendResponseHeaders(100, -1);
                        msg.sendResponseHeaders(100, -1);
                        msg.sendResponseHeaders(100, -1);
                        msg.getRequestBody().readAllBytes();
                        msg.sendResponseHeaders(200, reply.length);
                        msg.getResponseBody().write(reply);
                        msg.getResponseBody().close();
                    } finally {
                        System.err.println("Request handled: " + msg.getRequestURI());
                    }
                });
            server.start();
            System.out.println("Server started at port " + server.getAddress().getPort());

            runRawSocketHttpClient(loopback, server.getAddress().getPort(), true, replyMsg, 100, 100, 100, 100, 200);
        } finally {
            System.out.println("shutting server down");
            server.stop(0);
        }
        System.out.println("Server finished.");
    }

    @Test
    public void testSending123() throws Exception {
        System.out.println("testSending123()");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        String replyMsg = "Here is my reply!";
        try {
            server.createContext(
                someContext,
                msg -> {
                    System.err.println("Handling request: " + msg.getRequestURI());
                    byte[] reply = replyMsg.getBytes(UTF_8);
                    try {
                        msg.sendResponseHeaders(123, -1);
                        msg.sendResponseHeaders(123, -1);
                        msg.sendResponseHeaders(123, -1);
                        msg.getRequestBody().readAllBytes();
                        msg.sendResponseHeaders(200, reply.length);
                        msg.getResponseBody().write(reply);
                        msg.getResponseBody().close();
                    } finally {
                        System.err.println("Request handled: " + msg.getRequestURI());
                    }
                });
            server.start();
            System.out.println("Server started at port " + server.getAddress().getPort());

            runRawSocketHttpClient(loopback, server.getAddress().getPort(), false, replyMsg, 123, 123, 123, 200);
        } finally {
            System.out.println("shutting server down");
            server.stop(0);
        }
        System.out.println("Server finished.");
    }

    static void runRawSocketHttpClient(InetAddress address, int port, boolean expectContinue, String expectedReply, int... expectedStatusCodes)
        throws Exception {
        Socket socket = null;
        PrintWriter writer = null;
        BufferedReader reader = null;

        final String CRLF = "\r\n";
        try {
            socket = new Socket(address, port);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            System.out.println("Client connected by socket: " + socket);
            String body = "I will send all the data.";
            var contentLength = body.getBytes(UTF_8).length;

            writer.print("GET " + someContext + "/ HTTP/1.1" + CRLF);
            writer.print("User-Agent: Java/" + System.getProperty("java.version") + CRLF);
            writer.print("Host: " + address.getHostName() + CRLF);
            writer.print("Accept: */*" + CRLF);
            writer.print("Content-Length: " + contentLength + CRLF);
            writer.print("Connection: keep-alive" + CRLF);
            if (expectContinue) {
                writer.print("Expect: 100-continue" + CRLF);
            }
            writer.print(CRLF); // Important, else the server will expect that
            // there's more into the request.
            writer.print(body);
            System.out.println("Client wrote body to socket: " + socket);
            writer.flush();
            System.out.println("Client wrote request to socket: " + socket);
            System.out.println("Client read 100 Continue response from server and headers");
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = reader.readLine();
            List<String> statusCodes = new ArrayList<>();
            for (; line != null; line = reader.readLine()) {
                if (line.isEmpty() && statusCodes.size() == expectedStatusCodes.length) {
                    break;
                }
                if (line.startsWith("HTTP/1.1")) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        statusCodes.add(parts[1]);
                    }
                }
            }
            System.out.println("Received status codes: " + statusCodes);

            System.out.println("Client start reading from server:");
            line = reader.readLine();
            for (; line != null; line = reader.readLine()) {
                assertEquals(line, expectedReply);
                System.out.println("final response \"" + line + "\"");
            }
            System.out.println("Client finished reading from server");
            // Assert that the received status codes match the expected ones
            if (statusCodes.size() != expectedStatusCodes.length) {
                throw new IOException("Expected " + expectedStatusCodes.length + " status codes, but got " + statusCodes.size());
            }
            for (int i = 0; i < expectedStatusCodes.length; i++) {
                if (!statusCodes.get(i).equals(String.valueOf(expectedStatusCodes[i]))) {
                    throw new IOException("Expected status code " + expectedStatusCodes[i] + " at position " + i +
                        ", but got " + statusCodes.get(i));
                }
            }
        } finally {
            // give time to the server to try & drain its input stream
            Thread.sleep(500);
            // closes the client outputstream while the server is draining
            // it
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