/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8145255
 * @summary Tests for HKDF Expand and Extract Key Derivation Functions
 */

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.KDFParameterSpec;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

public class TestHKDFInitialization {
    public static void main(String[] args)
        throws NoSuchAlgorithmException, InvalidParameterSpecException,
               InvalidAlgorithmParameterException {

        byte[] ikm = new BigInteger("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                                    16).toByteArray();
        byte[] salt = new BigInteger("000102030405060708090a0b0c", 16).toByteArray();
        byte[] info = new BigInteger("f0f1f2f3f4f5f6f7f8f9", 16).toByteArray();

        KDFParameterSpec kdfParameterSpec = HKDFParameterSpec.extractExpand(
            HKDFParameterSpec.extract().addIKM(ikm).addSalt(salt).extractOnly(), info, 42);

        // OR THIS WAY NOW
        /*KDFParameterSpec kdfParameterSpec2 =
            HKDFParameterSpec.extract()
                             .addIKM(ikm)
                             .addSalt(salt).andExpand(info, 42);*/

        KDF kdfHkdf = KDF.getInstance("HKDF/HmacSHA256", (AlgorithmParameterSpec) null);

        kdfHkdf.deriveKey("AES", kdfParameterSpec);

    }
}