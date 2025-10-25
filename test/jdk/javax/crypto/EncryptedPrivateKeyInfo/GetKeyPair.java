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
 * @modules java.base/sun.security.util
 */

import jdk.test.lib.Asserts;
import sun.security.util.DerValue;
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
    static byte[] keyOrigPub, keyOrigPriv;

    public static void main(String[] args) throws Exception {
        Provider p = Security.getProvider(
            System.getProperty("test.provider.name", "SunJCE"));

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-KEM");
        KeyPair kpOrig = kpg.generateKeyPair();
        keyOrigPub = kpOrig.getPublic().getEncoded();
        keyOrigPriv = getPrivateKey(kpOrig.getPrivate());

        // Encode the KeyPair into PEM, constructing an OneAsymmetricKey encoding
        String pem = PEMEncoder.of().withEncryption(password).
            encodeToString(kpOrig);
        // Decode to a KeyPair from the generated PEM for verification.
        KeyPair mlkemKP = PEMDecoder.of().withDecryption(password).
            decode(pem, KeyPair.class);

        // Check decoded public key pair with original.
        Asserts.assertEqualsByteArray(mlkemKP.getPublic().getEncoded(),
            keyOrigPub, "Initial PublicKey compare didn't match.");
        byte[] priv = getPrivateKey(mlkemKP.getPrivate());
        Asserts.assertEqualsByteArray(priv, keyOrigPriv,
            "Initial PrivateKey compare didn't match");

        // Decode to a EncryptedPrivateKeyInfo.
        EncryptedPrivateKeyInfo ekpi = PEMDecoder.of().decode(pem,
            EncryptedPrivateKeyInfo.class);

        // Test getKeyPair(password)
        System.out.print("Testing getKeyPair(char[]): ");
        arrayCheck(ekpi.getKeyPair(password));

        // Test getKeyPair(key, provider) provider null
        System.out.print("Testing getKeyPair(key, null): ");
        arrayCheck(ekpi.getKeyPair(key, null));

        // Test getKeyPair(key, provider) provider SunJCE
        System.out.print("Testing getKeyPair(key, SunJCE): ");
        arrayCheck(ekpi.getKeyPair(key, p));
    }

    static void arrayCheck(KeyPair kp) throws Exception {
        Asserts.assertEqualsByteArray(getPrivateKey(kp.getPrivate()), keyOrigPriv,
            "PrivateKey didn't match with expected.");
        Asserts.assertEqualsByteArray(kp.getPublic().getEncoded(), keyOrigPub,
            "PublicKey didn't match with expected.");
        System.out.println("PASS");
    }

    static byte[] getPrivateKey(PrivateKey p) throws Exception{
        var val = new DerValue(p.getEncoded());
        // Get version
        val.data.getInteger();
        // Get AlgorithmID
        val.data.getDerValue();
        // Return PrivateKey
        return val.data.getOctetString();
    }
}
