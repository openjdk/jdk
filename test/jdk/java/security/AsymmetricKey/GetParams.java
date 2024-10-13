/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.spec.DHParameterSpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.DSAParams;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.NamedParameterSpec;

/**
 * @test
 * @bug 8318096
 * @summary Introduce AsymmetricKey interface with a getParams method
 * @library /test/lib
 */

public class GetParams {
    public static void main(String[] args) throws Exception {
        test("DSA", DSAParams.class);
        test("RSA", AlgorithmParameterSpec.class);
        test("RSASSA-PSS", AlgorithmParameterSpec.class);
        test("EC", ECParameterSpec.class);
        test("DH", DHParameterSpec.class);
        test("EdDSA", NamedParameterSpec.class);
        test("XDH", NamedParameterSpec.class);
    }

    static void test(String alg, Class<? extends AlgorithmParameterSpec> clazz)
            throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance(alg);
        KeyPair kp = g.generateKeyPair();
        AlgorithmParameterSpec spec1 = kp.getPrivate().getParams();
        Asserts.assertTrue(spec1 == null || clazz.isAssignableFrom(spec1.getClass()));
        AlgorithmParameterSpec spec2 = kp.getPublic().getParams();
        Asserts.assertTrue(spec2 == null || clazz.isAssignableFrom(spec2.getClass()));
    }
}
