/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @see PublicKeyInterop.sh
 */

import java.security.*;
import java.util.*;
import javax.crypto.*;

import sun.misc.HexDumpEncoder;

/*
 * Confirm interoperability of RSA public keys between SunMSCAPI and SunJCE
 * security providers.
 */
public class PublicKeyInterop {

    public static void main(String[] arg) throws Exception {
        KeyStore ks = KeyStore.getInstance("Windows-MY");
        ks.load(null, null);
        System.out.println("Loaded keystore: Windows-MY");

        PublicKey myPuKey =
            (PublicKey) ks.getCertificate("6888925").getPublicKey();
        System.out.println("Public key is a " + myPuKey.getClass().getName());
        PrivateKey myPrKey = (PrivateKey) ks.getKey("6888925", null);
        System.out.println("Private key is a " + myPrKey.getClass().getName());
        System.out.println();

        byte[] plain = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};
        HexDumpEncoder hde = new HexDumpEncoder();
        System.out.println("Plaintext:\n" + hde.encode(plain) + "\n");

        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, myPuKey);
        byte[] encrypted = rsa.doFinal(plain);
        System.out.println("Encrypted plaintext using RSA Cipher from " +
            rsa.getProvider().getName() + " JCE provider\n");
        System.out.println(hde.encode(encrypted) + "\n");

        Cipher rsa2 = Cipher.getInstance("RSA/ECB/PKCS1Padding", "SunMSCAPI");
        rsa2.init(Cipher.ENCRYPT_MODE, myPuKey);
        byte[] encrypted2 = rsa2.doFinal(plain);
        System.out.println("Encrypted plaintext using RSA Cipher from " +
            rsa2.getProvider().getName() + " JCE provider\n");
        System.out.println(hde.encode(encrypted2) + "\n");

        Cipher rsa3 = Cipher.getInstance("RSA/ECB/PKCS1Padding", "SunMSCAPI");
        rsa3.init(Cipher.DECRYPT_MODE, myPrKey);
        byte[] decrypted = rsa3.doFinal(encrypted);
        System.out.println("Decrypted first ciphertext using RSA Cipher from " +
            rsa3.getProvider().getName() + " JCE provider\n");
        System.out.println(hde.encode(decrypted) + "\n");
        if (! Arrays.equals(plain, decrypted)) {
            throw new Exception("First decrypted ciphertext does not match " +
                "original plaintext");
        }

        decrypted = rsa3.doFinal(encrypted2);
        System.out.println("Decrypted second ciphertext using RSA Cipher from "
            + rsa3.getProvider().getName() + " JCE provider\n");
        System.out.println(hde.encode(decrypted) + "\n");
        if (! Arrays.equals(plain, decrypted)) {
            throw new Exception("Second decrypted ciphertext does not match " +
                "original plaintext");
        }
    }
}
