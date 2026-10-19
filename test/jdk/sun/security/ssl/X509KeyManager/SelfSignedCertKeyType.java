/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.test.lib.Asserts.assertNotNull;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import jdk.test.lib.security.CertificateBuilder;

/*
 * @test
 * @bug 8379191
 * @summary SunX509KeyManagerImpl alias chooser methods returns null for EC_EC
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main SelfSignedCertKeyType SunX509
 * @run main SelfSignedCertKeyType PKIX
 */

public class SelfSignedCertKeyType {

    private static final String CERT_ALIAS = "testalias";
    private static final String KEY_ALG = "EC";
    private static final String KEY_TYPE = "EC_EC";
    private static final String CERT_SIG_ALG = "SHA256withECDSA";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Wrong number of arguments");
        }

        X509ExtendedKeyManager km = getKeyManager(args[0]);

        String serverAlias = km.chooseServerAlias(KEY_TYPE, null, null);
        String engineServerAlias = km.chooseEngineServerAlias(
                KEY_TYPE, null, null);
        String clientAlias = km.chooseClientAlias(
                new String[]{KEY_TYPE}, null, null);
        String engineClientAlias = km.chooseEngineClientAlias(
                new String[]{KEY_TYPE}, null, null);

        for (String alias : new String[]{serverAlias,
                engineServerAlias, clientAlias, engineClientAlias}) {
            assertNotNull(alias);
            assertEquals(CERT_ALIAS, normalizeAlias(alias));
        }
    }

    // PKIX KeyManager adds a cache prefix to an alias.
    private static String normalizeAlias(String alias) {
        return alias.substring(alias.lastIndexOf(".") + 1);
    }

    // Returns a KeyManager with a single self-signed certificate.
    private static X509ExtendedKeyManager getKeyManager(String kmAlg)
            throws Exception {
        KeyPair caKeys = KeyPairGenerator.getInstance(KEY_ALG)
                .generateKeyPair();
        X509Certificate trustedCert = createTrustedCert(caKeys);

        // create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // import the trusted cert
        ks.setCertificateEntry("TLS Signer", trustedCert);

        // generate certificate chain
        Certificate[] chain = new Certificate[1];
        chain[0] = trustedCert;

        // import the key entry.
        final char[] passphrase = "passphrase".toCharArray();
        ks.setKeyEntry(CERT_ALIAS, caKeys.getPrivate(), passphrase, chain);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmAlg);
        kmf.init(ks, passphrase);

        return (X509ExtendedKeyManager) kmf.getKeyManagers()[0];
    }

    private static X509Certificate createTrustedCert(KeyPair caKeys)
            throws Exception {
        return new CertificateBuilder()
                .setSubjectName("O=CA-Org, L=Some-City, ST=Some-State, C=US")
                .setPublicKey(caKeys.getPublic())
                .setNotBefore(
                        Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .setNotAfter(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .setSerialNumber(BigInteger.valueOf(
                        new SecureRandom().nextLong(1000000) + 1))
                .addSubjectKeyIdExt(caKeys.getPublic())
                .addAuthorityKeyIdExt(caKeys.getPublic())
                .addKeyUsageExt(new boolean[]{
                        true, true, true, true, true, true, true})
                .addBasicConstraintsExt(true, true, 1)
                .build(null, caKeys.getPrivate(), CERT_SIG_ALG);
    }
}
