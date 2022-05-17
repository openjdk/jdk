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

 /*
 * @test
 * @bug 8286646
 * @summary Test that we get a ConnectException when we connect
 *          a bound TCP socket and receive address in use.
 *          On Linux no exception is thrown.
 * @requires os.family=="windows" | os.family == "mac"
 * @run main Test
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Test {

    private static void serverThread(ServerSocket ss) {
        for (;;) {
            try (Socket sock = ss.accept()) {
                System.out.println("** Server accepted connection");
                try (InputStream stream = sock.getInputStream()) {
                    // wait for the client to close connection
                    stream.read();
                }
            } catch (IOException e) {
                System.out.println("** Server thread finished: " + e);
                break;
            }
        }
    }

    public static void main(String args[]) throws Exception {
        InetAddress localAddress = InetAddress.getLoopbackAddress();
        System.out.println("opening server socket");
        try (ServerSocket ss = new ServerSocket(0, 0, localAddress)) {
            Thread thread = new Thread(() -> serverThread(ss));
            thread.start();
            int clientPort;
            System.out.println("Server socket bound to " + ss.getLocalSocketAddress());

            try (Socket cs = new Socket()) {
                cs.setReuseAddress(true);
                cs.bind(new InetSocketAddress(localAddress, 0));
                clientPort = cs.getLocalPort();
                System.out.println("First client socket bound to " + cs.getLocalSocketAddress());
                cs.connect(ss.getLocalSocketAddress());
                System.out.println("First client socket connected");
            }
            try (Socket cs = new Socket()) {
                cs.setReuseAddress(true);
                cs.bind(new InetSocketAddress(localAddress, clientPort));
                System.out.println("Second client socket bound to " + cs.getLocalSocketAddress());
                cs.connect(ss.getLocalSocketAddress());
                throw new Exception("Second client socket unexpectedly connected");
            } catch (ConnectException ce) {
                System.out.println("Caught expected exception " + ce);
            }
            ss.close();
            thread.join();
        }
        System.out.println("Test passed");
    }
}
