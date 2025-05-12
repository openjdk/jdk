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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertNull;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import jdk.test.lib.security.CertificateBuilder;
import jdk.test.lib.security.SecurityUtils;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

/*
 * @test
 * @bug 8353113
 * @summary Peer supported certificate signature algorithms are not being
 *          checked with default SunX509 key manager
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm AlgorithmConstraintsCheck false SunX509 SHA256withRSA
 * @run main/othervm AlgorithmConstraintsCheck true SunX509 SHA256withRSA
 * @run main/othervm AlgorithmConstraintsCheck false PKIX SHA256withRSA
 * @run main/othervm AlgorithmConstraintsCheck true PKIX SHA256withRSA
 */

public class AlgorithmConstraintsCheck {

    private static final String CERT_ALIAS = "testalias";
    private static final String KEY_TYPE = "RSA";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new RuntimeException("Wrong number of arguments");
        }

        String disabled = args[0];
        String kmAlg = args[1];
        String certSignatureAlg = args[2];

        System.setProperty(
                "jdk.tls.keymanager.disableConstraintsChecking", disabled);
        SecurityUtils.addToDisabledTlsAlgs(certSignatureAlg);

        X509KeyManager km = getKeyManager(kmAlg, certSignatureAlg);
        String serverAlias = km.chooseServerAlias(KEY_TYPE, null, null);
        String clientAlias = km.chooseClientAlias(
                new String[]{KEY_TYPE}, null, null);

        // PKIX KeyManager adds a cache prefix to an alias.
        String serverAliasPrefix = kmAlg.equalsIgnoreCase("PKIX") ? "1.0." : "";
        String clientAliasPrefix = kmAlg.equalsIgnoreCase("PKIX") ? "2.0." : "";

        if ("true".equals(disabled)) {
            assertEquals(serverAliasPrefix + CERT_ALIAS, serverAlias);
            assertEquals(clientAliasPrefix + CERT_ALIAS, clientAlias);
        } else {
            assertNull(serverAlias);
            assertNull(clientAlias);

        }
    }

    private static X509KeyManager getKeyManager(String kmAlg,
            String certSignatureAlg)
            throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_TYPE);
        KeyPair caKeys = kpg.generateKeyPair();
        KeyPair endpointKeys = kpg.generateKeyPair();

        X509Certificate trustedCert = createTrustedCert(caKeys,
                certSignatureAlg);

        X509Certificate endpointCert = customCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                endpointKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), certSignatureAlg);

        // create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // import the trusted cert
        ks.setCertificateEntry("TLS Signer", trustedCert);

        // generate certificate chain
        Certificate[] chain = new Certificate[2];
        chain[0] = endpointCert;
        chain[1] = trustedCert;

        // import the key entry.
        final char[] passphrase = "passphrase".toCharArray();
        ks.setKeyEntry(CERT_ALIAS, endpointKeys.getPrivate(), passphrase,
                chain);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmAlg);
        kmf.init(ks, passphrase);

        return (X509KeyManager) kmf.getKeyManagers()[0];
    }

    // Certificate-building helper methods.

    private static X509Certificate createTrustedCert(KeyPair caKeys,
            String certSignatureAlg)
            throws Exception {
        SecureRandom random = new SecureRandom();

        KeyIdentifier kid = new KeyIdentifier(caKeys.getPublic());
        GeneralNames gns = new GeneralNames();
        GeneralName name = new GeneralName(new X500Name(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US"));
        gns.add(name);
        BigInteger serialNumber = BigInteger.valueOf(
                random.nextLong(1000000) + 1);
        return customCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                caKeys.getPublic(), caKeys.getPublic())
                .setSerialNumber(serialNumber)
                .addExtension(new AuthorityKeyIdentifierExtension(kid, gns,
                        new SerialNumber(serialNumber)))
                .addBasicConstraintsExt(true, true, -1)
                .build(null, caKeys.getPrivate(), certSignatureAlg);
    }

    private static CertificateBuilder customCertificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey)
            throws CertificateException, IOException {
        SecureRandom random = new SecureRandom();

        CertificateBuilder builder = new CertificateBuilder()
                .setSubjectName(subjectName)
                .setPublicKey(publicKey)
                .setNotAfter(
                        Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .setNotAfter(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .setSerialNumber(
                        BigInteger.valueOf(random.nextLong(1000000) + 1))
                .addSubjectKeyIdExt(publicKey)
                .addAuthorityKeyIdExt(caKey);
        builder.addKeyUsageExt(
                new boolean[]{true, true, true, true, true, true});

        return builder;
    }

}
