/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8179502
 * @summary Enhance OCSP, CRL and Certificate Fetch Timeouts
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm -Dcom.sun.security.enableAIAcaIssuers=true
 *      -Dcom.sun.security.cert.readtimeout=1 AIACertTimeout 5000 false
 * @run main/othervm -Dcom.sun.security.enableAIAcaIssuers=true
 *      -Dcom.sun.security.cert.readtimeout=1s AIACertTimeout 5000 false
 * @run main/othervm -Dcom.sun.security.enableAIAcaIssuers=true
 *      -Dcom.sun.security.cert.readtimeout=4 AIACertTimeout 1000 true
 * @run main/othervm -Dcom.sun.security.enableAIAcaIssuers=true
 *      -Dcom.sun.security.cert.readtimeout=1500ms AIACertTimeout 5000 false
 * @run main/othervm -Dcom.sun.security.enableAIAcaIssuers=true
 *      -Dcom.sun.security.cert.readtimeout=4500ms AIACertTimeout 1000 true
 * @run main/othervm -Djava.security.debug=certpath
 *      -Dcom.sun.security.enableAIAcaIssuers=false
 *      -Dcom.sun.security.cert.readtimeout=20000ms AIACertTimeout 10000 false
 */

import com.sun.net.httpserver.*;
import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.Security;
import java.security.cert.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.security.CertificateBuilder;

public class AIACertTimeout {

    private static final boolean logging = true;

    // PKI and server components we will need for this test
    private static KeyPair          rootKp;     // Root CA keys
    private static X509Certificate  rootCert;
    private static KeyPair          intKp;      // Intermediate CA keys
    private static X509Certificate  intCert;
    private static KeyPair          eeKp;       // End-entity keys
    private static X509Certificate  eeCert;

    public static void main(String[] args) throws Exception {
        Security.setProperty("com.sun.security.allowedAIALocations", "any");
        int servTimeoutMsec = (args != null && args.length >= 1) ?
                Integer.parseInt(args[0]) : -1;
        boolean expectedPass = args != null && args.length >= 2 &&
                Boolean.parseBoolean(args[1]);

        createAuthorities();
        CaCertHttpServer aiaServer = new CaCertHttpServer(intCert,
                servTimeoutMsec);
        try {
            aiaServer.start();
            createEE(aiaServer.getAddress());

            X509CertSelector target = new X509CertSelector();
            target.setCertificate(eeCert);
            PKIXParameters params = new PKIXBuilderParameters(Set.of(
                        new TrustAnchor(rootCert, null)), target);
            params.setRevocationEnabled(false);

            try {
                CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
                CertPathBuilderResult result = cpb.build(params);
                if (expectedPass) {
                    int pathLen = result.getCertPath().getCertificates().size();
                    if (pathLen != 2) {
                        throw new RuntimeException("Expected 2 certificates " +
                                "in certpath, got " + pathLen);
                    }
                } else {
                    throw new RuntimeException("Missing expected CertPathBuilderException");
                }
            } catch (CertPathBuilderException cpve) {
                if (!expectedPass) {
                    log("Cert path building failed as expected: " + cpve);
                } else {
                    throw cpve;
                }
            }
        } finally {
            aiaServer.stop();
        }
    }

    private static class CaCertHttpServer {

        private final X509Certificate caCert;
        private final HttpServer server;
        private final int timeout;

        public CaCertHttpServer(X509Certificate cert, int timeout)
                throws IOException {
            caCert = Objects.requireNonNull(cert, "Null CA cert disallowed");
            server = HttpServer.create();
            this.timeout = timeout;
            if (timeout > 0) {
                log("Created HttpServer with timeout of " + timeout + " msec.");
            } else {
                log("Created HttpServer with no timeout");
            }
        }

        public void start() throws IOException {
            server.bind(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/cacert", t -> {
                try (InputStream is = t.getRequestBody()) {
                    is.readAllBytes();
                }
                try {
                    if (timeout > 0) {
                        // Sleep in order to simulate network latency
                        log("Server sleeping for " + timeout + " msec.");
                        Thread.sleep(timeout);
                    }

                    byte[] derCert = caCert.getEncoded();
                    t.getResponseHeaders().add("Content-Type",
                            "application/pkix-cert");
                    t.sendResponseHeaders(200, derCert.length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(derCert);
                    }
                } catch (InterruptedException |
                        CertificateEncodingException exc) {
                    throw new IOException(exc);
                }
            });
            server.setExecutor(null);
            server.start();
            log("Started HttpServer: Listening on " + server.getAddress());
        }

        public void stop() {
            server.stop(0);
        }

        public InetSocketAddress getAddress() {
            return server.getAddress();
        }
    }


    /**
     * Creates the CA PKI components necessary for this test.
     */
    private static void createAuthorities() throws Exception {
        CertificateBuilder cbld = new CertificateBuilder();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));

        // Generate Root, IntCA, EE keys
        rootKp = keyGen.genKeyPair();
        log("Generated Root CA KeyPair");
        intKp = keyGen.genKeyPair();
        log("Generated Intermediate CA KeyPair");
        eeKp = keyGen.genKeyPair();
        log("Generated End Entity Cert KeyPair");

        // Make a 3 year validity starting from 60 days ago
        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
        long end = start + TimeUnit.DAYS.toMillis(1085);

        boolean[] kuBitSettings = {true, false, false, false, false, true,
                true, false, false};

        // Set up the Root CA Cert
        cbld.setSubjectName("CN=Root CA Cert, O=SomeCompany").
                setPublicKey(rootKp.getPublic()).
                setSerialNumber(new BigInteger("1")).
                setValidity(new Date(start), new Date(end)).
                addSubjectKeyIdExt(rootKp.getPublic()).
                addAuthorityKeyIdExt(rootKp.getPublic()).
                addBasicConstraintsExt(true, true, -1).
                addKeyUsageExt(kuBitSettings);

        // Make our Root CA Cert!
        rootCert = cbld.build(null, rootKp.getPrivate(), "SHA256withECDSA");
        log("Root CA Created:\n" + certInfo(rootCert));

        // Make a 2 year validity starting from 30 days ago
        start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        end = start + TimeUnit.DAYS.toMillis(730);

        // Now that we have the root keystore we can create our
        // intermediate CA.
        cbld.reset();
        cbld.setSubjectName("CN=Intermediate CA Cert, O=SomeCompany").
                setPublicKey(intKp.getPublic()).
                setSerialNumber(new BigInteger("100")).
                setValidity(new Date(start), new Date(end)).
                addSubjectKeyIdExt(intKp.getPublic()).
                addAuthorityKeyIdExt(rootKp.getPublic()).
                addBasicConstraintsExt(true, true, -1).
                addKeyUsageExt(kuBitSettings);

        // Make our Intermediate CA Cert!
        intCert = cbld.build(rootCert, rootKp.getPrivate(), "SHA256withECDSA");
        log("Intermediate CA Created:\n" + certInfo(intCert));
    }

    /**
     * Creates the end entity certificate from the previously generated
     * intermediate CA cert.
     *
     * @param aiaAddr the address/port of the server that will hold the issuer
     *                certificate. This will be used to create an AIA URI.
     */
    private static void createEE(InetSocketAddress aiaAddr) throws Exception {
        // Make a 1 year validity starting from 7 days ago
        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        long end = start + TimeUnit.DAYS.toMillis(365);
        boolean[] kuBits = {true, false, false, false, false, false,
                false, false, false};
        List<String> ekuOids = List.of("1.3.6.1.5.5.7.3.1",
                "1.3.6.1.5.5.7.3.2", "1.3.6.1.5.5.7.3.4");
        String aiaUri = String.format("http://%s:%d/cacert",
                aiaAddr.getHostName(), aiaAddr.getPort());

        CertificateBuilder cbld = new CertificateBuilder();
        cbld.setSubjectName("CN=Oscar T. Grouch, O=SomeCompany").
                setPublicKey(eeKp.getPublic()).
                setSerialNumber(new BigInteger("4096")).
                setValidity(new Date(start), new Date(end)).
                addSubjectKeyIdExt(eeKp.getPublic()).
                addAuthorityKeyIdExt(intKp.getPublic()).
                addKeyUsageExt(kuBits).addExtendedKeyUsageExt(ekuOids).
                addAIAExt(List.of("CAISSUER|" + aiaUri));

        // Build the cert
        eeCert = cbld.build(intCert, intKp.getPrivate(), "SHA256withECDSA");
        log("EE Certificate Created:\n" + certInfo(eeCert));
    }

    /**
     * Helper routine that dumps only a few cert fields rather than
     * the whole toString() output.
     *
     * @param cert an X509Certificate to be displayed
     *
     * @return the String output of the issuer, subject and
     * serial number
     */
    private static String certInfo(X509Certificate cert) {
        StringBuilder sb = new StringBuilder();
        sb.append("Issuer: ").append(cert.getIssuerX500Principal()).
                append("\n");
        sb.append("Subject: ").append(cert.getSubjectX500Principal()).
                append("\n");
        sb.append("Serial: ").append(cert.getSerialNumber()).append("\n");
        return sb.toString();
    }

    private static void log(String str) {
        if (logging) {
            System.out.println("[" + Thread.currentThread().getName() + "] " +
                    str);
        }
    }
}
