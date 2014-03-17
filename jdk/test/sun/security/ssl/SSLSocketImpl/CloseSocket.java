/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 */

import java.io.*;
import java.net.*;

import javax.net.ssl.*;

public class CloseSocket {

    public static void main(String[] args) throws Exception {
        final ServerSocket serverSocket = new ServerSocket(0);
        int serverPort = serverSocket.getLocalPort();
        new Thread() {
            public void run() {
                try {
                    Socket s = serverSocket.accept();
                    System.out.println("Server accepted connection");
                    // wait a bit before closing the socket to give
                    // the client time to send its hello message
                    Thread.currentThread().sleep(100);
                    s.close();
                    System.out.println("Server closed socket, done.");
                } catch (Exception e) {
                    System.out.println("Server exception:");
                    e.printStackTrace();
                }
            }
        }.start();
        SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket)factory.createSocket("localhost", serverPort);
        System.out.println("Client established TCP connection");
        boolean failed = false;
        try {
            System.out.println("Starting handshake...");
            socket.startHandshake();
            System.out.println("ERROR: no exception");
            failed = true;
        } catch (IOException e) {
            System.out.println("Failed as expected: " + e);
        }
        try {
            System.out.println("Trying read...");
            InputStream in = socket.getInputStream();
            int b = in.read();
            System.out.println("ERROR: no exception, read: " + b);
            failed = true;
        } catch (IOException e) {
            System.out.println("Failed as expected: " + e);
        }
        try {
            System.out.println("Trying read...");
            OutputStream out = socket.getOutputStream();
            out.write(43);
            System.out.println("ERROR: no exception");
            failed = true;
        } catch (IOException e) {
            System.out.println("Failed as expected: " + e);
        }
        if (failed) {
            throw new Exception("One or more tests failed");
        }
    }

}
