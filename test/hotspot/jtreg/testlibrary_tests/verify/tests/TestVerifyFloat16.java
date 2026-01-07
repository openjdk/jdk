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
 * @key randomness
 * @summary Test functionality of Verify implementations for Float16.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package verify.tests;

import java.lang.foreign.*;
import java.util.Random;
import jdk.test.lib.Utils;

import jdk.incubator.vector.Float16;

import compiler.lib.verify.*;

public class TestVerifyFloat16 {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        testArrayFloat16();
        testRawFloat16();
        testFloat16Random();
    }

    public static void testArrayFloat16() {
        Float16[] a = new Float16[1000];
        Float16[] b = new Float16[1001];
        Float16[] c = new Float16[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        // Size mismatch
        checkNE(a, b);

        c[RANDOM.nextInt(c.length)] = Float16.valueOf(1f);

        // Value mismatch
        checkNE(a, c);
    }

    public static void testRawFloat16() {
        Float16 nan1 = Float16.shortBitsToFloat16((short)0xFFFF);
        Float16 nan2 = Float16.shortBitsToFloat16((short)0x7FFF);
        if (!Float16.isNaN(nan1)) { throw new RuntimeException("must be NaN"); }
        if (!Float16.isNaN(nan2)) { throw new RuntimeException("must be NaN"); }
        if (Float16.float16ToRawShortBits(nan1) != (short)0xFFFF) { throw new RuntimeException("wrong bits"); }
        if (Float16.float16ToRawShortBits(nan2) != (short)0x7FFF) { throw new RuntimeException("wrong bits"); }

        Float16[] arr1 = new Float16[]{nan1};
        Float16[] arr2 = new Float16[]{nan2};

        Verify.checkEQ(nan1, Float16.NaN);
        Verify.checkEQ(nan1, nan1);
        Verify.checkEQWithRawBits(nan1, nan1);
        Verify.checkEQ(nan1, nan2);

        Verify.checkEQ(arr1, arr1);
        Verify.checkEQWithRawBits(arr1, arr1);
        Verify.checkEQ(arr1, arr2);

        checkNEWithRawBits(nan1, nan2);

        checkNEWithRawBits(arr1, arr2);
    }

    public static void testFloat16Random() {
        // Testing all 2^16 * 2^16 = 2^32 would take a bit long, so we randomly sample instead.
        for (int i = 0; i < 10_000; i++) {
            short bitsA = (short)RANDOM.nextInt();
            short bitsB = (short)RANDOM.nextInt();
            Float16 a = Float16.shortBitsToFloat16(bitsA);
            Float16 b = Float16.shortBitsToFloat16(bitsB);
            if (bitsA == bitsB) {
                Verify.checkEQWithRawBits(a, b);
            } else {
                checkNEWithRawBits(a, b);
            }
            if (a.equals(b)) {
                Verify.checkEQ(a, b);
            } else {
                checkNE(a, b);
            }
        }
    }

    public static void checkNE(Object a, Object b) {
         try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown: " + a + " vs " + b);
        } catch (VerifyException e) {}
    }

    public static void checkNEWithRawBits(Object a, Object b) {
         try {
            Verify.checkEQWithRawBits(a, b);
            throw new RuntimeException("Should have thrown: " + a + " vs " + b);
        } catch (VerifyException e) {}
    }
}
