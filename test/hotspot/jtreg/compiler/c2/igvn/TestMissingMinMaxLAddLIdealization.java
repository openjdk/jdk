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

/*
 * @test
 * @bug 8395687
 * @summary Missing notification for fold_subI_no_underflow_pattern
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingMinMaxLAddLIdealization {
    public static void main(String[] args) {
        // Ensure Math.min and Math.max are loaded/resolved properly.
        Math.min(1L, 2L);
        Math.max(1L, 2L);
        testMin2Hop(0);
        testMax2Hop(0);
        testMin3Hop(0, 0);
        testMax3Hop(0, 0);
        testMin4Hop(0, 0);
    }

    static long testMin2Hop(int x) {
        int delayedZero = 100;
        for (int i = 0; i < 100; i++) { delayedZero--; }

        long delayedLongMin = delayedZero == 0 ? Long.MIN_VALUE : 0L;

        // fold_subI_no_underflow_pattern optimizes a pattern of two
        // non-overflowing additions. We have a first non-overflowing
        // addition:
        // x is in int-range, and adding 2 will not overflow in long,
        // then we truncate back to int range.
        long inner = Math.min((long)x + 2L, Integer.MAX_VALUE);
        // Now, we insert a delay mechanism that will later fold to
        // "inner":
        long delayedInner = Math.max(inner, delayedLongMin);
        // And below is the second non-overflowing addition. Once the
        // delay mechanism collapses, we need to notify down, so
        // fold_subI_no_underflow_pattern can optimize the newly
        // connected non-overflowing additions into a singe non-overflow
        // addition.
        // We need notification for:
        //   n-> AddL -> MinL/MaxL
        long outer = Math.min(delayedInner + 1L, Integer.MAX_VALUE);
        return outer;
    }

    static long testMax2Hop(int x) {
        int delayedZero = 100;
        for (int i = 0; i < 100; i++) { delayedZero--; }

        long delayedLongMax = delayedZero == 0 ? Long.MAX_VALUE : 0L;

        long inner = Math.max((long)x - 2L, Integer.MIN_VALUE);
        long delayedInner = Math.min(inner, delayedLongMax);
        long outer = Math.max(delayedInner - 1L, Integer.MIN_VALUE);
        return outer;
    }

    static long testMin3Hop(int x, int y) {
        int delayedZero = 100;
        for (int i = 0; i < 100; i++) { delayedZero--; }

        // Trigger update at 3-hop, the inner add.
        // Make sure this does not trigger a type update which would
        // trickle down incrementally, so collapse from two expressions
        // that have exactly the same type.
        long delayedAdd = delayedZero == 0 ? (long)x + 2L : (long)y + 2L;

        long inner = Math.min(delayedAdd, Integer.MAX_VALUE);
        long outer = Math.min(inner + 1L, Integer.MAX_VALUE);
        return outer;
    }

    static long testMax3Hop(int x, int y) {
        int delayedZero = 100;
        for (int i = 0; i < 100; i++) { delayedZero--; }

        long delayedAdd = delayedZero == 0 ? (long)x - 2L : (long)y - 2L;

        long inner = Math.max(delayedAdd, Integer.MIN_VALUE);
        long outer = Math.max(inner - 1L, Integer.MIN_VALUE);
        return outer;
    }

    static long testMin4Hop(int x, int y) {
        int delayedZero = 100;
        for (int i = 0; i < 100; i++) { delayedZero--; }

        long delayedAdd = delayedZero == 0 ? (long)x + 4L : (long)y + 4L;
        delayedAdd = delayedAdd + -2L;

        long inner = Math.min(delayedAdd, Integer.MAX_VALUE);
        long outer = Math.min(inner + 1L, Integer.MAX_VALUE);
        return outer;
    }
}
