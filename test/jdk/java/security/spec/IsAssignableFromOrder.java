/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.util.CurveDB;

import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.RC2ParameterSpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.DSAParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PSSParameterSpec;

/**
 * @test
 * @bug 8279800
 * @modules java.base/sun.security.util
 * @summary isAssignableFrom checks in AlgorithmParametersSpi.engineGetParameterSpec appear to be backwards
 */

public class IsAssignableFromOrder {

    public static void main(String[] args) throws Exception {
        test("AES", new IvParameterSpec(new byte[16]));
        test("ChaCha20-Poly1305", new IvParameterSpec(new byte[12]));
        test("DiffieHellman", new DHParameterSpec(BigInteger.ONE, BigInteger.TWO));
        test("GCM", new GCMParameterSpec(96, new byte[16]));
        test("OAEP", OAEPParameterSpec.DEFAULT);
        test("PBEWithSHA1AndDESede", new PBEParameterSpec(
                "saltsalt".getBytes(StandardCharsets.UTF_8), 10000));
        test("PBEWithHmacSHA256AndAES_256", new PBEParameterSpec(
                "saltsalt".getBytes(StandardCharsets.UTF_8), 10000,
                new IvParameterSpec(new byte[16])));
        test("RC2", new RC2ParameterSpec(256, new byte[32]));
        test("DSA", new DSAParameterSpec(
                BigInteger.ONE, BigInteger.TWO, BigInteger.TEN));
        test("RSASSA-PSS", PSSParameterSpec.DEFAULT);
        test("EC", new ECGenParameterSpec("secp256r1"));
        test("EC", CurveDB.lookup("secp256r1"),
                ECParameterSpec.class, AlgorithmParameterSpec.class);
    }

    static void test(String algorithm, AlgorithmParameterSpec spec,
            Class<? extends AlgorithmParameterSpec>... classes) throws Exception {
        var ap1 = AlgorithmParameters.getInstance(algorithm);
        ap1.init(spec);
        var ap2 = AlgorithmParameters.getInstance(algorithm);
        ap2.init(ap1.getEncoded());
        if (classes.length == 0) {
            classes = new Class[]{spec.getClass(), AlgorithmParameterSpec.class};
        }
        for (var c : classes) {
            ap1.getParameterSpec(c);
            ap2.getParameterSpec(c);
        }
    }
}
