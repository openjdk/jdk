/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278114
 * @summary New addnode ideal optimization: converting "x + x" into "x << 1"
 * @library /test/lib
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestAddIdealXPlusX::test*
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestAddIdealXPlusX::test*
 *                   compiler.c2.TestAddIdealXPlusX
 */
package compiler.c2;

import jdk.test.lib.Asserts;

public class TestAddIdealXPlusX {

    public static int test1(int x) {
        return x + x;
    }

    public static long test1(long x) {
        return x + x;
    }

    public static void main(String... args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertTrue(test1(10) == 20);
            // Overflow (Integer.MAX_VALUE == 2147483647)
            Asserts.assertTrue(test1(1073741824) == Integer.MIN_VALUE);
            // Underflow (Integer.MIN_VALUE == -2147483648)
            Asserts.assertTrue(test1(-1073741825) == 2147483646);

            Asserts.assertTrue(test1(100_000_000_000L) == 200_000_000_000L);
            // Overflow (Long.MAX_VALUE == 9223372036854775807)
            Asserts.assertTrue(test1(4611686018427387904L) == Long.MIN_VALUE);
            // Underflow (Long.MIN_VALUE == -9223372036854775808)
            Asserts.assertTrue(test1(-4611686018427387905L) == 9223372036854775806L);
        }
    }
}
