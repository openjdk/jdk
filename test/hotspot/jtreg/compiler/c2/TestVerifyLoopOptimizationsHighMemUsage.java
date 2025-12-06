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
 * @run main/othervm -XX:CompileCommand=compileonly,*TestVerifyLoopOptimizationsHighMemUsage*::* -XX:-TieredCompilation -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressLoopPeeling -XX:+VerifyLoopOptimizations
 *                   -XX:StressSeed=3106998670 TestVerifyLoopOptimizationsHighMemUsage
 * @run main TestVerifyLoopOptimizationsHighMemUsage
 */

public class TestVerifyLoopOptimizationsHighMemUsage {

    public static final int N = 400;

    public static long instanceCount=-13L;
    public static volatile short sFld=-16143;
    public byte byFld=28;
    public boolean bFld=false;
    public int iFld=-159;
    public static float fArrFld[]=new float[N];
    public static volatile double dArrFld[]=new double[N];

    public static long lMeth_check_sum = 0;

    public long lMeth(int i1) {

        int i2=11, i3=37085, i4=177, i5=190, i6=-234, i7=13060, iArr[]=new int[N];
        float f=1.179F;
        double d=2.9685, d1=0.17775;
        long lArr[]=new long[N];
        boolean bArr[]=new boolean[N];

        for (i2 = 15; i2 < 330; ++i2) {
            for (i4 = 1; i4 < 5; ++i4) {
                TestVerifyLoopOptimizationsHighMemUsage.fArrFld[i4 + 1] = (++i1);
                for (i6 = 2; i6 > 1; i6 -= 3) {
                    iArr[i6] <<= i1;
                    switch ((i2 * 5) + 54) {
                    case 156:
                        if (i4 != 0) {
                        }
                        i5 += (int)(f++);
                        switch (((((i3++) >>> 1) % 8) * 5) + 92) {
                        case 123:
                            iArr[i6 - 1] -= ((i5++) * Math.max(i6 | i2, --i7));
                            switch (((i2 % 1) * 5) + 91) {
                            case 92:
                                f = i1;
                                f = -32.539F;
                            default:
                                i3 -= (int)TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                            }
                        case 109:
                            if (i4 != 0) {
                            }
                            break;
                        case 98:
                            i3 -= i6;
                            break;
                        case 99:
                            iArr = iArr;
                        case 100:
                            TestVerifyLoopOptimizationsHighMemUsage.sFld = (short)-166L;
                            break;
                        case 117:
                            i1 = i7;
                            break;
                        case 101:
                            TestVerifyLoopOptimizationsHighMemUsage.instanceCount += -3L;
                            break;
                        case 116:
                            byFld &= (byte)i5;
                        default:
                            TestVerifyLoopOptimizationsHighMemUsage.instanceCount += i6;
                        }
                        break;
                    case 168:
                        TestVerifyLoopOptimizationsHighMemUsage.instanceCount = -122;
                        break;
                    case 342:
                        byFld -= (byte)-135;
                        break;
                    case 283:
                        i1 = (int)TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                        break;
                    case 91:
                        i7 -= i1;
                        break;
                    case 281:
                        i1 += i3;
                        break;
                    case 328:
                        i5 += i6;
                        break;
                    case 322:
                        TestVerifyLoopOptimizationsHighMemUsage.instanceCount = i7;
                        break;
                    case 228:
                        if (bFld) continue;
                        break;
                    case 114:
                        lArr[i2 - 1] -= i1;
                        break;
                    case 207:
                        iArr = iArr;
                        break;
                    case 209:
                        i7 |= i1;
                        break;
                    case 354:
                        i7 += byFld;
                        break;
                    case 108:
                        i1 <<= i1;
                    case 398:
                        i5 += (i6 * i6);
                        break;
                    case 144:
                        bFld = true;
                        break;
                    case 218:
                        f += i1;
                        break;
                    case 116:
                        iArr = iArr;
                        break;
                    case 296:
                        i5 = (int)TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                    case 198:
                        i1 = (int)TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                    case 173:
                        i5 = (int)TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                        break;
                    case 258:
                        f = TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                        break;
                    case 105:
                        i5 = i1;
                    case 120:
                        d = -6201927065613177484L;
                        break;
                    case 374:
                        i3 = (int)TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                        break;
                    case 248:
                        i5 += i6;
                        break;
                    case 140:
                        i1 /= (int)(i1 | 1);
                        break;
                    case 366:
                        f = -181;
                        break;
                    case 222:
                    case 57:
                        iArr[i2 - 1] >>= i2;
                        break;
                    case 261:
                        i1 = -3;
                        break;
                    case 106:
                        TestVerifyLoopOptimizationsHighMemUsage.sFld = (short)-35383;
                        break;
                    case 306:
                        TestVerifyLoopOptimizationsHighMemUsage.dArrFld[i6 - 1] -= d1;
                        break;
                    case 292:
                        i7 = i3;
                        break;
                    case 93:
                    case 187:
                        i1 += i6;
                    case 399:
                        iArr[i6 + 1] = (int)TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                        break;
                    case 151:
                        i3 += i6;
                        break;
                    case 287:
                        bFld = bFld;
                        break;
                    case 148:
                        f = (float)-77.43986;
                    case 247:
                        i7 |= i1;
                    case 352:
                        try {
                            i5 = (i6 / -44130);
                            i1 = (i7 % i4);
                            iArr[i4 + 1] = (12 / i2);
                        } catch (ArithmeticException a_e) {}
                        break;
                    case 170:
                        f += (((i6 * i1) + i4) - i5);
                    case 404:
                        i5 += (i6 ^ TestVerifyLoopOptimizationsHighMemUsage.instanceCount);
                        break;
                    case 74:
                    case 370:
                    case 211:
                        i5 += i6;
                        break;
                    case 359:
                        TestVerifyLoopOptimizationsHighMemUsage.instanceCount += (-11 + (i6 * i6));
                    case 345:
                        f -= i4;
                        break;
                    case 238:
                        TestVerifyLoopOptimizationsHighMemUsage.instanceCount *= i6;
                    case 123:
                        i5 <<= i7;
                        break;
                    case 236:
                        if (i4 != 0) {
                        }
                        break;
                    case 61:
                        TestVerifyLoopOptimizationsHighMemUsage.instanceCount = i7;
                        break;
                    case 302:
                        if (bFld) continue;
                    case 231:
                        try {
                            i5 = (i2 / i5);
                            i1 = (-40725 / iArr[i4 + 1]);
                            i3 = (-1719830216 % iFld);
                        } catch (ArithmeticException a_e) {}
                        break;
                    case 340:
                        i3 += i1;
                        break;
                    case 162:
                        f += (-12 + (i6 * i6));
                        break;
                    case 252:
                        i7 |= i2;
                        break;
                    case 58:
                        bArr[i6] = bFld;
                        break;
                    case 251:
                        i7 += (int)d;
                        break;
                    case 179:
                        f += (((i6 * TestVerifyLoopOptimizationsHighMemUsage.sFld) + i4) - iFld);
                        break;
                    case 403:
                        iArr[i6 + 1] += iFld;
                        break;
                    case 78:
                        f *= TestVerifyLoopOptimizationsHighMemUsage.instanceCount;
                        break;
                    case 396:
                        byFld -= (byte)i4;
                        break;
                    case 301:
                        TestVerifyLoopOptimizationsHighMemUsage.instanceCount += (-48773 + (i6 * i6));
                        break;
                    case 378:
                        i7 -= (int)150L;
                        break;
                    case 244:
                        i5 += (((i6 * TestVerifyLoopOptimizationsHighMemUsage.instanceCount) + TestVerifyLoopOptimizationsHighMemUsage.instanceCount) - i2);
                    case 133:
                        if (iFld != 0) {
                        }
                        break;
                    case 391:
                        i3 += i6;
                    case 152:
                        lArr[i2 + 1] += i2;
                        break;
                    }
                }
            }
        }
        long meth_res = i1 + i2 + i3 + i4 + i5 + i6 + i7 + Float.floatToIntBits(f) + Double.doubleToLongBits(d) +
            Double.doubleToLongBits(d1)
            + checkSum(iArr)
            + checkSum(lArr)
            ;
        return (long)meth_res;
    }

    public static long checkSum(int[] a) {
        long sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        }
        return sum;
    }

    public static long checkSum(long[] a) {
        long sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        }
        return sum;
    }

    public static void main(String[] strArr) {
        TestVerifyLoopOptimizationsHighMemUsage _instance = new TestVerifyLoopOptimizationsHighMemUsage();
        for (int i = 0; i < 10; i++ ) {
            _instance.lMeth(-159);
        }
    }
}
