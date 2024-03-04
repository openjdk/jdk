/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6916074
 * @summary Add support for TLS 1.2
 * @library /test/jdk/java/security/testlibrary
 * @modules java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm SunX509ExtendedTM
 */

import java.security.*;
import java.security.cert.X509Certificate;
import java.io.*;
import javax.net.ssl.*;
import java.security.cert.Certificate;
import sun.security.testlibrary.CertificateBuilder;
import sun.security.x509.DNSName;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.SubjectAlternativeNameExtension;


public class SunX509ExtendedTM extends TMBase {
    private final X509Certificate trustedCertificate;
    private final X509Certificate serverCertificate;
    private final KeyPair serverKeyPair;
    private final X509Certificate clientCertificate;
    private final KeyPair clientKeyPair;

    static char[] passphrase = "passphrase".toCharArray();

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
        SSLContext context = getSSLContext(serverCertificate, serverKeyPair, passphrase);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        SSLServerSocket sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();


        // enable endpoint identification
        // ignore, we may test the feature when known how to parse client
        // hostname
        //SSLParameters params = sslServerSocket.getSSLParameters();
        //params.setEndpointIdentificationAlgorithm("HTTPS");
        //sslServerSocket.setSSLParameters(params);

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        sslSocket.setNeedClientAuth(true);

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

        SSLContext context = getSSLContext(clientCertificate, clientKeyPair, passphrase);

        SSLSocketFactory sslsf = context.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket)
            sslsf.createSocket("localhost", serverPort);

        // enable endpoint identification
        SSLParameters params = sslSocket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        sslSocket.setSSLParameters(params);

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();

        sslSocket.close();
    }

    // get the ssl context
    private SSLContext getSSLContext(X509Certificate eeCertificate, KeyPair eeKeyPair, char[] passphrase) throws Exception {

        // generate certificate from cert string
        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // import the trused cert
        ks.setCertificateEntry("RSA Export Signer", trustedCertificate);

        if (eeCertificate != null) {
            Certificate[] chain = new Certificate[2];
            chain[0] = eeCertificate;
            chain[1] = trustedCertificate;

            // import the key entry.
            ks.setKeyEntry("Whatever", eeKeyPair.getPrivate(), passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        TrustManager tms[] = tmf.getTrustManagers();
        if (tms == null || tms.length == 0) {
            throw new Exception("unexpected trust manager implementation");
        } else {
           if (!(tms[0] instanceof X509ExtendedTrustManager)) {
            throw new Exception("unexpected trust manager implementation: "
                                + tms[0].getClass().getCanonicalName());
           }
        }

        SSLContext ctx = SSLContext.getInstance("TLS");

        if (eeCertificate != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            ctx.init(null, tmf.getTrustManagers(), null);
        }

        return ctx;
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    // use any free port by default
    volatile int serverPort = 0;

    public static void main(String args[]) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "MD2, RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new SunX509ExtendedTM().run();
    }

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SunX509ExtendedTM() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);

        KeyPair caKeys = kpg.generateKeyPair();
        trustedCertificate = CertificateBuilder.createCACertificateBuilder(
            "C = US, ST = Some-State, L = Some-City, O = Some-Org", caKeys)
            .build(null, caKeys.getPrivate(), "MD5withRSA");

        GeneralNames gns = new GeneralNames();
        gns.add(new GeneralName(new DNSName("localhost")));

        serverKeyPair = kpg.generateKeyPair();
        serverCertificate = CertificateBuilder.createClientCertificateBuilder(
            "C=US, ST=Some-State, L=Some-City, O=Some-Org, OU=SSL-Server, CN=localhost",
            serverKeyPair.getPublic(), caKeys.getPublic(),
            new SubjectAlternativeNameExtension(true, gns))
            .build(trustedCertificate, caKeys.getPrivate(), "MD5withRSA");

        clientKeyPair = kpg.generateKeyPair();
        clientCertificate = CertificateBuilder.createClientCertificateBuilder(
            "C=US, ST=Some-State, L=Some-City, O=Some-Org, OU=SSL-Client, CN=localhost",
            clientKeyPair.getPublic(), caKeys.getPublic(),
            new SubjectAlternativeNameExtension(true, gns))
            .build(trustedCertificate, caKeys.getPrivate(), "MD5withRSA");
    }
}
