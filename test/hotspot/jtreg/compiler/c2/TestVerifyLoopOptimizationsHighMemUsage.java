/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @key stress randomness
 * @bug 8370519
 * @summary C2: Hit MemLimit when running with +VerifyLoopOptimizations
 * @run main/othervm -XX:CompileCommand=compileonly,${test.main.class}::* -XX:-TieredCompilation -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+StressLoopPeeling -XX:+VerifyLoopOptimizations
 *                   -XX:CompileCommand=memlimit,${test.main.class}::*,100M~crash
 *                   -XX:StressSeed=3106998670 ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.c2;

public class TestVerifyLoopOptimizationsHighMemUsage {

    static int b = 400;
    static long c;
    static boolean d;

    static long lMeth(int e) {
        int f, g, h, k[] = new int[b];
        long l[] = new long[b];
        boolean m[] = new boolean[b];
        for (f = 5; f < 330; ++f)
        for (g = 1; g < 5; ++g)
            for (h = 2; h > 1; h -= 3)
            switch (f * 5 + 54) {
            case 156:
            case 354:
            case 98:
            case 173:
            case 120:
            case 374:
            case 140:
            case 57:
            case 106:
            case 306:
            case 87:
            case 399:
                k[1] = (int)c;
            case 51:
            case 287:
            case 148:
            case 70:
            case 74:
            case 59:
                m[h] = d;
            }
        long n = p(l);
        return n;
    }

    public static long p(long[] a) {
        long o = 0;
        for (int j = 0; j < a.length; j++)
        o += j;
        return o;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++)
        lMeth(9);
    }
}
