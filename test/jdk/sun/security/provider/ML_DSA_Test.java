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

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;

public class ML_DSA_Test {
    public static void main(String[] args) throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA");
        Utils.runAndCheckException(() -> g.generateKeyPair(), IllegalStateException.class);

        Asserts.assertTrue(KeyPairGenerator.getInstance("ML-DSA-44").generateKeyPair().getPrivate().toString().contains("ML-DSA-44"));
        Asserts.assertTrue(KeyPairGenerator.getInstance("ML-DSA-65").generateKeyPair().getPrivate().toString().contains("ML-DSA-65"));
        Asserts.assertTrue(KeyPairGenerator.getInstance("ML-DSA-87").generateKeyPair().getPrivate().toString().contains("ML-DSA-87"));

        g.initialize(NamedParameterSpec.ML_DSA_65);
        var kp = g.generateKeyPair();

        Asserts.assertTrue(kp.getPrivate().toString().contains("ML-DSA-65"));

        var s = Signature.getInstance("ML-DSA");
        var msg = "hello".getBytes(StandardCharsets.UTF_8);
        s.initSign(kp.getPrivate());
        s.update(msg);
        var sig = s.sign();
        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sig));

        var s2 = Signature.getInstance("ML-DSA-44");
        Utils.runAndCheckException(() -> s2.initSign(kp.getPrivate()), InvalidKeyException.class);

        var s3 = Signature.getInstance("ML-DSA-65");
        s3.initSign(kp.getPrivate());

        var kf = KeyFactory.getInstance("ML-DSA");
        Asserts.assertTrue(kf.generatePrivate(kf.getKeySpec(kp.getPrivate(), PKCS8EncodedKeySpec.class)).toString().contains("ML-DSA-65"));
        Asserts.assertTrue(kf.generatePublic(kf.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)).toString().contains("ML-DSA-65"));

        var kf2 = KeyFactory.getInstance("ML-DSA-65");
        Asserts.assertTrue(kf2.generatePublic(kf2.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)).toString().contains("ML-DSA-65"));

        var kf3 = KeyFactory.getInstance("ML-DSA-44");
        Utils.runAndCheckException(() -> kf3.generatePublic(kf3.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)), InvalidKeySpecException.class);
        Utils.runAndCheckException(() -> kf3.generatePublic(kf2.getKeySpec(kp.getPublic(), X509EncodedKeySpec.class)), InvalidKeySpecException.class);

        var pbytes = ((NamedX509Key)kp.getPublic()).getRawBytes();
        var sbytes = ((NamedPKCS8Key)kp.getPrivate()).getRawBytes();

        var pk = new PublicKey() {
            @Override
            public String getAlgorithm() {
                return "ML-DSA";
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
                return "ML-DSA";
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

        Asserts.assertTrue(kf2.translateKey(pk).toString().contains("ML-DSA-65"));
        Asserts.assertTrue(kf2.translateKey(sk).toString().contains("ML-DSA-65"));
        Utils.runAndCheckException(() -> kf.translateKey(pk), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf.translateKey(sk), InvalidKeyException.class);

        Asserts.assertEqualsByteArray(kf2.getKeySpec(kf2.translateKey(pk), EncodedKeySpec.class).getEncoded(), pbytes);
        Asserts.assertEqualsByteArray(kf2.getKeySpec(kf2.translateKey(sk), EncodedKeySpec.class).getEncoded(), sbytes);
    }
}
