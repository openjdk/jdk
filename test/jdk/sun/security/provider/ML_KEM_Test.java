/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.pkcs
 * @library /test/lib
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import javax.crypto.KEM;
import java.security.*;
import java.security.spec.*;

public class ML_KEM_Test {
    public static void main(String[] args) throws Exception {
        var g = KeyPairGenerator.getInstance("ML-KEM");

        Asserts.assertTrue(g.generateKeyPair().getPrivate().toString().contains("ML-KEM-768"));
        Asserts.assertTrue(KeyPairGenerator.getInstance("ML-KEM-512").generateKeyPair().getPrivate().toString().contains("ML-KEM-512"));
        Asserts.assertTrue(KeyPairGenerator.getInstance("ML-KEM-768").generateKeyPair().getPrivate().toString().contains("ML-KEM-768"));
        Asserts.assertTrue(KeyPairGenerator.getInstance("ML-KEM-1024").generateKeyPair().getPrivate().toString().contains("ML-KEM-1024"));

        Utils.runAndCheckException(() -> g.initialize(NamedParameterSpec.ED448), InvalidAlgorithmParameterException.class);
        Utils.runAndCheckException(() -> g.initialize(new NamedParameterSpec("ML-KEM-2048")), InvalidAlgorithmParameterException.class);

        g.initialize(NamedParameterSpec.ML_KEM_768);
        var kp = g.generateKeyPair();

        Asserts.assertTrue(kp.getPrivate().toString().contains("ML-KEM-768"));

        testKEM("ML-KEM", kp.getPublic(), kp.getPrivate());

        var k2 = KEM.getInstance("ML-KEM-512");
        Utils.runAndCheckException(() -> k2.newDecapsulator(kp.getPrivate()), InvalidKeyException.class);

        var k3 = KEM.getInstance("ML-KEM-768");
        k3.newDecapsulator(kp.getPrivate());

        var kf = KeyFactory.getInstance("ML-KEM");
        Asserts.assertTrue(kf.generatePrivate(kf.getKeySpec(kp.getPrivate(), PKCS8EncodedKeySpec.class)).toString().contains("ML-KEM-768"));
        Asserts.assertTrue(kf.generatePublic(kf.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)).toString().contains("ML-KEM-768"));

        var kf2 = KeyFactory.getInstance("ML-KEM-768");
        Asserts.assertTrue(kf2.generatePublic(kf2.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)).toString().contains("ML-KEM-768"));

        var kf3 = KeyFactory.getInstance("ML-KEM-512");
        Utils.runAndCheckException(() -> kf3.generatePublic(kf3.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)), InvalidKeySpecException.class);
        Utils.runAndCheckException(() -> kf3.generatePublic(kf2.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)), InvalidKeySpecException.class);

        var pbytes = ((NamedX509Key)kp.getPublic()).getRawBytes();
        var sbytes = ((NamedPKCS8Key)kp.getPrivate()).getRawBytes();

        var pk = new PublicKey() {
            @Override
            public String getAlgorithm() {
                return "ML-KEM";
            }

            @Override
            public String getFormat() {
                return "RAW";
            }

            @Override
            public byte[] getEncoded() {
                return pbytes.clone();
            }
        };

        var sk = new PrivateKey() {
            @Override
            public String getAlgorithm() {
                return "ML-KEM";
            }

            @Override
            public String getFormat() {
                return "RAW";
            }

            @Override
            public byte[] getEncoded() {
                return sbytes.clone();
            }
        };

        Asserts.assertTrue(kf2.translateKey(pk).toString().contains("ML-KEM-768"));
        Asserts.assertTrue(kf2.translateKey(sk).toString().contains("ML-KEM-768"));
        Utils.runAndCheckException(() -> kf.translateKey(pk), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf.translateKey(sk), InvalidKeyException.class);

        Asserts.assertEqualsByteArray(kf2.getKeySpec(kf2.translateKey(pk), EncodedKeySpec.class).getEncoded(), pbytes);
        Asserts.assertEqualsByteArray(kf2.getKeySpec(kf2.translateKey(sk), EncodedKeySpec.class).getEncoded(), sbytes);

        Utils.runAndCheckException(() -> testKEM("ML-KEM", pk, sk), InvalidKeyException.class);
        testKEM("ML-KEM-768", pk, sk);
    }

    static void testKEM(String alg, PublicKey pk, PrivateKey sk) throws Exception {
        var k = KEM.getInstance(alg);
        var e = k.newEncapsulator(pk);
        var d = k.newDecapsulator(sk);
        var enc = e.encapsulate();
        var key = d.decapsulate(enc.encapsulation());
        Asserts.assertEqualsByteArray(enc.key().getEncoded(), key.getEncoded());
    }
}
