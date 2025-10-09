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
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true InputRead100Test
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.*;

public class InputRead100Test {
    private static final String someContext = "/context";

    private static final String someContext = "/context";
    static {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
    }

    @Test
    public void testAutoContinue() throws Exception {
        System.out.println("testContinue()");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
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

            runRawSocketHttpClient(loopback, server.getAddress().getPort(), 0);
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
        try {
            server.createContext(
                someContext,
                msg -> {
                    System.err.println("Handling request: " + msg.getRequestURI());
                    byte[] reply = "Here is my reply!".getBytes(UTF_8);
                    try {
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

            runRawSocketHttpClient(loopback, server.getAddress().getPort(), 0);
        } finally {
            System.out.println("shutting server down");
            server.stop(0);
        }
        System.out.println("Server finished.");
    }

    static void runRawSocketHttpClient(InetAddress address, int port, int contentLength)
        throws Exception {
        Socket socket = null;
        PrintWriter writer = null;
        BufferedReader reader = null;
        boolean foundContinue = false;

        final String CRLF = "\r\n";
        try {
            socket = new Socket(address, port);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            System.out.println("Client connected by socket: " + socket);
            String body = "I will send all the data.";
            if (contentLength <= 0) contentLength = body.getBytes(UTF_8).length;

            writer.print("GET " + someContext + "/ HTTP/1.1" + CRLF);
            writer.print("User-Agent: Java/" + System.getProperty("java.version") + CRLF);
            writer.print("Host: " + address.getHostName() + CRLF);
            writer.print("Accept: */*" + CRLF);
            writer.print("Content-Length: " + contentLength + CRLF);
            writer.print("Connection: keep-alive" + CRLF);
            writer.print("Expect: 100-continue" + CRLF);
            writer.print(CRLF); // Important, else the server will expect that
            // there's more into the request.
            writer.flush();
            System.out.println("Client wrote request to socket: " + socket);
            System.out.println("Client read 100 Continue response from server and headers");
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = reader.readLine();
            for (; line != null; line = reader.readLine()) {
                if (line.isEmpty()) {
                    break;
                }
                System.out.println("interim response \"" + line + "\"");
                if (line.startsWith("HTTP/1.1 100")) {
                    foundContinue = true;
                }
            }
            if (!foundContinue) {
                throw new IOException("Did not receive 100 continue from server");
            }
            writer.print(body);
            writer.flush();
            System.out.println("Client wrote body to socket: " + socket);

            System.out.println("Client start reading from server:");
            line = reader.readLine();
            for (; line != null; line = reader.readLine()) {
                if (line.isEmpty()) {
                    break;
                }
                System.out.println("final response \"" + line + "\"");
                if (foundContinue && line.startsWith("HTTP/1.1 100")) {
                    throw new IOException("continue response sent twice");
                }
            }
            System.out.println("Client finished reading from server");
        } finally {
            // give time to the server to try & drain its input stream
            Thread.sleep(500);
            // closes the client outputstream while the server is draining
            // it
            if (writer != null) {
                writer.close();
            }
            // give time to the server to trigger its assertion
            // error before closing the connection
            Thread.sleep(500);
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