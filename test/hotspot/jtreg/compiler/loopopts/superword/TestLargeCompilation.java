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

package compiler.loopopts.superword;

/*
 * @test
 * @bug 8327978
 * @summary Test compile time for large compilation, where SuperWord takes especially much time.
 * @requires vm.compiler2.enabled
 * @run main/othervm/timeout=30 -XX:LoopUnrollLimit=1000 -Xbatch
 *                              -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestLargeCompilation::test*
 *                              compiler.loopopts.superword.TestLargeCompilation
 */

import java.util.Random;

public class TestLargeCompilation {
    private static final Random random = new Random();
    static final int RANGE_CON = 1024 * 8;

    static int init = 593436;
    static int limit = 599554;
    static int offset1 = -592394;
    static int offset2 = -592386;
    static final int offset3 = -592394;
    static final int stride =  4;
    static final int scale =   1;
    static final int hand_unrolling1 = 2;
    static final int hand_unrolling2 = 8;
    static final int hand_unrolling3 = 15;

    public static void main(String[] args) {
        byte[] aB = generateB();
        byte[] bB = generateB();
        byte[] cB = generateB();

        for (int i = 1; i < 100; i++) {
            testUUBBBH(aB, bB, cB);
        }
    }

    static byte[] generateB() {
        byte[] a = new byte[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)random.nextInt();
        }
        return a;
    }

    static Object[] testUUBBBH(byte[] a, byte[] b, byte[] c) {
        int h1 = hand_unrolling1;
        int h2 = hand_unrolling2;
        int h3 = hand_unrolling3;

        for (int i = init; i < limit; i += stride) {
            if (h1 >=  1) { a[offset1 + i * scale +  0]++; }
            if (h1 >=  2) { a[offset1 + i * scale +  1]++; }
            if (h1 >=  3) { a[offset1 + i * scale +  2]++; }
            if (h1 >=  4) { a[offset1 + i * scale +  3]++; }
            if (h1 >=  5) { a[offset1 + i * scale +  4]++; }
            if (h1 >=  6) { a[offset1 + i * scale +  5]++; }
            if (h1 >=  7) { a[offset1 + i * scale +  6]++; }
            if (h1 >=  8) { a[offset1 + i * scale +  7]++; }
            if (h1 >=  9) { a[offset1 + i * scale +  8]++; }
            if (h1 >= 10) { a[offset1 + i * scale +  9]++; }
            if (h1 >= 11) { a[offset1 + i * scale + 10]++; }
            if (h1 >= 12) { a[offset1 + i * scale + 11]++; }
            if (h1 >= 13) { a[offset1 + i * scale + 12]++; }
            if (h1 >= 14) { a[offset1 + i * scale + 13]++; }
            if (h1 >= 15) { a[offset1 + i * scale + 14]++; }
            if (h1 >= 16) { a[offset1 + i * scale + 15]++; }

            if (h2 >=  1) { b[offset2 + i * scale +  0]++; }
            if (h2 >=  2) { b[offset2 + i * scale +  1]++; }
            if (h2 >=  3) { b[offset2 + i * scale +  2]++; }
            if (h2 >=  4) { b[offset2 + i * scale +  3]++; }
            if (h2 >=  5) { b[offset2 + i * scale +  4]++; }
            if (h2 >=  6) { b[offset2 + i * scale +  5]++; }
            if (h2 >=  7) { b[offset2 + i * scale +  6]++; }
            if (h2 >=  8) { b[offset2 + i * scale +  7]++; }
            if (h2 >=  9) { b[offset2 + i * scale +  8]++; }
            if (h2 >= 10) { b[offset2 + i * scale +  9]++; }
            if (h2 >= 11) { b[offset2 + i * scale + 10]++; }
            if (h2 >= 12) { b[offset2 + i * scale + 11]++; }
            if (h2 >= 13) { b[offset2 + i * scale + 12]++; }
            if (h2 >= 14) { b[offset2 + i * scale + 13]++; }
            if (h2 >= 15) { b[offset2 + i * scale + 14]++; }
            if (h2 >= 16) { b[offset2 + i * scale + 15]++; }

            if (h3 >=  1) { c[offset3 + i * scale +  0]++; }
            if (h3 >=  2) { c[offset3 + i * scale +  1]++; }
            if (h3 >=  3) { c[offset3 + i * scale +  2]++; }
            if (h3 >=  4) { c[offset3 + i * scale +  3]++; }
            if (h3 >=  5) { c[offset3 + i * scale +  4]++; }
            if (h3 >=  6) { c[offset3 + i * scale +  5]++; }
            if (h3 >=  7) { c[offset3 + i * scale +  6]++; }
            if (h3 >=  8) { c[offset3 + i * scale +  7]++; }
            if (h3 >=  9) { c[offset3 + i * scale +  8]++; }
            if (h3 >= 10) { c[offset3 + i * scale +  9]++; }
            if (h3 >= 11) { c[offset3 + i * scale + 10]++; }
            if (h3 >= 12) { c[offset3 + i * scale + 11]++; }
            if (h3 >= 13) { c[offset3 + i * scale + 12]++; }
            if (h3 >= 14) { c[offset3 + i * scale + 13]++; }
            if (h3 >= 15) { c[offset3 + i * scale + 14]++; }
            if (h3 >= 16) { c[offset3 + i * scale + 15]++; }
        }
        return new Object[]{ a, b, c };
    }
}
