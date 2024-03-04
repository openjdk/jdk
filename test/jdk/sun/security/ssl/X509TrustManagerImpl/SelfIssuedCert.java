/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 6822460
 * @summary support self-issued certificate
 * @library /test/jdk/java/security/testlibrary
 * @modules java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm SelfIssuedCert PKIX
 * @run main/othervm SelfIssuedCert SunX509
 * @author Xuelei Fan
 */

import java.io.*;
import javax.net.ssl.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import sun.security.testlibrary.CertificateBuilder;

public class SelfIssuedCert extends TMBase {

    static char passphrase[] = "passphrase".toCharArray();

    private final X509Certificate trustedCertificate;
    private final X509Certificate targetCertificate;
    private final KeyPair targetKeyPair;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = getSSLContext(null, targetCertificate,
                                            targetKeyPair);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        SSLServerSocket sslServerSocket =
            (SSLServerSocket)sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        sslSocket.setNeedClientAuth(false);

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
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

        SSLContext context = getSSLContext(trustedCertificate, null, null);
        SSLSocketFactory sslsf = context.getSocketFactory();

        SSLSocket sslSocket =
            (SSLSocket)sslsf.createSocket("localhost", serverPort);

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();

        sslSocket.close();
    }

    // get the ssl context
    private static SSLContext getSSLContext(X509Certificate trustedCert,
            X509Certificate targetCert, KeyPair targetKeys) throws Exception {

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // import the trused cert
        if (trustedCert != null) {
            ks.setCertificateEntry("RSA Export Signer", trustedCert);
        }

        if (targetCert != null) {
            Certificate[] chain = null;
            if (trustedCert != null) {
                chain = new Certificate[2];
                chain[0] = targetCert;
                chain[1] = trustedCert;
            } else {
                chain = new Certificate[1];
                chain[0] = targetCert;
            }

            // import the key entry.
            ks.setKeyEntry("Whatever", targetKeys.getPrivate(), passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLS");
        if (targetCert != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
            kmf.init(ks, passphrase);

            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            ks = null;
        } else {
            ctx.init(null, tmf.getTrustManagers(), null);
        }

        return ctx;
    }

    private static String tmAlgorithm;        // trust manager

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
    }

    // use any free port by default
    volatile int serverPort = 0;

    public static void main(String args[]) throws Exception {
        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Get the customized arguments.
         */
        parseArguments(args);

        /*
         * Start the tests.
         */
        new SelfIssuedCert().run();
    }

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SelfIssuedCert() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        KeyPair caKeys = kpg.generateKeyPair();
        trustedCertificate = CertificateBuilder.createCACertificateBuilder(
            "C = US, O = Example, CN = localhost", caKeys)
            .build(null, caKeys.getPrivate(), "SHA256withRSA");

        targetKeyPair = kpg.generateKeyPair();
        targetCertificate = CertificateBuilder.createClientCertificateBuilder(
            "C = US, O = Example, CN = localhost",
            targetKeyPair.getPublic(), caKeys.getPublic())
            .build(trustedCertificate, caKeys.getPrivate(), "SHA256withRSA");

        if (debug) {
            System.err.println("TRUSTED CERTIFICATE");
            CertificateBuilder.printCertificate(trustedCertificate, System.err);

            System.err.println("TARGET CERTIFICATE");
            CertificateBuilder.printCertificate(targetCertificate, System.err);
        }
    }
}
