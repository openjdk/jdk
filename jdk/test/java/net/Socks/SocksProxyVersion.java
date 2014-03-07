/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6964547 5001942
 * @run main/othervm SocksProxyVersion
 * @summary test socksProxyVersion system property
 */

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;

public class SocksProxyVersion implements Runnable {
    final ServerSocket ss;
    volatile boolean failed;

    public static void main(String[] args) throws Exception {
        if (InetAddress.getLocalHost().isLoopbackAddress()) {
            System.out.println("Test cannot run. getLocalHost returns a loopback address");
            return;
        }
        new SocksProxyVersion();
    }

    @SuppressWarnings("try")
    public SocksProxyVersion() throws Exception {
        ss = new ServerSocket(0);
        try (ServerSocket socket = ss) {
            runTest();
        }
    }

    void runTest() throws Exception {
        int port = ss.getLocalPort();
        Thread serverThread = new Thread(this);
        serverThread.start();

        /*
         * Retreving the IP Address of the machine
         * since "localhost" is bypassed as a non-proxy host
         */
        String addr = InetAddress.getLocalHost().getHostAddress();

        System.setProperty("socksProxyHost", addr);
        System.setProperty("socksProxyPort", Integer.toString(port));

        // SOCKS V4
        System.setProperty("socksProxyVersion", Integer.toString(4));
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(addr, port));
        } catch (SocketException e) {
            // java.net.SocketException: Malformed reply from SOCKS server
            // This exception is OK, since the "server" does not implement
            // the socks protocol. It just verifies the version and closes.
        }

        // SOCKS V5
        System.setProperty("socksProxyVersion", Integer.toString(5));
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(addr, port));
        } catch (SocketException e) { /* OK */ }

        serverThread.join();
        if (failed) {
            throw new RuntimeException("socksProxyVersion not being set correctly");
        }
    }

    public void run() {
        try {
            try (Socket s = ss.accept()) {
                int version = (s.getInputStream()).read();
                if (version != 4) {
                    System.out.println("Got " + version + ", expected 4");
                    failed = true;
                }
            }
            try (Socket s = ss.accept()) {
                int version = (s.getInputStream()).read();
                if (version != 5) {
                    System.out.println("Got " + version + ", expected 5");
                    failed = true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
