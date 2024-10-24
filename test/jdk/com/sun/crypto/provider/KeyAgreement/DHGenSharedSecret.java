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
 * @library /test/lib
 * @summary DHGenSharedSecret
 * @author Jan Luehe
 */
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.math.BigInteger;
import jdk.test.lib.security.DiffieHellmanGroup;
import jdk.test.lib.security.SecurityUtils;

public class DHGenSharedSecret {

    public static void main(String[] args) throws Exception {
        DHGenSharedSecret test = new DHGenSharedSecret();
        test.run();
    }

    public void run() throws Exception {
        long start, end;

        DiffieHellmanGroup dhGroup = SecurityUtils.getTestDHGroup();
        BigInteger p = dhGroup.getPrime();
        BigInteger g = new BigInteger(1, dhGroup.getBase().toByteArray());
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
