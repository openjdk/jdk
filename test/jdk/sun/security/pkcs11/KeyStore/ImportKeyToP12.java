/*
 * Copyright (c) 2023, Red Hat, Inc.
 *
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/*
 * @test
 * @bug 8301553
 * @summary test SunPKCS11's password based privacy and integrity
 *          applied to PKCS #12 keystores
 * @library /test/lib ..
 * @modules java.base/sun.security.util
 * @run main/othervm/timeout=30 ImportKeyToP12
 */

public final class ImportKeyToP12 extends PKCS11Test {
    private static final String alias = "alias";
    private static final char[] password = "123456".toCharArray();
    private static final Key key = new SecretKeySpec(new byte[] {
            0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7,
            0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf }, "AES");
    private static final String[] pbeCipherAlgs = new String[] {
            "PBEWithHmacSHA1AndAES_128", "PBEWithHmacSHA224AndAES_128",
            "PBEWithHmacSHA256AndAES_128", "PBEWithHmacSHA384AndAES_128",
            "PBEWithHmacSHA512AndAES_128", "PBEWithHmacSHA1AndAES_256",
            "PBEWithHmacSHA224AndAES_256", "PBEWithHmacSHA256AndAES_256",
            "PBEWithHmacSHA384AndAES_256", "PBEWithHmacSHA512AndAES_256"
    };
    private static final String[] pbeMacAlgs = new String[] {
            "HmacPBESHA1", "HmacPBESHA224", "HmacPBESHA256",
            "HmacPBESHA384", "HmacPBESHA512"
    };
    private static final KeyStore p12;
    private static final String sep = "======================================" +
            "===================================";

    static {
        KeyStore tP12 = null;
        try {
            tP12 = KeyStore.getInstance("PKCS12");
        } catch (KeyStoreException e) {}
        p12 = tP12;
    }

    public void main(Provider sunPKCS11) throws Exception {
        System.out.println("SunPKCS11: " + sunPKCS11.getName());
        // Test all privacy PBE algorithms with an integrity algorithm fixed
        for (String pbeCipherAlg : pbeCipherAlgs) {
            // Make sure that SunPKCS11 implements the Cipher algorithm
            Cipher.getInstance(pbeCipherAlg, sunPKCS11);
            testWith(sunPKCS11, pbeCipherAlg, pbeMacAlgs[0]);
        }
        // Test all integrity PBE algorithms with a privacy algorithm fixed
        for (String pbeMacAlg : pbeMacAlgs) {
            // Make sure that SunPKCS11 implements the Mac algorithm
            Mac.getInstance(pbeMacAlg, sunPKCS11);
            testWith(sunPKCS11, pbeCipherAlgs[0], pbeMacAlg);
        }
        System.out.println("TEST PASS - OK");
    }

    /*
     * Consistency test: 1) store a secret key in a PKCS #12 keystore using
     * PBE algorithms from SunPKCS11 and, 2) read the secret key from the
     * PKCS #12 keystore using PBE algorithms from other security providers
     * such as SunJCE.
     */
    private void testWith(Provider sunPKCS11, String pbeCipherAlg,
            String pbeMacAlg) throws Exception {
        System.out.println(sep + System.lineSeparator() +
                "Cipher PBE: " + pbeCipherAlg + System.lineSeparator() +
                "Mac PBE: " + pbeMacAlg);

        System.setProperty("keystore.pkcs12.macAlgorithm", pbeMacAlg);
        System.setProperty("keystore.pkcs12.keyProtectionAlgorithm",
                pbeCipherAlg);

        // Create an empty PKCS #12 keystore
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        p12.load(null, password);

        // Use PBE privacy and integrity algorithms from SunPKCS11 to store
        // the secret key
        Security.insertProviderAt(sunPKCS11, 1);
        p12.setKeyEntry(alias, key, password, null);
        p12.store(baos, password);

        // Use PBE privacy and integrity algorithms from other security
        // providers, such as SunJCE, to read the secret key
        Security.removeProvider(sunPKCS11.getName());
        p12.load(new ByteArrayInputStream(baos.toByteArray()), password);
        Key k = p12.getKey(alias, password);

        if (!MessageDigest.isEqual(key.getEncoded(), k.getEncoded())) {
            throw new Exception("Keys differ. Consistency check failed.");
        }
        System.out.println("Secret key import successful"
                + System.lineSeparator() + sep);
    }

    public static void main(String[] args) throws Exception {
        main(new ImportKeyToP12());
    }
}
