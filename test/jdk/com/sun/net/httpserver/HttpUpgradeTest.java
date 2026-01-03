/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8373877
 * @summary Test HTTP upgrade functionality
 * @run main HttpUpgradeTest
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpUpgradeHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HttpUpgradeTest {

    private static final CountDownLatch upgradeLatch = new CountDownLatch(1);
    private static final CountDownLatch messageLatch = new CountDownLatch(1);
    private static volatile String receivedMessage = null;

    public static void main(String[] args) throws Exception {
        HttpServer server = null;
        try {
            // Create and start server
            InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            server = HttpServer.create(addr, 0);
            
            server.createContext("/upgrade", new UpgradeHandler());
            server.setExecutor(null);
            server.start();

            int port = server.getAddress().getPort();
            System.out.println("Server started on port: " + port);

            // Test upgrade
            testUpgrade(port);

            System.out.println("Test PASSED");
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    static class UpgradeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var headers = exchange.getRequestHeaders();
            String upgrade = headers.getFirst("Upgrade");
            
            if ("echo-protocol".equalsIgnoreCase(upgrade)) {
                // Set upgrade response headers
                var responseHeaders = exchange.getResponseHeaders();
                responseHeaders.set("Upgrade", "echo-protocol");
                responseHeaders.set("Connection", "Upgrade");
                
                // Perform upgrade
                exchange.upgrade(new EchoUpgradeHandler());
            } else {
                // Not an upgrade request
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    static class EchoUpgradeHandler implements HttpUpgradeHandler {
        @Override
        public void handle(InputStream input, OutputStream output) throws IOException {
            upgradeLatch.countDown();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(output, true)) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Received in upgrade handler: " + line);
                    receivedMessage = line;
                    messageLatch.countDown();
                    
                    // Echo back
                    writer.println("ECHO: " + line);
                    break; // Exit after one message for test simplicity
                }
            }
        }
    }

    private static void testUpgrade(int port) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            
            // Send upgrade request
            String request = "GET /upgrade HTTP/1.1\r\n" +
                           "Host: localhost:" + port + "\r\n" +
                           "Upgrade: echo-protocol\r\n" +
                           "Connection: Upgrade\r\n" +
                           "\r\n";
            
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            
            // Read response
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            System.out.println("Status: " + statusLine);
            
            if (!statusLine.contains("101")) {
                throw new RuntimeException("Expected 101 Switching Protocols, got: " + statusLine);
            }
            
            // Read headers
            String line;
            boolean upgradeHeaderFound = false;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                System.out.println("Header: " + line);
                if (line.toLowerCase().contains("upgrade: echo-protocol")) {
                    upgradeHeaderFound = true;
                }
            }
            
            if (!upgradeHeaderFound) {
                throw new RuntimeException("Upgrade header not found in response");
            }
            
            // Wait for upgrade handler to be invoked
            if (!upgradeLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Upgrade handler not invoked");
            }
            
            // Now the protocol is upgraded, send a message
            PrintWriter writer = new PrintWriter(out, true);
            writer.println("Hello from client");
            
            // Wait for message to be received
            if (!messageLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Message not received by upgrade handler");
            }
            
            if (!"Hello from client".equals(receivedMessage)) {
                throw new RuntimeException("Unexpected message received: " + receivedMessage);
            }
            
            // Read echo response
            String echo = reader.readLine();
            System.out.println("Echo response: " + echo);
            
            if (!echo.equals("ECHO: Hello from client")) {
                throw new RuntimeException("Unexpected echo: " + echo);
            }
        }
    }
}
