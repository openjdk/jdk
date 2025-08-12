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

import com.sun.security.auth.UserPrincipal;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;
import jdk.test.lib.Asserts;
import jdk.test.lib.security.CertificateBuilder;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

/*
 * @test
 * @bug 8359956
 * @summary Support algorithm constraints and certificate checks in SunX509
 *          key manager
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm CertChecking false SunX509
 * @run main/othervm CertChecking true SunX509
 * @run main/othervm CertChecking false PKIX
 * @run main/othervm CertChecking true PKIX
 */

/*
 * This class tests against the certificate's expiration, key usage, key type
 * and issuers.
 */

public class CertChecking {

    private static final String PREFERRED_ALIAS = "preferred-alias";
    private static final String EXPIRED_ALIAS = "expired-alias";
    private static final String USAGE_MISMATCH_ALIAS = "usage-mismatch-alias";
    private static final String CA_KEY_TYPE = "RSA";
    private static final String CERT_SIG_ALG = "SHA256withRSA";
    private static final String CA_ISSUER_STRING =
            "O=TrustedCert, L=Some-City, ST=Some-State, C=US";
    private static final String EE_ISSUER_STRING =
            "O=EndpointCert, L=Some-City, ST=Some-State, C=US";
    private static final String UNKNOWN_ISSUER_STRING =
            "O=UnknownCert, L=Some-City, ST=Some-State, C=US";

    /*
     * Certificate KeyUsage reference:
     *
     *     digitalSignature        (0),
     *     nonRepudiation          (1),
     *     keyEncipherment         (2),
     *     dataEncipherment        (3),
     *     keyAgreement            (4),
     *     keyCertSign             (5),
     *     cRLSign                 (6),
     *     encipherOnly            (7),
     *     decipherOnly            (8)
     */

    private static final boolean[] DEFAULT_KEY_USAGES =
            new boolean[]{true, true, true, true, true, true};
    private static final boolean[] NONE_KEY_USAGES =
            new boolean[]{false, false, false, false, false, false};
    private static final boolean[] NO_DS_USAGE =
            new boolean[]{false, true, true, true, true, true};
    private static final boolean[] NO_DS_NO_KE_USAGE =
            new boolean[]{false, true, false, true, true, true};
    private static final boolean[] NO_KA_USAGE =
            new boolean[]{true, true, true, true, false, true};


    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new RuntimeException("Wrong number of arguments");
        }

        String enabled = args[0];
        String kmAlg = args[1];

        System.setProperty("jdk.tls.SunX509KeyManager.certChecking", enabled);

        // --- Usage and expired test cases --

        // Both client and server should be checked with no usages at all
        usageTestCase(enabled, kmAlg, "RSA", NONE_KEY_USAGES, true, true);

        // Only client should be checked with RSA algorithm and
        // no digital signature bit set
        usageTestCase(enabled, kmAlg, "RSA", NO_DS_USAGE, false, true);

        // Only server should be checked with RSA algorithm and
        // no digital signature bit set
        usageTestCase(enabled, kmAlg, "RSASSA-PSS", NO_DS_USAGE, true, false);

        // Both client and server should be checked with DSA algorithm and no
        // digital signature bit set
        usageTestCase(enabled, kmAlg, "DSA", NO_DS_USAGE, true, true);

        // Both client and server should be checked with EC algorithm and no
        // digital signature bit set
        usageTestCase(enabled, kmAlg, "EC", NO_DS_USAGE, true, true);

        // Both client and server should be checked with RSA algorithm and
        // missing digital signature and key encipherment bits.
        usageTestCase(enabled, kmAlg, "RSA", NO_DS_NO_KE_USAGE, true, true);

        // Both client and server should be checked with DH algorithm and no
        // key agreement bit set.
        usageTestCase(enabled, kmAlg, "DH", NO_KA_USAGE, true, true);

        // Only server should be checked with EC algorithm and
        // no digital signature bit set
        usageTestCase(enabled, kmAlg, "EC", NO_KA_USAGE, true, false);

        // --- Issuer match test cases ---

        // Check CA issuer match
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "RSA",
                new Principal[]{new X500Principal(CA_ISSUER_STRING)}, true);

        // Check CA issuer match with non-X500 principal
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "RSA",
                new Principal[]{new UserPrincipal(CA_ISSUER_STRING)}, true);

        // Non-convertable principal should match all
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "RSA",
                new Principal[]{new InvalidPrincipal()}, true);

        // Empty issuer array should match all
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "RSA",
                new Principal[]{}, true);

        // Null issuer array should match all
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "RSA", null, true);

        // Issuer that is not in the chain should not match.
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "RSA",
                new Principal[]{new X500Principal(UNKNOWN_ISSUER_STRING)},
                false);

        // --- Key Type match test cases ---

        // Exact key type match.
        issuerAndKeyTypeTestCase(enabled, kmAlg, "EC", "EC", null, true);

        // Key type with a signature algorithm match.
        issuerAndKeyTypeTestCase(
                enabled, kmAlg, "EC", "EC_" + CA_KEY_TYPE, null, true);

        // Null KeyType should not match.
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", null, null, false);

        // Wrong KeyType should not match.
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "EC", null, false);

        // Wrong signature algorithm should not match.
        issuerAndKeyTypeTestCase(enabled, kmAlg, "RSA", "RSA_EC", null, false);

        // Correct signature algorithm but incorrect key algorithm
        // should not match.
        issuerAndKeyTypeTestCase(
                enabled, kmAlg, "RSA", "EC_" + CA_KEY_TYPE, null, false);
    }

    private static void usageTestCase(String enabled, String kmAlg,
            String keyAlg, boolean[] certKeyUsages, boolean checkServer,
            boolean checkClient) throws Exception {

        X509ExtendedKeyManager km = (X509ExtendedKeyManager) getKeyManager(
                kmAlg, keyAlg, certKeyUsages);

        String chosenServerAlias = km.chooseServerAlias(keyAlg, null, null);
        String chosenEngineServerAlias = km.chooseEngineServerAlias(
                keyAlg, null, null);
        String chosenClientAlias = km.chooseClientAlias(
                new String[]{keyAlg}, null, null);
        String chosenEngineClientAlias = km.chooseEngineClientAlias(
                new String[]{keyAlg}, null, null);

        String[] allServerAliases = km.getServerAliases(keyAlg, null);
        String[] allClientAliases = km.getClientAliases(keyAlg, null);

        if ("false".equals(enabled) && kmAlg.equals("SunX509")) {
            // Initial order alias returned
            assertEquals(USAGE_MISMATCH_ALIAS,
                    normalizeAlias(chosenServerAlias));
            assertEquals(USAGE_MISMATCH_ALIAS,
                    normalizeAlias(chosenClientAlias));
            assertEquals(USAGE_MISMATCH_ALIAS,
                    normalizeAlias(chosenEngineServerAlias));
            assertEquals(USAGE_MISMATCH_ALIAS,
                    normalizeAlias(chosenEngineClientAlias));

            // Assert the initial order of all aliases.
            assertEquals(USAGE_MISMATCH_ALIAS,
                    normalizeAlias(allServerAliases[0]));
            assertEquals(USAGE_MISMATCH_ALIAS,
                    normalizeAlias(allClientAliases[0]));
            assertEquals(PREFERRED_ALIAS, normalizeAlias(allServerAliases[1]));
            assertEquals(PREFERRED_ALIAS, normalizeAlias(allClientAliases[1]));
            assertEquals(EXPIRED_ALIAS, normalizeAlias(allServerAliases[2]));
            assertEquals(EXPIRED_ALIAS, normalizeAlias(allClientAliases[2]));

        } else {
            if (checkServer) {
                // Preferred alias returned
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(chosenServerAlias));
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(chosenEngineServerAlias));

                // Assert the correct sorted order of all aliases.
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(allServerAliases[0]));
                assertEquals(EXPIRED_ALIAS,
                        normalizeAlias(allServerAliases[1]));
                assertEquals(USAGE_MISMATCH_ALIAS,
                        normalizeAlias(allServerAliases[2]));
            }

            if (checkClient) {
                // Preferred alias returned
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(chosenClientAlias));
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(chosenEngineClientAlias));

                // Assert the correct sorted order of all aliases.
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(allClientAliases[0]));
                assertEquals(EXPIRED_ALIAS,
                        normalizeAlias(allClientAliases[1]));
                assertEquals(USAGE_MISMATCH_ALIAS,
                        normalizeAlias(allClientAliases[2]));
            }
        }
    }

    private static void issuerAndKeyTypeTestCase(String enabled, String kmAlg,
            String keyAlg, String keyType, Principal[] issuers, boolean found)
            throws Exception {

        X509ExtendedKeyManager km = (X509ExtendedKeyManager) getKeyManager(
                kmAlg, keyAlg, NONE_KEY_USAGES);

        List<String> chosenAliases = new ArrayList<>(4);

        chosenAliases.add(km.chooseServerAlias(keyType, issuers, null));
        chosenAliases.add(km.chooseEngineServerAlias(keyType, issuers, null));
        chosenAliases.add(
                km.chooseClientAlias(new String[]{keyType}, issuers, null));
        chosenAliases.add(km.chooseEngineClientAlias(
                new String[]{keyType}, issuers, null));

        String[] allServerAliases = km.getServerAliases(keyType, issuers);
        String[] allClientAliases = km.getClientAliases(keyType, issuers);

        if (found) {
            if ("false".equals(enabled) && kmAlg.equals("SunX509")) {
                chosenAliases.forEach(a ->
                        assertEquals(USAGE_MISMATCH_ALIAS, normalizeAlias(a)));

                // Assert the initial order of all aliases.
                assertEquals(USAGE_MISMATCH_ALIAS,
                        normalizeAlias(allServerAliases[0]));
                assertEquals(USAGE_MISMATCH_ALIAS,
                        normalizeAlias(allClientAliases[0]));
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(allServerAliases[1]));
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(allClientAliases[1]));
                assertEquals(EXPIRED_ALIAS,
                        normalizeAlias(allServerAliases[2]));
                assertEquals(EXPIRED_ALIAS,
                        normalizeAlias(allClientAliases[2]));
            } else {
                chosenAliases.forEach(a ->
                        assertEquals(PREFERRED_ALIAS, normalizeAlias(a)));

                // Assert the correct sorted order of all aliases.
                assertEquals(PREFERRED_ALIAS,
                        normalizeAlias(allServerAliases[0]));
                assertEquals(EXPIRED_ALIAS,
                        normalizeAlias(allServerAliases[1]));
                assertEquals(USAGE_MISMATCH_ALIAS,
                        normalizeAlias(allServerAliases[2]));
            }
        } else {
            chosenAliases.forEach(Asserts::assertNull);
            assertNull(allServerAliases);
            assertNull(allClientAliases);
        }
    }

    // PKIX KeyManager adds a cache prefix to an alias.
    private static String normalizeAlias(String alias) {
        return alias.substring(alias.lastIndexOf(".") + 1);

    }

    private static class InvalidPrincipal implements Principal {

        @Override
        public String getName() {
            return null;
        }
    }

    private static X509KeyManager getKeyManager(String kmAlg,
            String keyAlg, boolean[] certKeyUsages)
            throws Exception {

        // Create a key store.
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // Generate and set the trusted cert.
        KeyPair caKeys = KeyPairGenerator.getInstance(CA_KEY_TYPE)
                .generateKeyPair();
        X509Certificate trustedCert = createTrustedCert(caKeys);
        ks.setCertificateEntry("CA entry", trustedCert);

        // Generate valid certificate chain.
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyAlg);
        KeyPair validEndpointKeys = kpg.generateKeyPair();

        X509Certificate validEndpointCert = customCertificateBuilder(
                EE_ISSUER_STRING,
                validEndpointKeys.getPublic(), caKeys.getPublic(),
                Instant.now(), DEFAULT_KEY_USAGES)
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), CERT_SIG_ALG);

        Certificate[] validChain = new Certificate[2];
        validChain[0] = validEndpointCert;
        validChain[1] = trustedCert;

        // Generate expired certificate chain.
        KeyPair expiredEndpointKeys = kpg.generateKeyPair();

        X509Certificate expiredEndpointCert = customCertificateBuilder(
                EE_ISSUER_STRING,
                expiredEndpointKeys.getPublic(), caKeys.getPublic(),
                Instant.now().minus(1, ChronoUnit.DAYS), DEFAULT_KEY_USAGES)
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), CERT_SIG_ALG);

        Certificate[] expiredChain = new Certificate[2];
        expiredChain[0] = expiredEndpointCert;
        expiredChain[1] = trustedCert;

        // Generate usage mismatch certificate chain.
        KeyPair usageMismatchEndpointKeys = kpg.generateKeyPair();

        X509Certificate usageMismatchEndpointCert = customCertificateBuilder(
                EE_ISSUER_STRING,
                usageMismatchEndpointKeys.getPublic(), caKeys.getPublic(),
                Instant.now(), certKeyUsages)
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), CERT_SIG_ALG);

        Certificate[] usageMismatchChain = new Certificate[2];
        usageMismatchChain[0] = usageMismatchEndpointCert;
        usageMismatchChain[1] = trustedCert;

        // Import the key entries, order matters.
        final char[] passphrase = "passphrase".toCharArray();
        ks.setKeyEntry(USAGE_MISMATCH_ALIAS,
                usageMismatchEndpointKeys.getPrivate(), passphrase,
                usageMismatchChain);
        ks.setKeyEntry(PREFERRED_ALIAS, validEndpointKeys.getPrivate(),
                passphrase,
                validChain);
        ks.setKeyEntry(EXPIRED_ALIAS, expiredEndpointKeys.getPrivate(),
                passphrase,
                expiredChain);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmAlg);
        kmf.init(ks, passphrase);

        return (X509KeyManager) kmf.getKeyManagers()[0];
    }

    // Certificate-building helper methods.

    private static X509Certificate createTrustedCert(KeyPair caKeys)
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
                CA_ISSUER_STRING,
                caKeys.getPublic(), caKeys.getPublic(), Instant.now(),
                DEFAULT_KEY_USAGES)
                .setSerialNumber(serialNumber)
                .addExtension(new AuthorityKeyIdentifierExtension(kid, gns,
                        new SerialNumber(serialNumber)))
                .addBasicConstraintsExt(true, true, -1)
                .build(null, caKeys.getPrivate(), CERT_SIG_ALG);
    }

    private static CertificateBuilder customCertificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey,
            Instant certDate, boolean[] certKeyUsages)
            throws CertificateException, IOException {
        SecureRandom random = new SecureRandom();

        CertificateBuilder builder = new CertificateBuilder()
                .setSubjectName(subjectName)
                .setPublicKey(publicKey)
                .setNotBefore(
                        Date.from(certDate.minus(1, ChronoUnit.HOURS)))
                .setNotAfter(Date.from(certDate.plus(1, ChronoUnit.HOURS)))
                .setSerialNumber(
                        BigInteger.valueOf(random.nextLong(1000000) + 1))
                .addSubjectKeyIdExt(publicKey)
                .addAuthorityKeyIdExt(caKey);
        builder.addKeyUsageExt(certKeyUsages);

        return builder;
    }
}
