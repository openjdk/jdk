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

import jdk.test.lib.Asserts;
import sun.security.util.KeyUtil;
import sun.security.util.RawKeySpec;

import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.Security;
import java.util.HexFormat;

/*
 * @test
 * @bug 8358594
 * @library /test/lib /test/jdk/sun/security/pkcs11
 * @modules java.base/sun.security.util
 * @summary confirm the hardcoded NIST categories in KeyUtil class
 */
public class NistCategories {
    public static void main(String[] args) throws Exception {
        Security.addProvider(PKCS11Test.getSunPKCS11(PKCS11Test.getNssConfig()));

        // Test all asymmetric keys we can generate
        for (var p : Security.getProviders()) {
            for (var s : p.getServices()) {
                if (s.getType().equals("KeyPairGenerator")) {
                    test(s);
                }
            }
        }

        // We cannot generate HSS/LMS keys
        testLMS();

        // SecretKey has no NIST category
        Asserts.assertEQ(-1, KeyUtil.getNistCategory(
                new SecretKeySpec(new byte[32], "AES")));
    }

    static void test(Provider.Service s) throws Exception {
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + s.getAlgorithm());
        var alg = s.getAlgorithm();
        var g = KeyPairGenerator.getInstance(alg);
        var kp = g.generateKeyPair();
        var size = switch (g.getAlgorithm()) {
            case "RSA", "RSASSA-PSS", "DSA", "DiffieHellman", "DH",
                 "EC", "EdDSA", "Ed25519", "Ed448",
                 "XDH", "X25519", "X448" -> -1;
            case "ML-KEM-512" -> 1;
            case "ML-DSA-44" -> 2;
            case "ML-KEM", "ML-KEM-768", "ML-DSA", "ML-DSA-65" -> 3;
            case "ML-KEM-1024", "ML-DSA-87" -> 5;
            default -> throw new UnsupportedOperationException(alg);
        };
        Asserts.assertEQ(size, KeyUtil.getNistCategory(kp.getPublic()));
        Asserts.assertEQ(size, KeyUtil.getNistCategory(kp.getPrivate()));
    }

    static void testLMS() throws Exception {
        System.out.println("HSS/LMS");
        var spec = new RawKeySpec(HexFormat.of().parseHex("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b8
                50650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878
                """.replaceAll("\\s", "")));
        var key = KeyFactory.getInstance("HSS/LMS")
                .generatePublic(spec);
        // This is not really a confirmation that HSS/LMS keys should
        // not have a category. It just shows they are not defined in JDK
        // at the moment.
        Asserts.assertEQ(-1, KeyUtil.getNistCategory(key));
    }
}
