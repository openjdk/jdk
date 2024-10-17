/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 0000000
 * @summary DHGenSharedSecret
 * @author Jan Luehe
 */
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.math.BigInteger;

public class DHGenSharedSecret {

    /**
    * RFC 7919 - ffdhe2048.
    */
    static byte[] DHPrime = {
    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
    (byte) 0xAD, (byte) 0xF8, (byte) 0x54, (byte) 0x58, (byte) 0xA2, (byte) 0xBB, (byte) 0x4A, (byte) 0x9A,
    (byte) 0xAF, (byte) 0xDC, (byte) 0x56, (byte) 0x20, (byte) 0x27, (byte) 0x3D, (byte) 0x3C, (byte) 0xF1,
    (byte) 0xD8, (byte) 0xB9, (byte) 0xC5, (byte) 0x83, (byte) 0xCE, (byte) 0x2D, (byte) 0x36, (byte) 0x95,
    (byte) 0xA9, (byte) 0xE1, (byte) 0x36, (byte) 0x41, (byte) 0x14, (byte) 0x64, (byte) 0x33, (byte) 0xFB,
    (byte) 0xCC, (byte) 0x93, (byte) 0x9D, (byte) 0xCE, (byte) 0x24, (byte) 0x9B, (byte) 0x3E, (byte) 0xF9,
    (byte) 0x7D, (byte) 0x2F, (byte) 0xE3, (byte) 0x63, (byte) 0x63, (byte) 0x0C, (byte) 0x75, (byte) 0xD8,
    (byte) 0xF6, (byte) 0x81, (byte) 0xB2, (byte) 0x02, (byte) 0xAE, (byte) 0xC4, (byte) 0x61, (byte) 0x7A,
    (byte) 0xD3, (byte) 0xDF, (byte) 0x1E, (byte) 0xD5, (byte) 0xD5, (byte) 0xFD, (byte) 0x65, (byte) 0x61,
    (byte) 0x24, (byte) 0x33, (byte) 0xF5, (byte) 0x1F, (byte) 0x5F, (byte) 0x06, (byte) 0x6E, (byte) 0xD0,
    (byte) 0x85, (byte) 0x63, (byte) 0x65, (byte) 0x55, (byte) 0x3D, (byte) 0xED, (byte) 0x1A, (byte) 0xF3,
    (byte) 0xB5, (byte) 0x57, (byte) 0x13, (byte) 0x5E, (byte) 0x7F, (byte) 0x57, (byte) 0xC9, (byte) 0x35,
    (byte) 0x98, (byte) 0x4F, (byte) 0x0C, (byte) 0x70, (byte) 0xE0, (byte) 0xE6, (byte) 0x8B, (byte) 0x77,
    (byte) 0xE2, (byte) 0xA6, (byte) 0x89, (byte) 0xDA, (byte) 0xF3, (byte) 0xEF, (byte) 0xE8, (byte) 0x72,
    (byte) 0x1D, (byte) 0xF1, (byte) 0x58, (byte) 0xA1, (byte) 0x36, (byte) 0xAD, (byte) 0xE7, (byte) 0x35,
    (byte) 0x30, (byte) 0xAC, (byte) 0xCA, (byte) 0x4F, (byte) 0x48, (byte) 0x3A, (byte) 0x79, (byte) 0x7A,
    (byte) 0xBC, (byte) 0x0A, (byte) 0xB1, (byte) 0x82, (byte) 0xB3, (byte) 0x24, (byte) 0xFB, (byte) 0x61,
    (byte) 0xD1, (byte) 0x08, (byte) 0xA9, (byte) 0x4B, (byte) 0xB2, (byte) 0xC8, (byte) 0xE3, (byte) 0xFB,
    (byte) 0xB9, (byte) 0x6A, (byte) 0xDA, (byte) 0xB7, (byte) 0x60, (byte) 0xD7, (byte) 0xF4, (byte) 0x68,
    (byte) 0x1D, (byte) 0x4F, (byte) 0x42, (byte) 0xA3, (byte) 0xDE, (byte) 0x39, (byte) 0x4D, (byte) 0xF4,
    (byte) 0xAE, (byte) 0x56, (byte) 0xED, (byte) 0xE7, (byte) 0x63, (byte) 0x72, (byte) 0xBB, (byte) 0x19,
    (byte) 0x0B, (byte) 0x07, (byte) 0xA7, (byte) 0xC8, (byte) 0xEE, (byte) 0x0A, (byte) 0x6D, (byte) 0x70,
    (byte) 0x9E, (byte) 0x02, (byte) 0xFC, (byte) 0xE1, (byte) 0xCD, (byte) 0xF7, (byte) 0xE2, (byte) 0xEC,
    (byte) 0xC0, (byte) 0x34, (byte) 0x04, (byte) 0xCD, (byte) 0x28, (byte) 0x34, (byte) 0x2F, (byte) 0x61,
    (byte) 0x91, (byte) 0x72, (byte) 0xFE, (byte) 0x9C, (byte) 0xE9, (byte) 0x85, (byte) 0x83, (byte) 0xFF,
    (byte) 0x8E, (byte) 0x4F, (byte) 0x12, (byte) 0x32, (byte) 0xEE, (byte) 0xF2, (byte) 0x81, (byte) 0x83,
    (byte) 0xC3, (byte) 0xFE, (byte) 0x3B, (byte) 0x1B, (byte) 0x4C, (byte) 0x6F, (byte) 0xAD, (byte) 0x73,
    (byte) 0x3B, (byte) 0xB5, (byte) 0xFC, (byte) 0xBC, (byte) 0x2E, (byte) 0xC2, (byte) 0x20, (byte) 0x05,
    (byte) 0xC5, (byte) 0x8E, (byte) 0xF1, (byte) 0x83, (byte) 0x7D, (byte) 0x16, (byte) 0x83, (byte) 0xB2,
    (byte) 0xC6, (byte) 0xF3, (byte) 0x4A, (byte) 0x26, (byte) 0xC1, (byte) 0xB2, (byte) 0xEF, (byte) 0xFA,
    (byte) 0x88, (byte) 0x6B, (byte) 0x42, (byte) 0x38, (byte) 0x61, (byte) 0x28, (byte) 0x5C, (byte) 0x97,
    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };

    static byte[] DHBase = {
            (byte) 0x02
    };

    public static void main(String[] args) throws Exception {
        DHGenSharedSecret test = new DHGenSharedSecret();
        test.run();
    }

    public void run() throws Exception {
        long start, end;

        BigInteger p = new BigInteger(1, DHPrime);
        BigInteger g = new BigInteger(1, DHBase);
        int l = 512;

        DHParameterSpec spec =
            new DHParameterSpec(p, g, l);

        // generate keyPairs using parameters
        KeyPairGenerator keyGen =
            KeyPairGenerator.getInstance("DH", "SunJCE");
        keyGen.initialize(spec);

        // Alice generates her key pairs
        KeyPair keyA = keyGen.generateKeyPair();

        // Bob generates his key pairs
        KeyPair keyB = keyGen.generateKeyPair();

        // Alice encodes her public key in x509 format
        // , and sends it over to Bob.
        byte[] alicePubKeyEnc = keyA.getPublic().getEncoded();

        // bob encodes his publicKey in x509 format and
        // sends it over to Alice
        byte[] bobPubKeyEnc = keyB.getPublic().getEncoded();

        // bob uses it to generate Secret
        X509EncodedKeySpec x509Spec =
            new X509EncodedKeySpec(alicePubKeyEnc);
        KeyFactory bobKeyFac = KeyFactory.getInstance("DH", "SunJCE");
        PublicKey alicePubKey = bobKeyFac.generatePublic(x509Spec);


        KeyAgreement bobAlice = KeyAgreement.getInstance("DH", "SunJCE");
        start = System.currentTimeMillis();
        bobAlice.init(keyB.getPrivate());
        bobAlice.doPhase(alicePubKey, true);
        byte[] bobSecret = bobAlice.generateSecret();
        end = System.currentTimeMillis();

        System.out.println("Time elapsed: " + (end - start));
        System.out.println("Test Passed!");
    }
}
