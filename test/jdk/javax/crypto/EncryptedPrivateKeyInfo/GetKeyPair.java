/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8360563
 * @library /test/lib
 * @summary Testing getKeyPair using ML-KEM
 * @enablePreview
 */

import jdk.test.lib.Asserts;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

/*
 * This generates an ML-KEM key pair and makes it into PEM data.  By using
 * PEM, it constructs a OneAsymmetricKey structure that combines
 * the public key into the private key encoding.  Decode the PEM data into
 * a KeyPair and an EKPI for verification.
 *
 * The original private key does not have the public key encapsulated, so it
 * cannot be used for verification.
 *
 * Verify the decoded PEM KeyPair and EKPI.getKeyPair() return matching public
 * and private keys encodings; as well as, verify the original public key
 * matches.
 */

public class GetKeyPair {
    private static final String passwdText = "fish";
    private static final char[] password = passwdText.toCharArray();
    private static final SecretKey key = new SecretKeySpec(
        passwdText.getBytes(), "PBE");

    public static void main(String[] args) throws Exception {
        Provider p = Security.getProvider(
            System.getProperty("test.provider.name", "SunJCE"));

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-KEM");
        KeyPair kpOrig = kpg.generateKeyPair();
        // Generate the PEM, constructing a OneAsymmetricKey (OAS) encoding
        String pem = PEMEncoder.of().withEncryption(password).
            encodeToString(kpOrig);
        // Extracted the KeyPair from the generated PEM for verification.
        KeyPair mlkemKP = PEMDecoder.of().withDecryption(password).
            decode(pem, KeyPair.class);
        // Extract the EncryptedPrivateKeyInfo.
        EncryptedPrivateKeyInfo ekpi = PEMDecoder.of().decode(pem,
            EncryptedPrivateKeyInfo.class);

        KeyPairs kps = new KeyPairs(kpOrig, mlkemKP);

        // Test getKey(password)
        System.out.println("Testing getKeyPair(char[]) ");
        KeyPair kp = ekpi.getKeyPair(password);
        arrayCheck(kps, kp);

        // Test getKey(key, provider) provider null
        System.out.println("Testing getKeyPair(key, null)");
        kp = ekpi.getKeyPair(key, null);
        arrayCheck(kps, kp);

        // Test getKey(key, provider) provider SunJCE
        System.out.println("Testing getKeyPair(key, SunJCE)");
        kp = ekpi.getKeyPair(key, p);
        arrayCheck(kps, kp);
    }

    static void arrayCheck(KeyPairs kps, KeyPair actual) {
        byte[] actualPrivEncoding = actual.getPrivate().getEncoded();
        byte[] actualPubEncoding = actual.getPublic().getEncoded();
        Asserts.assertEqualsByteArray(kps.mlkemPrivEncoding, actualPrivEncoding,
            "PrivateKey didn't match with expected.");
        Asserts.assertEqualsByteArray(kps.mlkemPubEncoding, actualPubEncoding,
            "PublicKey didn't match with decoded.");
        Asserts.assertEqualsByteArray(kps.origPubEncoding, actualPubEncoding,
            "PublicKey didn't match with decoded.");
        System.out.println("Got KeyPair:  Pass");
    }

    record KeyPairs(byte[] origPubEncoding, byte[] mlkemPrivEncoding,
                    byte[] mlkemPubEncoding) {
         KeyPairs(KeyPair orig, KeyPair mlkem) {
             this(orig.getPublic().getEncoded(),
                 mlkem.getPrivate().getEncoded(),
                 mlkem.getPublic().getEncoded());
         }
    };
}
