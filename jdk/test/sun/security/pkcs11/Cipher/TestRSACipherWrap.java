/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6572331
 * @summary basic test for RSA cipher key wrapping functionality
 * @author Valerie Peng
 * @library ..
 */
import java.io.*;
import java.util.*;

import java.security.*;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class TestRSACipherWrap extends PKCS11Test {

    private static final String RSA_ALGO = "RSA/ECB/PKCS1Padding";

    public void main(Provider p) throws Exception {
        try {
            Cipher.getInstance(RSA_ALGO, p);
        } catch (GeneralSecurityException e) {
            System.out.println("Not supported by provider, skipping");
            return;
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", p);
        kpg.initialize(1024);
        KeyPair kp = kpg.generateKeyPair();
        PublicKey publicKey = kp.getPublic();
        PrivateKey privateKey = kp.getPrivate();

        Cipher cipherPKCS11 = Cipher.getInstance(RSA_ALGO, p);
        Cipher cipherJce = Cipher.getInstance(RSA_ALGO, "SunJCE");

        String algos[] = {"AES", "RC2", "Blowfish"};
        int keySizes[] = {128, 256};

        for (int j = 0; j < algos.length; j++) {
            String algorithm = algos[j];
            KeyGenerator keygen =
                    KeyGenerator.getInstance(algorithm);

            for (int i = 0; i < keySizes.length; i++) {
                SecretKey secretKey = null;
                System.out.print("Generate " + keySizes[i] + "-bit " +
                        algorithm + " key using ");
                try {
                    keygen.init(keySizes[i]);
                    secretKey = keygen.generateKey();
                    System.out.println(keygen.getProvider().getName());
                } catch (InvalidParameterException ipe) {
                    secretKey = new SecretKeySpec(new byte[32], algorithm);
                    System.out.println("SecretKeySpec class");
                }
                test(kp, secretKey, cipherPKCS11, cipherJce);
                test(kp, secretKey, cipherPKCS11, cipherPKCS11);
                test(kp, secretKey, cipherJce, cipherPKCS11);
            }
        }
    }

    private static void test(KeyPair kp, SecretKey secretKey,
            Cipher wrapCipher, Cipher unwrapCipher)
            throws Exception {
        String algo = secretKey.getAlgorithm();
        wrapCipher.init(Cipher.WRAP_MODE, kp.getPublic());
        byte[] wrappedKey = wrapCipher.wrap(secretKey);
        unwrapCipher.init(Cipher.UNWRAP_MODE, kp.getPrivate());
        Key unwrappedKey =
                unwrapCipher.unwrap(wrappedKey, algo, Cipher.SECRET_KEY);

        System.out.println("Test " + wrapCipher.getProvider().getName() +
                "/" + unwrapCipher.getProvider().getName() + ": ");
        if (!Arrays.equals(secretKey.getEncoded(),
                unwrappedKey.getEncoded())) {
            throw new Exception("Test Failed!");
        }
        System.out.println("Passed");
    }

    public static void main(String[] args) throws Exception {
        main(new TestRSACipherWrap());
    }
}
