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

/*
 * @test
 * @bug 8327461
 * @summary engineGetEntry in PKCS12KeyStore should be thread-safe
 * @library /test/lib ../../../java/security/testlibrary
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @build CertificateBuilder
 * @run main GetSetEntryTest
 */

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.Date;

import sun.security.testlibrary.CertificateBuilder;

public class GetSetEntryTest {

    public static final String TEST = "test";

    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        char[] password = "password".toCharArray();
        KeyStore.PasswordProtection protParam = new KeyStore.PasswordProtection(password);
        ks.load(null, null);

        CertificateBuilder cbld = new CertificateBuilder();
        KeyPairGenerator keyPairGen1 = KeyPairGenerator.getInstance("EC");
        keyPairGen1.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecKeyPair = keyPairGen1.genKeyPair();

        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
        long end = start + TimeUnit.DAYS.toMillis(1085);
        boolean[] kuBitSettings = {true, false, false, false, false, true,
                true, false, false};

        // Set up the EC Cert
        cbld.setSubjectName("CN=EC Test Cert, O=SomeCompany").
                setPublicKey(ecKeyPair.getPublic()).
                setSerialNumber(new BigInteger("1")).
                setValidity(new Date(start), new Date(end)).
                addSubjectKeyIdExt(ecKeyPair.getPublic()).
                addAuthorityKeyIdExt(ecKeyPair.getPublic()).
                addBasicConstraintsExt(true, true, -1).
                addKeyUsageExt(kuBitSettings);

        X509Certificate ecCert = cbld.build(null, ecKeyPair.getPrivate(), "SHA256withECDSA");

        KeyPairGenerator keyPairGen2 = KeyPairGenerator.getInstance("RSA");
        keyPairGen2.initialize(4096);
        KeyPair rsaKeyPair = keyPairGen2.genKeyPair();

        cbld.reset();
        // Set up the RSA Cert
        cbld.setSubjectName("CN=RSA Test Cert, O=SomeCompany").
                setPublicKey(rsaKeyPair.getPublic()).
                setSerialNumber(new BigInteger("1")).
                setValidity(new Date(start), new Date(end)).
                addSubjectKeyIdExt(rsaKeyPair.getPublic()).
                addAuthorityKeyIdExt(rsaKeyPair.getPublic()).
                addBasicConstraintsExt(true, true, -1).
                addKeyUsageExt(kuBitSettings);

        X509Certificate rsaCert = cbld.build(null, rsaKeyPair.getPrivate(), "SHA256withRSA");

        KeyStore.PrivateKeyEntry ecEntry = new KeyStore.PrivateKeyEntry(ecKeyPair.getPrivate(),
                new X509Certificate[]{ecCert});
        KeyStore.PrivateKeyEntry rsaEntry = new KeyStore.PrivateKeyEntry(rsaKeyPair.getPrivate(),
                new X509Certificate[]{rsaCert});

        test(ks, ecEntry, rsaEntry, protParam);
    }

    private static final int MAX_ITERATIONS = 100;

    private static void test(KeyStore ks, KeyStore.PrivateKeyEntry ec,
                             KeyStore.PrivateKeyEntry rsa,
                             KeyStore.PasswordProtection protParam)
            throws Exception {
        ks.setEntry(TEST, ec, protParam);

        AtomicBoolean syncIssue = new AtomicBoolean(false);

        Thread thread = new Thread(() -> {
            int iterations = 0;
            while (!syncIssue.get() && iterations < MAX_ITERATIONS) {
                try {
                    ks.setEntry(TEST, ec, protParam);
                    ks.setEntry(TEST, rsa, protParam);
                } catch (Exception ex) {
                    syncIssue.set(true);
                    ex.printStackTrace();
                    System.out.println("Test failed");
                    System.exit(1);
                }
                iterations++;
            }
        });
        thread.start();

        int iterations = 0;
        while (!syncIssue.get() && iterations < MAX_ITERATIONS) {
            try {
                ks.getEntry(TEST, protParam);
            } catch (Exception ex) {
                syncIssue.set(true);
                ex.printStackTrace();
                System.out.println("Test failed");
                System.exit(1);
            }
            iterations++;
        }

        thread.join();

        if (!syncIssue.get()) {
            System.out.println("Test completed successfully");
        }
    }
}
