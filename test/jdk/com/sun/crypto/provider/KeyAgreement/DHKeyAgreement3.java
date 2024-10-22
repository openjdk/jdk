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
 * @library /test/lib
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
import jdk.test.lib.security.DiffieHellmanGroup;
import jdk.test.lib.security.SecurityUtils;

/**
 * This test utility executes the Diffie-Hellman key agreement protocol
 * between 3 parties: Alice, Bob, and Carol.
 *
 * By default, preconfigured parameters are used.
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

        DiffieHellmanGroup dhGroup = SecurityUtils.getTestDHGroup();
        DHParameterSpec dhParamSpec;
        System.err.println("Using " + dhGroup.name() + " Diffie-Hellman parameters");
        dhParamSpec = new DHParameterSpec(dhGroup.getPrime(), dhGroup.getBase());

        // Alice creates her own DH key pair
        System.err.println("ALICE: Generate DH keypair ...");
        KeyPairGenerator aliceKpairGen = KeyPairGenerator.getInstance("DH", "SunJCE");
        aliceKpairGen.initialize(dhParamSpec);
        KeyPair aliceKpair = aliceKpairGen.generateKeyPair();

        // Bob creates his own DH key pair
        System.err.println("BOB: Generate DH keypair ...");
        KeyPairGenerator bobKpairGen = KeyPairGenerator.getInstance("DH", "SunJCE");
        bobKpairGen.initialize(dhParamSpec);
        KeyPair bobKpair = bobKpairGen.generateKeyPair();

        // Carol creates her own DH key pair
        System.err.println("CAROL: Generate DH keypair ...");
        KeyPairGenerator carolKpairGen = KeyPairGenerator.getInstance("DH", "SunJCE");
        carolKpairGen.initialize(dhParamSpec);
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
}
