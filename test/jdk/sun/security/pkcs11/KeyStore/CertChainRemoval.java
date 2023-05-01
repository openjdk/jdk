/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8301154
 * @summary KeyStore support for NSS cert/key databases
 * @library /test/lib ..
 * @run testng/othervm CertChainRemoval
 */

import java.io.*;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.util.*;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.Security;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.*;
import java.security.spec.*;
import java.security.interfaces.*;

import javax.security.auth.Subject;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class CertChainRemoval extends PKCS11Test {

    private static final Path TEST_DATA_PATH = Path.of(BASE)
            .resolve("CertChainRemoval");
    private static final String DIR = TEST_DATA_PATH.toString();
    private static final char[] tokenPwd =
                        new char[] { 't', 'e', 's', 't', '1', '2' };

    private static final String KS_TYPE = "PKCS11";

    @BeforeClass
    public void setUp() throws Exception {
        copyNssCertKeyToClassesDir();
        setCommonSystemProps();
        System.setProperty("CUSTOM_P11_CONFIG",
                TEST_DATA_PATH.resolve("p11-nss.txt").toString());
    }

    private static class FooEntry implements KeyStore.Entry { }

    @Test
    public void test() throws Exception {
        main(new CertChainRemoval());
    }

    private static PrivateKey getPrivateKey(String fn)
            throws NoSuchAlgorithmException, IOException,
            InvalidKeySpecException, FileNotFoundException,
            ClassNotFoundException {
        KeyFactory kf = KeyFactory.getInstance("RSA");

        FileInputStream fis = new FileInputStream(new File(DIR, fn));
        String key = new String(fis.readAllBytes(), Charset.defaultCharset());
        String privKeyPEM = key.replace("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("\\n", "")
                .replace("-----END PRIVATE KEY-----", "");

        byte[] privKeyBytes = Base64.getDecoder().decode(privKeyPEM);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(privKeyBytes));
    }

    private static Certificate[] getCertificateChain(String... fn)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            CertificateException, FileNotFoundException {
        Certificate[] chain = new Certificate[fn.length];
        CertificateFactory cf = CertificateFactory.getInstance("X.509", "SUN");
        for (int i = 0; i < chain.length; i++) {
            chain[i] = cf.generateCertificate(new FileInputStream
                (new File(DIR, fn[i])));
        }
        return chain;
    }

    private static void printKeyStore(String header, KeyStore ks)
            throws KeyStoreException {
        System.out.println(header);
        Enumeration enu = ks.aliases();
        int count = 0;
        while (enu.hasMoreElements()) {
            count++;
            System.out.println("Entry# " + count +
                    " = " + (String)enu.nextElement());
        }
        System.out.println("========");
    }

    private static void checkEntry(KeyStore ks, String alias,
            Certificate[] expChain) throws KeyStoreException {
        Certificate c = ks.getCertificate(alias);
        Certificate[] chain = ks.getCertificateChain(alias);
        if (expChain == null) {
            if (c != null || (chain != null && chain.length != 0)) {
                throw new RuntimeException("Fail: " + alias + " not removed");
            }
        } else {
            if (!c.equals(expChain[0]) || !Arrays.equals(chain, expChain)) {
                throw new RuntimeException("Fail: " + alias +
                        " chain check diff");
            }
        }
    }

    public void main(Provider p) throws Exception {

        KeyStore ks = KeyStore.getInstance(KS_TYPE, p);
        ks.load(null, tokenPwd);
        printKeyStore("Initial: ", ks);

        PrivateKey pk1PrivKey = getPrivateKey("pk1.key");
        Certificate[] pk1Chain =
                getCertificateChain("pk1.cert", "ca.cert");

        PrivateKey caPrivKey = getPrivateKey("ca.key");
        Certificate[] caChain = getCertificateChain("ca.cert");

        // populate keystore with "pk1" and "ca", then delete "pk1"
        System.out.println("Add pk1 and ca, then delete pk1");
        ks.setKeyEntry("pk1", pk1PrivKey, null, pk1Chain);
        ks.setKeyEntry("ca", caPrivKey, null, caChain);
        ks.deleteEntry("pk1");

        // reload the keystore
        ks.store(null, tokenPwd);
        ks.load(null, tokenPwd);
        printKeyStore("Reload#1: ", ks);

        // should only have "ca"
        checkEntry(ks, "pk1", null);
        checkEntry(ks, "ca", caChain);

        // now add "pk1" and delete "ca"
        System.out.println("Now add pk1 and delete ca");
        ks.setKeyEntry("pk1", pk1PrivKey, null, pk1Chain);
        ks.deleteEntry("ca");

        // reload the keystore
        ks.store(null, tokenPwd);
        ks.load(null, tokenPwd);
        printKeyStore("Reload#2: ", ks);

        // should only have "pk1" now
        checkEntry(ks, "pk1", pk1Chain);
        checkEntry(ks, "ca", null);

        // now delete "pk1"
        System.out.println("Now delete pk1");
        ks.deleteEntry("pk1");

        // reload the keystore
        ks.store(null, tokenPwd);
        ks.load(null, tokenPwd);
        printKeyStore("Reload#3: ", ks);

        // should only have nothing now
        checkEntry(ks, "pk1", null);
        checkEntry(ks, "ca", null);

        System.out.println("Test Passed");
    }
}
