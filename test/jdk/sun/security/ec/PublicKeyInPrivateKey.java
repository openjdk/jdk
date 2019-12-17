/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import jdk.test.lib.security.DerUtils;
import sun.security.util.DerValue;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

/*
 * @test
 * @bug 8234465
 * @library /test/lib
 * @modules java.base/sun.security.util
 * @summary Encoded elliptic curve private keys should include the public point
 */
public class PublicKeyInPrivateKey {

    public static void main(String[] args) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        byte[] pubBytes = kp.getPublic().getEncoded();
        byte[] privBytes = kp.getPrivate().getEncoded();

        // https://tools.ietf.org/html/rfc5480#section-2.
        // subjectPublicKey is 2nd in SubjectPublicKeyInfo
        DerValue pubPoint = DerUtils.innerDerValue(pubBytes, "1");

        // https://tools.ietf.org/html/rfc5208#section-5.
        // privateKey as an OCTET STRING is 3rd in PrivateKeyInfo
        // https://tools.ietf.org/html/rfc5915#section-3
        // publicKey as [1] is 3rd (we do not have parameters) in ECPrivateKey
        DerValue pubPointInPriv = DerUtils.innerDerValue(privBytes, "2c20");

        // The two public keys should be the same
        Asserts.assertEQ(pubPoint, pubPointInPriv);

        // And it's reloadable
        KeyFactory kf = KeyFactory.getInstance("EC");
        byte[] privBytes2 = kf.generatePrivate(
                new PKCS8EncodedKeySpec(privBytes)).getEncoded();

        Asserts.assertTrue(Arrays.equals(privBytes, privBytes2));
    }
}
