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
 * @library /test/lib
 * @run testng KDFDelayedProviderSyncTest
 * @summary multi-threading test for KDF
 */

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.HexFormat;

public class KDFDelayedProviderSyncTest {
    KDF kdfUnderTest;
    byte[] ikm = new BigInteger("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                                16).toByteArray();
    byte[] salt = new BigInteger("000102030405060708090a0b0c",
                                 16).toByteArray();
    byte[] info = new BigInteger("f0f1f2f3f4f5f6f7f8f9", 16).toByteArray();
    AlgorithmParameterSpec derivationSpec =
        HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt).thenExpand(
            info, 42);
    String expectedResult =
        "666b33562ebc5e2f041774192e0534efca06f82a5fca17ec8c6ae1b9f5466adba1d77d06480567ddd2d1";

    @BeforeClass
    public void setUp() throws NoSuchAlgorithmException {
        kdfUnderTest = KDF.getInstance("HKDF-SHA256");
    }

    @Test(threadPoolSize = 50, invocationCount = 100)
    public void testDerive()
        throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        SecretKey result = kdfUnderTest.deriveKey("Generic", derivationSpec);
        assert (HexFormat.of().formatHex(result.getEncoded()).equals(
            expectedResult));

        byte[] resultData = kdfUnderTest.deriveData(derivationSpec);
        assert (HexFormat.of().formatHex(resultData).equals(expectedResult));
    }
}
