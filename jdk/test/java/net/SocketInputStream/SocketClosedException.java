/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4681556
 * @summary Wrong text if a read is performed on a socket after it
 *      has been closed
 */

import java.io.*;
import java.net.*;

public class SocketClosedException {

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    static void doServerSide() throws Exception {
        ServerSocket serverSocket = new ServerSocket(serverPort);
        serverPort = serverSocket.getLocalPort();

        /*
         * Signal Client, we're ready for a connect.
         */
        serverReady = true;

        Socket socket = serverSocket.accept();

        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        os.write(85);
        os.flush();
        socket.close();
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    static void doClientSide() throws Exception {

        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(5000);
        }

        Socket socket = new Socket("localhost", serverPort);
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        int read = is.read();
        socket.close();
        read = is.read();
    }

    static int serverPort = 0;
    static Exception serverException = null;

    public static void main(String[] args) throws Exception {
        startServer();
        try {
            doClientSide();
        } catch (SocketException e) {
            if (!e.getMessage().equalsIgnoreCase("Socket closed")) {
                throw new Exception("Received a wrong exception message: " +
                                        e.getMessage());
            }
            System.out.println("PASSED: received the right exception message: "
                                        + e.getMessage());
        }
        if (serverException != null) {
            throw serverException;
        }
    }

    static void startServer() {
        Thread serverThread = new Thread() {
            public void run() {
                try {
                    doServerSide();
                } catch (Exception e) {
                    /*
                     * server thread just died.
                     * Release the client, if not active already...
                     */
                    System.err.println("Server died...");
                    serverReady = true;
                    serverException = e;
                }
            }
        };
        serverThread.start();
    }
}
