/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029849 8132082
 * @summary Make sure signing via encrypt and verifying via decrypt are not
 * supported by OracleUcrypto provider.
 * @author Anthony Scarpino
 * @key randomness
 */

import java.util.Random;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.RSAPrivateKeySpec;
import javax.crypto.Cipher;

public class CipherSignNotSupported extends UcryptoTest {

    public static void main(String[] args) throws Exception {
        main(new CipherSignNotSupported(), null);
    }

    public void doTest(Provider p) throws Exception {
        Cipher c = null;
        Random random = new Random();
        byte[] pt = new byte[117];
        byte[] ct = new byte[200];
        random.nextBytes(pt);

        try {
            c = Cipher.getInstance("RSA/ECB/PKCS1Padding", p);
        } catch (NoSuchAlgorithmException e) {
            if (System.getProperty("os.version").compareTo("5.10") == 0) {
                System.out.println("RSA not supported in S10");
                return;
            }
            throw e;
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.generateKeyPair();

        // Encryption
        c.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        ct = c.doFinal(pt);
        // Decryption
        PrivateKey[] privKeys = new PrivateKey[2];
        privKeys[0] = kp.getPrivate();
        if (privKeys[0] instanceof RSAPrivateCrtKey) {
            RSAPrivateCrtKey k = (RSAPrivateCrtKey) privKeys[0];
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privKeys[1] = kf.generatePrivate
                (new RSAPrivateKeySpec(k.getModulus(), k.getPrivateExponent()));
        } else {
            privKeys = new PrivateKey[] {privKeys[0]};
        }

        for (PrivateKey pk : privKeys) {
            System.out.println("Testing " + pk);
            c.init(Cipher.DECRYPT_MODE, pk);
            c.doFinal(ct);

            // Sign
            try {
                c.init(Cipher.ENCRYPT_MODE, pk);
                ct = c.doFinal(pt);
                throw new RuntimeException("Encrypt operation should have failed.");
            } catch (InvalidKeyException e) {
                if (e.getMessage().compareTo("RSAPublicKey required for " +
                        "encryption") != 0) {
                    System.out.println("Wrong exception thrown.");
                    throw e;
                }
            }
        }

        // Verify
        try {
            c.init(Cipher.DECRYPT_MODE, kp.getPublic());
            c.doFinal(ct);
            throw new RuntimeException("Decrypt operation should have failed.");
        } catch (InvalidKeyException e) {
            if (e.getMessage().compareTo("RSAPrivateKey required for " +
                    "decryption") != 0) {
                System.out.println("Wrong exception thrown.");
                throw e;
            }
        }

        System.out.println("Pass");
    }
}
