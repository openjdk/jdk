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
 * @bug 8301154 8309214
 * @summary test cert chain deletion logic w/ NSS PKCS11 KeyStore
 * @library /test/lib ..
 * @run testng/othervm CertChainRemoval
 */
import jdk.test.lib.SecurityTools;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.cert.Certificate;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class CertChainRemoval extends PKCS11Test {

    private static final Path TEST_DATA_PATH = Path.of(BASE)
            .resolve("CertChainRemoval");
    private static final String DIR = TEST_DATA_PATH.toString();

    private record KeyStoreInfo(File file, String type, char[] passwd) {}

    private static final KeyStoreInfo TEMP = new KeyStoreInfo(
            new File(DIR, "temp.ks"),
            "JKS",
            new char[] { 'c', 'h', 'a', 'n', 'g', 'e', 'i', 't' });

    private static final KeyStoreInfo PKCS11KS = new KeyStoreInfo(
            null,
            "PKCS11",
            new char[] { 't', 'e', 's', 't', '1', '2' });

    @BeforeClass
    public void setUp() throws Exception {
        copyNssCertKeyToClassesDir();
        setCommonSystemProps();
        // if temp keystore already exists; skip the creation
        if (!TEMP.file.exists()) {
            createKeyStore(TEMP);
        }
        System.setProperty("CUSTOM_P11_CONFIG",
                TEST_DATA_PATH.resolve("p11-nss.txt").toString());
    }

    @Test
    public void test() throws Exception {
        main(new CertChainRemoval());
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
                System.out.println("expChain: " + expChain.length);
                System.out.println("actualChain: " + chain.length);
                throw new RuntimeException("Fail: " + alias +
                        " chain check diff");
            }
        }
    }

    public void main(Provider p) throws Exception {
        KeyStore sunks = KeyStore.getInstance(TEMP.type, "SUN");
        sunks.load(new FileInputStream(TEMP.file), TEMP.passwd);
        printKeyStore("Starting with: ", sunks);

        KeyStore p11ks;
        try {
            p11ks = KeyStore.getInstance(PKCS11KS.type, p);
            p11ks.load(null, PKCS11KS.passwd);
            printKeyStore("Initial PKCS11 KeyStore: ", p11ks);
        } catch (Exception e) {
            System.out.println("Skip test, due to " + e);
            return;
        }

        // get the necessary keys from the temp keystore
        Key pk1PrivKey = sunks.getKey("pk1", TEMP.passwd);
        Certificate pk1Cert = sunks.getCertificate("pk1");
        Key caPrivKey = sunks.getKey("ca1", TEMP.passwd);
        Certificate ca1Cert = sunks.getCertificate("ca1");
        Key rootPrivKey = sunks.getKey("root", TEMP.passwd);
        Certificate rootCert = sunks.getCertificate("root");

        Certificate[] pk1Chain = { pk1Cert, ca1Cert, rootCert };
        Certificate[] ca1Chain = { ca1Cert, rootCert };
        Certificate[] rootChain = { rootCert };

        // populate keystore with "pk1" and "ca", then delete "pk1"
        System.out.println("Add pk1, ca1 and root, then delete pk1");
        p11ks.setKeyEntry("pk1", pk1PrivKey, null, pk1Chain);
        p11ks.setKeyEntry("ca1", caPrivKey, null, ca1Chain);
        p11ks.setKeyEntry("root", rootPrivKey, null, rootChain);
        p11ks.deleteEntry("pk1");

        // reload the keystore
        p11ks.store(null, PKCS11KS.passwd);
        p11ks.load(null, PKCS11KS.passwd);
        printKeyStore("Reload#1: ca1 / root", p11ks);

        // should only have "ca1" and "root"
        checkEntry(p11ks, "pk1", null);
        checkEntry(p11ks, "ca1", ca1Chain);
        checkEntry(p11ks, "root", rootChain);

        // now add "pk1" and delete "ca1"
        System.out.println("Now add pk1 and delete ca1");
        p11ks.setKeyEntry("pk1", pk1PrivKey, null, pk1Chain);
        p11ks.deleteEntry("ca1");

        // reload the keystore
        p11ks.store(null, PKCS11KS.passwd);
        p11ks.load(null, PKCS11KS.passwd);
        printKeyStore("Reload#2: pk1 / root", p11ks);

        // should only have "pk1" and "root" now
        checkEntry(p11ks, "pk1", pk1Chain);
        checkEntry(p11ks, "ca1", null);
        checkEntry(p11ks, "root", rootChain);

        // now delete "root"
        System.out.println("Now delete root");
        p11ks.deleteEntry("root");

        // reload the keystore
        p11ks.store(null, PKCS11KS.passwd);
        p11ks.load(null, PKCS11KS.passwd);
        printKeyStore("Reload#3: pk1", p11ks);

        // should only have "pk1" now
        checkEntry(p11ks, "pk1", pk1Chain);
        checkEntry(p11ks, "ca1", null);
        checkEntry(p11ks, "root", null);

        // now delete "pk1"
        System.out.println("Now delete pk1");
        p11ks.deleteEntry("pk1");

        // reload the keystore
        p11ks.store(null, PKCS11KS.passwd);
        p11ks.load(null, PKCS11KS.passwd);
        printKeyStore("Reload#4: ", p11ks);

        // should have nothing now
        checkEntry(p11ks, "pk1", null);
        checkEntry(p11ks, "ca1", null);
        checkEntry(p11ks, "root", null);

        System.out.println("Test Passed");
    }

    private static void createKeyStore(KeyStoreInfo ksi) throws Exception {
        System.out.println("Creating keypairs and storing them into " +
            ksi.file.getAbsolutePath());
        String keyGenOptions = " -keyalg RSA -keysize 2048 ";
        String keyStoreOptions = " -keystore " + ksi.file.getAbsolutePath() +
                " -storetype " + ksi.type + " -storepass " +
                new String(ksi.passwd);

        String[] aliases = { "ROOT", "CA1", "PK1" };
        for (String n : aliases) {
            SecurityTools.keytool("-genkeypair -alias " + n +
                " -dname CN=" + n + keyGenOptions + keyStoreOptions);
            String issuer = switch (n) {
                case "CA1"-> "ROOT";
                case "PK1"-> "CA1";
                default-> null;
            };
            if (issuer != null) {
                // export CSR and issue the cert using the issuer
                SecurityTools.keytool("-certreq -alias " + n +
                    " -file tmp.req" + keyStoreOptions);
                SecurityTools.keytool("-gencert -alias " + issuer +
                    " -infile tmp.req -outfile tmp.cert -validity 3650" +
                    keyStoreOptions);
                SecurityTools.keytool("-importcert -alias " + n +
                    " -file tmp.cert" + keyStoreOptions);
            }
        }
    }

}
