/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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
 * @bug 8308363 8336406 8339473
 * @summary Verify FP16 unary, binary and ternary operations
 * @compile FP16ScalarOperations.java
 * @run main/othervm --enable-preview -XX:-TieredCompilation -Xbatch FP16ScalarOperations
 */

import java.util.Random;
import java.util.stream.IntStream;
import static java.lang.Float16.*;

public class FP16ScalarOperations {

    public static final int SIZE = 65504;
    public static Random r = new Random(SIZE);
    public static final Float16 ONE = Float16.valueOf(1.0);
    public static final Float16 ZERO = Float16.valueOf(0.0);
    public static final int EXP = 0x7c00; // Mask for Float16 Exponent in a NaN (which is all ones)
    public static final int SIGN_BIT = 0x8000; // Mask for sign bit for Float16

    public static Float16 actual_value(String oper, Float16... val) {
        switch (oper) {
            case "abs"        : return Float16.abs(val[0]);
            case "neg"        : return Float16.negate(val[0]);
            case "sqrt"       : return Float16.sqrt(val[0]);
            case "isInfinite" : return Float16.isInfinite(val[0]) ? ONE : ZERO;
            case "isFinite"   : return Float16.isFinite(val[0]) ? ONE : ZERO;
            case "isNaN"      : return Float16.isNaN(val[0]) ? ONE : ZERO;
            case "+"          : return Float16.add(val[0], val[1]);
            case "-"          : return Float16.subtract(val[0], val[1]);
            case "*"          : return Float16.multiply(val[0], val[1]);
            case "/"          : return Float16.divide(val[0], val[1]);
            case "min"        : return Float16.min(val[0], val[1]);
            case "max"        : return Float16.max(val[0], val[1]);
            case "fma"        : return Float16.fma(val[0], val[1], val[2]);
            default           : throw new AssertionError("Unsupported Operation!");
        }
    }

    public static Float16 expected_value(String oper, Float16... val) {
        switch (oper) {
            case "abs"        : return Float16.valueOf(Math.abs(val[0].floatValue()));
            case "neg"        : return Float16.shortBitsToFloat16((short)(Float16.float16ToRawShortBits(val[0]) ^ (short)0x0000_8000));
            case "sqrt"       : return Float16.valueOf(Math.sqrt(val[0].floatValue()));
            case "isInfinite" : return Float.isInfinite(val[0].floatValue()) ? ONE : ZERO;
            case "isFinite"   : return Float.isFinite(val[0].floatValue()) ? ONE : ZERO;
            case "isNaN"      : return Float.isNaN(val[0].floatValue()) ? ONE : ZERO;
            case "+"          : return Float16.valueOf(val[0].floatValue() + val[1].floatValue());
            case "-"          : return Float16.valueOf(val[0].floatValue() - val[1].floatValue());
            case "*"          : return Float16.valueOf(val[0].floatValue() * val[1].floatValue());
            case "/"          : return Float16.valueOf(val[0].floatValue() / val[1].floatValue());
            case "min"        : return Float16.valueOf(Float.min(val[0].floatValue(), val[1].floatValue()));
            case "max"        : return Float16.valueOf(Float.max(val[0].floatValue(), val[1].floatValue()));
            case "fma"        : return Float16.valueOf(val[0].floatValue() * val[1].floatValue() + val[2].floatValue());
            default           : throw new AssertionError("Unsupported Operation!");
        }
    }

    public static void validate(String oper, Float16... input) {
        int arity = input.length;

        Float16 actual = actual_value(oper, input);
        Float16 expected = expected_value(oper, input);

        if (!actual.equals(expected)) {
            switch (arity) {
                case 1:
                    throw new AssertionError("Test Failed: " + oper + "(" + Float16.float16ToRawShortBits(input[0]) + ") : " +  actual + " != " + expected);
                case 2:
                    throw new AssertionError("Test Failed: " + oper + "(" + Float16.float16ToRawShortBits(input[0]) + ", " + Float16.float16ToRawShortBits(input[1]) + ") : " + actual + " != " + expected);
                case 3:
                    throw new AssertionError("Test failed: " + oper + "(" + Float16.float16ToRawShortBits(input[0]) + ", " + Float16.float16ToRawShortBits(input[1]) + ", " + Float16.float16ToRawShortBits(input[2]) + ") : " + actual + " != " + expected);
                default:
                    throw new AssertionError("Incorrect operation (" + oper + ")  arity = " + arity);
            }
        }
    }

    public static void test_unary_operations(Float16 [] inp) {
        for (int i = 0; i < inp.length; i++) {
            validate("abs", inp[i]);
            validate("neg", inp[i]);
            validate("sqrt", inp[i]);
            validate("isInfinite", inp[i]);
            validate("isFinite", inp[i]);
            validate("isNaN", inp[i]);
        }
    }

    public static void test_binary_operations(Float16 [] inp1, Float16 inp2[]) {
        for (int i = 0; i < inp1.length; i++) {
            validate("+", inp1[i], inp2[i]);
            validate("-", inp1[i], inp2[i]);
            validate("*", inp1[i], inp2[i]);
            validate("/", inp1[i], inp2[i]);
        }
    }

    public static void test_ternary_operations(Float16 [] inp1, Float16 inp2[], Float16 inp3[]) {
        for (int i = 0; i < inp1.length; i++) {
            validate("fma", inp1[i], inp2[i], inp3[i]);
        }
    }

    public static void test_fin_inf_nan() {
        Float16 pos_nan, neg_nan;
        // Starting from 1 as the significand in a NaN value is always non-zero
        for (int i = 1; i < 0x03ff; i++) {
            pos_nan = Float16.shortBitsToFloat16((short)(EXP | i));
            neg_nan = Float16.shortBitsToFloat16((short)(Float16.float16ToRawShortBits(pos_nan) | SIGN_BIT));

            // Test isFinite, isInfinite, isNaN for all positive NaN values
            validate("isInfinite", pos_nan);
            validate("isFinite", pos_nan);
            validate("isNaN", pos_nan);

           // Test isFinite, isinfinite, isNaN for all negative NaN values
            validate("isInfinite", neg_nan);
            validate("isFinite", neg_nan);
            validate("isNaN", neg_nan);
        }
    }

    public static void main(String [] args) {
        Float16 [] input1 = new Float16[SIZE];
        Float16 [] input2 = new Float16[SIZE];
        Float16 [] input3 = new Float16[SIZE];

        // input1, input2, input3 contain the entire value range for FP16
        IntStream.range(0, input1.length).forEach(i -> {input1[i] = Float16.valueOf((float)i);});
        IntStream.range(0, input2.length).forEach(i -> {input2[i] = Float16.valueOf((float)i);});
        IntStream.range(0, input2.length).forEach(i -> {input3[i] = Float16.valueOf((float)i);});

        Float16 [] special_values = {
              Float16.NaN,                 // NAN
              Float16.POSITIVE_INFINITY,   // +Inf
              Float16.NEGATIVE_INFINITY,   // -Inf
              Float16.valueOf(0.0),        // +0.0
              Float16.valueOf(-0.0),       // -0.0
        };

        for (int i = 0;  i < 1000; i++) {
            test_unary_operations(input1);
            test_binary_operations(input1, input2);
            test_ternary_operations(input1, input2, input3);

            test_unary_operations(special_values);
            test_binary_operations(special_values, input1);
            test_ternary_operations(special_values, input1, input2);

            // The above functions test isFinite, isInfinite and isNaN for all possible finite FP16 values
            // and infinite values. The below method tests these functions for all possible NaN values as well.
            test_fin_inf_nan();
        }
        System.out.println("PASS");
    }
}
