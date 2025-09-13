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

/**
 * @test
 * @bug 8360563
 * @summary Testing getKeyPair using ML-KEM
 * @enablePreview
 */

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Arrays;

/*
 * This generates an ML-KEM key pair and makes it into PEM data.  By using
 * PEM, it will construct a OneAsymmetricKey structure that will combine
 * the public key into the private key encoding.  The original private key does
 * not have the public key encapsulated.
 *
 * Then use EKPI.getKeyPair() to retrieve the KeyPair and verify the
 * encoding with decoded PEM data.  The private key is checked using the
 * decoded PEM, the public key is verified using both the decoded PEM and
 * the original KeyPair.
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

        // Extract the EncryptedPrivateKeyInfo to test and the OAS encoding.
        EncryptedPrivateKeyInfo ekpi = PEMDecoder.of().decode(pem,
            EncryptedPrivateKeyInfo.class);
        KeyPair mlkemKP = PEMDecoder.of().withDecryption(password).
            decode(pem, KeyPair.class);

        // Test getKey(password)
        System.out.println("Testing getKeyPair(char[]) ");
        KeyPair kp = ekpi.getKeyPair(password);
        if (!Arrays.equals(mlkemKP.getPrivate().getEncoded(),
            kp.getPrivate().getEncoded())) {
            throw new AssertionError("PrivateKey didn't match with expected.");
        }
        if (!Arrays.equals(mlkemKP.getPublic().getEncoded(),
            kp.getPublic().getEncoded())) {
            throw new AssertionError("PublicKey didn't match with decoded.");
        }
        if (!Arrays.equals(kpOrig.getPublic().getEncoded(),
            kp.getPublic().getEncoded())) {
            throw new AssertionError("PublicKey didn't match the original.");
        }
        System.out.println("Got KeyPair:  Pass");

        // Test getKey(key, provider) provider null
        System.out.println("Testing getKeyPair(key, null)");
        kp = ekpi.getKeyPair(key, null);
        if (!Arrays.equals(mlkemKP.getPrivate().getEncoded(),
            kp.getPrivate().getEncoded())) {
            throw new AssertionError("PrivateKey didn't match with expected.");
        }
        if (!Arrays.equals(mlkemKP.getPublic().getEncoded(),
            kp.getPublic().getEncoded())) {
            throw new AssertionError("PublicKey didn't match with decoded.");
        }
        if (!Arrays.equals(kpOrig.getPublic().getEncoded(),
            kp.getPublic().getEncoded())) {
            throw new AssertionError("PublicKey didn't match the original.");
        }
        System.out.println("Got KeyPair:  Pass");

        // Test getKey(key, provider) provider SunJCE
        System.out.println("Testing getKeyPair(key, SunJCE)");
        kp = ekpi.getKeyPair(key, p);
        if (!Arrays.equals(mlkemKP.getPrivate().getEncoded(),
            kp.getPrivate().getEncoded())) {
            throw new AssertionError("PrivateKey didn't match with expected.");
        }
        if (!Arrays.equals(mlkemKP.getPublic().getEncoded(),
            kp.getPublic().getEncoded())) {
            throw new AssertionError("PublicKey didn't match with decoded.");
        }
        if (!Arrays.equals(kpOrig.getPublic().getEncoded(),
            kp.getPublic().getEncoded())) {
            throw new AssertionError("PublicKey didn't match the original.");
        }
        System.out.println("Got KeyPair:  Pass");
    }
}
