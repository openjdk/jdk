/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351351
 * @library /test/lib
 * @modules java.base/sun.security.util
 *          java.base/sun.security.provider
 *          java.base/sun.security.x509
 */

import jdk.test.lib.Asserts;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.SignatureException;

import sun.security.provider.SHA3;
import sun.security.util.InternalSignatureParameterSpec;
import sun.security.x509.NamedX509Key;

import static jdk.test.lib.Utils.toByteArray;

public class Parameters {
    public static void main(String[] args) throws Exception {
        paramsTest();
        signTest();
        muTest();
    }

    // Test on equivalence of ML-DSA with different "internal" levels
    static void muTest() throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA");
        var kp = g.generateKeyPair();

        if (!(kp.getPublic() instanceof NamedX509Key nk)) {
            throw new RuntimeException("Unknown public key: " + kp.getPublic().getClass());
        }

        var s = Signature.getInstance("ML-DSA");
        var context = toByteArray("010203");
        var msg = toByteArray("111213");

        s.setParameter(new InternalSignatureParameterSpec(null, context, "deterministic"));
        s.initSign(kp.getPrivate());
        s.update(msg);
        var sigNormal = s.sign();

        s.setParameter(new InternalSignatureParameterSpec(null, null, "deterministic", "internal"));
        s.initSign(kp.getPrivate());
        s.update(new byte[] { 0, 3 }); // 0 for normal sign, 3 for context size
        s.update(context);
        s.update(msg);
        var sigInternal = s.sign();

        s.setParameter(new InternalSignatureParameterSpec(null, null, "deterministic", "internal", "externalMu"));
        s.initSign(kp.getPrivate());
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

        Asserts.assertEqualsByteArray(sigNormal, sigInternal);
        Asserts.assertEqualsByteArray(sigNormal, sigExternalMu);
    }

    // Test on InternalSignatureParameterSpec usage in ML-DSA
    static void signTest() throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA");
        var kp = g.generateKeyPair();
        var msg = "hello".getBytes(StandardCharsets.UTF_8);

        var s = Signature.getInstance("ML-DSA");

        // deterministic
        s.setParameter(new InternalSignatureParameterSpec(null, null, "deterministic"));
        s.initSign(kp.getPrivate());
        s.update(msg);
        var sig = s.sign();
        s.update(msg);
        Asserts.assertEqualsByteArray(sig, s.sign());

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sig));

        // non-deterministic, aka "hedged" as in FIPS 204
        s.setParameter(new InternalSignatureParameterSpec(null, null));
        s.initSign(kp.getPrivate());
        s.update(msg);
        var sigRandom = s.sign();
        Asserts.assertNotEqualsByteArray(sig, sigRandom); // not same as deterministic
        s.update(msg);
        Asserts.assertNotEqualsByteArray(sigRandom, s.sign()); // not same every time

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sigRandom));

        // null context is the same of empty context
        s.setParameter(new InternalSignatureParameterSpec(null, new byte[0], "deterministic"));
        s.initSign(kp.getPrivate());
        s.update(msg);
        Asserts.assertEqualsByteArray(sig, s.sign());

        // non-null context is different
        s.setParameter(new InternalSignatureParameterSpec(null, new byte[1], "deterministic"));
        s.update(msg);
        var sigWithContext = s.sign();
        Asserts.assertNotEqualsByteArray(sig, sigWithContext);

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sigWithContext));

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertFalse(s.verify(sig));

        // pre-hash and context
        s.setParameter(new InternalSignatureParameterSpec("SHA-256", new byte[1]));
        s.initSign(kp.getPrivate());
        s.update(msg);
        var sigPreHash = s.sign();

        s.initVerify(kp.getPublic());
        s.update(msg);
        Asserts.assertTrue(s.verify(sigPreHash));

        // externalMu requires correct mu length
        s.setParameter(new InternalSignatureParameterSpec(null, null, "internal", "externalMu"));
        s.initSign(kp.getPrivate());
        s.update(new byte[64]);
        var sigXmu = s.sign();

        s.initSign(kp.getPrivate());
        s.update(new byte[63]);
        Asserts.assertThrows(SignatureException.class, s::sign);

        s.initVerify(kp.getPublic());
        s.update(new byte[64]);
        Asserts.assertTrue(s.verify(sigXmu));

        s.initVerify(kp.getPublic());
        s.update(new byte[63]);
        Asserts.assertThrows(SignatureException.class, () -> s.verify(sigXmu));
    }

    // Tests on general InternalSignatureParameterSpec API compliance
    static void paramsTest() throws Exception {
        new InternalSignatureParameterSpec(null, null);
        new InternalSignatureParameterSpec("SHA-256", new byte[10], "hello", "good-bye");

        // Just maximum allowed length
        new InternalSignatureParameterSpec(null, new byte[255]);
        // context too long
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> new InternalSignatureParameterSpec(null, new byte[256]));
        // features cannot be null
        Asserts.assertThrows(NullPointerException.class,
                () -> new InternalSignatureParameterSpec(null, null, (String)null));
        Asserts.assertThrows(NullPointerException.class,
                () -> new InternalSignatureParameterSpec(null, null, (String[])null));

        var s = Signature.getInstance("ML-DSA");

        // Unknown hash algorithm
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new InternalSignatureParameterSpec("UnknownHash", null)));
        // Unknown feature
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new InternalSignatureParameterSpec(null, null, "unknown")));
        // Wrong case
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new InternalSignatureParameterSpec(null, null, "Deterministic")));
        // externalMu without internal
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new InternalSignatureParameterSpec(null, null, "externalMu")));
    }
}
