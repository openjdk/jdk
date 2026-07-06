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
 * @summary Verify X509KeyManager selects the correct RSA or DSA key
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
import java.util.Arrays;

public class SelectOneKeyOutOfMany {
    private static final String NOTHING = "nothing";
    private static final String RSA = "RSA";
    private static final String DSA = "DSA";
    private static final String RSA_ALIAS = "dummyrsa";
    private static final String DSA_ALIAS = "dummydsa";

    public static void main(String[] args) throws Exception {
        X509KeyManager km = getKeyManager();

        // String[] getClientAliases(String keyType, Principal[] issuers)
        Asserts.assertNull(km.getClientAliases(NOTHING, null),
                "getClientAliases shouldn't return alias for unknown type");

        Asserts.assertTrue(Arrays.stream(km.getClientAliases(RSA,
                                null)).toList().contains(RSA_ALIAS),
                "getClientAliases should return RSA alias: " +
                        Arrays.toString(km.getClientAliases(RSA, null)));

        Asserts.assertTrue(Arrays.stream(km.getClientAliases(DSA,
                                null)).toList().contains(DSA_ALIAS),
                "getClientAliases should return DSA alias: " +
                        Arrays.toString(km.getClientAliases(DSA, null)));


        // String[] getServerAliases(String keyType, Principal[] issuers)
        Asserts.assertNull(km.getServerAliases(NOTHING, null),
                "getServerAliases shouldn't return alias for unknown type");

        Asserts.assertTrue(Arrays.stream(km.getServerAliases(RSA,
                                null)).toList().contains(RSA_ALIAS),
                "getServerAliases should return RSA alias: " +
                        Arrays.toString(km.getServerAliases(RSA, null)));

        Asserts.assertTrue(Arrays.stream(km.getServerAliases(DSA,
                                null)).toList().contains(DSA_ALIAS),
                "getServerAliases should return DSA alias: " +
                        Arrays.toString(km.getServerAliases(DSA, null)));


        //  String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket)
        Asserts.assertNull(km.chooseClientAlias(new String[]{NOTHING},
                        null,
                        null),
                "chooseClientAlias shouldn't return alias for unknown type");

        Asserts.assertEQ(
                km.chooseClientAlias(new String[]{RSA}, null, null),
                RSA_ALIAS,
                "chooseClientAlias should return RSA alias");

        Asserts.assertEQ(
                km.chooseClientAlias(new String[]{DSA}, null, null),
                DSA_ALIAS,
                "chooseClientAlias should return DSA alias");

        Asserts.assertEQ(
                km.chooseClientAlias(new String[]{RSA, DSA}, null, null),
                RSA_ALIAS,
                "chooseClientAlias should return RSA alias");

        Asserts.assertEQ(
                km.chooseClientAlias(new String[]{DSA, RSA}, null, null),
                DSA_ALIAS,
                "chooseClientAlias should return DSA alias");


        //  String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
        Asserts.assertNull(km.chooseServerAlias(NOTHING, null, null),
                "chooseServerAlias shouldn't return alias for unknown type");

        Asserts.assertEQ(km.chooseServerAlias(RSA, null, null),
                RSA_ALIAS,
                "chooseServerAlias should return RSA alias");

        Asserts.assertEQ(km.chooseServerAlias(DSA, null, null),
                DSA_ALIAS,
                "chooseServerAlias should return DSA alias");
    }

    private static X509KeyManager getKeyManager() throws Exception {
        char[] passphrase = "passphrase".toCharArray();

        KeyPair rsaKey = KeyPairGenerator.getInstance(RSA).generateKeyPair();
        KeyPair dsaKey = KeyPairGenerator.getInstance(DSA).generateKeyPair();

        // create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, passphrase);

        ks.setKeyEntry(RSA_ALIAS,
                rsaKey.getPrivate(),
                passphrase,
                new Certificate[]{createSelfSignedCert(rsaKey,
                        "SHA256withRSA")});
        ks.setKeyEntry(DSA_ALIAS,
                dsaKey.getPrivate(),
                passphrase,
                new Certificate[]{createSelfSignedCert(dsaKey,
                        "SHA256withDSA")});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
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
