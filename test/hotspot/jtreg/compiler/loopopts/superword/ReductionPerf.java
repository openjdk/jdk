/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8074981 8302652
 * @summary Test SuperWord Reduction Perf.
 * @library /test/lib /
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=exclude,compiler.loopopts.superword.ReductionPerf::main
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:LoopUnrollLimit=250
 *                   compiler.loopopts.superword.ReductionPerf
 */

package compiler.loopopts.superword;
import java.util.Random;
import jdk.test.lib.Utils;

public class ReductionPerf {
    static final int RANGE = 8192;
    static Random rand = Utils.getRandomInstance();

    public static void main(String args[]) {
        // Please increase iterations for measurement to 2_000 and 100_000.
        int iter_warmup = 100;
        int iter_perf   = 1_000;

        double[] aDouble = new double[RANGE];
        double[] bDouble = new double[RANGE];
        double[] cDouble = new double[RANGE];
        float[] aFloat = new float[RANGE];
        float[] bFloat = new float[RANGE];
        float[] cFloat = new float[RANGE];
        int[] aInt = new int[RANGE];
        int[] bInt = new int[RANGE];
        int[] cInt = new int[RANGE];
        long[] aLong = new long[RANGE];
        long[] bLong = new long[RANGE];
        long[] cLong = new long[RANGE];

        long start, stop;

        int startIntAdd = init(aInt, bInt, cInt);
        int goldIntAdd = testIntAdd(aInt, bInt, cInt, startIntAdd);
        for (int j = 0; j < iter_warmup; j++) {
            int total = testIntAdd(aInt, bInt, cInt, startIntAdd);
            verify("int add", total, goldIntAdd);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testIntAdd(aInt, bInt, cInt, startIntAdd);
        }
        stop = System.currentTimeMillis();
        System.out.println("int add    " + (stop - start));

        int startIntMul = init(aInt, bInt, cInt);
        int goldIntMul = testIntMul(aInt, bInt, cInt, startIntMul);
        for (int j = 0; j < iter_warmup; j++) {
            int total = testIntMul(aInt, bInt, cInt, startIntMul);
            verify("int mul", total, goldIntMul);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testIntMul(aInt, bInt, cInt, startIntMul);
        }
        stop = System.currentTimeMillis();
        System.out.println("int mul    " + (stop - start));

        int startIntMin = init(aInt, bInt, cInt);
        int goldIntMin = testIntMin(aInt, bInt, cInt, startIntMin);
        for (int j = 0; j < iter_warmup; j++) {
            int total = testIntMin(aInt, bInt, cInt, startIntMin);
            verify("int min", total, goldIntMin);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testIntMin(aInt, bInt, cInt, startIntMin);
        }
        stop = System.currentTimeMillis();
        System.out.println("int min    " + (stop - start));

        int startIntMax = init(aInt, bInt, cInt);
        int goldIntMax = testIntMax(aInt, bInt, cInt, startIntMax);
        for (int j = 0; j < iter_warmup; j++) {
            int total = testIntMax(aInt, bInt, cInt, startIntMax);
            verify("int max", total, goldIntMax);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testIntMax(aInt, bInt, cInt, startIntMax);
        }
        stop = System.currentTimeMillis();
        System.out.println("int max    " + (stop - start));

        int startIntAnd = init(aInt, bInt, cInt);
        int goldIntAnd = testIntAnd(aInt, bInt, cInt, startIntAnd);
        for (int j = 0; j < iter_warmup; j++) {
            int total = testIntAnd(aInt, bInt, cInt, startIntAnd);
            verify("int and", total, goldIntAnd);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testIntAnd(aInt, bInt, cInt, startIntAnd);
        }
        stop = System.currentTimeMillis();
        System.out.println("int and    " + (stop - start));

        int startIntOr = init(aInt, bInt, cInt);
        int goldIntOr = testIntOr(aInt, bInt, cInt, startIntOr);
        for (int j = 0; j < iter_warmup; j++) {
            int total = testIntOr(aInt, bInt, cInt, startIntOr);
            verify("int or", total, goldIntOr);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testIntOr(aInt, bInt, cInt, startIntOr);
        }
        stop = System.currentTimeMillis();
        System.out.println("int or     " + (stop - start));

        int startIntXor = init(aInt, bInt, cInt);
        int goldIntXor = testIntXor(aInt, bInt, cInt, startIntXor);
        for (int j = 0; j < iter_warmup; j++) {
            int total = testIntXor(aInt, bInt, cInt, startIntXor);
            verify("int xor", total, goldIntXor);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testIntXor(aInt, bInt, cInt, startIntXor);
        }
        stop = System.currentTimeMillis();
        System.out.println("int xor    " + (stop - start));

        long startLongAdd = init(aLong, bLong, cLong);
        long goldLongAdd = testLongAdd(aLong, bLong, cLong, startLongAdd);
        for (int j = 0; j < iter_warmup; j++) {
            long total = testLongAdd(aLong, bLong, cLong, startLongAdd);
            verify("long add", total, goldLongAdd);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testLongAdd(aLong, bLong, cLong, startLongAdd);
        }
        stop = System.currentTimeMillis();
        System.out.println("long add   " + (stop - start));

        long startLongMul = init(aLong, bLong, cLong);
        long goldLongMul = testLongMul(aLong, bLong, cLong, startLongMul);
        for (int j = 0; j < iter_warmup; j++) {
            long total = testLongMul(aLong, bLong, cLong, startLongMul);
            verify("long mul", total, goldLongMul);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testLongMul(aLong, bLong, cLong, startLongMul);
        }
        stop = System.currentTimeMillis();
        System.out.println("long mul   " + (stop - start));

        long startLongMin = init(aLong, bLong, cLong);
        long goldLongMin = testLongMin(aLong, bLong, cLong, startLongMin);
        for (int j = 0; j < iter_warmup; j++) {
            long total = testLongMin(aLong, bLong, cLong, startLongMin);
            verify("long min", total, goldLongMin);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testLongMin(aLong, bLong, cLong, startLongMin);
        }
        stop = System.currentTimeMillis();
        System.out.println("long min   " + (stop - start));

        long startLongMax = init(aLong, bLong, cLong);
        long goldLongMax = testLongMax(aLong, bLong, cLong, startLongMax);
        for (int j = 0; j < iter_warmup; j++) {
            long total = testLongMax(aLong, bLong, cLong, startLongMax);
            verify("long max", total, goldLongMax);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testLongMax(aLong, bLong, cLong, startLongMax);
        }
        stop = System.currentTimeMillis();
        System.out.println("long max   " + (stop - start));

        long startLongAnd = init(aLong, bLong, cLong);
        long goldLongAnd = testLongAnd(aLong, bLong, cLong, startLongAnd);
        for (int j = 0; j < iter_warmup; j++) {
            long total = testLongAnd(aLong, bLong, cLong, startLongAnd);
            verify("long and", total, goldLongAnd);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testLongAnd(aLong, bLong, cLong, startLongAnd);
        }
        stop = System.currentTimeMillis();
        System.out.println("long and   " + (stop - start));

        long startLongOr = init(aLong, bLong, cLong);
        long goldLongOr = testLongOr(aLong, bLong, cLong, startLongOr);
        for (int j = 0; j < iter_warmup; j++) {
            long total = testLongOr(aLong, bLong, cLong, startLongOr);
            verify("long or", total, goldLongOr);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testLongOr(aLong, bLong, cLong, startLongOr);
        }
        stop = System.currentTimeMillis();
        System.out.println("long or    " + (stop - start));

        long startLongXor = init(aLong, bLong, cLong);
        long goldLongXor = testLongXor(aLong, bLong, cLong, startLongXor);
        for (int j = 0; j < iter_warmup; j++) {
            long total = testLongXor(aLong, bLong, cLong, startLongXor);
            verify("long xor", total, goldLongXor);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testLongXor(aLong, bLong, cLong, startLongXor);
        }
        stop = System.currentTimeMillis();
        System.out.println("long xor   " + (stop - start));

        float startFloatAdd = init(aFloat, bFloat, cFloat);
        float goldFloatAdd = testFloatAdd(aFloat, bFloat, cFloat, startFloatAdd);
        for (int j = 0; j < iter_warmup; j++) {
            float total = testFloatAdd(aFloat, bFloat, cFloat, startFloatAdd);
            verify("float add", total, goldFloatAdd);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testFloatAdd(aFloat, bFloat, cFloat, startFloatAdd);
        }
        stop = System.currentTimeMillis();
        System.out.println("float add  " + (stop - start));

        float startFloatMul = init(aFloat, bFloat, cFloat);
        float goldFloatMul = testFloatMul(aFloat, bFloat, cFloat, startFloatMul);
        for (int j = 0; j < iter_warmup; j++) {
            float total = testFloatMul(aFloat, bFloat, cFloat, startFloatMul);
            verify("float mul", total, goldFloatMul);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testFloatMul(aFloat, bFloat, cFloat, startFloatMul);
        }
        stop = System.currentTimeMillis();
        System.out.println("float mul  " + (stop - start));

        float startFloatMin = init(aFloat, bFloat, cFloat);
        float goldFloatMin = testFloatMin(aFloat, bFloat, cFloat, startFloatMin);
        for (int j = 0; j < iter_warmup; j++) {
            float total = testFloatMin(aFloat, bFloat, cFloat, startFloatMin);
            verify("float min", total, goldFloatMin);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testFloatMin(aFloat, bFloat, cFloat, startFloatMin);
        }
        stop = System.currentTimeMillis();
        System.out.println("float min  " + (stop - start));

        float startFloatMax = init(aFloat, bFloat, cFloat);
        float goldFloatMax = testFloatMax(aFloat, bFloat, cFloat, startFloatMax);
        for (int j = 0; j < iter_warmup; j++) {
            float total = testFloatMax(aFloat, bFloat, cFloat, startFloatMax);
            verify("float max", total, goldFloatMax);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testFloatMax(aFloat, bFloat, cFloat, startFloatMax);
        }
        stop = System.currentTimeMillis();
        System.out.println("float max  " + (stop - start));

        double startDoubleAdd = init(aDouble, bDouble, cDouble);
        double goldDoubleAdd = testDoubleAdd(aDouble, bDouble, cDouble, startDoubleAdd);
        for (int j = 0; j < iter_warmup; j++) {
            double total = testDoubleAdd(aDouble, bDouble, cDouble, startDoubleAdd);
            verify("double add", total, goldDoubleAdd);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testDoubleAdd(aDouble, bDouble, cDouble, startDoubleAdd);
        }
        stop = System.currentTimeMillis();
        System.out.println("double add " + (stop - start));

        double startDoubleMul = init(aDouble, bDouble, cDouble);
        double goldDoubleMul = testDoubleMul(aDouble, bDouble, cDouble, startDoubleMul);
        for (int j = 0; j < iter_warmup; j++) {
            double total = testDoubleMul(aDouble, bDouble, cDouble, startDoubleMul);
            verify("double mul", total, goldDoubleMul);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testDoubleMul(aDouble, bDouble, cDouble, startDoubleMul);
        }
        stop = System.currentTimeMillis();
        System.out.println("double mul " + (stop - start));

        double startDoubleMin = init(aDouble, bDouble, cDouble);
        double goldDoubleMin = testDoubleMin(aDouble, bDouble, cDouble, startDoubleMin);
        for (int j = 0; j < iter_warmup; j++) {
            double total = testDoubleMin(aDouble, bDouble, cDouble, startDoubleMin);
            verify("double min", total, goldDoubleMin);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testDoubleMin(aDouble, bDouble, cDouble, startDoubleMin);
        }
        stop = System.currentTimeMillis();
        System.out.println("double min " + (stop - start));

        double startDoubleMax = init(aDouble, bDouble, cDouble);
        double goldDoubleMax = testDoubleMax(aDouble, bDouble, cDouble, startDoubleMax);
        for (int j = 0; j < iter_warmup; j++) {
            double total = testDoubleMax(aDouble, bDouble, cDouble, startDoubleMax);
            verify("double max", total, goldDoubleMax);
        }
        start = System.currentTimeMillis();
        for (int j = 0; j < iter_perf; j++) {
            testDoubleMax(aDouble, bDouble, cDouble, startDoubleMax);
        }
        stop = System.currentTimeMillis();
        System.out.println("double max " + (stop - start));

    }

    // ------------------- Tests -------------------

    static int testIntAdd(int[] a, int[] b, int[] c, int total) {
        for (int i = 0; i < RANGE; i++) {
            int v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total += v;
        }
        return total;
    }

    static int testIntMul(int[] a, int[] b, int[] c, int total) {
        for (int i = 0; i < RANGE; i++) {
            int v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total *= v;
        }
        return total;
    }

    static int testIntMin(int[] a, int[] b, int[] c, int total) {
        for (int i = 0; i < RANGE; i++) {
            int v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.min(total, v);
        }
        return total;
    }

    static int testIntMax(int[] a, int[] b, int[] c, int total) {
        for (int i = 0; i < RANGE; i++) {
            int v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.max(total, v);
        }
        return total;
    }

    static int testIntAnd(int[] a, int[] b, int[] c, int total) {
        for (int i = 0; i < RANGE; i++) {
            int v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total &= v;
        }
        return total;
    }

    static int testIntOr(int[] a, int[] b, int[] c, int total) {
        for (int i = 0; i < RANGE; i++) {
            int v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total |= v;
        }
        return total;
    }

    static int testIntXor(int[] a, int[] b, int[] c, int total) {
        for (int i = 0; i < RANGE; i++) {
            int v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total ^= v;
        }
        return total;
    }

    static long testLongAdd(long[] a, long[] b, long[] c, long total) {
        for (int i = 0; i < RANGE; i++) {
            long v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total += v;
        }
        return total;
    }

    static long testLongMul(long[] a, long[] b, long[] c, long total) {
        for (int i = 0; i < RANGE; i++) {
            long v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total *= v;
        }
        return total;
    }

    static long testLongMin(long[] a, long[] b, long[] c, long total) {
        for (int i = 0; i < RANGE; i++) {
            long v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.min(total, v);
        }
        return total;
    }

    static long testLongMax(long[] a, long[] b, long[] c, long total) {
        for (int i = 0; i < RANGE; i++) {
            long v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.max(total, v);
        }
        return total;
    }

    static long testLongAnd(long[] a, long[] b, long[] c, long total) {
        for (int i = 0; i < RANGE; i++) {
            long v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total &= v;
        }
        return total;
    }

    static long testLongOr(long[] a, long[] b, long[] c, long total) {
        for (int i = 0; i < RANGE; i++) {
            long v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total |= v;
        }
        return total;
    }

    static long testLongXor(long[] a, long[] b, long[] c, long total) {
        for (int i = 0; i < RANGE; i++) {
            long v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total ^= v;
        }
        return total;
    }

    static float testFloatAdd(float[] a, float[] b, float[] c, float total) {
        for (int i = 0; i < RANGE; i++) {
            float v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total += v;
        }
        return total;
    }

    static float testFloatMul(float[] a, float[] b, float[] c, float total) {
        for (int i = 0; i < RANGE; i++) {
            float v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total *= v;
        }
        return total;
    }

    static float testFloatMin(float[] a, float[] b, float[] c, float total) {
        for (int i = 0; i < RANGE; i++) {
            float v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.min(total, v);
        }
        return total;
    }

    static float testFloatMax(float[] a, float[] b, float[] c, float total) {
        for (int i = 0; i < RANGE; i++) {
            float v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.max(total, v);
        }
        return total;
    }

    static double testDoubleAdd(double[] a, double[] b, double[] c, double total) {
        for (int i = 0; i < RANGE; i++) {
            double v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total += v;
        }
        return total;
    }

    static double testDoubleMul(double[] a, double[] b, double[] c, double total) {
        for (int i = 0; i < RANGE; i++) {
            double v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total *= v;
        }
        return total;
    }

    static double testDoubleMin(double[] a, double[] b, double[] c, double total) {
        for (int i = 0; i < RANGE; i++) {
            double v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.min(total, v);
        }
        return total;
    }

    static double testDoubleMax(double[] a, double[] b, double[] c, double total) {
        for (int i = 0; i < RANGE; i++) {
            double v = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = Math.max(total, v);
        }
        return total;
    }

    // ------------------- Initialization -------------------

    static int init(int[] a, int[] b, int[] c) {
        for (int j = 0; j < RANGE; j++) {
            a[j] = rand.nextInt();
            b[j] = rand.nextInt();
            c[j] = rand.nextInt();
        }
        return rand.nextInt();
    }

    static long init(long[] a, long[] b, long[] c) {
        for (int j = 0; j < RANGE; j++) {
            a[j] = rand.nextLong();
            b[j] = rand.nextLong();
            c[j] = rand.nextLong();
        }
        return rand.nextLong();
    }

    static float init(float[] a, float[] b, float[] c) {
        for (int j = 0; j < RANGE; j++) {
            a[j] = rand.nextFloat();
            b[j] = rand.nextFloat();
            c[j] = rand.nextFloat();
        }
        return rand.nextFloat();
    }

    static double init(double[] a, double[] b, double[] c) {
        for (int j = 0; j < RANGE; j++) {
            a[j] = rand.nextDouble();
            b[j] = rand.nextDouble();
            c[j] = rand.nextDouble();
        }
        return rand.nextDouble();
    }

    // ------------------- Verification -------------------

    static void verify(String context, double total, double gold) {
        if (total != gold) {
            throw new RuntimeException("Wrong result for " + context + ": " + total + " != " + gold);
        }
    }
    static void verify(String context, float total, float gold) {
        if (total != gold) {
            throw new RuntimeException("Wrong result for " + context + ": " + total + " != " + gold);
        }
    }
    static void verify(String context, int total, int gold) {
        if (total != gold) {
            throw new RuntimeException("Wrong result for " + context + ": " + total + " != " + gold);
        }
    }
    static void verify(String context, long total, long gold) {
        if (total != gold) {
            throw new RuntimeException("Wrong result for " + context + ": " + total + " != " + gold);
        }
    }
}
