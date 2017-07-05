/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4393337
 * @summary [TEST RUNS ON SOLARIS ONLY] Throw an InterruptedIOException
 * when read on SSLSocket is * interrupted.
 */

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import javax.net.ssl.*;

/**
 * Interrupts an SSL socket that is blocked on a read. An
 * InterruptedIOException will be thrown and handled within the test if the
 * test completes correctly.
 */

public class InterruptedIO {

    /**
     * Starts a client and a server thread. Gives the client enough time to
     * block in a read, then interrupts it.
     */
    public static void main(String[] args) throws Exception {

        String reason =
            "Test valid only on SunOS.\n" +
            "=========================\n" +
            "It was determined that Thread.interrupt() could \n" +
            "not be reliably return InterruptedIOException \n" +
            "on non-Solaris implementations.  Thread.interrupt() \n" +
            "API was updated in merlin (JDK 1.4) to reflect this.\n";
        System.out.println(reason);

        String osName = System.getProperty("os.name", "");
        if (!osName.equalsIgnoreCase("SunOS")) {
            System.out.println("Ignoring test on '" + osName + "'");
            return;
        }

        String testRoot = System.getProperty("test.src", ".");
        System.setProperty("javax.net.ssl.keyStore",
                           testRoot +
                           "/../../../../../../../etc/keystore");
        System.setProperty("javax.net.ssl.keyStorePassword",
                           "passphrase");
        System.setProperty("javax.net.ssl.trustStore",
                           testRoot +
                           "/../../../../../../../etc/truststore");

        Server server = new Server();
        server.start();

        Client client = new Client(server.getPort()); // Will do handshake
        client.start(); // Will block in read

        // sleep for 5 seconds
        System.out.println("Main - Pausing for 5 seconds...");
        Thread.sleep(5 * 1000);

        System.out.println("Main - Interrupting client reader thread");
        client.interrupt();
        client.join(); // Wait for client thread to complete

        if (client.failed())
            throw new Exception("Main - Test InterruptedIO failed "
                                + "on client side.");
        else
            System.out.println("Main - Test InterruptedIO successful!");
    }

    /**
     * Accepts an incoming SSL Connection. Then blocks in a read.
     */
    static class Server extends Thread {

        private SSLServerSocket ss;

        public Server() throws Exception {
            ss = (SSLServerSocket) SSLServerSocketFactory.getDefault().
                createServerSocket(0);
        }

        public int getPort() {
            return ss.getLocalPort();
        }

        public void run() {
            try {
                System.out.println("Server - Will accept connections on port "
                                   + getPort());
                Socket s = ss.accept();
                InputStream is = s.getInputStream();
                // We want the client to block so deadlock
                is.read();
            } catch (IOException e) {
                // Happens when client closese connection.
                // If an error occurs, Client will detect problem
            }
        }
    }

    /**
     * Initiates an SSL connection to a server. Then blocks in a read. It
     * should be interrupted by another thread. An InterruptedIOException
     * is expected to be thrown.
     */
    static class Client extends Thread {

        private SSLSocket socket;
        private InputStream inStream;
        private boolean failed = false;

        public Client(int port) throws Exception {
            socket = (SSLSocket) SSLSocketFactory.getDefault().
                createSocket("localhost", port);
            inStream = socket.getInputStream();
            System.out.println("Client - "
                               + "Connected to: localhost" + ":" + port);
            System.out.println("Client - "
                               + "Doing SSL Handshake...");
            socket.startHandshake(); // Asynchronous call
            System.out.println("Client - Done with SSL Handshake");
        }

        public void run() {

            try {
                System.out.println("Client - Reading from input stream ...");
                if (inStream.read() == -1) {
                    System.out.println("Client - End-of-stream detected");
                    failed = true;
                }
            } catch (InterruptedIOException e) {
                System.out.println("Client - "
                                   + "As expected, InterruptedIOException "
                                   + "was thrown. Message: "
                                   + e.getMessage());
            } catch (Exception e) {
                System.out.println("Client - Unexpected exception:");
                e.printStackTrace();
                failed = true;
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Squelch it
                }
            }
        }

        public boolean failed() {
            return failed;
        }

    }

}
