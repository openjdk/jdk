/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary DHKeyAgreement3
 * @author Jan Luehe
 */

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;
import java.util.HexFormat;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.interfaces.*;

/**
 * This test utility executes the Diffie-Hellman key agreement protocol
 * between 3 parties: Alice, Bob, and Carol.
 *
 * We use the same 1024 bit prime modulus and base generator that are used by
 * SKIP.
 */

public class DHKeyAgreement3 {

    // Hex formatter to upper case with ":" delimiter
    private static final HexFormat HEX_FORMATTER = HexFormat.ofDelimiter(":").withUpperCase();

    private DHKeyAgreement3() {}

    public static void main(String argv[]) throws Exception {
            DHKeyAgreement3 keyAgree = new DHKeyAgreement3();
            keyAgree.run();
            System.out.println("Test Passed");
    }

    private void run() throws Exception {

        DHParameterSpec ffhde2048ParamSpec;
        System.err.println("Using FFDHE2048 Diffie-Hellman parameters");
        ffhde2048ParamSpec = new DHParameterSpec(ffhde2048Modulus, ffhde2048Base);

        // Alice creates her own DH key pair
        System.err.println("ALICE: Generate DH keypair ...");
        KeyPairGenerator aliceKpairGen = KeyPairGenerator.getInstance("DH", "SunJCE");
        aliceKpairGen.initialize(ffhde2048ParamSpec);
        KeyPair aliceKpair = aliceKpairGen.generateKeyPair();

        // Bob creates his own DH key pair
        System.err.println("BOB: Generate DH keypair ...");
        KeyPairGenerator bobKpairGen = KeyPairGenerator.getInstance("DH", "SunJCE");
        bobKpairGen.initialize(ffhde2048ParamSpec);
        KeyPair bobKpair = bobKpairGen.generateKeyPair();

        // Carol creates her own DH key pair
        System.err.println("CAROL: Generate DH keypair ...");
        KeyPairGenerator carolKpairGen = KeyPairGenerator.getInstance("DH", "SunJCE");
        carolKpairGen.initialize(ffhde2048ParamSpec);
        KeyPair carolKpair = carolKpairGen.generateKeyPair();


        // Alice initialize
        System.err.println("ALICE: Initialize ...");
        KeyAgreement aliceKeyAgree = KeyAgreement.getInstance("DH", "SunJCE");
        aliceKeyAgree.init(aliceKpair.getPrivate());

        // Bob initialize
        System.err.println("BOB: Initialize ...");
        KeyAgreement bobKeyAgree = KeyAgreement.getInstance("DH", "SunJCE");
        bobKeyAgree.init(bobKpair.getPrivate());

        // Carol initialize
        System.err.println("CAROL: Initialize ...");
        KeyAgreement carolKeyAgree = KeyAgreement.getInstance("DH", "SunJCE");
        carolKeyAgree.init(carolKpair.getPrivate());


        // Alice uses Carol's public key
        Key ac = aliceKeyAgree.doPhase(carolKpair.getPublic(), false);

        // Bob uses Alice's public key
        Key ba = bobKeyAgree.doPhase(aliceKpair.getPublic(), false);

        // Carol uses Bob's public key
        Key cb = carolKeyAgree.doPhase(bobKpair.getPublic(), false);


        // Alice uses Carol's result from above
        aliceKeyAgree.doPhase(cb, true);

        // Bob uses Alice's result from above
        bobKeyAgree.doPhase(ac, true);

        // Carol uses Bob's result from above
        carolKeyAgree.doPhase(ba, true);


        // Alice, Bob and Carol compute their secrets
        byte[] aliceSharedSecret = aliceKeyAgree.generateSecret();
        int aliceLen = aliceSharedSecret.length;
        System.out.println("Alice secret: " + HEX_FORMATTER.formatHex(aliceSharedSecret));

        byte[] bobSharedSecret = bobKeyAgree.generateSecret();
        int bobLen = bobSharedSecret.length;
        System.out.println("Bob secret: " + HEX_FORMATTER.formatHex(bobSharedSecret));

        byte[] carolSharedSecret = carolKeyAgree.generateSecret();
        int carolLen = carolSharedSecret.length;
        System.out.println("Carol secret: " + HEX_FORMATTER.formatHex(carolSharedSecret));


        // Compare Alice and Bob
        if (aliceLen != bobLen) {
            throw new Exception("Alice and Bob have different lengths");
        }
        for (int i=0; i<aliceLen; i++) {
            if (aliceSharedSecret[i] != bobSharedSecret[i]) {
                throw new Exception("Alice and Bob differ");
            }
        }
        System.err.println("Alice and Bob are the same");

        // Compare Bob and Carol
        if (bobLen != carolLen) {
            throw new Exception("Bob and Carol have different lengths");
        }
        for (int i=0; i<bobLen; i++) {
            if (bobSharedSecret[i] != carolSharedSecret[i]) {
                throw new Exception("Bob and Carol differ");
            }
        }
        System.err.println("Bob and Carol are the same");
    }


    /*
     * Converts a byte to hex digit and writes to the supplied buffer
     */
    private void byte2hex(byte b, StringBuffer buf) {
        char[] hexChars = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
                            '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        int high = ((b & 0xf0) >> 4);
        int low = (b & 0x0f);
        buf.append(hexChars[high]);
        buf.append(hexChars[low]);
    }

    /*
     * Prints the usage of this test.
     */
    private void usage() {
        System.err.print("DHKeyAgreement usage: ");
        System.err.println("[-gen]");
    }

    /**
     * RFC 7919 - ffdhe2048.
     */
    private static byte[] FFDHE2048PrimeBytes = {
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
    private static final BigInteger ffhde2048Modulus = new BigInteger(1, FFDHE2048PrimeBytes);
    private static final BigInteger ffhde2048Base = BigInteger.valueOf(2);
}
