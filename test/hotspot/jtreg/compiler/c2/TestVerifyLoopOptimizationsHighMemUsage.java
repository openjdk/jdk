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

/**
 * @test
 * @key stress randomness
 * @bug 8370519
 * @summary C2: Hit MemLimit when running with +VerifyLoopOptimizations
 * @run main/othervm -XX:CompileCommand=compileonly,${test.main.class}::* -XX:-TieredCompilation -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+StressLoopPeeling -XX:+VerifyLoopOptimizations
 *                   -XX:CompileCommand=memlimit,${test.main.class}::*,600M~crash
 *                   -XX:StressSeed=3106998670 ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.c2;

public class TestVerifyLoopOptimizationsHighMemUsage {
    public static final int N = 400;
    public static long instanceCount = -13L;
    public static volatile short sFld = -16143;
    public static int iFld = -159;
    public static float fArrFld[] = new float[N];

    public static long lMeth(int i1) {
        int i2 = 11, i3 = 37085, i4 = 177, i5 = 190, i6 = -234, i7 = 13060,
                iArr[] = new int[N];
        float f = 1.179F;
        double d = 2.9685;
        long lArr[] = new long[N];
        for (i2 = 15; i2 < 330; ++i2)
            for (i4 = 1; i4 < 5; ++i4) {
                fArrFld[i4 + 1] = (++i1);
                for (i6 = 2; i6 > 1; i6 -= 3)
                    switch ((i2 * 5) + 54) {
                        case 156:
                            if (i4 != 0)
                                ;
                        case 168:
                        case 342:
                        case 283:
                        case 281:
                        case 328:
                        case 322:
                        case 228:
                        case 114:
                        case 207:
                        case 209:
                        case 354:
                        case 108:
                            i1 <<= i1;
                        case 398:
                        case 144:
                        case 218:
                        case 116:
                        case 296:
                        case 198:
                        case 173:
                        case 105:
                        case 120:
                        case 248:
                        case 140:
                        case 352:
                            try {
                            } catch (ArithmeticException a_e) {
                            }
                        case 404:
                            i5 += (i6 ^ instanceCount);
                        case 370:
                        case 211:
                        case 231:
                            try {
                            } catch (ArithmeticException a_e) {
                            }
                        case 251:
                        case 179:
                            f += (((i6 * sFld) + i4) -
                                    iFld);
                    }
            }
        long meth_res = i1 + i2 + i3 + i4 + i5 + i6 + i7 + Float.floatToIntBits(f) +
                Double.doubleToLongBits(d) + +checkSum(iArr) +
                checkSum(lArr);
        return meth_res;
    }

    public static long checkSum(int[] a) {
        long sum = 0;
        for (int j = 0; j < a.length; j++)
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        return sum;
    }

    public static long checkSum(long[] a) {
        long sum = 0;
        for (int j = 0; j < a.length; j++)
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        return sum;
    }

  public static void main(String[] strArr) {
    for (int i = 0; i < 10; i++)
      lMeth(-159);
  }
}
