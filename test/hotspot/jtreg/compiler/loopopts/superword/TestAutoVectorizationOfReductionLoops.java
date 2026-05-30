/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @summary Test auto-vectorization of loops containing sum and mul reductions
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAutoVectorizationOfReductionLoops
 */
public class TestAutoVectorizationOfReductionLoops {
    static final int DIM = 16;
    static final int SIZE = 256;
    static final Random RANDOM = Utils.getRandomInstance();

    float[] fx;
    float[] fy;
    float[] fm;
    float[] fm2;

    double[] dx;
    double[] dy;
    double[] dm;
    double[] dm2;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation");
    }

    public TestAutoVectorizationOfReductionLoops() {
        fx = new float[SIZE];
        fy = new float[SIZE];
        fm = new float[SIZE * DIM];
        fm2 = new float[SIZE * DIM];

        dx = new double[SIZE];
        dy = new double[SIZE];
        dm = new double[SIZE * DIM];
        dm2 = new double[SIZE * DIM];

        for (int i = 0; i < SIZE; i++) {
            // `RANDOM.nextFloat()` (and `RANDOM.nextDouble()`) will produce
            // values in the range [0.0, 1.0), all of which have the same
            // magnitude, so we instead generate `int` (or `long`) values and
            // convert them to `float` (or `double`) values.

            fx[i] = Float.intBitsToFloat(RANDOM.nextInt());
            fy[i] = Float.intBitsToFloat(RANDOM.nextInt());
            dx[i] = Double.longBitsToDouble(RANDOM.nextLong());
            dy[i] = Double.longBitsToDouble(RANDOM.nextLong());

            for (int j = 0; j < DIM; j++) {
                int idx = i * DIM + j;
                fm[idx] = Float.intBitsToFloat(RANDOM.nextInt());
                fm2[idx] = Float.intBitsToFloat(RANDOM.nextInt());
                dm[idx] = Double.longBitsToDouble(RANDOM.nextLong());
                dm2[idx] = Double.longBitsToDouble(RANDOM.nextLong());
            }
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0",
                  IRNode.MUL_VF,  "> 0",
                  IRNode.ADD_REDUCTION_VF,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static float testSDot(int n, float[] x, float[] y) {
        float sum = 0.0f;
        for (int i = 0; i < n; i++) {
            sum += x[i] * y[i];
        }
        return sum;
    }

    @Run(test = "testSDot")
    void runSDot() {
        float expected = 0.0f;
        for (int i = 0; i < SIZE; i++) {
            expected += fx[i] * fy[i];
        }
        int expectedBits = Float.floatToIntBits(expected);
        int computedBits = Float.floatToIntBits(testSDot(SIZE, fx, fy));
        Asserts.assertEquals(computedBits, expectedBits);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "> 0",
                  IRNode.MUL_VD,  "> 0",
                  IRNode.ADD_REDUCTION_VD,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static double testDDot(int n, double[] x, double[] y) {
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += x[i] * y[i];
        }
        return sum;
    }

    @Run(test = "testDDot")
    void runDDot() {
        double expected = 0.0;
        for (int i = 0; i < SIZE; i++) {
            expected += dx[i] * dy[i];
        }
        long expectedBits = Double.doubleToLongBits(expected);
        long computedBits = Double.doubleToLongBits(testDDot(SIZE, dx, dy));
        Asserts.assertEquals(computedBits, expectedBits);
    }

    @Test
    @IR(counts = {IRNode.MUL_VD, "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static float testSdsdot(int n, float sb, float[] x, float[] y) {
        double sum = sb;
        for (int i = 0; i < n; i++) {
            sum += (double) x[i] * (double) y[i];
        }
        return (float) sum;
    }

    @Run(test = "testSdsdot")
    void runSdsdot() {
        double sum = 1.0f;
        for (int i = 0; i < SIZE; i++) {
            sum += (double) fx[i] * (double) fy[i];
        }
        float expected = (float) sum;
        int expectedBits = Float.floatToIntBits(expected);
        int computedBits = Float.floatToIntBits(testSdsdot(SIZE, 1.0f, fx, fy));
        Asserts.assertEquals(computedBits, expectedBits);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0",
                  IRNode.MUL_VF, "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testSgemvT(int m, int n, float alpha, float[] a, int lda, float[] x, float beta, float[] y) {
        for (int col = 0; col < n; col++) {
            float sum = 0.0f;
            for (int row = 0; row < m; row++) {
                sum += x[row] * a[row + col * lda];
            }
            if (beta != 0.0f) {
                y[col] = alpha * sum + beta * y[col];
            } else {
                y[col] = alpha * sum;
            }
        }
    }

    @Run(test = "testSgemvT")
    void runSgemvT() {
        float[] yCopy = new float[DIM];
        float[] yExpected = new float[DIM];
        for (int i = 0; i < DIM; i++) {
            yCopy[i] = fy[i];
            yExpected[i] = fy[i];
        }
        testSgemvT(SIZE, DIM, 2.0f, fm, SIZE, fx, 1.0f, yCopy);
        for (int col = 0; col < DIM; col++) {
            float sum = 0.0f;
            for (int row = 0; row < SIZE; row++) {
                sum += fx[row] * fm[row + col * SIZE];
            }
            yExpected[col] = 2.0f * sum + 1.0f * yExpected[col];
        }
        for (int i = 0; i < DIM; i++) {
            Asserts.assertEquals(Float.floatToIntBits(yCopy[i]), Float.floatToIntBits(yExpected[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "> 0",
                  IRNode.MUL_VD, "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testDgemvT(int m, int n, double alpha, double[] a, int lda, double[] x, double beta, double[] y) {
        for (int col = 0; col < n; col++) {
            double sum = 0.0;
            for (int row = 0; row < m; row++) {
                sum += x[row] * a[row + col * lda];
            }
            if (beta != 0.0) {
                y[col] = alpha * sum + beta * y[col];
            } else {
                y[col] = alpha * sum;
            }
        }
    }

    @Run(test = "testDgemvT")
    void runDgemvT() {
        double[] yCopy = new double[DIM];
        double[] yExpected = new double[DIM];
        for (int i = 0; i < DIM; i++) {
            yCopy[i] = dy[i];
            yExpected[i] = dy[i];
        }
        testDgemvT(SIZE, DIM, 2.0, dm, SIZE, dx, 1.0, yCopy);
        for (int col = 0; col < DIM; col++) {
            double sum = 0.0;
            for (int row = 0; row < SIZE; row++) {
                sum += dx[row] * dm[row + col * SIZE];
            }
            yExpected[col] = 2.0 * sum + 1.0 * yExpected[col];
        }
        for (int i = 0; i < DIM; i++) {
            Asserts.assertEquals(Double.doubleToLongBits(yCopy[i]), Double.doubleToLongBits(yExpected[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0",
                  IRNode.MUL_VF, "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testSgemmTN(int m, int n, int k, float alpha, float[] a, int lda, float[] b, int ldb, float beta, float[] c, int ldc) {
        for (int col = 0; col < n; col++) {
            for (int row = 0; row < m; row++) {
                float sum = 0.0f;
                for (int i = 0; i < k; i++) {
                    sum += a[i + row * lda] * b[i + col * ldb];
                }
                if (beta != 0.0f) {
                    c[row + col * ldc] = alpha * sum + beta * c[row + col * ldc];
                } else {
                    c[row + col * ldc] = alpha * sum;
                }
            }
        }
    }

    @Run(test = "testSgemmTN")
    void runSgemmTN() {
        float[] c = new float[DIM * DIM];
        float[] cExpected = new float[DIM * DIM];
        for (int i = 0; i < DIM * DIM; i++) {
            c[i] = RANDOM.nextFloat();
            cExpected[i] = c[i];
        }
        testSgemmTN(DIM, DIM, SIZE, 1.0f, fm, SIZE, fm2, SIZE, 1.0f, c, DIM);
        for (int col = 0; col < DIM; col++) {
            for (int row = 0; row < DIM; row++) {
                float sum = 0.0f;
                for (int i = 0; i < SIZE; i++) {
                    sum += fm[i + row * SIZE] * fm2[i + col * SIZE];
                }
                cExpected[row + col * DIM] = 1.0f * sum + 1.0f * cExpected[row + col * DIM];
            }
        }
        for (int i = 0; i < DIM * DIM; i++) {
            Asserts.assertEquals(Float.floatToIntBits(c[i]), Float.floatToIntBits(cExpected[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "> 0",
                  IRNode.MUL_VD, "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testDgemmTN(int m, int n, int k, double alpha, double[] a, int lda, double[] b, int ldb, double beta, double[] c, int ldc) {
        for (int col = 0; col < n; col++) {
            for (int row = 0; row < m; row++) {
                double sum = 0.0;
                for (int i = 0; i < k; i++) {
                    sum += a[i + row * lda] * b[i + col * ldb];
                }
                if (beta != 0.0) {
                    c[row + col * ldc] = alpha * sum + beta * c[row + col * ldc];
                } else {
                    c[row + col * ldc] = alpha * sum;
                }
            }
        }
    }

    @Run(test = "testDgemmTN")
    void runDgemmTN() {
        double[] c = new double[DIM * DIM];
        double[] cExpected = new double[DIM * DIM];
        for (int i = 0; i < DIM * DIM; i++) {
            c[i] = RANDOM.nextDouble();
            cExpected[i] = c[i];
        }
        testDgemmTN(DIM, DIM, SIZE, 1.0, dm, SIZE, dm2, SIZE, 1.0, c, DIM);
        for (int col = 0; col < DIM; col++) {
            for (int row = 0; row < DIM; row++) {
                double sum = 0.0;
                for (int i = 0; i < SIZE; i++) {
                    sum += dm[i + row * SIZE] * dm2[i + col * SIZE];
                }
                cExpected[row + col * DIM] = 1.0 * sum + 1.0 * cExpected[row + col * DIM];
            }
        }
        for (int i = 0; i < DIM * DIM; i++) {
            Asserts.assertEquals(Double.doubleToLongBits(c[i]), Double.doubleToLongBits(cExpected[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0",
                  IRNode.MUL_VF, "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testSgebpTN(int rows, int rowe, int cols, int cole, int is, int ie, float alpha, float[] a, int lda, float[] b, int ldb, float[] c, int ldc) {
        for (int col = cols; col < cole; col++) {
            for (int row = rows; row < rowe; row++) {
                float sum = 0.0f;
                for (int i = is; i < ie; i++) {
                    sum += a[i + row * lda] * b[i + col * ldb];
                }
                c[row + col * ldc] += alpha * sum;
            }
        }
    }

    @Run(test = "testSgebpTN")
    void runSgebpTN() {
        float[] c = new float[DIM * DIM];
        float[] cExpected = new float[DIM * DIM];
        for (int i = 0; i < DIM * DIM; i++) {
            c[i] = RANDOM.nextFloat();
            cExpected[i] = c[i];
        }
        testSgebpTN(0, DIM, 0, DIM, 0, SIZE, 1.0f, fm, SIZE, fm2, SIZE, c, DIM);
        for (int col = 0; col < DIM; col++) {
            for (int row = 0; row < DIM; row++) {
                float sum = 0.0f;
                for (int i = 0; i < SIZE; i++) {
                    sum += fm[i + row * SIZE] * fm2[i + col * SIZE];
                }
                cExpected[row + col * DIM] += 1.0f * sum;
            }
        }
        for (int i = 0; i < DIM * DIM; i++) {
            Asserts.assertEquals(Float.floatToIntBits(c[i]), Float.floatToIntBits(cExpected[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "> 0",
                  IRNode.MUL_VD, "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testDgebpTN(int rows, int rowe, int cols, int cole, int is, int ie, double alpha, double[] a, int lda, double[] b, int ldb, double[] c, int ldc) {
        for (int col = cols; col < cole; col++) {
            for (int row = rows; row < rowe; row++) {
                double sum = 0.0;
                for (int i = is; i < ie; i++) {
                    sum += a[i + row * lda] * b[i + col * ldb];
                }
                c[row + col * ldc] += alpha * sum;
            }
        }
    }

    @Run(test = "testDgebpTN")
    void runDgebpTN() {
        double[] c = new double[DIM * DIM];
        double[] cExpected = new double[DIM * DIM];
        for (int i = 0; i < DIM * DIM; i++) {
            c[i] = RANDOM.nextDouble();
            cExpected[i] = c[i];
        }
        testDgebpTN(0, DIM, 0, DIM, 0, SIZE, 1.0, dm, SIZE, dm2, SIZE, c, DIM);
        for (int col = 0; col < DIM; col++) {
            for (int row = 0; row < DIM; row++) {
                double sum = 0.0;
                for (int i = 0; i < SIZE; i++) {
                    sum += dm[i + row * SIZE] * dm2[i + col * SIZE];
                }
                cExpected[row + col * DIM] += 1.0 * sum;
            }
        }
        for (int i = 0; i < DIM * DIM; i++) {
            Asserts.assertEquals(Double.doubleToLongBits(c[i]), Double.doubleToLongBits(cExpected[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0",
                  IRNode.MUL_VF, "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testSgepdotTN(int rows, int rowe, int cols, int cole, int is, int ie, float alpha, float[] a, int lda, float[] b, int ldb, float[] c, int ldc) {
        for (int col = cols; col < cole; col++) {
            for (int row = rows; row < rowe; row++) {
                float sum = 0.0f;
                for (int i = is; i < ie; i++) {
                    sum += a[i + row * lda] * b[i + col * ldb];
                }
                c[row + col * ldc] += alpha * sum;
            }
        }
    }

    @Run(test = "testSgepdotTN")
    void runSgepdotTN() {
        float[] c = new float[DIM * DIM];
        float[] cExpected = new float[DIM * DIM];
        for (int i = 0; i < DIM * DIM; i++) {
            c[i] = RANDOM.nextFloat();
            cExpected[i] = c[i];
        }
        testSgepdotTN(0, 3, 0, 3, 0, SIZE, 1.0f, fm, SIZE, fm2, SIZE, c, DIM);
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                float sum = 0.0f;
                for (int i = 0; i < SIZE; i++) {
                    sum += fm[i + row * SIZE] * fm2[i + col * SIZE];
                }
                cExpected[row + col * DIM] += 1.0f * sum;
            }
        }
        for (int i = 0; i < DIM * DIM; i++) {
            Asserts.assertEquals(Float.floatToIntBits(c[i]), Float.floatToIntBits(cExpected[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "> 0",
                  IRNode.MUL_VD, "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void testDgepdotTN(int rows, int rowe, int cols, int cole, int is, int ie, double alpha, double[] a, int lda, double[] b, int ldb, double[] c, int ldc) {
        for (int col = cols; col < cole; col++) {
            for (int row = rows; row < rowe; row++) {
                double sum = 0.0;
                for (int i = is; i < ie; i++) {
                    sum += a[i + row * lda] * b[i + col * ldb];
                }
                c[row + col * ldc] += alpha * sum;
            }
        }
    }

    @Run(test = "testDgepdotTN")
    void runDgepdotTN() {
        double[] c = new double[DIM * DIM];
        double[] cExpected = new double[DIM * DIM];
        for (int i = 0; i < DIM * DIM; i++) {
            c[i] = RANDOM.nextDouble();
            cExpected[i] = c[i];
        }
        testDgepdotTN(0, 3, 0, 3, 0, SIZE, 1.0, dm, SIZE, dm2, SIZE, c, DIM);
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                double sum = 0.0;
                for (int i = 0; i < SIZE; i++) {
                    sum += dm[i + row * SIZE] * dm2[i + col * SIZE];
                }
                cExpected[row + col * DIM] += 1.0 * sum;
            }
        }
        for (int i = 0; i < DIM * DIM; i++) {
            Asserts.assertEquals(Double.doubleToLongBits(c[i]), Double.doubleToLongBits(cExpected[i]));
        }
    }
}
