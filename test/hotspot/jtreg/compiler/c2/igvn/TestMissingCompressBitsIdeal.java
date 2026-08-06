/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.igvn;

import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8389579
 * @key randomness
 * @summary Test missing Ideal optimization opportunity for CompressBits.
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:VerifyIterativeGVN=100
 *      -XX:CompileCommand=compileonly,${test.main.class}::test*
 *      ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingCompressBitsIdeal {
    private static final Random R = Utils.getRandomInstance();

    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            int intValue = R.nextInt();
            int intShift = R.nextInt(Integer.SIZE);
            Asserts.assertEQ(testIntCompressWithOneLeftShift(intValue, intShift),
                             (intValue >>> intShift) & 1);
            Asserts.assertEQ(testIntCompressWithMinusOneLeftShift(intValue, intShift),
                             intValue >>> intShift);
            int intMask = R.nextInt();
            Asserts.assertEQ(testIntCompressExpandWithSameMask(intValue, intMask),
                             intValue & Integer.compress(intMask, intMask));

            long longValue = R.nextLong();
            int longShift = R.nextInt(Long.SIZE);
            Asserts.assertEQ(testLongCompressWithOneLeftShift(longValue, longShift),
                             (longValue >>> longShift) & 1L);
            Asserts.assertEQ(testLongCompressWithMinusOneLeftShift(longValue, longShift),
                             longValue >>> longShift);
            long longMask = R.nextLong();
            Asserts.assertEQ(testLongCompressExpandWithSameMask(longValue, longMask),
                             longValue & Long.compress(longMask, longMask));

        }
    }

    // compress(x, 1 << n) == (x >> n & 1)
    public static int testIntCompressWithOneLeftShift(int val, int Lshift) {
        int i;
        for (i = -10; i < 1; i++) { }
        int mask = i << Lshift;
        return Integer.compress(val, mask);
    }

    // compress(x, -1 << n) == x >>> n
    public static int testIntCompressWithMinusOneLeftShift(int val, int Lshift) {
        int i;
        for (i = -10; i < -1; i++) { }
        int mask = i << Lshift;
        return Integer.compress(val, mask);
    }

    // compress(x, 1L << n) == (x >> n & 1L)
    public static long testLongCompressWithOneLeftShift(long val, int Lshift) {
        long i;
        for (i = -10; i < 1; i++) { }
        long mask = i << Lshift;
        return Long.compress(val, mask);
    }

    // compress(x, -1L << n) == x >>> n
    public static long testLongCompressWithMinusOneLeftShift(long val, int Lshift) {
        long i;
        for (i = -10; i < -1; i++) { }
        long mask = i << Lshift;
        return Long.compress(val, mask);
    }

    // compress(expand(x, m), m) == x & compress(m, m)
    public static int testIntCompressExpandWithSameMask(int val, int mask) {
        int i;
        for (i = -10; i < -1; i++) { }
        int expandMask = i & mask;
        return Integer.compress(Integer.expand(val, expandMask), mask);
    }

    // compress(expand(x, m), m) == x & compress(m, m)
    public static long testLongCompressExpandWithSameMask(long val, long mask) {
        long i;
        for (i = -10; i < -1; i++) { }
        long expandMask = i & mask;
        return Long.compress(Long.expand(val, expandMask), mask);
    }
}
