/*
 * Copyright (C) 2024, THL A29 Limited, a Tencent company. All rights reserved.
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
 * @bug 8320449
 * @summary ECDHKeyAgreement should validate parameters before assigning them to fields.
 * @library /test/lib
 * @run main ECDHKeyAgreementParamValidation
 */

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECPrivateKeySpec;

import jdk.test.lib.Asserts;

public class ECDHKeyAgreementParamValidation {

    private static void testInitWithInvalidKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        ECPrivateKey privateKey = (ECPrivateKey) kp.getPrivate();

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        ECPrivateKey invalidPrivateKey
                = (ECPrivateKey) keyFactory.generatePrivate(
                        new ECPrivateKeySpec(BigInteger.ZERO,
                                privateKey.getParams()));

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");

        // The first initiation should succeed.
        ka.init(privateKey);

        // The second initiation should fail with invalid private key,
        // and the private key assigned by the first initiation should be cleared.
        Asserts.assertThrows(
                InvalidKeyException.class,
                () -> ka.init(invalidPrivateKey));

        // Cannot doPhase due to no private key.
        Asserts.assertThrows(
                IllegalStateException.class,
                () -> ka.doPhase(kp.getPublic(), true));

        // Cannot generate shared key due to no key
        Asserts.assertThrows(IllegalStateException.class, ka::generateSecret);
    }

    private static void testDoPhaseWithInvalidKey() throws Exception {
        // SECP256R1 key pair
        KeyPairGenerator kpgP256 = KeyPairGenerator.getInstance("EC");
        kpgP256.initialize(256);
        KeyPair kpP256 = kpgP256.generateKeyPair();

        // SECP384R1 key pair
        KeyPairGenerator kpgP384 = KeyPairGenerator.getInstance("EC");
        kpgP384.initialize(384);
        KeyPair kpP384 = kpgP384.generateKeyPair();

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(kpP256.getPrivate());

        Asserts.assertThrows(
                InvalidKeyException.class,
                () -> ka.doPhase(kpP384.getPublic(), true));

        // Should not generate shared key with SECP256R1 private key and SECP384R1 public key
        Asserts.assertThrows(IllegalStateException.class, ka::generateSecret);
    }

    public static void main(String[] args) {
        boolean failed = false;

        try {
            testInitWithInvalidKey();
        } catch (Exception e) {
            failed = true;
            e.printStackTrace();
        }

        try {
            testDoPhaseWithInvalidKey();
        } catch (Exception e) {
            failed = true;
            e.printStackTrace();
        }

        if (failed) {
            throw new RuntimeException("Test failed");
        }
    }
}
