/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8342103
 * @summary C2 compiler support for Float16 type and associated operations
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @compile ScalarFloat16OperationsTest.java
 * @run testng/othervm/timeout=300 -ea -esa -Xbatch -XX:-TieredCompilation -XX:-UseSuperWord ScalarFloat16OperationsTest
 * @run testng/othervm/timeout=300 -ea -esa -Xbatch -XX:-TieredCompilation -XX:+UseSuperWord ScalarFloat16OperationsTest
 */

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Random;
import java.util.stream.IntStream;
import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;

public class ScalarFloat16OperationsTest {
    static final int SIZE = 65504;
    static Random r = jdk.test.lib.Utils.getRandomInstance();
    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);

    @DataProvider
    public static Object[][] unaryOpProvider() {
        Float16 [] input = new Float16[SIZE];
        Float16 [] special_input = {
            Float16.MAX_VALUE, Float16.MIN_VALUE, Float16.MIN_NORMAL, Float16.POSITIVE_INFINITY,
            Float16.NEGATIVE_INFINITY, Float16.valueOf(0.0f), Float16.valueOf(-0.0f), Float16.NaN
        };

        // Input array covers entire Float16 value range
        IntStream.range(0, input.length).forEach(i -> {input[i] = valueOf(i);});

        return new Object[][] {
            {input},
            {special_input}
        };
    }

    @DataProvider
    public static Object[][] binaryOpProvider() {
        Float16 [] input1 = new Float16[SIZE];
        Float16 [] input2 = new Float16[SIZE];
        Float16 [] special_input = {
            Float16.MAX_VALUE, Float16.MIN_VALUE, Float16.MIN_NORMAL, Float16.POSITIVE_INFINITY,
            Float16.NEGATIVE_INFINITY, Float16.valueOf(0.0f), Float16.valueOf(-0.0f), Float16.NaN
        };

        // Input arrays covers entire Float16 value range interspersed with special values.
        IntStream.range(0, input1.length).forEach(i -> {input1[i] = valueOf(i);});
        IntStream.range(0, input2.length).forEach(i -> {input2[i] = valueOf(i);});

        for (int i = 0; i < special_input.length; i += 256) {
            input1[r.nextInt(input1.length)] = special_input[i];
            input2[r.nextInt(input2.length)] = special_input[i];
        }

        return new Object[][] {
            {input1, input2},
            {special_input, special_input},
        };
    }

    @DataProvider
    public static Object[][] ternaryOpProvider() {
        Float16 [] input1 = new Float16[SIZE];
        Float16 [] input2 = new Float16[SIZE];
        Float16 [] input3 = new Float16[SIZE];
        Float16 [] special_input = {
            Float16.MAX_VALUE, Float16.MIN_VALUE, Float16.MIN_NORMAL, Float16.POSITIVE_INFINITY,
            Float16.NEGATIVE_INFINITY, Float16.valueOf(0.0f), Float16.valueOf(-0.0f), Float16.NaN
        };

        // Input arrays covers entire Float16 value range interspersed with special values.
        IntStream.range(0, input1.length).forEach(i -> {input1[i] = valueOf(i);});
        IntStream.range(0, input2.length).forEach(i -> {input2[i] = valueOf(i);});
        IntStream.range(0, input3.length).forEach(i -> {input3[i] = valueOf(i);});
        for (int i = 0; i < special_input.length; i += 256) {
            input1[r.nextInt(input1.length)] = special_input[i];
            input2[r.nextInt(input2.length)] = special_input[i];
            input3[r.nextInt(input3.length)] = special_input[i];
        }

        return new Object[][] {
            {input1, input2, input3},
            {special_input, special_input, special_input},
        };
    }

    interface FUnOp1 {
        Float16 apply(Float16 a);
    }

    interface FUnOp2 {
        boolean apply(Float16 a);
    }

    static void assertArraysEquals(Float16[] r, Float16[] a, FUnOp1 f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i]), "at index #" + i + ", input = " + a[i]);
        }
    }

    static void assertArraysEquals(boolean[] r, Float16[] a, FUnOp2 f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i]), "at index #" + i + ", input = " + a[i]);
        }
    }

    interface FBinOp {
        Float16 apply(Float16 a, Float16 b);
    }

    static void assertArraysEquals(Float16[] r, Float16[] a, Float16[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i]);
        }
    }

    interface FTernOp {
        Float16 apply(Float16 a, Float16 b, Float16 c);
    }

    static void assertArraysEquals(Float16[] r, Float16[] a, Float16[] b, Float16[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
        }
    }


    @Test(dataProvider = "unaryOpProvider")
    public static void absTest(Object input) {
        Float16 [] farr =  (Float16[])input;
        Float16 [] res  = new Float16[farr.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = abs(farr[i]);
            }
        }
        assertArraysEquals(res, farr, (fp16) -> valueOf(Math.abs(fp16.floatValue())));
    }

    @Test(dataProvider = "unaryOpProvider")
    public static void negTest(Object input) {
        Float16 [] farr =  (Float16[])input;
        Float16 [] res  = new Float16[farr.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = negate(farr[i]);
            }
        }
        assertArraysEquals(res, farr, (fp16) -> shortBitsToFloat16((short)(float16ToRawShortBits(fp16) ^ (short)0x0000_8000)));
    }

    @Test(dataProvider = "unaryOpProvider")
    public static void sqrtTest(Object input) {
        Float16 [] farr =  (Float16[])input;
        Float16 [] res  = new Float16[farr.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = sqrt(farr[i]);
            }
        }
        assertArraysEquals(res, farr, (fp16) -> valueOf(Math.sqrt(fp16.floatValue())));
    }

    @Test(dataProvider = "unaryOpProvider")
    public static void isInfiniteTest(Object input) {
        Float16 [] farr =  (Float16[])input;
        boolean [] res  = new boolean[farr.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = isInfinite(farr[i]);
            }
        }
        assertArraysEquals(res, farr, (fp16) -> Float.isInfinite(fp16.floatValue()));
    }

    @Test(dataProvider = "unaryOpProvider")
    public static void isFiniteTest(Object input) {
        Float16 [] farr =  (Float16[])input;
        boolean [] res  = new boolean[farr.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = isFinite(farr[i]);
            }
        }
        assertArraysEquals(res, farr, (fp16) -> Float.isFinite(fp16.floatValue()));
    }

    @Test(dataProvider = "unaryOpProvider")
    public static void isNaNTest(Object input) {
        Float16 [] farr =  (Float16[])input;
        boolean [] res  = new boolean[farr.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = isNaN(farr[i]);
            }
        }
        assertArraysEquals(res, farr, (fp16) -> Float.isNaN(fp16.floatValue()));
    }

    @Test(dataProvider = "binaryOpProvider")
    public static void addTest(Object input1, Object input2) {
        Float16 [] farr1 =  (Float16[])input1;
        Float16 [] farr2 =  (Float16[])input2;

        Float16 [] res  = new Float16[farr1.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = add(farr1[i], farr2[i]);
            }
        }
        assertArraysEquals(res, farr1, farr2, (fp16_val1, fp16_val2) -> valueOf(fp16_val1.floatValue() + fp16_val2.floatValue()));
    }

    @Test(dataProvider = "binaryOpProvider")
    public static void subtractTest(Object input1, Object input2) {
        Float16 [] farr1 =  (Float16[])input1;
        Float16 [] farr2 =  (Float16[])input2;

        Float16 [] res  = new Float16[farr1.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = subtract(farr1[i], farr2[i]);
            }
        }
        assertArraysEquals(res, farr1, farr2, (fp16_val1, fp16_val2) -> valueOf(fp16_val1.floatValue() - fp16_val2.floatValue()));
    }

    @Test(dataProvider = "binaryOpProvider")
    public static void multiplyTest(Object input1, Object input2) {
        Float16 [] farr1 =  (Float16[])input1;
        Float16 [] farr2 =  (Float16[])input2;

        Float16 [] res  = new Float16[farr1.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = multiply(farr1[i], farr2[i]);
            }
        }
        assertArraysEquals(res, farr1, farr2, (fp16_val1, fp16_val2) -> valueOf(fp16_val1.floatValue() * fp16_val2.floatValue()));
    }

    @Test(dataProvider = "binaryOpProvider")
    public static void divideTest(Object input1, Object input2) {
        Float16 [] farr1 =  (Float16[])input1;
        Float16 [] farr2 =  (Float16[])input2;

        Float16 [] res  = new Float16[farr1.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = divide(farr1[i], farr2[i]);
            }
        }
        assertArraysEquals(res, farr1, farr2, (fp16_val1, fp16_val2) -> valueOf(fp16_val1.floatValue() / fp16_val2.floatValue()));
    }

    @Test(dataProvider = "binaryOpProvider")
    public static void maxTest(Object input1, Object input2) {
        Float16 [] farr1 =  (Float16[])input1;
        Float16 [] farr2 =  (Float16[])input2;

        Float16 [] res  = new Float16[farr1.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = max(farr1[i], farr2[i]);
            }
        }
        assertArraysEquals(res, farr1, farr2,  (fp16_val1, fp16_val2) -> valueOf(Float.max(fp16_val1.floatValue(), fp16_val2.floatValue())));
    }

    @Test(dataProvider = "binaryOpProvider")
    public static void minTest(Object input1, Object input2) {
        Float16 [] farr1 =  (Float16[])input1;
        Float16 [] farr2 =  (Float16[])input2;

        Float16 [] res  = new Float16[farr1.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = min(farr1[i], farr2[i]);
            }
        }
        assertArraysEquals(res, farr1, farr2, (fp16_val1, fp16_val2) -> valueOf(Float.min(fp16_val1.floatValue(), fp16_val2.floatValue())));
    }

    @Test(dataProvider = "ternaryOpProvider")
    public static void fmaTest(Object input1, Object input2, Object input3) {
        Float16 [] farr1 =  (Float16[])input1;
        Float16 [] farr2 =  (Float16[])input2;
        Float16 [] farr3 =  (Float16[])input2;

        Float16 [] res  = new Float16[farr1.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < res.length; i++) {
                res[i] = fma(farr1[i], farr2[i], farr3[i]);
            }
        }
        assertArraysEquals(res, farr1, farr2, farr3, (fp16_val1, fp16_val2, fp16_val3) -> valueOf(Math.fma(fp16_val1.floatValue(), fp16_val2.floatValue(), fp16_val3.floatValue())));
    }
}
