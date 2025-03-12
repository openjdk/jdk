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
 * @bug 8351351
 * @library /test/lib
 */

import jdk.test.lib.Asserts;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.SignatureParameterSpec;

public class Parameters {
    public static void main(String[] args) throws Exception {
        paramsTest();
        signTest();
    }

    static void signTest() throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA");
        var kp = g.generateKeyPair();
        var msg = "hello".getBytes(StandardCharsets.UTF_8);

        var s = Signature.getInstance("ML-DSA");

        // deterministic
        s.setParameter(new SignatureParameterSpec(null, null, "deterministic"));
        s.initSign(kp.getPrivate());
        s.update(msg);
        var sig1 = s.sign();
        s.update(msg);
        var sig2 = s.sign();
        // non-deterministic, aka "hedged" as in FIPS 204
        s.setParameter(new SignatureParameterSpec(null, null));
        s.update(msg);
        var sig3 = s.sign();
        s.update(msg);
        var sig4 = s.sign();

        Asserts.assertEqualsByteArray(sig1, sig2);
        Asserts.assertNotEqualsByteArray(sig3, sig4);

        // null context is the same of empty context
        s.setParameter(new SignatureParameterSpec(null, new byte[0], "deterministic"));
        s.update(msg);
        var sig5 = s.sign();
        Asserts.assertEqualsByteArray(sig1, sig5);

        // Unknown hash algorithm
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new SignatureParameterSpec("NOHASH", null)));
        // Unknown feature
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new SignatureParameterSpec(null, null, "unknown")));
        // externalMu without internal
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new SignatureParameterSpec(null, null, "externalMu")));
    }

    static void paramsTest() throws Exception {
        new SignatureParameterSpec(null, null);
        new SignatureParameterSpec("SHA-256", new byte[10], "hello", "good-bye");

        // Just maximum allowed length
        new SignatureParameterSpec(null, new byte[255]);
        // context too long
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> new SignatureParameterSpec(null, new byte[256]));
        // features cannot be null
        Asserts.assertThrows(NullPointerException.class,
                () -> new SignatureParameterSpec(null, null, (String)null));
        Asserts.assertThrows(NullPointerException.class,
                () -> new SignatureParameterSpec(null, null, (String[])null));
    }
}
