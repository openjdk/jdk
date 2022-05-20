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

/*
 * @test
 * @bug 8274524
 * @summary 8274524: SSLSocket.close() hangs if it is called during the ssl handshake
 * @library /javax/net/ssl/templates
 * @run main/othervm ClientSocketCloseHang TLSv1.2
 * @run main/othervm ClientSocketCloseHang TLSv1.3
 */


import javax.net.ssl.*;
import java.net.InetAddress;

public class ClientSocketCloseHang implements SSLContextTemplate {

    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.tls.client.protocols", args[0]);
        for (int i = 0; i<= 20; i++) {
            System.err.println("===================================");
            System.err.println("loop " + i);
            System.err.println("===================================");
            new ClientSocketCloseHang().test();
        }
    }

    private void test() throws Exception {
        SSLServerSocket listenSocket = null;
        SSLSocket serverSocket = null;
        ClientSocket clientSocket = null;
        try {
            SSLServerSocketFactory serversocketfactory =
                    createServerSSLContext().getServerSocketFactory();
            listenSocket =
                    (SSLServerSocket)serversocketfactory.createServerSocket(0);
            listenSocket.setNeedClientAuth(false);
            listenSocket.setEnableSessionCreation(true);
            listenSocket.setUseClientMode(false);


            System.err.println("Starting client");
            clientSocket = new ClientSocket(listenSocket.getLocalPort());
            clientSocket.start();

            System.err.println("Accepting client requests");
            serverSocket = (SSLSocket) listenSocket.accept();

            serverSocket.startHandshake();
        } finally {
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (listenSocket != null) {
                listenSocket.close();
            }

            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    private class ClientSocket extends Thread{
        int serverPort = 0;
        SSLSocket clientSocket = null;

        public ClientSocket(int serverPort) {
            this.serverPort = serverPort;
        }

        @Override
        public void run() {
            try {
                System.err.println(
                        "Connecting to server at port " + serverPort);
                SSLSocketFactory sslSocketFactory =
                        createClientSSLContext().getSocketFactory();
                clientSocket = (SSLSocket)sslSocketFactory.createSocket(
                        InetAddress.getLocalHost(), serverPort);
                clientSocket.setSoLinger(true, 3);
                clientSocket.startHandshake();
            } catch (Exception e) {
            }
        }

        public void close() {
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        if (clientSocket != null) {
                            clientSocket.close();
                        }
                    } catch (Exception ex) {
                    }
                }
            };
            try {
                // Close client connection
                t.start();
                t.join(2000); // 2 sec
            } catch (InterruptedException ex) {
                return;
            }

            if (t.isAlive()) {
                throw new RuntimeException("SSL Client hangs on close");
            }
        }
    }
}

