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
 * @library /test/lib
 * @summary Session resumption errors
 * @run main/othervm InvalidateSession
 */

import javax.net.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;

import jdk.test.lib.security.SecurityUtils;

public class InvalidateSession {
    static String pathToStores = "../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";
    static Server server;
    static SSLSession cacheSession;
    static final String[] CLIENT_VERSIONS = {"TLSv1", "TLSv1.1", "TLSv1.2"};

    public static void main(String args[]) throws Exception {
        String keyFilename =
                System.getProperty("test.src", "./") + "/" + pathToStores +
                        "/" + keyStoreFile;
        String trustFilename =
                System.getProperty("test.src", "./") + "/" + pathToStores +
                        "/" + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        // drop the supported_versions extension to force test to use the legacy
        // TLS protocol version field during handshakes
        System.setProperty("jdk.tls.client.disableExtensions", "supported_versions");

        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1", "TLSv1.1");
        server = startServer();
        while (!server.started) {
            Thread.yield();
        }

        InvalidateSession test = new InvalidateSession();
        test.clientTest();
        server.go = false;
    }

    /**
     * 4 test iterations
     * 1) Server configured with TLSv1, client with TLSv1, v1.1, v1.2
     * - Handshake should succeed
     * - Session "A" established
     * 2) 2nd iteration, server configured with TLSv1.2 only
     * - Session resumption should fail (Attempted to resume with TLSv1)
     * - Session "A" should be invalidated
     * 3) 3rd iteration, same server/client config
     * - Session "A" should now be invalidated and no longer attempted
     * - Handshake should succeed
     * = Session "B" established
     * 4) 4th iteration, same server/client config
     * - Session "B" should resume without issue
     */
    private void clientTest() throws Exception {
        for (int i = 1; i <= 4; i++) {
            clientConnect(i);
            Thread.sleep(1000);
        }
    }

    public void clientConnect(int testIterationCount) throws Exception {
        System.out.printf("Connecting to: localhost: %s, iteration count %d%n",
                "localhost:" + server.port, testIterationCount);
        SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket = (SSLSocket) ssf.createSocket("localhost", server.port);
        sslSocket.setEnabledProtocols(CLIENT_VERSIONS);

        try {
            sslSocket.startHandshake();
        } catch (Exception e) {

            if (testIterationCount != 2) {
                // only the 2nd handshake should fail (in which case we continue)
                throw new RuntimeException("Unexpected exception", e);
            }
            return;
        }
        if (testIterationCount == 1) {
            // capture the session ID
            cacheSession = sslSocket.getSession();
        } else {
            // should be on 3rd or 4th iteration
            // new session ID should be in use (check in 4th iteration)
            if (Objects.equals(cacheSession, sslSocket.getSession()) && testIterationCount != 4) {
                throw new RuntimeException("Same session should not be resumed");
            }
            cacheSession = sslSocket.getSession();
        }

        System.out.println("Got session: " + sslSocket.getSession());
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
                SSLContext sc = SSLContext.getDefault();
                ServerSocketFactory fac = sc.getServerSocketFactory();
                SSLServerSocket ssock = (SSLServerSocket)
                        fac.createServerSocket(0);
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