/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 7030966
 * @summary Support AEAD CipherSuites
 * @library /javax/net/ssl/templates
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_RSA_WITH_AES_128_GCM_SHA256
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_DH_anon_WITH_AES_128_GCM_SHA256
 */

/*
 * Need additional key materials to run the following cases.
 *
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256
 *
 * Need unlimited JCE Unlimited Strength Jurisdiction Policy to run the
 * following cases.
 *
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_RSA_WITH_AES_256_GCM_SHA384
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
 * @run main/othervm ShortRSAKeyGCM PKIX TLS_DH_anon_WITH_AES_256_GCM_SHA384
 */

import java.net.*;
import java.util.*;
import java.io.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.KeyStore;
import java.security.KeyFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.*;
import java.security.interfaces.*;


public class ShortRSAKeyGCM extends SSLContextTemplate {

    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = createSSLContext(null, new Cert[]{Cert.EE_RSA_MD5_512},
                getServerContextParameters());
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        SSLServerSocket sslServerSocket =
            (SSLServerSocket)sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
        sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write('A');
        sslOS.flush();

        sslSocket.close();
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {

        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(50);
        }

       SSLContext context = createSSLContext(new Cert[]{Cert.CA_RSA_MD5_512},
                null, getClientContextParameters());
        SSLSocketFactory sslsf = context.getSocketFactory();

        SSLSocket sslSocket =
            (SSLSocket)sslsf.createSocket("localhost", serverPort);

        // enable TLSv1.2 only
        sslSocket.setEnabledProtocols(new String[] {"TLSv1.2"});

        // enable a block cipher
        sslSocket.setEnabledCipherSuites(new String[] {cipherSuite});

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write('B');
        sslOS.flush();
        sslIS.read();

        sslSocket.close();
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */
    private static String tmAlgorithm;        // trust manager
    private static String cipherSuite;        // cipher suite

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
        cipherSuite = args[1];
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return new ContextParameters("TLSv1.2", tmAlgorithm, "NewSunX509");
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters("TLSv1.2", tmAlgorithm, "NewSunX509");
    }

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    public static void main(String[] args) throws Exception {
        // reset the security property to make sure that the algorithms
        // and keys used in this test are not disabled.
        Security.setProperty("jdk.certpath.disabledAlgorithms", "MD2");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        /*
         * Get the customized arguments.
         */
        parseArguments(args);

        /*
         * Start the tests.
         */
        new ShortRSAKeyGCM();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    ShortRSAKeyGCM() throws Exception {
        try {
            if (separateServerThread) {
                startServer(true);
                startClient(false);
            } else {
                startClient(true);
                startServer(false);
            }
        } catch (Exception e) {
            // swallow for now.  Show later
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            serverThread.join();
        } else {
            clientThread.join();
        }

        /*
         * When we get here, the test is pretty much over.
         * Which side threw the error?
         */
        Exception local;
        Exception remote;
        String whichRemote;

        if (separateServerThread) {
            remote = serverException;
            local = clientException;
            whichRemote = "server";
        } else {
            remote = clientException;
            local = serverException;
            whichRemote = "client";
        }

        /*
         * If both failed, return the curthread's exception, but also
         * print the remote side Exception
         */
        if ((local != null) && (remote != null)) {
            System.out.println(whichRemote + " also threw:");
            remote.printStackTrace();
            System.out.println();
            throw local;
        }

        if (remote != null) {
            throw remote;
        }

        if (local != null) {
            throw local;
        }
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        System.err.println("Server died..." + e);
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            try {
                doServerSide();
            } catch (Exception e) {
                serverException = e;
            } finally {
                serverReady = true;
            }
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        System.err.println("Client died..." + e);
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            try {
                doClientSide();
            } catch (Exception e) {
                clientException = e;
            }
        }
    }
}
