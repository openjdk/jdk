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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.SignatureParameterSpec;

public class PreHashed {
    public static void main(String[] args) throws Exception {
        function();
        paramsTest();
    }

    static void function() throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA-44");
        var kp = g.generateKeyPair();

        var s = Signature.getInstance("HashML-DSA-44-SHA512");
        s.initSign(kp.getPrivate());
        var sig = s.sign();

        var v = Signature.getInstance("ML-DSA");
        v.setParameter(new SignatureParameterSpec("SHA-512", null));
        v.initVerify(kp.getPublic());
        Asserts.assertTrue(v.verify(sig));
    }

    static void paramsTest() throws Exception {
        var g = KeyPairGenerator.getInstance("ML-DSA-44");
        var kp = g.generateKeyPair();

        var s = Signature.getInstance("HashML-DSA-65-SHA512");
        s.setParameter(new SignatureParameterSpec("SHA-512", new byte[10]));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new SignatureParameterSpec(null, null)));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> s.setParameter(new SignatureParameterSpec("SHA-256", null)));

        Asserts.assertThrows(InvalidKeyException.class,
                () -> s.initSign(kp.getPrivate()));
    }
}
