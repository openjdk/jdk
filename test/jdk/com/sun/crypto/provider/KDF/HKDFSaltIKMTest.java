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
 * @summary addIKM and addSalt consistency checks
 * @library /test/lib
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.security.SeededSecureRandom;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class HKDFSaltIKMTest {
    static String[] NAMES = {"HKDF-SHA256", "HKDF-SHA384", "HKDF-SHA512"};
    public static void main(String[] args) throws Exception {
        var r = SeededSecureRandom.one();
        var atlast = 0;
        KDF kdf = null;
        var alg = "";
        for (var i = 0; i < 1_000_000; i++) {
            if (kdf == null || r.nextBoolean()) {
                alg = NAMES[r.nextInt(3)];
                kdf = KDF.getInstance(alg); // randomly recreate KDF object
            }
            var b = HKDFParameterSpec.ofExtract();
            var salts = new ByteArrayOutputStream(); // accumulate salt fragments
            var ikms = new ByteArrayOutputStream(); // accumulate ikm fragments
            while (r.nextBoolean()) {
                if (r.nextBoolean()) {
                    var ikm = r.nBytes(r.nextInt(10));
                    if (r.nextBoolean() && ikm.length > 0) {
                        b.addIKM(new SecretKeySpec(ikm, "X"));
                    } else {
                        b.addIKM(ikm);
                    }
                    ikms.writeBytes(ikm);
                } else {
                    var salt = r.nBytes(r.nextInt(10));
                    if (r.nextBoolean() && salt.length > 0) {
                        b.addSalt(new SecretKeySpec(salt, "X"));
                    } else {
                        b.addSalt(salt);
                    }
                    salts.writeBytes(salt);
                }
            }
            var info = r.nextBoolean() ? null : r.nBytes(r.nextInt(100));
            var l = r.nextInt(200) + 1;
            var kdf2 = r.nextBoolean() ? kdf : KDF.getInstance(alg);
            var k1 = kdf2.deriveData(HKDFParameterSpec.ofExtract().addIKM(ikms.toByteArray())
                                                      .addSalt(salts.toByteArray()).thenExpand(info, l));
            atlast = Arrays.hashCode(k1) + 17 * atlast;
            if (r.nextBoolean()) {
                var k2 = kdf.deriveData(b.thenExpand(info, l));
                Asserts.assertEqualsByteArray(k1, k2);
            } else {
                var prk = kdf.deriveKey("PRK", b.extractOnly());
                var k2 = kdf.deriveData(HKDFParameterSpec.expandOnly(prk, info, l));
                Asserts.assertEqualsByteArray(k1, k2);
            }
        }
        System.out.println(atlast);
    }
}
