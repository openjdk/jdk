/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4674913
 * @summary Verify that EOFException are correctly handled during the handshake
 * @author Andreas Sterbenz
 * @run main/othervm CloseSocket
 */

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class CloseSocket {

    private static ArrayList<TestCase> testCases = new ArrayList<>();

    static {
        testCases.add(socket -> socket.startHandshake());
        testCases.add(socket -> {
            InputStream in = socket.getInputStream();
            in.read();
        });
        testCases.add(socket -> {
            OutputStream out = socket.getOutputStream();
            out.write(43);
        });
    }

    public static void main(String[] args) throws Exception {
        try (Server server = new Server()) {
            new Thread(server).start();

            SocketFactory factory = SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket("localhost",
                    server.getPort())) {
                socket.setSoTimeout(2000);
                System.out.println("Client established TCP connection");
                boolean failed = false;
                for (TestCase testCase : testCases) {
                    try {
                        testCase.test(socket);
                        System.out.println("ERROR: no exception");
                        failed = true;
                    } catch (IOException e) {
                        System.out.println("Failed as expected: " + e);
                    }
                }
                if (failed) {
                    throw new Exception("One or more tests failed");
                }
            }
        }
    }

    static class Server implements AutoCloseable, Runnable {

        final ServerSocket serverSocket;

        Server() throws IOException {
            serverSocket = new ServerSocket(0);
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            try (Socket s = serverSocket.accept()) {
                System.out.println("Server accepted connection");
                // wait a bit before closing the socket to give
                // the client time to send its hello message
                Thread.currentThread().sleep(100);
                s.close();
                System.out.println("Server closed socket, done.");
            } catch (Exception e) {
                throw new RuntimeException("Problem in test execution", e);
            }
        }

        @Override
        public void close() throws Exception {
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        }
    }

    interface TestCase {
        void test(SSLSocket socket) throws IOException;
    }
}
