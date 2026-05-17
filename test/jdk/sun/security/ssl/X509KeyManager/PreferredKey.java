/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
// Security properties, once set, cannot revert to unset.  To avoid
// conflicts with tests running in the same VM isolate this test by
// running it in otherVM mode.
//

/*
 * @test
 * @bug 6302644
 * @summary X509KeyManager implementation for NewSunX509 doesn't return most
 *          preferable key
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm PreferredKey
 */

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertNotNull;

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
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

public class PreferredKey {

    private static final String RSA_ALIAS = "rsa-alias";
    private static final String DSA_ALIAS = "dsa-alias";
    private static final char[] PASSPHRASE = "passphrase".toCharArray();

    private static final String CA_KEY_TYPE = "RSA";
    private static final String CERT_SIG_ALG = "SHA256withRSA";
    private static final String CA_ISSUER_STRING =
            "O=TrustedCert, L=Some-City, ST=Some-State, C=US";
    private static final String EE_ISSUER_STRING =
            "O=EndpointCert, L=Some-City, ST=Some-State, C=US";

    private static final boolean[] DEFAULT_KEY_USAGES =
            new boolean[]{true, true, true, true, true, true};

    public static void main(String[] args) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "MD2, RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        X509KeyManager km = getKeyManager();

        checkPreferredKey(km, "RSA", new String[] {"RSA", "DSA"});
        checkPreferredKey(km, "DSA", new String[] {"DSA", "RSA"});
    }

    private static void checkPreferredKey(X509KeyManager km, String keyType,
            String[] keyTypes) throws Exception {
        String[] aliases = km.getClientAliases(keyType, null);
        String alias = km.chooseClientAlias(keyTypes, null, null);

        assertNotNull(aliases, "Expected client aliases for " + keyType);
        assertNotNull(alias, "Expected chosen alias for " + keyTypes[0]);

        String algorithm = km.getPrivateKey(alias).getAlgorithm();
        String firstAliasAlgorithm =
                km.getPrivateKey(aliases[0]).getAlgorithm();

        assertEquals(keyType, algorithm,
                "chooseClientAlias did not return preferable " + keyType
                        + " key");
        assertEquals(keyType, firstAliasAlgorithm,
                "getClientAliases did not list preferable " + keyType
                        + " key first");
        assertEquals(algorithm, firstAliasAlgorithm,
                "chosen key algorithm does not match first alias");
    }

    private static X509KeyManager getKeyManager() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        KeyPair caKeys = KeyPairGenerator.getInstance(CA_KEY_TYPE)
                .generateKeyPair();
        X509Certificate trustedCert = createTrustedCert(caKeys);
        ks.setCertificateEntry("ca", trustedCert);

        addKeyEntry(ks, RSA_ALIAS, "RSA", caKeys, trustedCert);
        addKeyEntry(ks, DSA_ALIAS, "DSA", caKeys, trustedCert);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(ks, PASSPHRASE);
        return (X509KeyManager) kmf.getKeyManagers()[0];
    }

    private static void addKeyEntry(KeyStore ks, String alias, String keyAlg,
            KeyPair caKeys, X509Certificate trustedCert) throws Exception {
        KeyPair endpointKeys = KeyPairGenerator.getInstance(keyAlg)
                .generateKeyPair();

        X509Certificate endpointCert = certificateBuilder(
                EE_ISSUER_STRING,
                endpointKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), CERT_SIG_ALG);

        Certificate[] chain = {endpointCert, trustedCert};
        ks.setKeyEntry(alias, endpointKeys.getPrivate(), PASSPHRASE, chain);
    }

    private static X509Certificate createTrustedCert(KeyPair caKeys)
            throws Exception {
        SecureRandom random = new SecureRandom();

        KeyIdentifier kid = new KeyIdentifier(caKeys.getPublic());
        GeneralNames gns = new GeneralNames();
        GeneralName name = new GeneralName(new X500Name(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US"));
        gns.add(name);
        BigInteger serialNumber =
                BigInteger.valueOf(random.nextLong(1_000_000) + 1);
        return certificateBuilder(
                CA_ISSUER_STRING,
                caKeys.getPublic(), caKeys.getPublic())
                .setSerialNumber(serialNumber)
                .addExtension(new AuthorityKeyIdentifierExtension(kid, gns,
                        new SerialNumber(serialNumber)))
                .addBasicConstraintsExt(true, true, -1)
                .build(null, caKeys.getPrivate(), CERT_SIG_ALG);
    }

    private static CertificateBuilder certificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey)
            throws CertificateException, IOException {
        SecureRandom random = new SecureRandom();
        Instant now = Instant.now();

        return new CertificateBuilder()
                .setSubjectName(subjectName)
                .setPublicKey(publicKey)
                .setNotBefore(Date.from(now.minus(1, ChronoUnit.DAYS)))
                .setNotAfter(Date.from(now.plus(3650, ChronoUnit.DAYS)))
                .setSerialNumber(
                        BigInteger.valueOf(random.nextLong(1_000_000) + 1))
                .addSubjectKeyIdExt(publicKey)
                .addAuthorityKeyIdExt(caKey)
                .addKeyUsageExt(DEFAULT_KEY_USAGES);
    }
}
