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

/*
 * @test
 * @bug 6302644
 * @summary X509KeyManager implementation for NewSunX509 doesn't return most
 *          preferable key
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.security.CertificateBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class PreferredKey {

    public static void main(String[] args) throws Exception {
        X509KeyManager km = getKeyManager();

        testPreferredKey(km, "RSA", new String[] {"RSA", "DSA"});
        testPreferredKey(km, "DSA", new String[] {"DSA", "RSA"});
    }

    private static void testPreferredKey(X509KeyManager km,
                                         String keyType,
                                         String[] multiKeyTypes) {
        String[] aliases = km.getClientAliases(keyType, null);
        String alias = km.chooseClientAlias(multiKeyTypes, null, null);

        Asserts.assertTrue(aliases != null && alias != null,
                "Should return preferred alias");

        String algorithm = km.getPrivateKey(alias).getAlgorithm();
        Asserts.assertTrue(algorithm.equals(keyType) && algorithm.equals(
                km.getPrivateKey(aliases[0]).getAlgorithm()),
                "Failed to get the preferable key aliases");
    }

    private static X509KeyManager getKeyManager() throws Exception {
        char[] passphrase = "passphrase".toCharArray();

        KeyPair rsaKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair dsaKey = KeyPairGenerator.getInstance("DSA").generateKeyPair();

        // create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, passphrase);

        ks.setKeyEntry("dummyrsa",
                rsaKey.getPrivate(),
                passphrase,
                new Certificate[]{createSelfSignedCert(rsaKey,
                        "SHA256withRSA")});
        ks.setKeyEntry("dummydsa",
                dsaKey.getPrivate(),
                passphrase,
                new Certificate[]{createSelfSignedCert(dsaKey,
                        "SHA256withDSA")});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(ks, passphrase);

        return (X509KeyManager) kmf.getKeyManagers()[0];
    }

    private static X509Certificate createSelfSignedCert(KeyPair caKeys,
                                                        String keyAlg)
            throws CertificateException, IOException {
        return (new CertificateBuilder()
                .setSubjectName("CN=dummy.example.com, OU=Dummy, " +
                        "O=Dummy, L=Cupertino, ST=CA, C=US")
                .setPublicKey(caKeys.getPublic())
                .setOneHourValidity()
                .setSerialNumber(BigInteger.valueOf(
                        new SecureRandom().nextLong(1000000) + 1))
        ).build(null, caKeys.getPrivate(), keyAlg);
    }
}
