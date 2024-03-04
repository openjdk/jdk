/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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


import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import sun.security.testlibrary.CertificateBuilder;

public abstract class IdentitiesBase {

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    protected KeyPair caKeysRsa1024;
    protected X509Certificate caCertificate;
    protected X509Certificate serverCertificate;
    protected KeyPair serverKeysRsa1024;
    protected X509Certificate clientCertificate;
    protected KeyPair clientKeysRsa1024;
    volatile Exception serverException = null;
    volatile Exception clientException = null;
    Thread clientThread = null;
    Thread serverThread = null;

    volatile static boolean serverReady = false;


    abstract void doServerSide() throws Exception;
    abstract void doClientSide() throws Exception;

    public IdentitiesBase() throws Exception {
        setupCertificates();
    }

    void setupCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);

        caKeysRsa1024 = kpg.generateKeyPair();
        caCertificate = CertificateBuilder.createCACertificateBuilder(
            "C = US, ST = Some-State, L = Some-City, O = Some-Org", caKeysRsa1024)
            .build(null, caKeysRsa1024.getPrivate(), "MD5withRSA");


        serverKeysRsa1024 = kpg.generateKeyPair();
        serverCertificate = CertificateBuilder.createClientCertificateBuilder(
            "C = US, ST = Some-State, L = Some-City, O = Some-Org, OU = SSL-Server, CN = localhost",
            serverKeysRsa1024.getPublic(), caKeysRsa1024.getPublic(),
            CertificateBuilder.createIPSubjectAltNameExt(true, "127.0.0.1"))
            .build(caCertificate, caKeysRsa1024.getPrivate(), "MD5withRSA");

        clientKeysRsa1024 = kpg.generateKeyPair();
        clientCertificate = CertificateBuilder.createClientCertificateBuilder(
            "C = US, ST = Some-State, L = Some-City, O = Some-Org, OU = SSL-Client, CN = localhost",
            clientKeysRsa1024.getPublic(), caKeysRsa1024.getPublic(),
            CertificateBuilder.createIPSubjectAltNameExt(true, "127.0.0.1"))
            .build(caCertificate, caKeysRsa1024.getPrivate(), "MD5withRSA");
    }

    void printCertificate(String certificateName, X509Certificate certificate) {
        System.err.println("CERTIFICATE: " + certificateName);
        CertificateBuilder.printCertificate(certificate, System.err);
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
                        System.err.println("Server died...");
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            doServerSide();
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
                        System.err.println("Client died...");
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            doClientSide();
        }
    }

    // get the ssl context
    protected SSLContext getSSLContext(X509Certificate endEntityCert,
               KeyPair endEntityKey, char[] passphrase) throws Exception {

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // import the trused cert
        ks.setCertificateEntry("RSA Export Signer", caCertificate);

        if (endEntityCert != null) {
            Certificate[] chain = new Certificate[2];
            chain[0] = endEntityCert;
            chain[1] = caCertificate;

            // import the key entry.
            ks.setKeyEntry("Whatever", endEntityKey.getPrivate(), passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLS");

        if (endEntityCert != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            ctx.init(null, tmf.getTrustManagers(), null);
        }

        return ctx;
    }

    protected void run() throws Exception {

        if (separateServerThread) {
            startServer(true);
            startClient(false);
        } else {
            startClient(true);
            startServer(false);
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
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null)
            throw serverException;
        if (clientException != null)
            throw clientException;
    }
}
