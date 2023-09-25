/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8049321
 * @summary Support SHA256WithDSA in JSSE
 * @library /javax/net/ssl/templates
 * @run main/othervm SignatureAlgorithms PKIX "SHA-224,SHA-256"
 *                   TLS_DHE_DSS_WITH_AES_128_CBC_SHA
 * @run main/othervm SignatureAlgorithms PKIX "SHA-1,SHA-224"
 *                   TLS_DHE_DSS_WITH_AES_128_CBC_SHA
 * @run main/othervm SignatureAlgorithms PKIX "SHA-1,SHA-256"
 *                   TLS_DHE_DSS_WITH_AES_128_CBC_SHA
 * @run main/othervm SignatureAlgorithms PKIX "SHA-224,SHA-256"
 *                   TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
 * @run main/othervm SignatureAlgorithms PKIX "SHA-1,SHA-224"
 *                   TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
 * @run main/othervm SignatureAlgorithms PKIX "SHA-1,SHA-256"
 *                   TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
 */

import java.util.*;
import java.io.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public class SignatureAlgorithms extends SSLContextTemplate {

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
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    /*
     * Is the server ready to serve?
     */
    volatile boolean serverReady = false;

    private final Cert[] SERVER_CERTS = {
            SSLContextTemplate.Cert.EE_DSA_SHA1_1024,
            SSLContextTemplate.Cert.EE_DSA_SHA224_1024,
            SSLContextTemplate.Cert.EE_DSA_SHA256_1024,
    };

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = createSSLContext(null, SERVER_CERTS, getServerContextParameters());
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        try (SSLServerSocket sslServerSocket =
                (SSLServerSocket)sslssf.createServerSocket(serverPort)) {

            serverPort = sslServerSocket.getLocalPort();

            /*
             * Signal Client, we're ready for his connect.
             */
            serverReady = true;

            try (SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept()) {
                sslSocket.setEnabledCipherSuites(
                        sslSocket.getSupportedCipherSuites());
                InputStream sslIS = sslSocket.getInputStream();
                OutputStream sslOS = sslSocket.getOutputStream();

                sslIS.read();
                sslOS.write('A');
                sslOS.flush();

                dumpSignatureAlgorithms(sslSocket);
            }
        }
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

        SSLContext context = createSSLContext(new Cert[]{Cert.CA_DSA_SHA1_1024}, null, getClientContextParameters());
        SSLSocketFactory sslsf = context.getSocketFactory();

        try (SSLSocket sslSocket =
                (SSLSocket)sslsf.createSocket("localhost", serverPort)) {

            // enable TLSv1.2 only
            sslSocket.setEnabledProtocols(new String[] {"TLSv1.2"});

            // enable a block cipher
            sslSocket.setEnabledCipherSuites(new String[] {cipherSuite});

            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslOS.write('B');
            sslOS.flush();
            sslIS.read();

            dumpSignatureAlgorithms(sslSocket);
        }
    }

    static void dumpSignatureAlgorithms(SSLSocket sslSocket) throws Exception {

        boolean isClient = sslSocket.getUseClientMode();
        String mode = "[" + (isClient ? "Client" : "Server") + "]";
        ExtendedSSLSession session =
                (ExtendedSSLSession)sslSocket.getSession();
        String[] signAlgs = session.getLocalSupportedSignatureAlgorithms();
        System.out.println(
                mode + " local supported signature algorithms: " +
                Arrays.asList(signAlgs));

        if (!isClient) {
            signAlgs = session.getPeerSupportedSignatureAlgorithms();
            System.out.println(
                mode + " peer supported signature algorithms: " +
                Arrays.asList(signAlgs));
        } else {
            Certificate[] serverCerts = session.getPeerCertificates();

            // server should always send the authentication cert.
            String sigAlg = ((X509Certificate)serverCerts[0]).getSigAlgName();
            System.out.println(
                mode + " the signature algorithm of server certificate: " +
                sigAlg);
            if (sigAlg.contains("SHA1")) {
                if (disabledAlgorithms.contains("SHA-1")) {
                    throw new Exception(
                            "Not the expected server certificate. " +
                            "SHA-1 should be disabled");
                }
            } else if (sigAlg.contains("SHA224")) {
                if (disabledAlgorithms.contains("SHA-224")) {
                    throw new Exception(
                            "Not the expected server certificate. " +
                            "SHA-224 should be disabled");
                }
            } else {    // SHA-256
                if (disabledAlgorithms.contains("SHA-256")) {
                    throw new Exception(
                            "Not the expected server certificate. " +
                            "SHA-256 should be disabled");
                }
            }
        }
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */
    private static String tmAlgorithm;          // trust manager
    private static String disabledAlgorithms;   // disabled algorithms
    private static String cipherSuite;          // cipher suite

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
        disabledAlgorithms = args[1];
        cipherSuite = args[2];
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
        /*
         * debug option
         */
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        /*
         * Get the customized arguments.
         */
        parseArguments(args);


        /*
         * Ignore testing on Windows if only SHA-224 is available.
         */
        if ((Security.getProvider("SunMSCAPI") != null) &&
                (disabledAlgorithms.contains("SHA-1")) &&
                (disabledAlgorithms.contains("SHA-256"))) {

            System.out.println(
                "Windows system does not support SHA-224 algorithms yet. " +
                "Ignore the testing");

            return;
        }

        /*
         * Expose the target algorithms by diabling unexpected algorithms.
         */
        Security.setProperty(
                "jdk.certpath.disabledAlgorithms", disabledAlgorithms);

        /*
         * Reset the security property to make sure that the algorithms
         * and keys used in this test are not disabled by default.
         */
        Security.setProperty( "jdk.tls.disabledAlgorithms", "");

        /*
         * Start the tests.
         */
        new SignatureAlgorithms();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SignatureAlgorithms() throws Exception {
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
