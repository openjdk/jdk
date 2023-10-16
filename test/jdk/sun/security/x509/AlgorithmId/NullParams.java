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

/*
 * @test
 * @bug 8301793
 * @summary AlgorithmId should not encode a missing parameters field as NULL unless hardcoded
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 */

import sun.security.util.DerInputStream;
import sun.security.util.DerValue;
import sun.security.x509.AlgorithmId;

public class NullParams {

    static boolean failed = false;

    public static void main(String[] args) throws Exception {

        // Full new list: must have NULL
        test("MD2", true);
        test("MD5", true);
        test("SHA-1", true);
        test("SHA-224", true);
        test("SHA-256", true);
        test("SHA-384", true);
        test("SHA-512", true);
        test("SHA-512/224", true);
        test("SHA-512/256", true);
        test("SHA3-224", true);
        test("SHA3-256", true);
        test("SHA3-384", true);
        test("SHA3-512", true);
        test("RSA", true);
        test("MD2withRSA", true);
        test("MD5withRSA", true);
        test("SHA1withRSA", true);
        test("SHA224withRSA", true);
        test("SHA256withRSA", true);
        test("SHA384withRSA", true);
        test("SHA512/224withRSA", true);
        test("SHA512/256withRSA", true);
        test("SHA512withRSA", true);
        test("SHA3-224withRSA", true);
        test("SHA3-256withRSA", true);
        test("SHA3-384withRSA", true);
        test("SHA3-512withRSA", true);

        // Full old list: must be absent
        test("SHA1withECDSA", false);
        test("SHA224withECDSA", false);
        test("SHA256withECDSA", false);
        test("SHA384withECDSA", false);
        test("SHA512withECDSA", false);
        test("Ed25519", false);
        test("Ed448", false);
        test("X25519", false);
        test("X448", false);
        test("RSASSA-PSS", false);

        // Others
        test("DSA", false);
        test("SHA1withDSA", false);
        test("HmacSHA1", false);

        if (failed) {
            throw new RuntimeException("At least one failed");
        }
    }

    static void test(String name, boolean hasNull) throws Exception {
        System.out.printf("%20s  ", name);
        AlgorithmId aid = AlgorithmId.get(name);
        byte[] encoding = aid.encode();
        DerValue v = new DerValue(encoding);
        DerInputStream data = v.data();
        data.getOID();
        if (hasNull) {
            if (data.available() == 0) {
                System.out.println("NULL missing");
                failed = true;
                return;
            }
        } else {
            if (data.available() != 0) {
                System.out.println("Has unexpected NULL");
                failed = true;
                return;
            }
        }
        System.out.println("OK");
    }
}
