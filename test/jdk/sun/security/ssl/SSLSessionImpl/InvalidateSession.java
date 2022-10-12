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
 * @bug 8270344
 * @library /test/lib /javax/net/ssl/templates
 * @summary Session resumption errors
 * @run main/othervm InvalidateSession
 */

import javax.net.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;

import jdk.test.lib.security.SecurityUtils;

public class InvalidateSession implements SSLContextTemplate {

    static ServerSocketFactory serverSsf = null;
    static SSLSocketFactory clientSsf = null;

    static Server server;
    static SSLSession cacheSession;
    static final String[] CLIENT_VERSIONS = {"TLSv1", "TLSv1.1", "TLSv1.2"};

    public static void main(String args[]) throws Exception {
        // drop the supported_versions extension to force test to use the legacy
        // TLS protocol version field during handshakes
        System.setProperty("jdk.tls.client.disableExtensions", "supported_versions");
        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1", "TLSv1.1");

        InvalidateSession test = new InvalidateSession();
        test.sessionTest();
        server.go = false;
    }

    /**
     * 3 test iterations
     * 1) Server configured with TLSv1, client with TLSv1, v1.1, v1.2
     * - Handshake should succeed
     * - Session "A" established
     * 2) 2nd iteration, server configured with TLSv1.2 only
     * - Connection should succeed but with a new session due to TLS protocol version change
     * - Session "A" should be invalidated
     * - Session "B" is created
     * 3) 3rd iteration, same server/client config
     * - Session "B" should continue to be in use
     */
    private void sessionTest() throws Exception {
        serverSsf = createServerSSLContext().getServerSocketFactory();
        clientSsf = createClientSSLContext().getSocketFactory();
        server = startServer();
        while (!server.started) {
            Thread.yield();
        }

        for (int i = 1; i <= 3; i++) {
            clientConnect(i);
            Thread.sleep(1000);
        }
    }

    public void clientConnect(int testIterationCount) throws Exception {
        System.out.printf("Connecting to: localhost: %s, iteration count %d%n",
                "localhost:" + server.port, testIterationCount);
        SSLSocket sslSocket = (SSLSocket) clientSsf.createSocket("localhost", server.port);
        sslSocket.setEnabledProtocols(CLIENT_VERSIONS);
        sslSocket.startHandshake();

        System.out.println("Got session: " + sslSocket.getSession());

        if (testIterationCount == 2 && Objects.equals(cacheSession, sslSocket.getSession())) {
            throw new RuntimeException("Same session should not have resumed");
        }
        if (testIterationCount == 3 && !Objects.equals(cacheSession, sslSocket.getSession())) {
            throw new RuntimeException("Same session should have resumed");
        }

        cacheSession = sslSocket.getSession();

        try (
        ObjectOutputStream oos = new ObjectOutputStream(sslSocket.getOutputStream());
        ObjectInputStream ois = new ObjectInputStream(sslSocket.getInputStream())) {
            oos.writeObject("Hello");
            String serverMsg = (String) ois.readObject();
            System.out.println("Server message : " + serverMsg);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            sslSocket.close();
        }
    }

    private static Server startServer() {
        Server server = new Server();
        new Thread(server).start();
        return server;
    }

    private static class Server implements Runnable {
        public volatile boolean go = true;
        public volatile int port = 0;
        public volatile boolean started = false;

        @Override
        public void run() {
            try {
                SSLServerSocket ssock = (SSLServerSocket)
                        serverSsf.createServerSocket(0);
                this.port = ssock.getLocalPort();
                ssock.setEnabledProtocols(new String[]{"TLSv1"});
                started = true;
                while (go) {
                    try {
                        System.out.println("Waiting for connection");
                        Socket sock = ssock.accept();
                        // now flip server to TLSv1.2 mode for successive connections
                        ssock.setEnabledProtocols(new String[]{"TLSv1.2"});
                        try (
                        ObjectInputStream ois = new ObjectInputStream(sock.getInputStream());
                        ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream())) {
                            String recv = (String) ois.readObject();
                            oos.writeObject("Received: " + recv);
                        } catch (SSLHandshakeException she) {
                            System.out.println("Server caught :" + she);
                        } finally {
                            sock.close();
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}

