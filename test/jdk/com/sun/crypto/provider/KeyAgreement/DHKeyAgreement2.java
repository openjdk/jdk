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
 * @bug 7146728
 * @library /test/lib
 * @summary DHKeyAgreement2
 * @author Jan Luehe
 * @run main/othervm -Djdk.crypto.KeyAgreement.legacyKDF=true DHKeyAgreement2
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
 * between 2 parties: Alice and Bob.
 *
 * By default, preconfigured parameters are used.
 * If this program is called with the "-gen" option, a new set of parameters
 * are created.
 */

public class DHKeyAgreement2 {

    private static final String SUNJCE = "SunJCE";

    // Hex formatter to upper case with ":" delimiter
    private static final HexFormat HEX_FORMATTER = HexFormat.ofDelimiter(":").withUpperCase();

    private DHKeyAgreement2() {}

    public static void main(String argv[]) throws Exception {
            String mode = "USE_PRECONFIGURED_DH_PARAMS";

            DHKeyAgreement2 keyAgree = new DHKeyAgreement2();

            if (argv.length > 1) {
                keyAgree.usage();
                throw new Exception("Wrong number of command options");
            } else if (argv.length == 1) {
                if (!(argv[0].equals("-gen"))) {
                    keyAgree.usage();
                    throw new Exception("Unrecognized flag: " + argv[0]);
                }
                mode = "GENERATE_DH_PARAMS";
            }

            keyAgree.run(mode);
            System.out.println("Test Passed");
    }

    private void run(String mode) throws Exception {

        DHParameterSpec dhParameterSpec;
        String algorithm = "DH";
        int primeSize = SecurityUtils.getTestKeySize(algorithm);

        if (mode.equals("GENERATE_DH_PARAMS")) {
            // Some central authority creates new DH parameters
            System.err.println("Creating Diffie-Hellman parameters ...");
            AlgorithmParameterGenerator paramGen
                = AlgorithmParameterGenerator.getInstance("DH", SUNJCE);
            paramGen.init(primeSize);
            AlgorithmParameters params = paramGen.generateParameters();
            dhParameterSpec = (DHParameterSpec)params.getParameterSpec
                (DHParameterSpec.class);
        } else {
            // use some pre-generated, test default DH parameters
            DiffieHellmanGroup dhGroup = SecurityUtils.getTestDHGroup(primeSize);
            System.err.println("Using " + dhGroup.name() + " Diffie-Hellman parameters");
            dhParameterSpec = new DHParameterSpec(dhGroup.getPrime(),
                    dhGroup.getBase());
        }

        /*
         * Alice creates her own DH key pair, using the DH parameters from
         * above
         */
        System.err.println("ALICE: Generate DH keypair ...");
        KeyPairGenerator aliceKpairGen = KeyPairGenerator.getInstance("DH", SUNJCE);
        aliceKpairGen.initialize(dhParameterSpec);
        KeyPair aliceKpair = aliceKpairGen.generateKeyPair();
        System.out.println("Alice DH public key:\n" +
                           aliceKpair.getPublic().toString());
        System.out.println("Alice DH private key:\n" +
                           aliceKpair.getPrivate().toString());
        DHParameterSpec dhParamSpec =
            ((DHPublicKey)aliceKpair.getPublic()).getParams();
        AlgorithmParameters algParams = AlgorithmParameters.getInstance("DH", SUNJCE);
        algParams.init(dhParamSpec);
        System.out.println("Alice DH parameters:\n"
                           + algParams.toString());

        // Alice executes Phase1 of her version of the DH protocol
        System.err.println("ALICE: Execute PHASE1 ...");
        KeyAgreement aliceKeyAgree = KeyAgreement.getInstance("DH", SUNJCE);
        aliceKeyAgree.init(aliceKpair.getPrivate());

        // Alice encodes her public key, and sends it over to Bob.
        byte[] alicePubKeyEnc = aliceKpair.getPublic().getEncoded();

        /*
         * Let's turn over to Bob. Bob has received Alice's public key
         * in encoded format.
         * He instantiates a DH public key from the encoded key material.
         */
        KeyFactory bobKeyFac = KeyFactory.getInstance("DH", SUNJCE);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec
            (alicePubKeyEnc);
        PublicKey alicePubKey = bobKeyFac.generatePublic(x509KeySpec);

        /*
         * Bob gets the DH parameters associated with Alice's public key.
         * He must use the same parameters when he generates his own key
         * pair.
         */
        dhParamSpec = ((DHPublicKey)alicePubKey).getParams();

        // Bob creates his own DH key pair
        System.err.println("BOB: Generate DH keypair ...");
        KeyPairGenerator bobKpairGen = KeyPairGenerator.getInstance("DH", SUNJCE);
        bobKpairGen.initialize(dhParamSpec);
        KeyPair bobKpair = bobKpairGen.generateKeyPair();
        System.out.println("Bob DH public key:\n" +
                           bobKpair.getPublic().toString());
        System.out.println("Bob DH private key:\n" +
                           bobKpair.getPrivate().toString());

        // Bob executes Phase1 of his version of the DH protocol
        System.err.println("BOB: Execute PHASE1 ...");
        KeyAgreement bobKeyAgree = KeyAgreement.getInstance("DH", SUNJCE);
        bobKeyAgree.init(bobKpair.getPrivate());

        // Bob encodes his public key, and sends it over to Alice.
        byte[] bobPubKeyEnc = bobKpair.getPublic().getEncoded();

        /*
         * Alice uses Bob's public key for Phase2 of her version of the DH
         * protocol.
         * Before she can do so, she has to instanticate a DH public key
         * from Bob's encoded key material.
         */
        KeyFactory aliceKeyFac = KeyFactory.getInstance("DH", SUNJCE);
        x509KeySpec = new X509EncodedKeySpec(bobPubKeyEnc);
        PublicKey bobPubKey = aliceKeyFac.generatePublic(x509KeySpec);
        System.err.println("ALICE: Execute PHASE2 ...");
        aliceKeyAgree.doPhase(bobPubKey, true);

        /*
         * Bob uses Alice's public key for Phase2 of his version of the DH
         * protocol.
         */
        System.err.println("BOB: Execute PHASE2 ...");
        bobKeyAgree.doPhase(alicePubKey, true);

        /*
         * At this stage, both Alice and Bob have completed the DH key
         * agreement protocol.
         * Each generates the (same) shared secret.
         */
        byte[] aliceSharedSecret = aliceKeyAgree.generateSecret();
        int aliceLen = aliceSharedSecret.length;

        // check if alice's key agreement has been reset afterwards
        try {
            aliceKeyAgree.generateSecret();
            throw new Exception("Error: alice's KeyAgreement not reset");
        } catch (IllegalStateException e) {
            System.out.println("EXPECTED:  " + e.getMessage());
        }

        byte[] bobSharedSecret = new byte[aliceLen];
        int bobLen;
        try {
            // provide output buffer that is too short
            bobLen = bobKeyAgree.generateSecret(bobSharedSecret, 1);
        } catch (ShortBufferException e) {
            System.out.println("EXPECTED:  " + e.getMessage());
        }
        // retry w/ output buffer of required size
        bobLen = bobKeyAgree.generateSecret(bobSharedSecret, 0);

        // check if bob's key agreement has been reset afterwards
        try {
            bobKeyAgree.generateSecret(bobSharedSecret, 0);
            throw new Exception("Error: bob's KeyAgreement not reset");
        } catch (IllegalStateException e) {
            System.out.println("EXPECTED:  " + e.getMessage());
        }

        System.out.println("Alice secret: " + HEX_FORMATTER.formatHex(aliceSharedSecret));
        System.out.println("Bob secret: " + HEX_FORMATTER.formatHex(bobSharedSecret));

        if (aliceLen != bobLen) {
            throw new Exception("Shared secrets have different lengths");
        }
        for (int i=0; i<aliceLen; i++) {
            if (aliceSharedSecret[i] != bobSharedSecret[i]) {
                throw new Exception("Shared secrets differ");
            }
        }
        System.err.println("Shared secrets are the same");

        testSecretKey(bobKeyAgree, alicePubKey, "DES");
        testSecretKey(bobKeyAgree, alicePubKey, "AES");
    }

    private static void testSecretKey(KeyAgreement bobKeyAgree, PublicKey alicePubKey, String algo)
            throws Exception {
        // Now let's return the shared secret as a SecretKey object
        // and use it for encryption
        System.out.println("Return shared secret as SecretKey object with algorithm: " + algo);
        bobKeyAgree.doPhase(alicePubKey, true);
        SecretKey key = bobKeyAgree.generateSecret(algo);

        Cipher cipher = Cipher.getInstance(algo + "/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] cleartext = "This is just an example".getBytes();
        byte[] ciphertext = cipher.doFinal(cleartext);

        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] cleartext1 = cipher.doFinal(ciphertext);

        int clearLen = cleartext.length;
        int clear1Len = cleartext1.length;
        if (clearLen != clear1Len) {
            throw new Exception("DIFFERENT");
        }
        for (int i=0; i < clear1Len; i++) {
            if (cleartext[i] != cleartext1[i]) {
                throw new Exception("DIFFERENT");
            }
        }
        System.err.println("SAME");
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
