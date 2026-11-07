/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351351 8381859
 * @library /test/lib
 * @modules java.base/sun.security.util
 *          java.base/sun.security.provider
 *          java.base/sun.security.x509
 */

import jdk.test.lib.Asserts;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.MLDSAParameterSpec;

import jdk.test.lib.security.FixedSecureRandom;
import sun.security.provider.SHA3;
import sun.security.x509.NamedX509Key;

import static jdk.test.lib.Utils.toByteArray;

public class Parameters {
    public static void main(String[] args) throws Exception {
        paramsTest();
        signTest();
        muTest();
    }

    // Test on equivalence of ML-DSA between normal call and externalMu call
    static void muTest() throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA");
        var kp = g.generateKeyPair();

        if (!(kp.getPublic() instanceof NamedX509Key nk)) {
            throw new RuntimeException("Unknown public key: " + kp.getPublic().getClass());
        }

        var s = Signature.getInstance("ML-DSA");
        var context = toByteArray("010203");
        var msg = toByteArray("111213");

        s.setParameter(MLDSAParameterSpec.ofContext(context));
        s.initSign(kp.getPrivate(), new FixedSecureRandom(new byte[32]));
        s.update(msg);
        var sigNormal = s.sign();

        s.setParameter(MLDSAParameterSpec.ofExternalMu());
        s.initSign(kp.getPrivate(), new FixedSecureRandom(new byte[32]));
        var shake = new SHA3.SHAKE256(64);
        shake.update(nk.getRawBytes());
        var tr = shake.digest();
        shake.update(tr);
        shake.update(new byte[] { 0, 3 }); // 0 for normal sign, 3 for context size
        shake.update(context);
        shake.update(msg);
        var mu = shake.digest();
        s.update(mu);
        var sigExternalMu = s.sign();

        Asserts.assertEqualsByteArray(sigNormal, sigExternalMu);
    }

    // Test on MLDSAParameterSpec usage in ML-DSA
    static void signTest() throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA");
        var kp = g.generateKeyPair();
        var msg = "hello".getBytes(StandardCharsets.UTF_8);
        var ctxt = "context".getBytes(StandardCharsets.UTF_8);

        var s = Signature.getInstance("ML-DSA");

        // deterministic
        s.initSign(kp.getPrivate(), new FixedSecureRandom(new byte[64]));
        s.update(msg);
        var sig = s.sign();
        s.update(msg);
        Asserts.assertEqualsByteArray(sig, s.sign());

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sig));

        // non-deterministic, aka "hedged" as in FIPS 204
        s.initSign(kp.getPrivate(), null);
        s.update(msg);
        var sigRandom = s.sign();
        Asserts.assertNotEqualsByteArray(sig, sigRandom); // not same as deterministic
        s.update(msg);
        Asserts.assertNotEqualsByteArray(sigRandom, s.sign()); // not same every time

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sigRandom));

        // no context is empty context
        s.setParameter(MLDSAParameterSpec.ofContext(new byte[0]));
        s.initSign(kp.getPrivate(), new FixedSecureRandom(new byte[32]));
        s.update(msg);
        Asserts.assertEqualsByteArray(sig, s.sign());

        // non-null context is different
        s.setParameter(MLDSAParameterSpec.ofContext(new byte[1]));
        s.initSign(kp.getPrivate(), new FixedSecureRandom(new byte[32]));
        s.update(msg);
        var sigWithContext = s.sign();
        Asserts.assertNotEqualsByteArray(sig, sigWithContext);

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sigWithContext));

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertFalse(s.verify(sig));

        // externalMu requires correct mu length
        s.setParameter(MLDSAParameterSpec.ofExternalMu());
        s.initSign(kp.getPrivate(), null);
        s.update(new byte[64]);
        var sigXmu = s.sign();

        s.initSign(kp.getPrivate(), null);
        s.update(new byte[63]);
        Asserts.assertThrows(SignatureException.class, s::sign);

        s.initVerify(kp.getPublic());
        s.update(new byte[64]);
        Asserts.assertTrue(s.verify(sigXmu));

        s.initVerify(kp.getPublic());
        s.update(new byte[63]);
        Asserts.assertThrows(SignatureException.class, () -> s.verify(sigXmu));
    }

    // Tests on general MLDSAParameterSpec API compliance
    static void paramsTest() throws Exception {
        MLDSAParameterSpec.ofContext(new byte[3]);
        MLDSAParameterSpec.ofContext(new byte[255]);
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> MLDSAParameterSpec.ofContext(new byte[256]));
        Asserts.assertThrows(NullPointerException.class,
                () -> MLDSAParameterSpec.ofContext(null));

        Asserts.assertEquals(MLDSAParameterSpec.Context.class,
                MLDSAParameterSpec.ofContext(new byte[0]).getClass());
        Asserts.assertEquals(MLDSAParameterSpec.ExternalMu.class,
                MLDSAParameterSpec.ofExternalMu().getClass());
    }
}
