/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 6223624
 * @library /test/lib
 * @summary SSLSocket.setUseClientMode() fails to throw expected
 *        IllegalArgumentException
 * @run main/othervm SetClientMode TLSv1
 * @run main/othervm SetClientMode TLSv1.1
 * @run main/othervm SetClientMode TLSv1.2
 * @run main/othervm SetClientMode TLSv1.3
 */

/*
 * Attempts to replicate a TCK test failure which creates SSLServerSockets
 * and then runs client threads which connect and start handshaking. Once
 * handshaking is begun the server side attempts to invoke
 * SSLSocket.setUseClientMode() on one or the other of the ends of the
 * connection, expecting an IllegalArgumentException.
 *
 * If the server side of the connection tries setUseClientMode() we
 * see the expected exception. If the setting is tried on the
 * client side SSLSocket, we do *not* see the exception, except
 * occasionally on the very first iteration.
 */

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import jdk.test.lib.security.SecurityUtils;

public class SetClientMode {
    volatile int serverPort = 0;
    private static final CountDownLatch handshakeComplete = new CountDownLatch(1);

    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";


    public SetClientMode() {
        // trivial constructor
    }

    public static void main(String[] args) throws Exception {
        String protocol = args[0];

        if ("TLSv1".equals(protocol) || "TLSv1.1".equals(protocol)) {
          SecurityUtils.removeFromDisabledTlsAlgs(protocol);
        }
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

        new SetClientMode().run(protocol);
    }

    public void run(String protocol) throws Exception {
        Exception modeException = null ;

        // Create a server socket
        SSLServerSocketFactory ssf =
            (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket =
            (SSLServerSocket)ssf.createServerSocket(serverPort);
        serverSocket.setEnabledProtocols(new String[] { protocol });
        serverPort = serverSocket.getLocalPort();

        // Create a client socket
        SSLSocketFactory sf = (SSLSocketFactory)SSLSocketFactory.getDefault();
        SSLSocket clientSocket = (SSLSocket)sf.createSocket(
                                InetAddress.getLocalHost(),
                                serverPort );

        // Create a client which will use the SSLSocket to talk to the server
        SocketClient client = new SocketClient(clientSocket);

        // Start the client and then accept any connection
        client.start();

        SSLSocket connectedSocket = (SSLSocket)serverSocket.accept();

        // force handshaking to complete
        connectedSocket.getSession();

        if (!handshakeComplete.await(5, TimeUnit.SECONDS)) {
          throw new RuntimeException("Handshake didn't complete within 5 seconds.");
        }

        try {
            // Now try invoking setClientMode() on one
            // or the other of our two sockets. We expect
            // to see an IllegalArgumentException because
            // handshaking has begun.
            clientSocket.setUseClientMode(false);

            modeException = new Exception("no IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
            System.out.println("succeeded, we can't set the client mode");
        } catch (Exception e) {
            modeException = e;
        } finally {
            // Shut down.
            connectedSocket.close();
            serverSocket.close();

            if (modeException != null) {
                throw modeException;
            }
        }

        return;
    }

    // A thread-based client which does nothing except
    // start handshaking on the socket it's given.
    class SocketClient extends Thread {
        SSLSocket clientsideSocket;

        public SocketClient( SSLSocket s ) {
            clientsideSocket = s;
        }

        public void run() {
            try {
              clientsideSocket.startHandshake();
              handshakeComplete.countDown();

                // If we were to invoke setUseClientMode()
                // here, the expected exception will happen.
                //clientsideSocket.getSession();
                //clientsideSocket.setUseClientMode( false );
            } catch ( Exception e ) {
                e.printStackTrace();
            } finally {
                try {
                    clientsideSocket.close();
                } catch ( IOException e ) {
                    // eat it
                }
            }
        }
    }
}
