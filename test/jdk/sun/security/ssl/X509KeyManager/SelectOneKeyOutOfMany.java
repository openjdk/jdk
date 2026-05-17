/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4387949 4302197
 * @summary Need to add Sockets and key arrays to the
 *      X509KeyManager.choose*Alias() methods & There's no mechanism
 *      to select one key out of many in a keystore
 *
 *      chooseServerAlias method is reverted back to accept a single
 *      keytype as a parameter, please see RFE: 4501014
 *      The part of the test on the server-side is changed to test
 *      passing in a single keytype parameter to chooseServerAlias method.
 *
 * @author Brad Wetmore
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main SelectOneKeyOutOfMany
 */

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
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

public class SelectOneKeyOutOfMany {

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
        X509KeyManager km = getKeyManager();

        /*
         * There should be one of each key type here.
         */
        String[] nothing = new String[] { "nothing" };
        String[] rsa = new String[] { "RSA" };
        String[] dsa = new String[] { "DSA" };
        String[] rsaDsa = new String[] { "RSA", "DSA" };
        String[] dsaRsa = new String[] { "DSA", "RSA" };

        String resultsRsaDsa, resultsDsaRsa;
        String resultsRsa, resultsDsa;
        String resultsNone;

        String[] resultArrayRSA;
        String[] resultArrayDSA;

        /*
         * Check get*Aliases for null returns
         */
        if (km.getClientAliases("nothing", null) != null) {
            throw new Exception("km.getClientAliases(nothing) != null");
        }
        System.out.println("km.getClientAlias(nothing) returning nulls");

        if (km.getServerAliases("nothing", null) != null) {
            throw new Exception("km.getServerAliases(nothing) != null");
        }
        System.out.println("km.getServerAlias(nothing) returning nulls");
        System.out.println("=====");

        System.out.println("Dumping Certs...");
        if ((resultArrayRSA = km.getServerAliases("RSA", null)) == null) {
            throw new Exception("km.getServerAliases(RSA) == null");
        }
        for (int i = 0; i < resultArrayRSA.length; i++) {
            System.out.println("        resultArrayRSA#" + i + ": "
                    + resultArrayRSA[i]);
        }

        if ((resultArrayDSA = km.getServerAliases("DSA", null)) == null) {
            throw new Exception("km.getServerAliases(DSA) == null");
        }
        for (int i = 0; i < resultArrayDSA.length; i++) {
            System.out.println("        resultArrayDSA#" + i + ": "
                    + resultArrayDSA[i]);
        }
        System.out.println("=====");

        /*
         * Check chooseClientAliases for null returns
         */
        resultsNone = km.chooseClientAlias(nothing, null, null);
        if (resultsNone != null) {
            throw new Exception("km.chooseClientAlias(nothing) != null");
        }
        System.out.println("km.ChooseClientAlias(nothing) passed");

        /*
         * Check chooseClientAlias for RSA keys.
         */
        resultsRsa = km.chooseClientAlias(rsa, null, null);
        if (resultsRsa == null) {
            throw new Exception(
                    "km.chooseClientAlias(rsa, null, null) != null");
        }
        System.out.println("km.chooseClientAlias(rsa) passed");

        /*
         * Check chooseClientAlias for DSA keys.
         */
        resultsDsa = km.chooseClientAlias(dsa, null, null);
        if (resultsDsa == null) {
            throw new Exception(
                    "km.chooseClientAlias(dsa, null, null) != null");
        }
        System.out.println("km.chooseClientAlias(dsa) passed");

        /*
         * There should be both an rsa and a dsa entry in the
         * keystore.
         *
         * Check chooseClientAlias for RSA/DSA keys and be sure
         * the ordering is correct.
         */
        resultsRsaDsa = km.chooseClientAlias(rsaDsa, null, null);
        if ((resultsRsaDsa == null) || (resultsRsaDsa != resultsRsa)) {
            throw new Exception("km.chooseClientAlias(rsaDsa) failed");
        }
        System.out.println("km.chooseClientAlias(rsaDsa) passed");

        resultsDsaRsa = km.chooseClientAlias(dsaRsa, null, null);
        if ((resultsDsaRsa == null) || (resultsDsaRsa != resultsDsa)) {
            throw new Exception("km.chooseClientAlias(DsaRsa) failed");
        }
        System.out.println("km.chooseClientAlias(DsaRsa) passed");

        System.out.println("=====");

        /*
         * Check chooseServerAliases for null returns
         */
        resultsNone = km.chooseServerAlias("nothing", null, null);
        if (resultsNone != null) {
            throw new Exception("km.chooseServerAlias(\"nothing\") != null");
        }
        System.out.println("km.ChooseServerAlias(\"nothing\") passed");

        /*
         * Check chooseServerAlias for RSA keys.
         */
        resultsRsa = km.chooseServerAlias("RSA", null, null);
        if (resultsRsa == null) {
            throw new Exception(
                    "km.chooseServerAlias(\"RSA\", null, null) != null");
        }
        System.out.println("km.chooseServerAlias(\"RSA\") passed");

        /*
         * Check chooseServerAlias for DSA keys.
         */
        resultsDsa = km.chooseServerAlias("DSA", null, null);
        if (resultsDsa == null) {
            throw new Exception(
                    "km.chooseServerAlias(\"DSA\", null, null) != null");
        }
        System.out.println("km.chooseServerAlias(\"DSA\") passed");
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

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
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
