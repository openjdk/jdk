/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4416068 4478803 4479736
 * @summary 4273544 JSSE request for function forceV3ClientHello()
 *          4479736 setEnabledProtocols API does not work correctly
 *          4478803 Need APIs to determine the protocol versions used in an SSL
 *                  session
 *          4701722 protocol mismatch exceptions should be consistent between
 *                  SSLv3 and TLSv1
 * @run main/othervm testEnabledProtocols
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
 * @author Ram Marti
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;
import javax.net.ssl.*;
import java.security.cert.*;

public class testEnabledProtocols {

    /*
     * For each of the valid protocols combinations, start a server thread
     * that sets up an SSLServerSocket supporting that protocol. Then run
     * a client thread that attemps to open a connection with all
     * possible protocol combinataion.  Verify that we get handshake
     * exceptions correctly. Whenever the connection is established
     * successfully, verify that the negotiated protocol was correct.
     * See results file in this directory for complete results.
     */

    static final String[][] protocolStrings = {
                                {"TLSv1"},
                                {"TLSv1", "SSLv2Hello"},
                                {"TLSv1", "SSLv3"},
                                {"SSLv3", "SSLv2Hello"},
                                {"SSLv3"},
                                {"TLSv1", "SSLv3", "SSLv2Hello"}
                                };

    static final boolean [][] eXceptionArray = {
        // Do we expect exception?       Protocols supported by the server
        { false, true,  false, true,  true,  true }, // TLSv1
        { false, false, false, true,  true,  false}, // TLSv1,SSLv2Hello
        { false, true,  false, true,  false, true }, // TLSv1,SSLv3
        { true,  true,  false, false, false, false}, // SSLv3, SSLv2Hello
        { true,  true,  false, true,  false, true }, // SSLv3
        { false, false, false, false, false, false } // TLSv1,SSLv3,SSLv2Hello
        };

    static final String[][] protocolSelected = {
        // TLSv1
        { "TLSv1",  null,   "TLSv1",  null,   null,     null },

        // TLSv1,SSLv2Hello
        { "TLSv1", "TLSv1", "TLSv1",  null,   null,    "TLSv1"},

        // TLSv1,SSLv3
        { "TLSv1",  null,   "TLSv1",  null,   "SSLv3",  null },

        // SSLv3, SSLv2Hello
        {  null,    null,   "SSLv3", "SSLv3", "SSLv3",  "SSLv3"},

        // SSLv3
        {  null,    null,   "SSLv3",  null,   "SSLv3",  null },

        // TLSv1,SSLv3,SSLv2Hello
        { "TLSv1", "TLSv1", "TLSv1", "SSLv3", "SSLv3", "TLSv1" }

    };

    /*
     * Where do we find the keystores?
     */
    final static String pathToStores = "../../../../etc";
    static String passwd = "passphrase";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    final static boolean debug = false;

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception clientException = null;

    public static void main(String[] args) throws Exception {
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

        if (debug)
            System.setProperty("javax.net.debug", "all");

        new testEnabledProtocols();
    }

    testEnabledProtocols() throws Exception  {
        /*
         * Start the tests.
         */
        SSLServerSocketFactory sslssf =
            (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();
        // sslServerSocket.setNeedClientAuth(true);

        for (int i = 0; i < protocolStrings.length; i++) {
            String [] serverProtocols = protocolStrings[i];
            startServer ss = new startServer(serverProtocols,
                sslServerSocket, protocolStrings.length);
            ss.setDaemon(true);
            ss.start();
            for (int j = 0; j < protocolStrings.length; j++) {
                String [] clientProtocols = protocolStrings[j];
                startClient sc = new startClient(
                    clientProtocols, serverProtocols,
                    eXceptionArray[i][j], protocolSelected[i][j]);
                sc.start();
                sc.join();
                if (clientException != null) {
                    ss.requestStop();
                    throw clientException;
                }
            }
            ss.requestStop();
            System.out.println("Waiting for the server to complete");
            ss.join();
        }
    }

    class startServer extends Thread  {
        private String[] enabledP = null;
        SSLServerSocket sslServerSocket = null;
        int numExpConns;
        volatile boolean stopRequested = false;

        public startServer(String[] enabledProtocols,
                            SSLServerSocket sslServerSocket,
                            int numExpConns) {
            super("Server Thread");
            serverReady = false;
            enabledP = enabledProtocols;
            this.sslServerSocket = sslServerSocket;
            sslServerSocket.setEnabledProtocols(enabledP);
            this.numExpConns = numExpConns;
        }

        public void requestStop() {
            stopRequested = true;
        }

        public void run() {
            int conns = 0;
            while (!stopRequested) {
                SSLSocket socket = null;
                try {
                    serverReady = true;
                    socket = (SSLSocket)sslServerSocket.accept();
                    conns++;

                    // set ready to false. this is just to make the
                    // client wait and synchronise exception messages
                    serverReady = false;
                    socket.startHandshake();
                    SSLSession session = socket.getSession();
                    session.invalidate();

                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    out.write(280);
                    in.read();

                    socket.close();
                    // sleep for a while so that the server thread can be
                    // stopped
                    Thread.sleep(30);
                } catch (SSLHandshakeException se) {
                    // ignore it; this is part of the testing
                    // log it for debugging
                    System.out.println("Server SSLHandshakeException:");
                    se.printStackTrace(System.out);
                } catch (java.io.InterruptedIOException ioe) {
                    // must have been interrupted, no harm
                    break;
                } catch (java.lang.InterruptedException ie) {
                    // must have been interrupted, no harm
                    break;
                } catch (Exception e) {
                    System.out.println("Server exception:");
                    e.printStackTrace(System.out);
                    throw new RuntimeException(e);
                } finally {
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
                if (conns >= numExpConns) {
                    break;
                }
            }
        }
    }

    private static void showProtocols(String name, String[] protocols) {
        System.out.println("Enabled protocols on the " + name + " are: " + Arrays.asList(protocols));
    }

    class startClient extends Thread {
        boolean hsCompleted = false;
        boolean exceptionExpected = false;
        private String[] enabledP = null;
        private String[] serverP = null; // used to print the result
        private String protocolToUse = null;

        startClient(String[] enabledProtocol,
                    String[] serverP,
                    boolean eXception,
                    String protocol) throws Exception {
            super("Client Thread");
            this.enabledP = enabledProtocol;
            this.serverP = serverP;
            this.exceptionExpected = eXception;
            this.protocolToUse = protocol;
        }

        public void run() {
            SSLSocket sslSocket = null;
            try {
                while (!serverReady) {
                    Thread.sleep(50);
                }
                System.out.flush();
                System.out.println("=== Starting new test run ===");
                showProtocols("server", serverP);
                showProtocols("client", enabledP);

                SSLSocketFactory sslsf =
                    (SSLSocketFactory)SSLSocketFactory.getDefault();
                sslSocket = (SSLSocket)
                    sslsf.createSocket("localhost", serverPort);
                sslSocket.setEnabledProtocols(enabledP);
                sslSocket.startHandshake();

                SSLSession session = sslSocket.getSession();
                session.invalidate();
                String protocolName = session.getProtocol();
                System.out.println("Protocol name after getSession is " +
                    protocolName);

                if (protocolName.equals(protocolToUse)) {
                    System.out.println("** Success **");
                } else {
                    System.out.println("** FAILURE ** ");
                    throw new RuntimeException
                        ("expected protocol " + protocolToUse +
                         " but using " + protocolName);
                }

                InputStream in = sslSocket.getInputStream();
                OutputStream out = sslSocket.getOutputStream();
                in.read();
                out.write(280);

                sslSocket.close();

            } catch (SSLHandshakeException e) {
                if (!exceptionExpected) {
                    System.out.println("Client got UNEXPECTED SSLHandshakeException:");
                    e.printStackTrace(System.out);
                    System.out.println("** FAILURE **");
                    clientException = e;
                } else {
                    System.out.println("Client got expected SSLHandshakeException:");
                    e.printStackTrace(System.out);
                    System.out.println("** Success **");
                }
            } catch (RuntimeException e) {
                clientException = e;
            } catch (Exception e) {
                System.out.println("Client got UNEXPECTED Exception:");
                e.printStackTrace(System.out);
                System.out.println("** FAILURE **");
                clientException = e;
            }
        }
    }

}
