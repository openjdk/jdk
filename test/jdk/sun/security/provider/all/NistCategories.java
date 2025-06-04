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

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.util.HexFormat;

/*
 * @test
 * @bug 8358594
 * @library /test/lib
 * @modules java.base/sun.security.util
 * @summary confirm the hardcoded NIST categories in KeyUtil class
 */
public class NistCategories {
    public static void main(String[] args) throws Exception {
        check("ML-KEM-512", 1);
        check("ML-KEM-768", 3);
        check("ML-KEM-1024", 5);
        check("ML-DSA-44", 2);
        check("ML-DSA-65", 3);
        check("ML-DSA-87", 5);
        check("RSA", -1);
        check("Ed25519", -1);

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

    static void check(String alg, int expected) throws Exception {
        System.out.println(alg);
        var kp = KeyPairGenerator.getInstance(alg).generateKeyPair();
        Asserts.assertEQ(expected, KeyUtil.getNistCategory(kp.getPrivate()));
        Asserts.assertEQ(expected, KeyUtil.getNistCategory(kp.getPublic()));
    }
}
