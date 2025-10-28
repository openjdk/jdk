/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331008
 * @summary basic HKDF operations
 * @library /test/lib
 */

import java.util.HexFormat;
import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import jdk.test.lib.Asserts;

public class HKDFBasicFunctionsTest {
    public static void main(String[] args) throws Exception {
        var ikm = HexFormat.of().parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var salt = HexFormat.of().parseHex("000102030405060708090a0b0c");
        var info = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9");
        var len = 42;

        var kdf = KDF.getInstance("HKDF-SHA256");
        var expectedPrk = HexFormat.of().parseHex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5");
        var expectedOkm = HexFormat.of().parseHex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");

        var extractOnly = HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt).extractOnly();
        var prk = kdf.deriveKey("PRK", extractOnly);
        var expandOnly = HKDFParameterSpec.expandOnly(prk, info, len);
        var okm1 = kdf.deriveKey("OKM", expandOnly);
        var extractAndExpand = HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt).thenExpand(info, len);
        var okm2 = kdf.deriveKey("OKM", extractAndExpand);

        Asserts.assertEqualsByteArray(expectedPrk, prk.getEncoded(),
                                      "the PRK must match the expected value");

        Asserts.assertEqualsByteArray(expectedOkm, okm1.getEncoded(),
                                      "the OKM must match the expected value "
                                      + "(expand)");

        Asserts.assertEqualsByteArray(expectedOkm, okm2.getEncoded(),
                                      "the OKM must match the expected value "
                                      + "(extract expand)");

        // test empty extract
        test(HKDFParameterSpec.ofExtract().extractOnly());
        // test expand with empty info
        test(HKDFParameterSpec.ofExtract().thenExpand(new byte[0], 32));
        // test expand with null info
        test(HKDFParameterSpec.ofExtract().thenExpand(null, 32));
        // test extract with zero-length salt
        test(HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(new byte[0]).extractOnly());
    }

    static void test(HKDFParameterSpec p) throws Exception {
        var kdf = KDF.getInstance("HKDF-SHA256");
        System.out.println(HexFormat.of().formatHex(kdf.deriveData(p)));
    }
}
