/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.security.CertificateBuilder;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public abstract class IdentitiesBase {
    static X509Certificate trustedCert;
    static X509Certificate serverCert;
    static X509Certificate clientCert;
    static KeyPair serverKeys;
    static KeyPair clientKeys;
    static char[] passphrase = "passphrase".toCharArray();

    protected final String protocol;
    protected final String signatureAlg;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

    public IdentitiesBase(String protocol, String signatureAlg) throws Exception {
        this.protocol = protocol;
        this.signatureAlg = signatureAlg;

        setupCertificates();
    }

    protected String getTrustedDname() {
        return "O=Some-Org, L=Some-City, ST=Some-State, C=US";
    }

    protected String getServerDname() {
        return "CN=localhost, OU=SSL-Server, O=Some-Org, L=Some-City, ST=Some-State, C=US";
    }

    protected String getClientDname() {
        return "CN=localhost, OU=SSL-Client, O=Some-Org, L=Some-City, ST=Some-State, C=US";
    }

    protected CertificateBuilder customizeServerCert(CertificateBuilder builder) throws Exception {
        return builder;
    }

    protected CertificateBuilder customizeClientCert(CertificateBuilder builder) throws Exception {
        return builder;
    }

    private void setupCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        KeyPair caKeys = kpg.generateKeyPair();
        serverKeys = kpg.generateKeyPair();
        clientKeys = kpg.generateKeyPair();

        trustedCert = createTrustedCert(getTrustedDname(), caKeys, signatureAlg);
        if (debug) {
            System.out.println("----------- Trusted Cert -----------");
            CertificateBuilder.printCertificate(trustedCert, System.out);
        }

         CertificateBuilder builder = CertificateBuilder.newCertificateBuilder(
                        getServerDname(),
                        serverKeys.getPublic(), caKeys.getPublic(),
                        CertificateBuilder.KeyUsage.DIGITAL_SIGNATURE,
                        CertificateBuilder.KeyUsage.NONREPUDIATION,
                        CertificateBuilder.KeyUsage.KEY_ENCIPHERMENT)
                .addBasicConstraintsExt(false, false, -1)
                .addExtension(CertificateBuilder.createIPSubjectAltNameExt(true, "127.0.0.1", "::1"))
                .setOneHourValidity();
        serverCert = customizeServerCert(builder)
                .build(trustedCert, caKeys.getPrivate(), signatureAlg);
        if (debug) {
            System.out.println("----------- Server Cert -----------");
            CertificateBuilder.printCertificate(serverCert, System.out);
        }

         builder = CertificateBuilder.newCertificateBuilder(
                        getClientDname(),
                        clientKeys.getPublic(), caKeys.getPublic(),
                        CertificateBuilder.KeyUsage.DIGITAL_SIGNATURE,
                        CertificateBuilder.KeyUsage.NONREPUDIATION,
                        CertificateBuilder.KeyUsage.KEY_ENCIPHERMENT)
                .addExtension(CertificateBuilder.createIPSubjectAltNameExt(true, "127.0.0.1", "::1"))
                .addBasicConstraintsExt(false, false, -1)
                .setOneHourValidity();
        builder = customizeClientCert(builder);
        clientCert = builder.build(trustedCert, caKeys.getPrivate(), signatureAlg);
        if (debug) {
            System.out.println("----------- Client Cert -----------");
            CertificateBuilder.printCertificate(clientCert, System.out);
        }
    }

    protected static X509Certificate createTrustedCert(String dname, KeyPair caKeys,
                                                       String signatureAlgo) throws Exception {
        SecureRandom random = new SecureRandom();

        KeyIdentifier kid = new KeyIdentifier(caKeys.getPublic());
        GeneralNames gns = new GeneralNames();
        GeneralName name = new GeneralName(new X500Name(dname));
        gns.add(name);
        BigInteger serialNumber = BigInteger.valueOf(random.nextLong(1000000) + 1);
        return CertificateBuilder.newCertificateBuilder(dname,
                        caKeys.getPublic(), caKeys.getPublic())
                .setSerialNumber(serialNumber)
                .addExtension(new AuthorityKeyIdentifierExtension(kid, gns,
                        new SerialNumber(serialNumber)))
                .addBasicConstraintsExt(true, true, -1)
                .setOneHourValidity()
                .build(null, caKeys.getPrivate(), signatureAlgo);
    }

    protected SSLContext getClientSSLContext() throws Exception {
        return getSSLContext(trustedCert, clientCert, clientKeys, passphrase);
    }

    protected SSLContext getServerSSLContext() throws Exception {
        return getSSLContext(trustedCert, serverCert, serverKeys, passphrase);
    }

    // get the ssl context
    private SSLContext getSSLContext(X509Certificate trustedCert,
                   X509Certificate keyCert, KeyPair key, char[] passphrase)
            throws Exception {

        // create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // import the trused cert
        ks.setCertificateEntry("RSA Export Signer", trustedCert);

        Certificate[] chain = new Certificate[2];
        chain[0] = keyCert;
        chain[1] = trustedCert;

        // import the key entry.
        ks.setKeyEntry("Whatever", key.getPrivate(), passphrase, chain);

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance(protocol);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ctx;
    }
}
