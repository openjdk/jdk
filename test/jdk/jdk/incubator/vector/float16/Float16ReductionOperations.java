/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308363
 * @summary Test Float16 reduction operations.
 * @modules jdk.incubator.vector
 * @compile Float16ReductionOperations.java
 * @run main/othervm -XX:-TieredCompilation -Xbatch Float16ReductionOperations
 */

import java.util.Random;

import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;

public class Float16ReductionOperations {

    public static Random r = new Random(1024);

    public static short test_reduction_add_constants() {
        Float16 hf0 = shortBitsToFloat16((short)0);
        Float16 hf1 = shortBitsToFloat16((short)15360);
        Float16 hf2 = shortBitsToFloat16((short)16384);
        Float16 hf3 = shortBitsToFloat16((short)16896);
        Float16 hf4 = shortBitsToFloat16((short)17408);
        return float16ToRawShortBits(add(add(add(add(hf0, hf1), hf2), hf3), hf4));
    }

    public static short expected_reduction_add_constants() {
        Float16 hf0 = shortBitsToFloat16((short)0);
        Float16 hf1 = shortBitsToFloat16((short)15360);
        Float16 hf2 = shortBitsToFloat16((short)16384);
        Float16 hf3 = shortBitsToFloat16((short)16896);
        Float16 hf4 = shortBitsToFloat16((short)17408);
        return Float.floatToFloat16(Float.float16ToFloat(float16ToRawShortBits(hf0)) +
                                    Float.float16ToFloat(float16ToRawShortBits(hf1)) +
                                    Float.float16ToFloat(float16ToRawShortBits(hf2)) +
                                    Float.float16ToFloat(float16ToRawShortBits(hf3)) +
                                    Float.float16ToFloat(float16ToRawShortBits(hf4)));
    }

    public static boolean compare(short actual, short expected) {
        return !((0xFFFF & actual) == (0xFFFF & expected));
    }

    public static void test_reduction_constants(char oper) {
        short actual = 0;
        short expected = 0;
        switch(oper) {
            case '+' ->  {
                             actual = test_reduction_add_constants();
                             expected = expected_reduction_add_constants();
                         }
            default  ->  throw new AssertionError("Unsupported Operation.");
        }
        if (compare(actual,expected)) {
            throw new AssertionError("Result mismatch!, expected = " + expected + " actual = " + actual);
        }
    }

    public static short test_reduction_add(short [] arr) {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < arr.length; i++) {
            res = add(res, shortBitsToFloat16(arr[i]));
        }
        return float16ToRawShortBits(res);
    }

    public static short expected_reduction_add(short [] arr) {
        short res = 0;
        for (int i = 0; i < arr.length; i++) {
            res = Float.floatToFloat16(Float.float16ToFloat(res) + Float.float16ToFloat(arr[i]));
        }
        return res;
    }

    public static void test_reduction(char oper, short [] arr) {
        short actual = 0;
        short expected = 0;
        switch(oper) {
            case '+' ->  {
                             actual = test_reduction_add(arr);
                             expected = expected_reduction_add(arr);
                         }
            default  ->  throw new AssertionError("Unsupported Operation.");
        }
        if (compare(actual,expected)) {
            throw new AssertionError("Result mismatch!, expected = " + expected + " actual = " + actual);
        }
    }

    public static short [] get_float16_array(int size) {
        short [] arr = new short[size];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = Float.floatToFloat16(r.nextFloat());
        }
        return arr;
    }

    public static void main(String [] args) {
        int res = 0;
        short [] input = get_float16_array(1024);
        short [] special_values = {
              32256,          // NAN
              31744,          // +Inf
              (short)-1024,   // -Inf
              0,              // +0.0
              (short)-32768,  // -0.0
        };
        for (int i = 0;  i < 1000; i++) {
            test_reduction('+', input);
            test_reduction('+', special_values);
            test_reduction_constants('+');
        }
        System.out.println("PASS");
    }
}
