/*
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.List;

/*
 * @test
 * @bug 8358892 8357551
 * @summary The test is to trigger code path of BoolTest::ge/gt in C2_MacroAssembler::enc_cmove_cmp_fp
 * @requires os.arch == "riscv64"
 * @requires vm.debug
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestFPComparison2
 */
public class TestFPComparison2 {
    static final double[] DOUBLES = new double[] {
        Double.NEGATIVE_INFINITY,
        -Double.MAX_VALUE,
        -1.0,
        -Double.MIN_VALUE,
        -0.0,
        0.0,
        Double.MIN_VALUE,
        1.0,
        Double.MAX_VALUE,
        Double.POSITIVE_INFINITY,
        Double.NaN,
    };

    static final float[] FLOATS = new float[] {
        Float.NEGATIVE_INFINITY,
        -Float.MAX_VALUE,
        -1.0F,
        -Float.MIN_VALUE,
        -0.0F,
        0.0F,
        Float.MIN_VALUE,
        1.0F,
        Float.MAX_VALUE,
        Float.POSITIVE_INFINITY,
        Float.NaN,
    };

    static final int[] INTS = new int[] {
        Integer.MIN_VALUE,
        -100,
        -1,
        0,
        1,
        100,
        Integer.MAX_VALUE,
    };

    public static void main(String[] args) {
        List<String> options = List.of("-XX:-TieredCompilation", "-Xlog:jit+compilation=trace");
        // Booltest::ge
        TestFramework
        framework = new TestFramework(Test_ge_1.class);
        framework.addFlags(options.toArray(new String[0])).start();
        framework = new TestFramework(Test_ge_cmove_fp_1.class);
        framework.addFlags(options.toArray(new String[0])).start();

        framework = new TestFramework(Test_ge_2.class);
        framework.addFlags(options.toArray(new String[0])).start();
        framework = new TestFramework(Test_ge_cmove_fp_2.class);
        framework.addFlags(options.toArray(new String[0])).start();

        // Booltest::gt
        framework = new TestFramework(Test_gt_1.class);
        framework.addFlags(options.toArray(new String[0])).start();
        framework = new TestFramework(Test_gt_cmove_fp_1.class);
        framework.addFlags(options.toArray(new String[0])).start();

        framework = new TestFramework(Test_gt_2.class);
        framework.addFlags(options.toArray(new String[0])).start();
        framework = new TestFramework(Test_gt_cmove_fp_2.class);
        framework.addFlags(options.toArray(new String[0])).start();

        // BoolTest::ge/gt in C2_MacroAssembler::enc_cmove_fp_cmp_fp
        framework = new TestFramework(Test_cmov_fp_cmp_fp_ge_3.class);
        framework.addFlags(options.toArray(new String[0])).start();
        framework = new TestFramework(Test_cmov_fp_cmp_fp_ge_4.class);
        framework.addFlags(options.toArray(new String[0])).start();
    }
}

class Test_ge_1 {
    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x > y
        // return 0
        //      when neither is NaN, and x <= y
        return !(x <= y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_fixed_1_0(float x, float y) {
        return !(x <= y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x > y
        // return 0
        //      when neither is NaN, and x <= y
        return !(x <= y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_fixed_1_0(double x, double y) {
        return !(x <= y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x <= y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x <= y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x <= y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x <= y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x <= y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x <= y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x <= y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x <= y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_variable_results(float x, float y, int a, int b) {
        return !(x <= y) ? a : b;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_variable_results(float x, float y, int a, int b) {
        return !(x <= y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_variable_results(double x, double y, int a, int b) {
        return !(x <= y) ? a : b;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_variable_results(double x, double y, int a, int b) {
        return !(x <= y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_ge_fixed_1_0", "test_double_BoolTest_ge_fixed_1_0",
                 "test_float_BoolTest_ge_fixed_0_1", "test_double_BoolTest_ge_fixed_0_1",
                 "test_float_BoolTest_ge_fixed_10_20", "test_double_BoolTest_ge_fixed_10_20",
                 "test_float_BoolTest_ge_variable_results", "test_double_BoolTest_ge_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_ge_fixed_1_0(x, y);
                int expected = golden_float_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 1, 0), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_ge_fixed_1_0(x, y);
                int expected = golden_double_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 1, 0), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_ge_fixed_0_1(x, y);
                int expected = golden_float_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_ge_fixed_0_1(x, y);
                int expected = golden_double_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_ge_fixed_10_20(x, y);
                int expected = golden_float_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_ge_fixed_10_20(x, y);
                int expected = golden_double_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_float_BoolTest_ge_variable_results(x, y, a, b);
                        int expected = golden_float_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Float failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_double_BoolTest_ge_variable_results(x, y, a, b);
                        int expected = golden_double_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Double failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}


class Test_ge_cmove_fp_1 {
    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x > y
        // return 0
        //      when neither is NaN, and x <= y
        return !(x <= y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_fixed_1_0(float x, float y) {
        return !(x <= y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x > y
        // return 0
        //      when neither is NaN, and x <= y
        return !(x <= y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_fixed_1_0(double x, double y) {
        return !(x <= y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x <= y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x <= y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x <= y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x <= y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x <= y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x <= y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x <= y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x <= y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_variable_results(float x, float y, float a, float b) {
        return !(x <= y) ? a : b;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_variable_results(float x, float y, float a, float b) {
        return !(x <= y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_variable_results(double x, double y, float a, float b) {
        return !(x <= y) ? a : b;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_variable_results(double x, double y, float a, float b) {
        return !(x <= y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_ge_fixed_1_0", "test_double_BoolTest_ge_fixed_1_0",
                 "test_float_BoolTest_ge_fixed_0_1", "test_double_BoolTest_ge_fixed_0_1",
                 "test_float_BoolTest_ge_fixed_10_20", "test_double_BoolTest_ge_fixed_10_20",
                 "test_float_BoolTest_ge_variable_results", "test_double_BoolTest_ge_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_ge_fixed_1_0(x, y);
                float expected = golden_float_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 1, 0), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_ge_fixed_1_0(x, y);
                float expected = golden_double_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 1, 0), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_ge_fixed_0_1(x, y);
                float expected = golden_float_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_ge_fixed_0_1(x, y);
                float expected = golden_double_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_ge_fixed_10_20(x, y);
                float expected = golden_float_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_ge_fixed_10_20(x, y);
                float expected = golden_double_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_float_BoolTest_ge_variable_results(x, y, a, b);
                        float expected = golden_float_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Float failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_double_BoolTest_ge_variable_results(x, y, a, b);
                        float expected = golden_double_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Double failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_ge_2 {
    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x < y
        // return 0
        //      when neither is NaN, and x >= y
        return !(x >= y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_fixed_1_0(float x, float y) {
        return !(x >= y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x < y
        // return 0
        //      when neither is NaN, and x >= y
        return !(x >= y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_fixed_1_0(double x, double y) {
        return !(x >= y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x >= y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x >= y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x >= y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x >= y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x >= y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x >= y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x >= y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x >= y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_ge_variable_results(float x, float y, int a, int b) {
        return !(x >= y) ? a : b;
    }
    @DontCompile
    public static int golden_float_BoolTest_ge_variable_results(float x, float y, int a, int b) {
        return !(x >= y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_ge_variable_results(double x, double y, int a, int b) {
        return !(x >= y) ? a : b;
    }
    @DontCompile
    public static int golden_double_BoolTest_ge_variable_results(double x, double y, int a, int b) {
        return !(x >= y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_ge_fixed_1_0", "test_double_BoolTest_ge_fixed_1_0",
                 "test_float_BoolTest_ge_fixed_0_1", "test_double_BoolTest_ge_fixed_0_1",
                 "test_float_BoolTest_ge_fixed_10_20", "test_double_BoolTest_ge_fixed_10_20",
                 "test_float_BoolTest_ge_variable_results", "test_double_BoolTest_ge_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_ge_fixed_1_0(x, y);
                int expected = golden_float_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_ge_fixed_1_0(x, y);
                int expected = golden_double_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_ge_fixed_0_1(x, y);
                int expected = golden_float_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_ge_fixed_0_1(x, y);
                int expected = golden_double_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_ge_fixed_10_20(x, y);
                int expected = golden_float_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_ge_fixed_10_20(x, y);
                int expected = golden_double_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_float_BoolTest_ge_variable_results(x, y, a, b);
                        int expected = golden_float_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Float failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_double_BoolTest_ge_variable_results(x, y, a, b);
                        int expected = golden_double_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Double failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_ge_cmove_fp_2 {
    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x < y
        // return 0
        //      when neither is NaN, and x >= y
        return !(x >= y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_fixed_1_0(float x, float y) {
        return !(x >= y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x < y
        // return 0
        //      when neither is NaN, and x >= y
        return !(x >= y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_fixed_1_0(double x, double y) {
        return !(x >= y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x >= y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_fixed_0_1(float x, float y) {
        return !(x >= y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x >= y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_fixed_0_1(double x, double y) {
        return !(x >= y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x >= y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_fixed_10_20(float x, float y) {
        return !(x >= y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x >= y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_fixed_10_20(double x, double y) {
        return !(x >= y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_variable_results(float x, float y, float a, float b) {
        return !(x >= y) ? a : b;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_variable_results(float x, float y, float a, float b) {
        return !(x >= y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_ge_variable_results(double x, double y, float a, float b) {
        return !(x >= y) ? a : b;
    }
    @DontCompile
    public static float golden_double_BoolTest_ge_variable_results(double x, double y, float a, float b) {
        return !(x >= y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_ge_fixed_1_0", "test_double_BoolTest_ge_fixed_1_0",
                 "test_float_BoolTest_ge_fixed_0_1", "test_double_BoolTest_ge_fixed_0_1",
                 "test_float_BoolTest_ge_fixed_10_20", "test_double_BoolTest_ge_fixed_10_20",
                 "test_float_BoolTest_ge_variable_results", "test_double_BoolTest_ge_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_ge_fixed_1_0(x, y);
                float expected = golden_float_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_ge_fixed_1_0(x, y);
                float expected = golden_double_BoolTest_ge_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_ge_fixed_0_1(x, y);
                float expected = golden_float_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_ge_fixed_0_1(x, y);
                float expected = golden_double_BoolTest_ge_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_ge_fixed_10_20(x, y);
                float expected = golden_float_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_ge_fixed_10_20(x, y);
                float expected = golden_double_BoolTest_ge_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (ge, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_float_BoolTest_ge_variable_results(x, y, a, b);
                        float expected = golden_float_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Float failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_double_BoolTest_ge_variable_results(x, y, a, b);
                        float expected = golden_double_BoolTest_ge_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Double failed (ge), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_gt_1 {
    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x >= y
        // return 0
        //      when neither is NaN, and x < y
        return !(x < y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_fixed_1_0(float x, float y) {
        return !(x < y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x >= y
        // return 0
        //      when neither is NaN, and x < y
        return !(x < y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_fixed_1_0(double x, double y) {
        return !(x < y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x < y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x < y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x < y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x < y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x < y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x < y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x < y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x < y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_variable_results(float x, float y, int a, int b) {
        return !(x < y) ? a : b;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_variable_results(float x, float y, int a, int b) {
        return !(x < y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_variable_results(double x, double y, int a, int b) {
        return !(x < y) ? a : b;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_variable_results(double x, double y, int a, int b) {
        return !(x < y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_gt_fixed_1_0", "test_double_BoolTest_gt_fixed_1_0",
                 "test_float_BoolTest_gt_fixed_0_1", "test_double_BoolTest_gt_fixed_0_1",
                 "test_float_BoolTest_gt_fixed_10_20", "test_double_BoolTest_gt_fixed_10_20",
                 "test_float_BoolTest_gt_variable_results", "test_double_BoolTest_gt_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_gt_fixed_1_0(x, y);
                int expected = golden_float_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_gt_fixed_1_0(x, y);
                int expected = golden_double_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_gt_fixed_0_1(x, y);
                int expected = golden_float_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_gt_fixed_0_1(x, y);
                int expected = golden_double_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_gt_fixed_10_20(x, y);
                int expected = golden_float_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_gt_fixed_10_20(x, y);
                int expected = golden_double_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_float_BoolTest_gt_variable_results(x, y, a, b);
                        int expected = golden_float_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Float failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_double_BoolTest_gt_variable_results(x, y, a, b);
                        int expected = golden_double_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Double failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_gt_cmove_fp_1 {
    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x >= y
        // return 0
        //      when neither is NaN, and x < y
        return !(x < y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_fixed_1_0(float x, float y) {
        return !(x < y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x >= y
        // return 0
        //      when neither is NaN, and x < y
        return !(x < y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_fixed_1_0(double x, double y) {
        return !(x < y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x < y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x < y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x < y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x < y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x < y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x < y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x < y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x < y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_variable_results(float x, float y, float a, float b) {
        return !(x < y) ? a : b;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_variable_results(float x, float y, float a, float b) {
        return !(x < y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_variable_results(double x, double y, float a, float b) {
        return !(x < y) ? a : b;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_variable_results(double x, double y, float a, float b) {
        return !(x < y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_gt_fixed_1_0", "test_double_BoolTest_gt_fixed_1_0",
                 "test_float_BoolTest_gt_fixed_0_1", "test_double_BoolTest_gt_fixed_0_1",
                 "test_float_BoolTest_gt_fixed_10_20", "test_double_BoolTest_gt_fixed_10_20",
                 "test_float_BoolTest_gt_variable_results", "test_double_BoolTest_gt_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_gt_fixed_1_0(x, y);
                float expected = golden_float_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_gt_fixed_1_0(x, y);
                float expected = golden_double_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_gt_fixed_0_1(x, y);
                float expected = golden_float_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_gt_fixed_0_1(x, y);
                float expected = golden_double_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_gt_fixed_10_20(x, y);
                float expected = golden_float_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_gt_fixed_10_20(x, y);
                float expected = golden_double_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_float_BoolTest_gt_variable_results(x, y, a, b);
                        float expected = golden_float_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Float failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_double_BoolTest_gt_variable_results(x, y, a, b);
                        float expected = golden_double_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Double failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_gt_2 {
    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x <= y
        // return 0
        //      when neither is NaN, and x > y
        return !(x > y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_fixed_1_0(float x, float y) {
        return !(x > y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x <= y
        // return 0
        //      when neither is NaN, and x > y
        return !(x > y) ? 1 : 0;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_fixed_1_0(double x, double y) {
        return !(x > y) ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x > y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x > y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x > y) ? 0 : 1;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x > y) ? 0 : 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x > y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x > y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x > y) ? 10 : 20;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x > y) ? 10 : 20;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_float_BoolTest_gt_variable_results(float x, float y, int a, int b) {
        return !(x > y) ? a : b;
    }
    @DontCompile
    public static int golden_float_BoolTest_gt_variable_results(float x, float y, int a, int b) {
        return !(x > y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public static int test_double_BoolTest_gt_variable_results(double x, double y, int a, int b) {
        return !(x > y) ? a : b;
    }
    @DontCompile
    public static int golden_double_BoolTest_gt_variable_results(double x, double y, int a, int b) {
        return !(x > y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_gt_fixed_1_0", "test_double_BoolTest_gt_fixed_1_0",
                 "test_float_BoolTest_gt_fixed_0_1", "test_double_BoolTest_gt_fixed_0_1",
                 "test_float_BoolTest_gt_fixed_10_20", "test_double_BoolTest_gt_fixed_10_20",
                 "test_float_BoolTest_gt_variable_results", "test_double_BoolTest_gt_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_gt_fixed_1_0(x, y);
                int expected = golden_float_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_gt_fixed_1_0(x, y);
                int expected = golden_double_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_gt_fixed_0_1(x, y);
                int expected = golden_float_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_gt_fixed_0_1(x, y);
                int expected = golden_double_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                int actual = test_float_BoolTest_gt_fixed_10_20(x, y);
                int expected = golden_float_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                int actual = test_double_BoolTest_gt_fixed_10_20(x, y);
                int expected = golden_double_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_float_BoolTest_gt_variable_results(x, y, a, b);
                        int expected = golden_float_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Float failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.INTS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.INTS.length; n++) {
                        int a = TestFPComparison2.INTS[m];
                        int b = TestFPComparison2.INTS[n];
                        int actual = test_double_BoolTest_gt_variable_results(x, y, a, b);
                        int expected = golden_double_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected) {
                            System.out.println("Double failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_gt_cmove_fp_2 {
    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_fixed_1_0(float x, float y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x <= y
        // return 0
        //      when neither is NaN, and x > y
        return !(x > y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_fixed_1_0(float x, float y) {
        return !(x > y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_fixed_1_0(double x, double y) {
        // return 1
        //      when either x or y is NaN
        //      when neither is NaN, and x <= y
        // return 0
        //      when neither is NaN, and x > y
        return !(x > y) ? 1.0f : 0.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_fixed_1_0(double x, double y) {
        return !(x > y) ? 1.0f : 0.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x > y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_fixed_0_1(float x, float y) {
        return !(x > y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x > y) ? 0.0f : 1.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_fixed_0_1(double x, double y) {
        return !(x > y) ? 0.0f : 1.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x > y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_fixed_10_20(float x, float y) {
        return !(x > y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x > y) ? 10.0f : 20.0f;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_fixed_10_20(double x, double y) {
        return !(x > y) ? 10.0f : 20.0f;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_variable_results(float x, float y, float a, float b) {
        return !(x > y) ? a : b;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_variable_results(float x, float y, float a, float b) {
        return !(x > y) ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_double_BoolTest_gt_variable_results(double x, double y, float a, float b) {
        return !(x > y) ? a : b;
    }
    @DontCompile
    public static float golden_double_BoolTest_gt_variable_results(double x, double y, float a, float b) {
        return !(x > y) ? a : b;
    }

    @Run(test = {"test_float_BoolTest_gt_fixed_1_0", "test_double_BoolTest_gt_fixed_1_0",
                 "test_float_BoolTest_gt_fixed_0_1", "test_double_BoolTest_gt_fixed_0_1",
                 "test_float_BoolTest_gt_fixed_10_20", "test_double_BoolTest_gt_fixed_10_20",
                 "test_float_BoolTest_gt_variable_results", "test_double_BoolTest_gt_variable_results"})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_gt_fixed_1_0(x, y);
                float expected = golden_float_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_gt_fixed_1_0(x, y);
                float expected = golden_double_BoolTest_gt_fixed_1_0(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_gt_fixed_0_1(x, y);
                float expected = golden_float_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_gt_fixed_0_1(x, y);
                float expected = golden_double_BoolTest_gt_fixed_0_1(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 0, 1), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                float actual = test_float_BoolTest_gt_fixed_10_20(x, y);
                float expected = golden_float_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Float failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                float actual = test_double_BoolTest_gt_fixed_10_20(x, y);
                float expected = golden_double_BoolTest_gt_fixed_10_20(x, y);
                if (actual != expected) {
                    System.out.println("Double failed (gt, 10, 20), x: " + x + ", y: " + y +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float y = TestFPComparison2.FLOATS[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_float_BoolTest_gt_variable_results(x, y, a, b);
                        float expected = golden_float_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Float failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.DOUBLES.length; i++) {
            for (int j = 0; j < TestFPComparison2.DOUBLES.length; j++) {
                double x = TestFPComparison2.DOUBLES[i];
                double y = TestFPComparison2.DOUBLES[j];
                for (int m = 0; m < TestFPComparison2.FLOATS.length; m++) {
                    for (int n = 0; n < TestFPComparison2.FLOATS.length; n++) {
                        float a = TestFPComparison2.FLOATS[m];
                        float b = TestFPComparison2.FLOATS[n];
                        float actual = test_double_BoolTest_gt_variable_results(x, y, a, b);
                        float expected = golden_double_BoolTest_gt_variable_results(x, y, a, b);
                        if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                            System.out.println("Double failed (gt), x: " + x + ", y: " + y + ", a: " + a + ", b: " + b +
                                               ", actual: " + actual + ", expected: " + expected);
                            err++;
                        }
                    }
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_cmov_fp_cmp_fp_ge_3 {
    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_x_lt_0(float x) {
        return x < 0 ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_x_lt_0(float x) {
        return x < 0 ? 0 : x;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_x_gt_0(float x) {
        return x > 0 ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_x_gt_0(float x) {
        return x > 0 ? 0 : x;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_neg_x_lt_0(float x) {
        return !(x < 0) ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_neg_x_lt_0(float x) {
        return !(x < 0) ? 0 : x;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_gt_neg_x_gt_0(float x) {
        return !(x > 0) ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_gt_neg_x_gt_0(float x) {
        return !(x > 0) ? 0 : x;
    }

    @Run(test = {"test_float_BoolTest_gt_x_lt_0", "test_float_BoolTest_gt_x_gt_0",
                 "test_float_BoolTest_gt_neg_x_lt_0", "test_float_BoolTest_gt_neg_x_gt_0",})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_gt_x_lt_0(x);
                float expected = golden_float_BoolTest_gt_x_lt_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (lt, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_gt_x_gt_0(x);
                float expected = golden_float_BoolTest_gt_x_gt_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (gt, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_gt_neg_x_lt_0(x);
                float expected = golden_float_BoolTest_gt_neg_x_lt_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (neg lt, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_gt_neg_x_gt_0(x);
                float expected = golden_float_BoolTest_gt_neg_x_gt_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (neg gt, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}

class Test_cmov_fp_cmp_fp_ge_4 {
    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_x_le_0(float x) {
        return x <= 0 ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_x_le_0(float x) {
        return x <= 0 ? 0 : x;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_x_ge_0(float x) {
        return x >= 0 ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_x_ge_0(float x) {
        return x >= 0 ? 0 : x;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_neg_x_le_0(float x) {
        return !(x <= 0) ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_neg_x_le_0(float x) {
        return !(x <= 0) ? 0 : x;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_F, "1"})
    public static float test_float_BoolTest_ge_neg_x_ge_0(float x) {
        return !(x >= 0) ? 0 : x;
    }
    @DontCompile
    public static float golden_float_BoolTest_ge_neg_x_ge_0(float x) {
        return !(x >= 0) ? 0 : x;
    }

    @Run(test = {"test_float_BoolTest_ge_x_le_0", "test_float_BoolTest_ge_x_ge_0",
                 "test_float_BoolTest_ge_neg_x_le_0", "test_float_BoolTest_ge_neg_x_ge_0",})
    public void runTests() {
        int err = 0;

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_ge_x_le_0(x);
                float expected = golden_float_BoolTest_ge_x_le_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (le, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_ge_x_ge_0(x);
                float expected = golden_float_BoolTest_ge_x_ge_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (ge, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_ge_neg_x_le_0(x);
                float expected = golden_float_BoolTest_ge_neg_x_le_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (neg le, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        for (int i = 0; i < TestFPComparison2.FLOATS.length; i++) {
            for (int j = 0; j < TestFPComparison2.FLOATS.length; j++) {
                float x = TestFPComparison2.FLOATS[i];
                float actual = test_float_BoolTest_ge_neg_x_ge_0(x);
                float expected = golden_float_BoolTest_ge_neg_x_ge_0(x);
                if (actual != expected && (!Float.isNaN(actual) || !Float.isNaN(expected))) {
                    System.out.println("Float failed (neg ge, x, 0), x: " + x +
                                        ", actual: " + actual + ", expected: " + expected);
                    err++;
                }
            }
        }

        if (err != 0) {
            throw new RuntimeException("Some tests failed");
        }
    }
}
