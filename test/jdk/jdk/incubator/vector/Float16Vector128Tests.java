/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 *
 * @library /test/lib
 * @modules jdk.incubator.vector
 * @run testng/othervm/timeout=300 -ea -esa -Xbatch -XX:-TieredCompilation Float16Vector128Tests
 */

// -- This file was mechanically generated: Do not edit! -- //

import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.Vector;

import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;
import jdk.incubator.vector.Float16Vector;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.Integer;
import java.util.List;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Test
public class Float16Vector128Tests extends AbstractVectorTest {

    static final VectorSpecies<Float16> SPECIES =
                Float16Vector.SPECIES_128;

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);

    static Float16Vector bcast_vec = Float16Vector.broadcast(SPECIES, (short)10);

    static void AssertEquals(short actual, short expected) {
        Assert.assertEquals(Float.float16ToFloat((short)actual), Float.float16ToFloat((short)expected));
    }
    static void AssertEquals(short actual, short expected, String msg) {
        Assert.assertEquals(Float.float16ToFloat((short)actual), Float.float16ToFloat((short)expected), msg);
    }
    static void AssertEquals(short actual, short expected, short delta) {
        Assert.assertEquals(Float.float16ToFloat((short)actual), Float.float16ToFloat((short)expected), Float.float16ToFloat((short)delta));
    }
    static void AssertEquals(short actual, short expected, short delta, String msg) {
        Assert.assertEquals(Float.float16ToFloat((short)actual), Float.float16ToFloat((short)expected), Float.float16ToFloat((short)delta), msg);
    }
    static void AssertEquals(short [] actual, short [] expected) {
        Assert.assertEquals(actual.length, expected.length);
        for (int i = 0; i < actual.length; i++) {
            Assert.assertEquals(Float.float16ToFloat((short)actual[i]), Float.float16ToFloat((short)expected[i]));
        }
    }
    static void AssertEquals(short [] actual, short [] expected, String msg) {
        Assert.assertEquals(actual.length, expected.length);
        for (int i = 0; i < actual.length; i++) {
            Assert.assertEquals(Float.float16ToFloat((short)actual[i]), Float.float16ToFloat((short)expected[i]), msg);
        }
    }
    static void AssertEquals(long actual, long expected) {
        Assert.assertEquals(Float.float16ToFloat((short)actual), Float.float16ToFloat((short)expected));
    }
    static void AssertEquals(long actual, long expected, String msg) {
        Assert.assertEquals(Float.float16ToFloat((short)actual), Float.float16ToFloat((short)expected), msg);
    }
    static void AssertEquals(String actual, String expected) {
        Assert.assertEquals(actual, expected);
    }
    static void AssertEquals(Object actual, Object expected) {
        Assert.assertEquals(actual, expected);
    }
    static void AssertEquals(double actual, double expected) {
        Assert.assertEquals(actual, expected);
    }
    static void AssertEquals(double actual, double expected, String msg) {
        Assert.assertEquals(actual, expected, msg);
    }
    static void AssertEquals(boolean actual, boolean expected) {
        Assert.assertEquals(actual, expected);
    }
    static void AssertEquals(boolean actual, boolean expected, String msg) {
        Assert.assertEquals(actual, expected, msg);
    }


    // for floating point addition reduction ops that may introduce rounding errors
    private static final short RELATIVE_ROUNDING_ERROR_FACTOR_ADD = (short)10.0;

    // for floating point multiplication reduction ops that may introduce rounding errors
    private static final short RELATIVE_ROUNDING_ERROR_FACTOR_MUL = (short)50.0;

    static final int BUFFER_REPS = Integer.getInteger("jdk.incubator.vector.test.buffer-vectors", 25000 / 128);

    static void assertArraysStrictlyEquals(short[] r, short[] a) {
        for (int i = 0; i < a.length; i++) {
            short ir = r[i];
            short ia = a[i];
            if (ir != ia) {
                Assert.fail(String.format("at index #%d, expected = %016X, actual = %016X", i, ia, ir));
            }
        }
    }

    interface FUnOp {
        short apply(short a);
    }

    static void assertArraysEquals(short[] r, short[] a, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i]), "at index #" + i + ", input = " + a[i]);
        }
    }

    interface FUnArrayOp {
        short[] apply(short a);
    }

    static void assertArraysEquals(short[] r, short[] a, FUnArrayOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a[i]));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a[i]);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, boolean[] mask, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i]);
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], "at index #" + i + ", input = " + a[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    interface FReductionOp {
        short apply(short[] a, int idx);
    }

    interface FReductionAllOp {
        short apply(short[] a);
    }

    static void assertReductionArraysEquals(short[] r, short rc, short[] a,
                                            FReductionOp f, FReductionAllOp fa) {
        assertReductionArraysEquals(r, rc, a, f, fa, (short)0.0);
    }

    static void assertReductionArraysEquals(short[] r, short rc, short[] a,
                                            FReductionOp f, FReductionAllOp fa,
                                            short relativeErrorFactor) {
        int i = 0;
        try {
            AssertEquals(rc, fa.apply(a), float16ToShortBits(Float16.multiply(Float16.ulp(shortBitsToFloat16(rc)), shortBitsToFloat16(relativeErrorFactor))));
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(r[i], f.apply(a, i), float16ToShortBits(Float16.multiply(Float16.ulp(shortBitsToFloat16(r[i])), shortBitsToFloat16(relativeErrorFactor))));
            }
        } catch (AssertionError e) {
            AssertEquals(rc, fa.apply(a), float16ToShortBits(Float16.multiply(Float16.ulp(shortBitsToFloat16(rc)), shortBitsToFloat16(relativeErrorFactor))), "Final result is incorrect!");
            AssertEquals(r[i], f.apply(a, i), float16ToShortBits(Float16.multiply(Float16.ulp(shortBitsToFloat16(r[i])), shortBitsToFloat16(relativeErrorFactor))), "at index #" + i);
        }
    }

    interface FReductionMaskedOp {
        short apply(short[] a, int idx, boolean[] mask);
    }

    interface FReductionAllMaskedOp {
        short apply(short[] a, boolean[] mask);
    }

    static void assertReductionArraysEqualsMasked(short[] r, short rc, short[] a, boolean[] mask,
                                            FReductionMaskedOp f, FReductionAllMaskedOp fa) {
        assertReductionArraysEqualsMasked(r, rc, a, mask, f, fa, (short)0.0);
    }

    static void assertReductionArraysEqualsMasked(short[] r, short rc, short[] a, boolean[] mask,
                                            FReductionMaskedOp f, FReductionAllMaskedOp fa,
                                            short relativeError) {
        int i = 0;
        try {
            AssertEquals(rc, fa.apply(a, mask), float16ToShortBits(Float16.abs(Float16.multiply(shortBitsToFloat16(rc), shortBitsToFloat16(relativeError)))));
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(r[i], f.apply(a, i, mask), float16ToShortBits(Float16.abs(Float16.multiply(shortBitsToFloat16(r[i]), shortBitsToFloat16(relativeError)))));
            }
        } catch (AssertionError e) {
            AssertEquals(rc, fa.apply(a, mask), float16ToShortBits(Float16.abs(Float16.multiply(shortBitsToFloat16(rc), shortBitsToFloat16(relativeError)))), "Final result is incorrect!");
            AssertEquals(r[i], f.apply(a, i, mask), float16ToShortBits(Float16.abs(Float16.multiply(shortBitsToFloat16(r[i]), shortBitsToFloat16(relativeError)))), "at index #" + i);
        }
    }

    interface FReductionOpLong {
        long apply(short[] a, int idx);
    }

    interface FReductionAllOpLong {
        long apply(short[] a);
    }

    static void assertReductionLongArraysEquals(long[] r, long rc, short[] a,
                                            FReductionOpLong f, FReductionAllOpLong fa) {
        int i = 0;
        try {
            AssertEquals(rc, fa.apply(a));
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            AssertEquals(rc, fa.apply(a), "Final result is incorrect!");
            AssertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FReductionMaskedOpLong {
        long apply(short[] a, int idx, boolean[] mask);
    }

    interface FReductionAllMaskedOpLong {
        long apply(short[] a, boolean[] mask);
    }

    static void assertReductionLongArraysEqualsMasked(long[] r, long rc, short[] a, boolean[] mask,
                                            FReductionMaskedOpLong f, FReductionAllMaskedOpLong fa) {
        int i = 0;
        try {
            AssertEquals(rc, fa.apply(a, mask));
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(r[i], f.apply(a, i, mask));
            }
        } catch (AssertionError e) {
            AssertEquals(rc, fa.apply(a, mask), "Final result is incorrect!");
            AssertEquals(r[i], f.apply(a, i, mask), "at index #" + i);
        }
    }

    interface FBoolReductionOp {
        boolean apply(boolean[] a, int idx);
    }

    static void assertReductionBoolArraysEquals(boolean[] r, boolean[] a, FBoolReductionOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FMaskReductionOp {
        int apply(boolean[] a, int idx);
    }

    static void assertMaskReductionArraysEquals(int[] r, boolean[] a, FMaskReductionOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    static void assertRearrangeArraysEquals(short[] r, short[] a, int[] order, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    AssertEquals(r[i+j], a[i+order[i+j]]);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            AssertEquals(r[i+j], a[i+order[i+j]], "at index #" + idx + ", input = " + a[i+order[i+j]]);
        }
    }

    static void assertcompressArraysEquals(short[] r, short[] a, boolean[] m, int vector_len) {
        int i = 0, j = 0, k = 0;
        try {
            for (; i < a.length; i += vector_len) {
                k = 0;
                for (j = 0; j < vector_len; j++) {
                    if (m[(i + j) % SPECIES.length()]) {
                        AssertEquals(r[i + k], a[i + j]);
                        k++;
                    }
                }
                for (; k < vector_len; k++) {
                    AssertEquals(r[i + k], (short)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + k;
            if (m[(i + j) % SPECIES.length()]) {
                AssertEquals(r[idx], a[i + j], "at index #" + idx);
            } else {
                AssertEquals(r[idx], (short)0, "at index #" + idx);
            }
        }
    }

    static void assertexpandArraysEquals(short[] r, short[] a, boolean[] m, int vector_len) {
        int i = 0, j = 0, k = 0;
        try {
            for (; i < a.length; i += vector_len) {
                k = 0;
                for (j = 0; j < vector_len; j++) {
                    if (m[(i + j) % SPECIES.length()]) {
                        AssertEquals(r[i + j], a[i + k]);
                        k++;
                    } else {
                        AssertEquals(r[i + j], (short)0);
                    }
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (m[idx % SPECIES.length()]) {
                AssertEquals(r[idx], a[i + k], "at index #" + idx);
            } else {
                AssertEquals(r[idx], (short)0, "at index #" + idx);
            }
        }
    }

    static void assertSelectFromTwoVectorEquals(short[] r, short[] order, short[] a, short[] b, int vector_len) {
        int i = 0, j = 0;
        boolean is_exceptional_idx = false;
        int idx = 0, wrapped_index = 0, oidx = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    idx = i + j;
                    wrapped_index = Math.floorMod(shortBitsToFloat16(order[idx]).intValue(), 2 * vector_len);
                    is_exceptional_idx = wrapped_index >= vector_len;
                    oidx = is_exceptional_idx ? (wrapped_index - vector_len) : wrapped_index;
                    AssertEquals(r[idx], (is_exceptional_idx ? b[i + oidx] : a[i + oidx]));
                }
            }
        } catch (AssertionError e) {
            AssertEquals(r[idx], (is_exceptional_idx ? b[i + oidx] : a[i + oidx]), "at index #" + idx + ", order = " + order[idx] + ", a = " + a[i + oidx] + ", b = " + b[i + oidx]);
        }
    }

    static void assertSelectFromArraysEquals(short[] r, short[] a, short[] order, int vector_len) {
        int i = 0, j = 0;
        int idx = 0, wrapped_index = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    idx = shortBitsToFloat16(order[i+j]).intValue();
                    wrapped_index = Integer.remainderUnsigned(idx, vector_len);
                    AssertEquals(r[i+j], a[i+wrapped_index]);
                }
            }
        } catch (AssertionError e) {
            AssertEquals(r[i+j], a[i+wrapped_index], "at index #" + idx + ", input = " + a[i+wrapped_index]);
        }
    }

    static void assertRearrangeArraysEquals(short[] r, short[] a, int[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    if (mask[j % SPECIES.length()])
                         AssertEquals(r[i+j], a[i+order[i+j]]);
                    else
                         AssertEquals(r[i+j], (short)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (mask[j % SPECIES.length()])
                AssertEquals(r[i+j], a[i+order[i+j]], "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
            else
                AssertEquals(r[i+j], (short)0, "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
        }
    }

    static void assertSelectFromArraysEquals(short[] r, short[] a, short[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        int idx = 0, wrapped_index = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    idx = shortBitsToFloat16(order[i+j]).intValue();
                    wrapped_index = Integer.remainderUnsigned(idx, vector_len);
                    if (mask[j % SPECIES.length()])
                         AssertEquals(r[i+j], a[i+wrapped_index]);
                    else
                         AssertEquals(r[i+j], (short)0);
                }
            }
        } catch (AssertionError e) {
            if (mask[j % SPECIES.length()])
                AssertEquals(r[i+j], a[i+wrapped_index], "at index #" + idx + ", input = " + a[i+wrapped_index] + ", mask = " + mask[j % SPECIES.length()]);
            else
                AssertEquals(r[i+j], (short)0, "at index #" + idx + ", input = " + a[i+wrapped_index] + ", mask = " + mask[j % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a) {
        int i = 0;
        for (; i < a.length; i += SPECIES.length()) {
            int idx = i;
            for (int j = idx; j < (idx + SPECIES.length()); j++)
                a[j]=a[idx];
        }

        try {
            for (i = 0; i < a.length; i++) {
                AssertEquals(r[i], a[i]);
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], a[i], "at index #" + i + ", input = " + a[i]);
        }
    }

    interface FBinOp {
        short apply(short a, short b);
    }

    interface FBinMaskOp {
        short apply(short a, short b, boolean m);

        static FBinMaskOp lift(FBinOp f) {
            return (a, b, m) -> m ? f.apply(a, b) : a;
        }
    }

    static void assertArraysEqualsAssociative(short[] rl, short[] rr, short[] a, short[] b, short[] c, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                //Left associative
                AssertEquals(rl[i], f.apply(f.apply(a[i], b[i]), c[i]));

                //Right associative
                AssertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i])));

                //Results equal sanity check
                AssertEquals(rl[i], rr[i]);
            }
        } catch (AssertionError e) {
            AssertEquals(rl[i], f.apply(f.apply(a[i], b[i]), c[i]), "left associative test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
            AssertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i])), "right associative test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
            AssertEquals(rl[i], rr[i], "Result checks not equal at index #" + i + "leftRes = " + rl[i] + ", rightRes = " + rr[i]);
        }
    }

   static void assertArraysEqualsAssociative(short[] rl, short[] rr, short[] a, short[] b, short[] c, boolean[] mask, FBinOp f) {
       assertArraysEqualsAssociative(rl, rr, a, b, c, mask, FBinMaskOp.lift(f));
   }

    static void assertArraysEqualsAssociative(short[] rl, short[] rr, short[] a, short[] b, short[] c, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        boolean mask_bit = false;
        try {
            for (; i < a.length; i++) {
                mask_bit = mask[i % SPECIES.length()];
                //Left associative
                AssertEquals(rl[i], f.apply(f.apply(a[i], b[i], mask_bit), c[i], mask_bit));

                //Right associative
                AssertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i], mask_bit), mask_bit));

                //Results equal sanity check
                AssertEquals(rl[i], rr[i]);
            }
        } catch (AssertionError e) {
            AssertEquals(rl[i], f.apply(f.apply(a[i], b[i], mask_bit), c[i], mask_bit), "left associative masked test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i] + ", mask = " + mask_bit);
            AssertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i], mask_bit), mask_bit), "right associative masked test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i] + ", mask = " + mask_bit);
            AssertEquals(rl[i], rr[i], "Result checks not equal at index #" + i + "leftRes = " + rl[i] + ", rightRes = " + rr[i]);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], b[i]), "(" + a[i] + ", " + b[i] + ") at index #" + i);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], b), "(" + a[i] + ", " + b + ") at index #" + i);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]),
                                "(" + a[i] + ", " + b[(i / SPECIES.length()) * SPECIES.length()] + ") at index #" + i);
        }
    }

    static void assertBroadcastLongArraysEquals(short[] r, short[] a, short[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[(i / SPECIES.length()) * SPECIES.length()]).longValue()))));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], (float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[(i / SPECIES.length()) * SPECIES.length()]).longValue())))),
                                "(" + a[i] + ", " + b[(i / SPECIES.length()) * SPECIES.length()] + ") at index #" + i);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinOp f) {
        assertArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short b, boolean[] mask, FBinOp f) {
        assertArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(short[] r, short[] a, short b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b, mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], b, mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinOp f) {
        assertBroadcastArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] +
                                ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastLongArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinOp f) {
        assertBroadcastLongArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertBroadcastLongArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[(i / SPECIES.length()) * SPECIES.length()]).longValue())), mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[(i / SPECIES.length()) * SPECIES.length()]).longValue())),
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] +
                                ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }

    static void assertShiftArraysEquals(short[] r, short[] a, short[] b, FBinOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    AssertEquals(r[i+j], f.apply(a[i+j], b[j]));
                }
            }
        } catch (AssertionError e) {
            AssertEquals(r[i+j], f.apply(a[i+j], b[j]), "at index #" + i + ", " + j);
        }
    }

    static void assertShiftArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinOp f) {
        assertShiftArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertShiftArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    AssertEquals(r[i+j], f.apply(a[i+j], b[j], mask[i]));
                }
            }
        } catch (AssertionError err) {
            AssertEquals(r[i+j], f.apply(a[i+j], b[j], mask[i]), "at index #" + i + ", input1 = " + a[i+j] + ", input2 = " + b[j] + ", mask = " + mask[i]);
        }
    }

    interface FBinConstOp {
        short apply(short a);
    }

    interface FBinConstMaskOp {
        short apply(short a, boolean m);

        static FBinConstMaskOp lift(FBinConstOp f) {
            return (a, m) -> m ? f.apply(a) : a;
        }
    }

    static void assertShiftConstEquals(short[] r, short[] a, FBinConstOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    AssertEquals(r[i+j], f.apply(a[i+j]));
                }
            }
        } catch (AssertionError e) {
            AssertEquals(r[i+j], f.apply(a[i+j]), "at index #" + i + ", " + j);
        }
    }

    static void assertShiftConstEquals(short[] r, short[] a, boolean[] mask, FBinConstOp f) {
        assertShiftConstEquals(r, a, mask, FBinConstMaskOp.lift(f));
    }

    static void assertShiftConstEquals(short[] r, short[] a, boolean[] mask, FBinConstMaskOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    AssertEquals(r[i+j], f.apply(a[i+j], mask[i]));
                }
            }
        } catch (AssertionError err) {
            AssertEquals(r[i+j], f.apply(a[i+j], mask[i]), "at index #" + i + ", input1 = " + a[i+j] + ", mask = " + mask[i]);
        }
    }

    interface FTernOp {
        short apply(short a, short b, short c);
    }

    interface FTernMaskOp {
        short apply(short a, short b, short c, boolean m);

        static FTernMaskOp lift(FTernOp f) {
            return (a, b, c, m) -> m ? f.apply(a, b, c) : a;
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, short[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[i], c[i]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], b[i], c[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask, FTernOp f) {
        assertArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask, FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[i], c[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], b[i], c[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = "
              + b[i] + ", input3 = " + c[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()]), "at index #" +
                                i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " +
                                c[(i / SPECIES.length()) * SPECIES.length()]);
        }
    }

    static void assertAltBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i]), "at index #" +
                                i + ", input1 = " + a[i] + ", input2 = " +
                                b[(i / SPECIES.length()) * SPECIES.length()] + ",  input3 = " + c[i]);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask,
                                            FTernOp f) {
        assertBroadcastArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask,
                                            FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()],
                                    mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()],
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " +
                                b[i] + ", input3 = " + c[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }

    static void assertAltBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask,
                                            FTernOp f) {
        assertAltBroadcastArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertAltBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask,
                                            FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i],
                                    mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i],
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] +
                                ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] +
                                ", input3 = " + c[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertDoubleBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                    c[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                c[(i / SPECIES.length()) * SPECIES.length()]), "at index #" + i + ", input1 = " + a[i]
                                + ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] + ", input3 = " +
                                c[(i / SPECIES.length()) * SPECIES.length()]);
        }
    }

    static void assertDoubleBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask,
                                                  FTernOp f) {
        assertDoubleBroadcastArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertDoubleBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask,
                                                  FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                    c[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            AssertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                c[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]), "at index #"
                                + i + ", input1 = " + a[i] + ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] +
                                ", input3 = " + c[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }


    static boolean isWithin1Ulp(short actual, short expected) {
        Float16 act = shortBitsToFloat16(actual);
        Float16 exp = shortBitsToFloat16(expected);
        if (Float16.isNaN(exp) && !Float16.isNaN(act)) {
            return false;
        } else if (!Float16.isNaN(exp) && Float16.isNaN(act)) {
             return false;
        }

        Float16 low = Float16.nextDown(exp);
        Float16 high = Float16.nextUp(exp);

        if (Float16.compare(low, exp) > 0) {
            return false;
        }

        if (Float16.compare(high, exp) < 0) {
            return false;
        }

        return true;
    }

    static void assertArraysEqualsWithinOneUlp(short[] r, short[] a, FUnOp mathf, FUnOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float16.compare(shortBitsToFloat16(r[i]), shortBitsToFloat16(mathf.apply(a[i]))) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float16.compare(shortBitsToFloat16(r[i]), shortBitsToFloat16(mathf.apply(a[i]))) == 0, "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected = " + mathf.apply(a[i]));
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i])), "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected (within 1 ulp) = " + strictmathf.apply(a[i]));
        }
    }

    static void assertArraysEqualsWithinOneUlp(short[] r, short[] a, short[] b, FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float16.compare(shortBitsToFloat16(r[i]), shortBitsToFloat16(mathf.apply(a[i], b[i]))) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float16.compare(shortBitsToFloat16(r[i]), shortBitsToFloat16(mathf.apply(a[i], b[i]))) == 0, "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected = " + mathf.apply(a[i], b[i]));
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", actual = " + r[i] + ", expected (within 1 ulp) = " + strictmathf.apply(a[i], b[i]));
        }
    }

    static void assertBroadcastArraysEqualsWithinOneUlp(short[] r, short[] a, short[] b,
                                                        FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float16.compare(shortBitsToFloat16(r[i]),
                                  shortBitsToFloat16(mathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]))) == 0 ||
                                  isWithin1Ulp(r[i],
                                  strictmathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float16.compare(shortBitsToFloat16(r[i]),
                              shortBitsToFloat16(mathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]))) == 0,
                              "at index #" + i + ", input1 = " + a[i] + ", input2 = " +
                              b[(i / SPECIES.length()) * SPECIES.length()] + ", actual = " + r[i] +
                              ", expected = " + mathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]));
            Assert.assertTrue(isWithin1Ulp(r[i],
                              strictmathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()])),
                             "at index #" + i + ", input1 = " + a[i] + ", input2 = " +
                             b[(i / SPECIES.length()) * SPECIES.length()] + ", actual = " + r[i] +
                             ", expected (within 1 ulp) = " + strictmathf.apply(a[i],
                             b[(i / SPECIES.length()) * SPECIES.length()]));
        }
    }

    interface FGatherScatterOp {
        short[] apply(short[] a, int ix, int[] b, int iy);
    }

    static void assertArraysEquals(short[] r, short[] a, int[] b, FGatherScatterOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, b, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, i, b, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref,
              "(ref: " + Arrays.toString(ref) + ", res: " + Arrays.toString(res) + ", a: "
              + Arrays.toString(Arrays.copyOfRange(a, i, i+SPECIES.length()))
              + ", b: "
              + Arrays.toString(Arrays.copyOfRange(b, i, i+SPECIES.length()))
              + " at index #" + i);
        }
    }

    interface FGatherMaskedOp {
        short[] apply(short[] a, int ix, boolean[] mask, int[] b, int iy);
    }

    interface FScatterMaskedOp {
        short[] apply(short[] r, short[] a, int ix, boolean[] mask, int[] b, int iy);
    }

    static void assertArraysEquals(short[] r, short[] a, int[] b, boolean[] mask, FGatherMaskedOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, i, mask, b, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref,
              "(ref: " + Arrays.toString(ref) + ", res: " + Arrays.toString(res) + ", a: "
              + Arrays.toString(Arrays.copyOfRange(a, i, i+SPECIES.length()))
              + ", b: "
              + Arrays.toString(Arrays.copyOfRange(b, i, i+SPECIES.length()))
              + ", mask: "
              + Arrays.toString(mask)
              + " at index #" + i);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, int[] b, boolean[] mask, FScatterMaskedOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(r, a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(r, a, i, mask, b, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref,
              "(ref: " + Arrays.toString(ref) + ", res: " + Arrays.toString(res) + ", a: "
              + Arrays.toString(Arrays.copyOfRange(a, i, i+SPECIES.length()))
              + ", b: "
              + Arrays.toString(Arrays.copyOfRange(b, i, i+SPECIES.length()))
              + ", r: "
              + Arrays.toString(Arrays.copyOfRange(r, i, i+SPECIES.length()))
              + ", mask: "
              + Arrays.toString(mask)
              + " at index #" + i);
        }
    }

    interface FLaneOp {
        short[] apply(short[] a, int origin, int idx);
    }

    static void assertArraysEquals(short[] r, short[] a, int origin, FLaneOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, origin, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, origin, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i);
        }
    }

    interface FLaneBop {
        short[] apply(short[] a, short[] b, int origin, int idx);
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, int origin, FLaneBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin);
        }
    }

    interface FLaneMaskedBop {
        short[] apply(short[] a, short[] b, int origin, boolean[] mask, int idx);
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, int origin, boolean[] mask, FLaneMaskedBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, mask, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, mask, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin);
        }
    }

    interface FLanePartBop {
        short[] apply(short[] a, short[] b, int origin, int part, int idx);
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, int origin, int part, FLanePartBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, part, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin
              + ", with part #" + part);
        }
    }

    interface FLanePartMaskedBop {
        short[] apply(short[] a, short[] b, int origin, int part, boolean[] mask, int idx);
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, int origin, int part, boolean[] mask, FLanePartMaskedBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                AssertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, mask, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, part, mask, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            AssertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin
              + ", with part #" + part);
        }
    }

    static short genValue(int i) {
        return float16ToShortBits(Float16.valueOf(i));
    }

    static int intCornerCaseValue(int i) {
        switch(i % 5) {
            case 0:
                return Integer.MAX_VALUE;
            case 1:
                return Integer.MIN_VALUE;
            case 2:
                return Integer.MIN_VALUE;
            case 3:
                return Integer.MAX_VALUE;
            default:
                return (int)0;
        }
    }

    static final List<IntFunction<short[]>> INT_FLOAT16_GENERATORS = List.of(
            withToString("Float16[-i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> genValue(-i * 5));
            }),
            withToString("Float16[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> genValue(i * 5));
            }),
            withToString("Float16[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (((short)(i + 1) == 0) ? genValue(1) : genValue(i + 1)));
            }),
            withToString("Float16[intCornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)intCornerCaseValue(i));
            })
    );

    static void assertArraysEquals(int[] r, short[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                AssertEquals(r[i], (int)Float.float16ToFloat(a[i+offs]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], (int)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }

    static long longCornerCaseValue(int i) {
        switch(i % 5) {
            case 0:
                return Long.MAX_VALUE;
            case 1:
                return Long.MIN_VALUE;
            case 2:
                return Long.MIN_VALUE;
            case 3:
                return Long.MAX_VALUE;
            default:
                return (long)0;
        }
    }

    static short genValue(long i) {
        return float16ToShortBits(Float16.valueOf(i));
    }

    static final List<IntFunction<short[]>> LONG_FLOAT16_GENERATORS = List.of(
            withToString("Float16[-i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> genValue(-i * 5));
            }),
            withToString("Float16[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> genValue(i * 5));
            }),
            withToString("Float16[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (((short)(i + 1) == 0) ? genValue(1) : genValue(i + 1)));
            }),
            withToString("Float16[cornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)genValue(longCornerCaseValue(i)));
            })
    );


    static void assertArraysEquals(long[] r, short[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                AssertEquals(r[i], (long)Float.float16ToFloat(a[i+offs]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], (long)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }

    static void assertArraysEquals(double[] r, short[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                AssertEquals(r[i], (double)Float.float16ToFloat(a[i+offs]));
            }
        } catch (AssertionError e) {
            AssertEquals(r[i], (double)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }

    static short bits(short e) {
        return e;
    }

    static final List<IntFunction<short[]>> FLOAT16_GENERATORS = List.of(
            withToString("Float16[-i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> genValue(-i * 5));
            }),
            withToString("Float16[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> genValue(i * 5));
            }),
            withToString("Float16[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (((short)(i + 1) == 0) ? genValue(1) : genValue(i + 1)));
            }),
            withToString("Float16[0.01 + (i / (i + 1))]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> Float.floatToFloat16((0.01f + ((float)i / (i + 1)))));
            }),
            withToString("Float16[i -> i % 17 == 0 ? cornerCaseValue(i) : 0.01f + (i / (i + 1))]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (i % 17 == 0) ? cornerCaseValue(i) : Float.floatToFloat16((0.01f + ((float)i / (i + 1)))));
            }),
            withToString("short[cornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> cornerCaseValue(i));
            })
    );

    // Create combinations of pairs
    // @@@ Might be sensitive to order e.g. div by 0
    static final List<List<IntFunction<short[]>>> FLOAT16_GENERATOR_PAIRS =
        Stream.of(FLOAT16_GENERATORS.get(0)).
                flatMap(fa -> FLOAT16_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] boolUnaryOpProvider() {
        return BOOL_ARRAY_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<short[]>>> FLOAT16_GENERATOR_TRIPLES =
        FLOAT16_GENERATOR_PAIRS.stream().
                flatMap(pair -> FLOAT16_GENERATORS.stream().map(f -> List.of(pair.get(0), pair.get(1), f))).
                collect(Collectors.toList());

    static final List<IntFunction<short[]>> SELECT_FROM_INDEX_GENERATORS = List.of(
            withToString("short[0..VECLEN*2)", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)(RAND.nextInt()));
            })
    );

    static final List<List<IntFunction<short[]>>> FLOAT16_GENERATOR_SELECT_FROM_TRIPLES =
        FLOAT16_GENERATOR_PAIRS.stream().
                flatMap(pair -> SELECT_FROM_INDEX_GENERATORS.stream().map(f -> List.of(pair.get(0), pair.get(1), f))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortBinaryOpProvider() {
        return FLOAT16_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortIndexedOpProvider() {
        return FLOAT16_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT16_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortTernaryOpProvider() {
        return FLOAT16_GENERATOR_TRIPLES.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortSelectFromTwoVectorOpProvider() {
        return FLOAT16_GENERATOR_SELECT_FROM_TRIPLES.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortTernaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT16_GENERATOR_TRIPLES.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpProvider() {
        return FLOAT16_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT16_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shorttoIntUnaryOpProvider() {
        return INT_FLOAT16_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shorttoLongUnaryOpProvider() {
        return LONG_FLOAT16_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] maskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] maskCompareOpProvider() {
        return BOOLEAN_MASK_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shuffleProvider() {
        return INT_SHUFFLE_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shuffleCompareOpProvider() {
        return INT_SHUFFLE_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpShuffleProvider() {
        return INT_SHUFFLE_GENERATORS.stream().
                flatMap(fs -> FLOAT16_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpShuffleMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_SHUFFLE_GENERATORS.stream().
                    flatMap(fs -> FLOAT16_GENERATORS.stream().map(fa -> {
                        return new Object[] {fa, fs, fm};
                }))).
                toArray(Object[][]::new);
    }

    static final List<BiFunction<Integer,Integer,short[]>> FLOAT16_SHUFFLE_GENERATORS = List.of(
            withToStringBi("shuffle[random]", (Integer l, Integer m) -> {
                short[] a = new short[l];
                int upper = m;
                for (int i = 0; i < 1; i++) {
                    a[i] = (short)RAND.nextInt(upper);
                }
                return a;
            })
    );

    @DataProvider
    public Object[][] shortUnaryOpSelectFromProvider() {
        return FLOAT16_SHUFFLE_GENERATORS.stream().
                flatMap(fs -> FLOAT16_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpSelectFromMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT16_SHUFFLE_GENERATORS.stream().
                    flatMap(fs -> FLOAT16_GENERATORS.stream().map(fa -> {
                        return new Object[] {fa, fs, fm};
                }))).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<short[]>> FLOAT16_COMPARE_GENERATORS = List.of(
            withToString("short[i]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)i);
            }),
            withToString("short[i - length / 2]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)(i - (s * BUFFER_REPS / 2)));
            }),
            withToString("short[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)(i + 1));
            }),
            withToString("short[i - 2]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)(i - 2));
            }),
            withToString("short[zigZag(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> i%3 == 0 ? (short)i : (i%3 == 1 ? (short)(i + 1) : (short)(i - 2)));
            }),
            withToString("short[cornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> cornerCaseValue(i));
            })
    );

    static final List<List<IntFunction<short[]>>> FLOAT16_TEST_GENERATOR_ARGS =
        FLOAT16_COMPARE_GENERATORS.stream().
                map(fa -> List.of(fa)).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortTestOpProvider() {
        return FLOAT16_TEST_GENERATOR_ARGS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortTestOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT16_TEST_GENERATOR_ARGS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<short[]>>> FLOAT16_COMPARE_GENERATOR_PAIRS =
        FLOAT16_COMPARE_GENERATORS.stream().
                flatMap(fa -> FLOAT16_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortCompareOpProvider() {
        return FLOAT16_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortCompareOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT16_COMPARE_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    interface ToFloat16F {
        short apply(int i);
    }

    static short[] fill(int s , ToFloat16F f) {
        return fill(new short[s], f);
    }

    static short[] fill(short[] a, ToFloat16F f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    static short cornerCaseValue(int i) {
        return switch(i % 8) {
            case 0  -> float16ToShortBits(Float16.MAX_VALUE);
            case 1  -> float16ToShortBits(Float16.MIN_VALUE);
            case 2  -> float16ToShortBits(Float16.NEGATIVE_INFINITY);
            case 3  -> float16ToShortBits(Float16.POSITIVE_INFINITY);
            case 4  -> float16ToShortBits(Float16.NaN);
            case 5  -> float16ToShortBits(shortBitsToFloat16((short)0x7FFA));
            case 6  -> ((short)0.0);
            default -> ((short)-0.0);
        };
    }

    static final IntFunction<short[]> fr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new short[length];
    };

    static final IntFunction<boolean[]> fmr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new boolean[length];
    };

    static final IntFunction<long[]> lfr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new long[length];
    };

    static boolean eq(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return at.floatValue() == bt.floatValue();
    }

    static boolean neq(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return at.floatValue() != bt.floatValue();
    }

    static boolean lt(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return at.floatValue() < bt.floatValue();
    }

    static boolean le(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return at.floatValue() <= bt.floatValue();
    }

    static boolean gt(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return at.floatValue() > bt.floatValue();
    }

    static boolean ge(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return at.floatValue() >= bt.floatValue();
    }

    static short firstNonZero(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        Float16 zero = shortBitsToFloat16((short)0);
        return Float16.compare(at, zero) != 0 ? a : b;
    }

    static short multiplicativeIdentity() {
        return (short)float16ToShortBits(Float16.valueOf(1.0f));
    }

    static short scalar_add(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return float16ToShortBits(Float16.add(at, bt));
    }

    static short scalar_sub(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return float16ToShortBits(Float16.subtract(at, bt));
    }

    static short scalar_mul(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return float16ToShortBits(Float16.multiply(at, bt));

    }
    static short scalar_max(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return float16ToShortBits(Float16.max(at, bt));
    }

    static short scalar_min(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return float16ToShortBits(Float16.min(at, bt));
    }

    static short scalar_div(short a, short b) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        return float16ToShortBits(Float16.divide(at, bt));
    }

    static short scalar_fma(short a, short b, short c) {
        Float16 at = shortBitsToFloat16(a);
        Float16 bt = shortBitsToFloat16(b);
        Float16 ct = shortBitsToFloat16(c);
        return float16ToShortBits(Float16.fma(at, bt, ct));
    }

    static short scalar_abs(short a) {
        Float16 at = shortBitsToFloat16(a);
        return float16ToShortBits(Float16.abs(at));
    }

    static short scalar_neg(short a) {
        Float16 at = shortBitsToFloat16(a);
        return float16ToShortBits(Float16.valueOf(-at.floatValue()));
    }

    static short scalar_sin(short a) {
        return float16ToShortBits(Float16.valueOf(Math.sin(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_exp(short a) {
        return float16ToShortBits(Float16.valueOf(Math.exp(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_log1p(short a) {
        return float16ToShortBits(Float16.valueOf(Math.log1p(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_log(short a) {
        return float16ToShortBits(Float16.valueOf(Math.log(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_log10(short a) {
        return float16ToShortBits(Float16.valueOf(Math.log10(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_expm1(short a) {
        return float16ToShortBits(Float16.valueOf(Math.expm1(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_cos(short a) {
        return float16ToShortBits(Float16.valueOf(Math.cos(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_tan(short a) {
        return float16ToShortBits(Float16.valueOf(Math.tan(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_sinh(short a) {
        return float16ToShortBits(Float16.valueOf(Math.sinh(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_cosh(short a) {
        return float16ToShortBits(Float16.valueOf(Math.cosh(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_tanh(short a) {
        return float16ToShortBits(Float16.valueOf(Math.tanh(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_asin(short a) {
        return float16ToShortBits(Float16.valueOf(Math.asin(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_acos(short a) {
        return float16ToShortBits(Float16.valueOf(Math.acos(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_atan(short a) {
        return float16ToShortBits(Float16.valueOf(Math.atan(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_cbrt(short a) {
        return float16ToShortBits(Float16.valueOf(Math.cbrt(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_sqrt(short a) {
        return float16ToShortBits(Float16.valueOf(Math.sqrt(shortBitsToFloat16(a).doubleValue())));
    }

    static short scalar_hypot(short a, short b) {
        return float16ToShortBits(Float16.valueOf(Math.hypot(shortBitsToFloat16(a).doubleValue(),
                                                                     shortBitsToFloat16(b).doubleValue())));
    }

    static short scalar_pow(short a, short b) {
        return float16ToShortBits(Float16.valueOf(Math.pow(shortBitsToFloat16(a).doubleValue(),
                                                                   shortBitsToFloat16(b).doubleValue())));
    }

    static short scalar_atan2(short a, short b) {
        return float16ToShortBits(Float16.valueOf(Math.atan2(shortBitsToFloat16(a).doubleValue(),
                                                                     shortBitsToFloat16(b).doubleValue())));
    }

    static short strict_scalar_sin(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.sin(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_exp(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.exp(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_log1p(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.log1p(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_log(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.log(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_log10(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.log10(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_expm1(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.expm1(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_cos(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.cos(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_tan(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.tan(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_sinh(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.sinh(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_cosh(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.cosh(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_tanh(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.tanh(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_asin(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.asin(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_acos(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.acos(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_atan(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.atan(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_cbrt(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.cbrt(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_sqrt(short a) {
        return float16ToShortBits(Float16.valueOf(StrictMath.sqrt(shortBitsToFloat16(a).doubleValue())));
    }

    static short strict_scalar_hypot(short a, short b) {
        return float16ToShortBits(Float16.valueOf(StrictMath.hypot(shortBitsToFloat16(a).doubleValue(),
                                                                     shortBitsToFloat16(b).doubleValue())));
    }

    static short strict_scalar_pow(short a, short b) {
        return float16ToShortBits(Float16.valueOf(StrictMath.pow(shortBitsToFloat16(a).doubleValue(),
                                                                   shortBitsToFloat16(b).doubleValue())));
    }

    static short strict_scalar_atan2(short a, short b) {
        return float16ToShortBits(Float16.valueOf(StrictMath.atan2(shortBitsToFloat16(a).doubleValue(),
                                                                     shortBitsToFloat16(b).doubleValue())));
    }
    static short additiveIdentity() {
        return (short)0;
    }


    static short zeroValue() {
        return (short) 0;
    }

    static short maxValue() {
        return float16ToShortBits(Float16.POSITIVE_INFINITY);
    }

    static short minValue() {
        return float16ToShortBits(Float16.NEGATIVE_INFINITY);
    }

    static boolean isNaN(short a) {
        return Float16.isNaN(shortBitsToFloat16(a));
    }
    static boolean isFinite(short a) {
        return Float16.isFinite(shortBitsToFloat16(a));
    }
    static boolean isInfinite(short a) {
        return Float16.isInfinite(shortBitsToFloat16(a));
    }

    @Test
    static void smokeTest1() {
        Float16Vector three = Float16Vector.broadcast(SPECIES, float16ToShortBits(Float16.valueOf(-3)));
        Float16Vector three2 = (Float16Vector) SPECIES.broadcast(Float16.valueOf(-3).longValue());
        assert(three.eq(three2).allTrue());
        Float16Vector three3 = three2.broadcast(float16ToShortBits(Float16.valueOf(1))).broadcast(Float16.valueOf(-3).longValue());
        assert(three.eq(three3).allTrue());
        int scale = 2;
        Class<?> ETYPE = short.class;
        if (ETYPE == double.class || ETYPE == long.class)
            scale = 1000000;
        else if (ETYPE == byte.class && SPECIES.length() >= 64)
            scale = 1;
        Float16Vector higher = three.addIndex(scale);
        VectorMask<Float16> m = three.compare(VectorOperators.LE, higher);
        assert(m.allTrue());
        m = higher.min((short)-1).test(VectorOperators.IS_NEGATIVE);
        assert(m.allTrue());
        m = higher.test(VectorOperators.IS_FINITE);
        assert(m.allTrue());
        short max = higher.reduceLanes(VectorOperators.MAX);
        assert(max == float16ToShortBits(Float16.add(Float16.valueOf(-3), Float16.multiply(Float16.valueOf(scale), Float16.valueOf((SPECIES.length()-1))))));
    }

    private static short[]
    bothToArray(Float16Vector a, Float16Vector b) {
        short[] r = new short[a.length() + b.length()];
        a.intoArray(r, 0);
        b.intoArray(r, a.length());
        return r;
    }

    @Test
    static void smokeTest2() {
        // Do some zipping and shuffling.
        Float16Vector io = (Float16Vector) SPECIES.broadcast(0).addIndex(1);
        Float16Vector io2 = (Float16Vector) VectorShuffle.iota(SPECIES,0,1,false).toVector();
        AssertEquals(io, io2);
        Float16Vector a = io.add((short)1); //[1,2]
        Float16Vector b = a.neg();  //[-1,-2]
        short[] abValues = bothToArray(a,b); //[1,2,-1,-2]
        VectorShuffle<Float16> zip0 = VectorShuffle.makeZip(SPECIES, 0);
        VectorShuffle<Float16> zip1 = VectorShuffle.makeZip(SPECIES, 1);
        Float16Vector zab0 = a.rearrange(zip0,b); //[1,-1]
        Float16Vector zab1 = a.rearrange(zip1,b); //[2,-2]
        short[] zabValues = bothToArray(zab0, zab1); //[1,-1,2,-2]
        // manually zip
        short[] manual = new short[zabValues.length];
        for (int i = 0; i < manual.length; i += 2) {
            manual[i+0] = abValues[i/2];
            manual[i+1] = abValues[a.length() + i/2];
        }
        AssertEquals(Arrays.toString(zabValues), Arrays.toString(manual));
        VectorShuffle<Float16> unz0 = VectorShuffle.makeUnzip(SPECIES, 0);
        VectorShuffle<Float16> unz1 = VectorShuffle.makeUnzip(SPECIES, 1);
        Float16Vector uab0 = zab0.rearrange(unz0,zab1);
        Float16Vector uab1 = zab0.rearrange(unz1,zab1);
        short[] abValues1 = bothToArray(uab0, uab1);
        AssertEquals(Arrays.toString(abValues), Arrays.toString(abValues1));
    }

    static void iotaShuffle() {
        Float16Vector io = (Float16Vector) SPECIES.broadcast(0).addIndex(1);
        Float16Vector io2 = (Float16Vector) VectorShuffle.iota(SPECIES, 0 , 1, false).toVector();
        AssertEquals(io, io2);
    }

    @Test
    // Test all shuffle related operations.
    static void shuffleTest() {
        // To test backend instructions, make sure that C2 is used.
        for (int loop = 0; loop < INVOC_COUNT * INVOC_COUNT; loop++) {
            iotaShuffle();
        }
    }

    @Test
    void viewAsIntegeralLanesTest() {
        Vector<?> asIntegral = SPECIES.zero().viewAsIntegralLanes();
        VectorSpecies<?> asIntegralSpecies = asIntegral.species();
        Assert.assertNotEquals(asIntegralSpecies.elementType(), SPECIES.elementType());
        AssertEquals(asIntegralSpecies.vectorShape(), SPECIES.vectorShape());
        AssertEquals(asIntegralSpecies.length(), SPECIES.length());
        AssertEquals(asIntegral.viewAsFloatingLanes().species(), SPECIES);
    }

    @Test
    void viewAsFloatingLanesTest() {
        Vector<?> asFloating = SPECIES.zero().viewAsFloatingLanes();
        AssertEquals(asFloating.species(), SPECIES);
    }

    static short ADD(short a, short b) {
        return (short)(scalar_add(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void ADDFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::ADD);
    }

    static short add(short a, short b) {
        return (short)(scalar_add(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void addFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.add(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::add);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void ADDFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::ADD);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void addFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.add(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::add);
    }

    static short SUB(short a, short b) {
        return (short)(scalar_sub(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void SUBFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::SUB);
    }

    static short sub(short a, short b) {
        return (short)(scalar_sub(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void subFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.sub(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::sub);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void SUBFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::SUB);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void subFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.sub(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::sub);
    }

    static short MUL(short a, short b) {
        return (short)(scalar_mul(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MULFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::MUL);
    }

    static short mul(short a, short b) {
        return (short)(scalar_mul(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void mulFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.mul(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::mul);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void MULFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::MUL);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void mulFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.mul(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::mul);
    }

    static short DIV(short a, short b) {
        return (short)(scalar_div(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void DIVFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::DIV);
    }

    static short div(short a, short b) {
        return (short)(scalar_div(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void divFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.div(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::div);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void DIVFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::DIV);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void divFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.div(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::div);
    }

    static short FIRST_NONZERO(short a, short b) {
        return (short)(firstNonZero(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void FIRST_NONZEROFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.FIRST_NONZERO, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::FIRST_NONZERO);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void FIRST_NONZEROFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.FIRST_NONZERO, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::FIRST_NONZERO);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void addFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.add(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::add);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void addFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.add(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, Float16Vector128Tests::add);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void subFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.sub(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::sub);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void subFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.sub(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, Float16Vector128Tests::sub);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void mulFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.mul(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::mul);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void mulFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.mul(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, Float16Vector128Tests::mul);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void divFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.div(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::div);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void divFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.div(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, Float16Vector128Tests::div);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void ADDFloat16Vector128TestsBroadcastLongSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.ADD, shortBitsToFloat16(b[i]).longValue()).intoArray(r, i);
        }

        assertBroadcastLongArraysEquals(r, a, b, Float16Vector128Tests::ADD);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void ADDFloat16Vector128TestsBroadcastMaskedLongSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.ADD, shortBitsToFloat16(b[i]).longValue(), vmask).intoArray(r, i);
        }

        assertBroadcastLongArraysEquals(r, a, b, mask, Float16Vector128Tests::ADD);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void MINFloat16Vector128TestsWithMemOp(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MIN, bcast_vec).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (short)10, Float16Vector128Tests::MIN);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void minFloat16Vector128TestsWithMemOp(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.min(bcast_vec).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (short)10, Float16Vector128Tests::min);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void MINFloat16Vector128TestsMaskedWithMemOp(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MIN, bcast_vec, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (short)10, mask, Float16Vector128Tests::MIN);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void MAXFloat16Vector128TestsWithMemOp(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MAX, bcast_vec).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (short)10, Float16Vector128Tests::MAX);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void maxFloat16Vector128TestsWithMemOp(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.max(bcast_vec).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (short)10, Float16Vector128Tests::max);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void MAXFloat16Vector128TestsMaskedWithMemOp(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MAX, bcast_vec, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (short)10, mask, Float16Vector128Tests::MAX);
    }

    static short MIN(short a, short b) {
        return (short)(scalar_min(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MINFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MIN, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::MIN);
    }

    static short min(short a, short b) {
        return (short)(scalar_min(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void minFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.min(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::min);
    }

    static short MAX(short a, short b) {
        return (short)(scalar_max(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MAXFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MAX, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::MAX);
    }

    static short max(short a, short b) {
        return (short)(scalar_max(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void maxFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.max(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, Float16Vector128Tests::max);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MINFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.MIN, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::MIN);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void minFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.min(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::min);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MAXFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.MAX, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::MAX);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void maxFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.max(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, Float16Vector128Tests::max);
    }

    static short ADDReduce(short[] a, int idx) {
        short res = additiveIdentity();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = scalar_add(res, a[i]);
        }

        return res;
    }

    static short ADDReduceAll(short[] a) {
        short res = additiveIdentity();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_add(res, ADDReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ADDReduceFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        short ra = additiveIdentity();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.ADD);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = additiveIdentity();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_add(ra, av.reduceLanes(VectorOperators.ADD));
            }
        }

        assertReductionArraysEquals(r, ra, a,
                Float16Vector128Tests::ADDReduce, Float16Vector128Tests::ADDReduceAll, RELATIVE_ROUNDING_ERROR_FACTOR_ADD);
    }

    static short ADDReduceMasked(short[] a, int idx, boolean[] mask) {
        short res = additiveIdentity();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = scalar_add(res, a[i]);
        }

        return res;
    }

    static short ADDReduceAllMasked(short[] a, boolean[] mask) {
        short res = additiveIdentity();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_add(res, ADDReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void ADDReduceFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        short ra = additiveIdentity();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.ADD, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = additiveIdentity();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_add(ra, av.reduceLanes(VectorOperators.ADD, vmask));
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                Float16Vector128Tests::ADDReduceMasked, Float16Vector128Tests::ADDReduceAllMasked, RELATIVE_ROUNDING_ERROR_FACTOR_ADD);
    }

    static short MULReduce(short[] a, int idx) {
        short res = multiplicativeIdentity();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = scalar_mul(res, a[i]);
        }

        return res;
    }

    static short MULReduceAll(short[] a) {
        short res = multiplicativeIdentity();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_mul(res, MULReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void MULReduceFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        short ra = multiplicativeIdentity();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MUL);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = multiplicativeIdentity();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_mul(ra, av.reduceLanes(VectorOperators.MUL));
            }
        }

        assertReductionArraysEquals(r, ra, a,
                Float16Vector128Tests::MULReduce, Float16Vector128Tests::MULReduceAll, RELATIVE_ROUNDING_ERROR_FACTOR_MUL);
    }

    static short MULReduceMasked(short[] a, int idx, boolean[] mask) {
        short res = multiplicativeIdentity();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = scalar_mul(res, a[i]);
        }

        return res;
    }

    static short MULReduceAllMasked(short[] a, boolean[] mask) {
        short res = multiplicativeIdentity();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_mul(res, MULReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void MULReduceFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        short ra = multiplicativeIdentity();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MUL, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = multiplicativeIdentity();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_mul(ra, av.reduceLanes(VectorOperators.MUL, vmask));
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                Float16Vector128Tests::MULReduceMasked, Float16Vector128Tests::MULReduceAllMasked, RELATIVE_ROUNDING_ERROR_FACTOR_MUL);
    }

    static short MINReduce(short[] a, int idx) {
        short res = maxValue();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = scalar_min(res, a[i]);
        }

        return res;
    }

    static short MINReduceAll(short[] a) {
        short res = maxValue();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_min(res, MINReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void MINReduceFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        short ra = maxValue();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MIN);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = maxValue();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_min(ra, av.reduceLanes(VectorOperators.MIN));
            }
        }

        assertReductionArraysEquals(r, ra, a,
                Float16Vector128Tests::MINReduce, Float16Vector128Tests::MINReduceAll);
    }

    static short MINReduceMasked(short[] a, int idx, boolean[] mask) {
        short res = maxValue();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = scalar_min(res, a[i]);
        }

        return res;
    }

    static short MINReduceAllMasked(short[] a, boolean[] mask) {
        short res = maxValue();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_min(res, MINReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void MINReduceFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        short ra = maxValue();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MIN, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = maxValue();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_min(ra, av.reduceLanes(VectorOperators.MIN, vmask));
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                Float16Vector128Tests::MINReduceMasked, Float16Vector128Tests::MINReduceAllMasked);
    }

    static short MAXReduce(short[] a, int idx) {
        short res = minValue();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = scalar_max(res, a[i]);
        }

        return res;
    }

    static short MAXReduceAll(short[] a) {
        short res = minValue();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_max(res, MAXReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void MAXReduceFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        short ra = minValue();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MAX);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = minValue();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_max(ra, av.reduceLanes(VectorOperators.MAX));
            }
        }

        assertReductionArraysEquals(r, ra, a,
                Float16Vector128Tests::MAXReduce, Float16Vector128Tests::MAXReduceAll);
    }

    static short MAXReduceMasked(short[] a, int idx, boolean[] mask) {
        short res = minValue();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = scalar_max(res, a[i]);
        }

        return res;
    }

    static short MAXReduceAllMasked(short[] a, boolean[] mask) {
        short res = minValue();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = scalar_max(res, MAXReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void MAXReduceFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        short ra = minValue();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MAX, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = minValue();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = scalar_max(ra, av.reduceLanes(VectorOperators.MAX, vmask));
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                Float16Vector128Tests::MAXReduceMasked, Float16Vector128Tests::MAXReduceAllMasked);
    }

    static short FIRST_NONZEROReduce(short[] a, int idx) {
        short res = zeroValue();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = firstNonZero(res, a[i]);
        }

        return res;
    }

    static short FIRST_NONZEROReduceAll(short[] a) {
        short res = zeroValue();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = firstNonZero(res, FIRST_NONZEROReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void FIRST_NONZEROReduceFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        short ra = zeroValue();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.FIRST_NONZERO);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = zeroValue();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = firstNonZero(ra, av.reduceLanes(VectorOperators.FIRST_NONZERO));
            }
        }

        assertReductionArraysEquals(r, ra, a,
                Float16Vector128Tests::FIRST_NONZEROReduce, Float16Vector128Tests::FIRST_NONZEROReduceAll);
    }

    static short FIRST_NONZEROReduceMasked(short[] a, int idx, boolean[] mask) {
        short res = zeroValue();
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = firstNonZero(res, a[i]);
        }

        return res;
    }

    static short FIRST_NONZEROReduceAllMasked(short[] a, boolean[] mask) {
        short res = zeroValue();
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = firstNonZero(res, FIRST_NONZEROReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void FIRST_NONZEROReduceFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        short ra = zeroValue();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.FIRST_NONZERO, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = zeroValue();
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                ra = firstNonZero(ra, av.reduceLanes(VectorOperators.FIRST_NONZERO, vmask));
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                Float16Vector128Tests::FIRST_NONZEROReduceMasked, Float16Vector128Tests::FIRST_NONZEROReduceAllMasked);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void withFloat16Vector128Tests(IntFunction<short []> fa, IntFunction<short []> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0, j = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.withLane(j, b[i + j]).intoArray(r, i);
                a[i + j] = b[i + j];
                j = (j + 1) & (SPECIES.length() - 1);
            }
        }


        assertArraysStrictlyEquals(r, a);
    }

    static boolean testIS_DEFAULT(short a) {
        return bits(a)==0;
    }

    @Test(dataProvider = "shortTestOpProvider")
    static void IS_DEFAULTFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_DEFAULT);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), testIS_DEFAULT(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortTestOpMaskProvider")
    static void IS_DEFAULTMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_DEFAULT, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j),  vmask.laneIsSet(j) && testIS_DEFAULT(a[i + j]));
                }
            }
        }
    }

    static boolean testIS_NEGATIVE(short a) {
        return bits(a)<0;
    }

    @Test(dataProvider = "shortTestOpProvider")
    static void IS_NEGATIVEFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_NEGATIVE);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), testIS_NEGATIVE(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortTestOpMaskProvider")
    static void IS_NEGATIVEMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_NEGATIVE, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j),  vmask.laneIsSet(j) && testIS_NEGATIVE(a[i + j]));
                }
            }
        }
    }

    static boolean testIS_FINITE(short a) {
        return isFinite(a);
    }

    @Test(dataProvider = "shortTestOpProvider")
    static void IS_FINITEFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_FINITE);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), testIS_FINITE(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortTestOpMaskProvider")
    static void IS_FINITEMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_FINITE, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j),  vmask.laneIsSet(j) && testIS_FINITE(a[i + j]));
                }
            }
        }
    }

    static boolean testIS_NAN(short a) {
        return isNaN(a);
    }

    @Test(dataProvider = "shortTestOpProvider")
    static void IS_NANFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_NAN);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), testIS_NAN(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortTestOpMaskProvider")
    static void IS_NANMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_NAN, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j),  vmask.laneIsSet(j) && testIS_NAN(a[i + j]));
                }
            }
        }
    }

    static boolean testIS_INFINITE(short a) {
        return isInfinite(a);
    }

    @Test(dataProvider = "shortTestOpProvider")
    static void IS_INFINITEFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_INFINITE);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), testIS_INFINITE(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortTestOpMaskProvider")
    static void IS_INFINITEMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                VectorMask<Float16> mv = av.test(VectorOperators.IS_INFINITE, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j),  vmask.laneIsSet(j) && testIS_INFINITE(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void LTFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.LT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), lt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void ltFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.lt(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), lt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void LTFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.LT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), mask[j] && lt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void GTFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.GT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), gt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void GTFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.GT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), mask[j] && gt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void EQFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.EQ, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), eq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void eqFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.eq(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), eq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void EQFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.EQ, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), mask[j] && eq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void NEFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.NE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), neq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void NEFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.NE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), mask[j] && neq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void LEFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.LE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), le(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void LEFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.LE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), mask[j] && le(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void GEFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.GE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), ge(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void GEFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                VectorMask<Float16> mv = av.compare(VectorOperators.GE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    AssertEquals(mv.laneIsSet(j), mask[j] && ge(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void LTFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.LT, b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), lt(a[i + j], b[i]));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void LTFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa,
                                IntFunction<short[]> fb, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.LT, b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), mask[j] && (lt(a[i + j], b[i])));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void LTFloat16Vector128TestsBroadcastLongSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.LT, shortBitsToFloat16(b[i]).longValue());

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), lt(a[i + j], float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[i]).longValue()))));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void LTFloat16Vector128TestsBroadcastLongMaskedSmokeTest(IntFunction<short[]> fa,
                                IntFunction<short[]> fb, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.LT, shortBitsToFloat16(b[i]).longValue(), vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), mask[j] && (lt(a[i + j],float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[i]).longValue())))));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void EQFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.EQ, b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), eq(a[i + j], b[i]));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void EQFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa,
                                IntFunction<short[]> fb, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.EQ, b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), mask[j] && (eq(a[i + j], b[i])));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void EQFloat16Vector128TestsBroadcastLongSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.EQ, shortBitsToFloat16(b[i]).longValue());

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), eq(a[i + j], float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[i]).longValue()))));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpMaskProvider")
    static void EQFloat16Vector128TestsBroadcastLongMaskedSmokeTest(IntFunction<short[]> fa,
                                IntFunction<short[]> fb, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.compare(VectorOperators.EQ, shortBitsToFloat16(b[i]).longValue(), vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), mask[j] && (eq(a[i + j],float16ToShortBits(Float16.valueOf(shortBitsToFloat16(b[i]).longValue())))));
            }
        }
    }

    static short blend(short a, short b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void blendFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.blend(bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Float16Vector128Tests::blend);
    }

    @Test(dataProvider = "shortUnaryOpShuffleProvider")
    static void RearrangeFloat16Vector128Tests(IntFunction<short[]> fa,
                                           BiFunction<Integer,Integer,int[]> fs) {
        short[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.rearrange(VectorShuffle.fromArray(SPECIES, order, i)).intoArray(r, i);
            }
        }

        assertRearrangeArraysEquals(r, a, order, SPECIES.length());
    }

    @Test(dataProvider = "shortUnaryOpShuffleMaskProvider")
    static void RearrangeFloat16Vector128TestsMaskedSmokeTest(IntFunction<short[]> fa,
                                                          BiFunction<Integer,Integer,int[]> fs,
                                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.rearrange(VectorShuffle.fromArray(SPECIES, order, i), vmask).intoArray(r, i);
        }

        assertRearrangeArraysEquals(r, a, order, mask, SPECIES.length());
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void compressFloat16Vector128Tests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.compress(vmask).intoArray(r, i);
            }
        }

        assertcompressArraysEquals(r, a, mask, SPECIES.length());
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void expandFloat16Vector128Tests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.expand(vmask).intoArray(r, i);
            }
        }

        assertexpandArraysEquals(r, a, mask, SPECIES.length());
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void getFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                int num_lanes = SPECIES.length();
                // Manually unroll because full unroll happens after intrinsification.
                // Unroll is needed because get intrinsic requires for index to be a known constant.
                if (num_lanes == 1) {
                    r[i]=av.lane(0);
                } else if (num_lanes == 2) {
                    r[i]=av.lane(0);
                    r[i+1]=av.lane(1);
                } else if (num_lanes == 4) {
                    r[i]=av.lane(0);
                    r[i+1]=av.lane(1);
                    r[i+2]=av.lane(2);
                    r[i+3]=av.lane(3);
                } else if (num_lanes == 8) {
                    r[i]=av.lane(0);
                    r[i+1]=av.lane(1);
                    r[i+2]=av.lane(2);
                    r[i+3]=av.lane(3);
                    r[i+4]=av.lane(4);
                    r[i+5]=av.lane(5);
                    r[i+6]=av.lane(6);
                    r[i+7]=av.lane(7);
                } else if (num_lanes == 16) {
                    r[i]=av.lane(0);
                    r[i+1]=av.lane(1);
                    r[i+2]=av.lane(2);
                    r[i+3]=av.lane(3);
                    r[i+4]=av.lane(4);
                    r[i+5]=av.lane(5);
                    r[i+6]=av.lane(6);
                    r[i+7]=av.lane(7);
                    r[i+8]=av.lane(8);
                    r[i+9]=av.lane(9);
                    r[i+10]=av.lane(10);
                    r[i+11]=av.lane(11);
                    r[i+12]=av.lane(12);
                    r[i+13]=av.lane(13);
                    r[i+14]=av.lane(14);
                    r[i+15]=av.lane(15);
                } else if (num_lanes == 32) {
                    r[i]=av.lane(0);
                    r[i+1]=av.lane(1);
                    r[i+2]=av.lane(2);
                    r[i+3]=av.lane(3);
                    r[i+4]=av.lane(4);
                    r[i+5]=av.lane(5);
                    r[i+6]=av.lane(6);
                    r[i+7]=av.lane(7);
                    r[i+8]=av.lane(8);
                    r[i+9]=av.lane(9);
                    r[i+10]=av.lane(10);
                    r[i+11]=av.lane(11);
                    r[i+12]=av.lane(12);
                    r[i+13]=av.lane(13);
                    r[i+14]=av.lane(14);
                    r[i+15]=av.lane(15);
                    r[i+16]=av.lane(16);
                    r[i+17]=av.lane(17);
                    r[i+18]=av.lane(18);
                    r[i+19]=av.lane(19);
                    r[i+20]=av.lane(20);
                    r[i+21]=av.lane(21);
                    r[i+22]=av.lane(22);
                    r[i+23]=av.lane(23);
                    r[i+24]=av.lane(24);
                    r[i+25]=av.lane(25);
                    r[i+26]=av.lane(26);
                    r[i+27]=av.lane(27);
                    r[i+28]=av.lane(28);
                    r[i+29]=av.lane(29);
                    r[i+30]=av.lane(30);
                    r[i+31]=av.lane(31);
                } else if (num_lanes == 64) {
                    r[i]=av.lane(0);
                    r[i+1]=av.lane(1);
                    r[i+2]=av.lane(2);
                    r[i+3]=av.lane(3);
                    r[i+4]=av.lane(4);
                    r[i+5]=av.lane(5);
                    r[i+6]=av.lane(6);
                    r[i+7]=av.lane(7);
                    r[i+8]=av.lane(8);
                    r[i+9]=av.lane(9);
                    r[i+10]=av.lane(10);
                    r[i+11]=av.lane(11);
                    r[i+12]=av.lane(12);
                    r[i+13]=av.lane(13);
                    r[i+14]=av.lane(14);
                    r[i+15]=av.lane(15);
                    r[i+16]=av.lane(16);
                    r[i+17]=av.lane(17);
                    r[i+18]=av.lane(18);
                    r[i+19]=av.lane(19);
                    r[i+20]=av.lane(20);
                    r[i+21]=av.lane(21);
                    r[i+22]=av.lane(22);
                    r[i+23]=av.lane(23);
                    r[i+24]=av.lane(24);
                    r[i+25]=av.lane(25);
                    r[i+26]=av.lane(26);
                    r[i+27]=av.lane(27);
                    r[i+28]=av.lane(28);
                    r[i+29]=av.lane(29);
                    r[i+30]=av.lane(30);
                    r[i+31]=av.lane(31);
                    r[i+32]=av.lane(32);
                    r[i+33]=av.lane(33);
                    r[i+34]=av.lane(34);
                    r[i+35]=av.lane(35);
                    r[i+36]=av.lane(36);
                    r[i+37]=av.lane(37);
                    r[i+38]=av.lane(38);
                    r[i+39]=av.lane(39);
                    r[i+40]=av.lane(40);
                    r[i+41]=av.lane(41);
                    r[i+42]=av.lane(42);
                    r[i+43]=av.lane(43);
                    r[i+44]=av.lane(44);
                    r[i+45]=av.lane(45);
                    r[i+46]=av.lane(46);
                    r[i+47]=av.lane(47);
                    r[i+48]=av.lane(48);
                    r[i+49]=av.lane(49);
                    r[i+50]=av.lane(50);
                    r[i+51]=av.lane(51);
                    r[i+52]=av.lane(52);
                    r[i+53]=av.lane(53);
                    r[i+54]=av.lane(54);
                    r[i+55]=av.lane(55);
                    r[i+56]=av.lane(56);
                    r[i+57]=av.lane(57);
                    r[i+58]=av.lane(58);
                    r[i+59]=av.lane(59);
                    r[i+60]=av.lane(60);
                    r[i+61]=av.lane(61);
                    r[i+62]=av.lane(62);
                    r[i+63]=av.lane(63);
                } else {
                    for (int j = 0; j < SPECIES.length(); j++) {
                        r[i+j]=av.lane(j);
                    }
                }
            }
        }

        assertArraysStrictlyEquals(r, a);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void BroadcastFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector.broadcast(SPECIES, a[i]).intoArray(r, i);
            }
        }

        assertBroadcastArraysEquals(r, a);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ZeroFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector.zero(SPECIES).intoArray(a, i);
            }
        }

        AssertEquals(a, r);
    }

    static short[] sliceUnary(short[] a, int origin, int idx) {
        short[] res = new short[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = a[idx+i+origin];
            else
                res[i] = (short)0;
        }
        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void sliceUnaryFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.slice(origin).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, origin, Float16Vector128Tests::sliceUnary);
    }

    static short[] sliceBinary(short[] a, short[] b, int origin, int idx) {
        short[] res = new short[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = a[idx+i+origin];
            else {
                res[i] = b[idx+j];
                j++;
            }
        }
        return res;
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void sliceBinaryFloat16Vector128TestsBinary(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.slice(origin, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, Float16Vector128Tests::sliceBinary);
    }

    static short[] slice(short[] a, short[] b, int origin, boolean[] mask, int idx) {
        short[] res = new short[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = mask[i] ? a[idx+i+origin] : (short)0;
            else {
                res[i] = mask[i] ? b[idx+j] : (short)0;
                j++;
            }
        }
        return res;
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void sliceFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
    IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        short[] r = new short[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.slice(origin, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, mask, Float16Vector128Tests::slice);
    }

    static short[] unsliceUnary(short[] a, int origin, int idx) {
        short[] res = new short[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i < origin)
                res[i] = (short)0;
            else {
                res[i] = a[idx+j];
                j++;
            }
        }
        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void unsliceUnaryFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.unslice(origin).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, origin, Float16Vector128Tests::unsliceUnary);
    }

    static short[] unsliceBinary(short[] a, short[] b, int origin, int part, int idx) {
        short[] res = new short[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if (part == 0) {
                if (i < origin)
                    res[i] = b[idx+i];
                else {
                    res[i] = a[idx+j];
                    j++;
                }
            } else if (part == 1) {
                if (i < origin)
                    res[i] = a[idx+SPECIES.length()-origin+i];
                else {
                    res[i] = b[idx+origin+j];
                    j++;
                }
            }
        }
        return res;
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void unsliceBinaryFloat16Vector128TestsBinary(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        int part = RAND.nextInt(2);
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.unslice(origin, bv, part).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, part, Float16Vector128Tests::unsliceBinary);
    }

    static short[] unslice(short[] a, short[] b, int origin, int part, boolean[] mask, int idx) {
        short[] res = new short[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = b[idx+i+origin];
            else {
                res[i] = b[idx+j];
                j++;
            }
        }
        for (int i = 0; i < SPECIES.length(); i++){
            res[i] = mask[i] ? a[idx+i] : res[i];
        }
        short[] res1 = new short[SPECIES.length()];
        if (part == 0) {
            for (int i = 0, j = 0; i < SPECIES.length(); i++){
                if (i < origin)
                    res1[i] = b[idx+i];
                else {
                   res1[i] = res[j];
                   j++;
                }
            }
        } else if (part == 1) {
            for (int i = 0, j = 0; i < SPECIES.length(); i++){
                if (i < origin)
                    res1[i] = res[SPECIES.length()-origin+i];
                else {
                    res1[i] = b[idx+origin+j];
                    j++;
                }
            }
        }
        return res1;
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void unsliceFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
    IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        short[] r = new short[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        int part = RAND.nextInt(2);
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.unslice(origin, bv, part, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, part, mask, Float16Vector128Tests::unslice);
    }

    static short SIN(short a) {
        return (short)(scalar_sin(a));
    }

    static short strictSIN(short a) {
        return (short)(strict_scalar_sin(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void SINFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SIN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::SIN, Float16Vector128Tests::strictSIN);
    }

    static short EXP(short a) {
        return (short)(scalar_exp(a));
    }

    static short strictEXP(short a) {
        return (short)(strict_scalar_exp(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void EXPFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.EXP).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::EXP, Float16Vector128Tests::strictEXP);
    }

    static short LOG1P(short a) {
        return (short)(scalar_log1p(a));
    }

    static short strictLOG1P(short a) {
        return (short)(strict_scalar_log1p(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void LOG1PFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG1P).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::LOG1P, Float16Vector128Tests::strictLOG1P);
    }

    static short LOG(short a) {
        return (short)(scalar_log(a));
    }

    static short strictLOG(short a) {
        return (short)(strict_scalar_log(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void LOGFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::LOG, Float16Vector128Tests::strictLOG);
    }

    static short LOG10(short a) {
        return (short)(scalar_log10(a));
    }

    static short strictLOG10(short a) {
        return (short)(strict_scalar_log10(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void LOG10Float16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG10).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::LOG10, Float16Vector128Tests::strictLOG10);
    }

    static short EXPM1(short a) {
        return (short)(scalar_expm1(a));
    }

    static short strictEXPM1(short a) {
        return (short)(strict_scalar_expm1(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void EXPM1Float16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.EXPM1).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::EXPM1, Float16Vector128Tests::strictEXPM1);
    }

    static short COS(short a) {
        return (short)(scalar_cos(a));
    }

    static short strictCOS(short a) {
        return (short)(strict_scalar_cos(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void COSFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.COS).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::COS, Float16Vector128Tests::strictCOS);
    }

    static short TAN(short a) {
        return (short)(scalar_tan(a));
    }

    static short strictTAN(short a) {
        return (short)(strict_scalar_tan(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void TANFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TAN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::TAN, Float16Vector128Tests::strictTAN);
    }

    static short SINH(short a) {
        return (short)(scalar_sinh(a));
    }

    static short strictSINH(short a) {
        return (short)(strict_scalar_sinh(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void SINHFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SINH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::SINH, Float16Vector128Tests::strictSINH);
    }

    static short COSH(short a) {
        return (short)(scalar_cosh(a));
    }

    static short strictCOSH(short a) {
        return (short)(strict_scalar_cosh(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void COSHFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.COSH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::COSH, Float16Vector128Tests::strictCOSH);
    }

    static short TANH(short a) {
        return (short)(scalar_tanh(a));
    }

    static short strictTANH(short a) {
        return (short)(strict_scalar_tanh(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void TANHFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TANH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::TANH, Float16Vector128Tests::strictTANH);
    }

    static short ASIN(short a) {
        return (short)(scalar_asin(a));
    }

    static short strictASIN(short a) {
        return (short)(strict_scalar_asin(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ASINFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ASIN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::ASIN, Float16Vector128Tests::strictASIN);
    }

    static short ACOS(short a) {
        return (short)(scalar_acos(a));
    }

    static short strictACOS(short a) {
        return (short)(strict_scalar_acos(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ACOSFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ACOS).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::ACOS, Float16Vector128Tests::strictACOS);
    }

    static short ATAN(short a) {
        return (short)(scalar_atan(a));
    }

    static short strictATAN(short a) {
        return (short)(strict_scalar_atan(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ATANFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ATAN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::ATAN, Float16Vector128Tests::strictATAN);
    }

    static short CBRT(short a) {
        return (short)(scalar_cbrt(a));
    }

    static short strictCBRT(short a) {
        return (short)(strict_scalar_cbrt(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void CBRTFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.CBRT).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Float16Vector128Tests::CBRT, Float16Vector128Tests::strictCBRT);
    }

    static short HYPOT(short a, short b) {
        return (short)(scalar_hypot(a, b));
    }

    static short strictHYPOT(short a, short b) {
        return (short)(strict_scalar_hypot(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void HYPOTFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.HYPOT, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Float16Vector128Tests::HYPOT, Float16Vector128Tests::strictHYPOT);
    }


    static short POW(short a, short b) {
        return (short)(scalar_pow(a, b));
    }

    static short strictPOW(short a, short b) {
        return (short)(strict_scalar_pow(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void POWFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.POW, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Float16Vector128Tests::POW, Float16Vector128Tests::strictPOW);
    }


    static short pow(short a, short b) {
        return (short)(scalar_pow(a, b));
    }

    static short strictpow(short a, short b) {
        return (short)(strict_scalar_pow(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void powFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.pow(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Float16Vector128Tests::pow, Float16Vector128Tests::strictpow);
    }


    static short ATAN2(short a, short b) {
        return (short)(scalar_atan2(a, b));
    }

    static short strictATAN2(short a, short b) {
        return (short)(strict_scalar_atan2(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void ATAN2Float16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ATAN2, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Float16Vector128Tests::ATAN2, Float16Vector128Tests::strictATAN2);
    }


    @Test(dataProvider = "shortBinaryOpProvider")
    static void POWFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.POW, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEqualsWithinOneUlp(r, a, b, Float16Vector128Tests::POW, Float16Vector128Tests::strictPOW);
    }


    @Test(dataProvider = "shortBinaryOpProvider")
    static void powFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.pow(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEqualsWithinOneUlp(r, a, b, Float16Vector128Tests::pow, Float16Vector128Tests::strictpow);
    }


    static short FMA(short a, short b, short c) {
        return (short)(scalar_fma(a, b, c));
    }

    static short fma(short a, short b, short c) {
        return (short)(scalar_fma(a, b, c));
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void FMAFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                Float16Vector cv = Float16Vector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.FMA, bv, cv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, c, Float16Vector128Tests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void fmaFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            Float16Vector cv = Float16Vector.fromArray(SPECIES, c, i);
            av.fma(bv, cv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, c, Float16Vector128Tests::fma);
    }

    @Test(dataProvider = "shortTernaryOpMaskProvider")
    static void FMAFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<short[]> fc, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                Float16Vector cv = Float16Vector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.FMA, bv, cv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, c, mask, Float16Vector128Tests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void FMAFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.lanewise(VectorOperators.FMA, bv, c[i]).intoArray(r, i);
        }
        assertBroadcastArraysEquals(r, a, b, c, Float16Vector128Tests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void FMAFloat16Vector128TestsAltBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector cv = Float16Vector.fromArray(SPECIES, c, i);
            av.lanewise(VectorOperators.FMA, b[i], cv).intoArray(r, i);
        }
        assertAltBroadcastArraysEquals(r, a, b, c, Float16Vector128Tests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpMaskProvider")
    static void FMAFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<short[]> fc, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
            av.lanewise(VectorOperators.FMA, bv, c[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, c, mask, Float16Vector128Tests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpMaskProvider")
    static void FMAFloat16Vector128TestsAltBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<short[]> fc, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector cv = Float16Vector.fromArray(SPECIES, c, i);
            av.lanewise(VectorOperators.FMA, b[i], cv, vmask).intoArray(r, i);
        }

        assertAltBroadcastArraysEquals(r, a, b, c, mask, Float16Vector128Tests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void FMAFloat16Vector128TestsDoubleBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.FMA, b[i], c[i]).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(r, a, b, c, Float16Vector128Tests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void fmaFloat16Vector128TestsDoubleBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.fma(b[i], c[i]).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(r, a, b, c, Float16Vector128Tests::fma);
    }

    @Test(dataProvider = "shortTernaryOpMaskProvider")
    static void FMAFloat16Vector128TestsDoubleBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<short[]> fc, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.FMA, b[i], c[i], vmask).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(r, a, b, c, mask, Float16Vector128Tests::FMA);
    }

    static short NEG(short a) {
        return (short)(scalar_neg((short)a));
    }

    static short neg(short a) {
        return (short)(scalar_neg((short)a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void NEGFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Float16Vector128Tests::NEG);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void negFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.neg().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Float16Vector128Tests::neg);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void NEGMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, Float16Vector128Tests::NEG);
    }

    static short ABS(short a) {
        return (short)(scalar_abs((short)a));
    }

    static short abs(short a) {
        return (short)(scalar_abs((short)a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ABSFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Float16Vector128Tests::ABS);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void absFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.abs().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Float16Vector128Tests::abs);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void ABSMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, Float16Vector128Tests::ABS);
    }

    static short SQRT(short a) {
        return (short)(scalar_sqrt(a));
    }

    static short sqrt(short a) {
        return (short)(scalar_sqrt(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void SQRTFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SQRT).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Float16Vector128Tests::SQRT);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void sqrtFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.sqrt().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Float16Vector128Tests::sqrt);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void SQRTMaskedFloat16Vector128Tests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SQRT, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, Float16Vector128Tests::SQRT);
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void ltFloat16Vector128TestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.lt(b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), lt(a[i + j], b[i]));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void eqFloat16Vector128TestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.eq(b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                AssertEquals(mv.laneIsSet(j), eq(a[i + j], b[i]));
            }
        }
    }

    @Test(dataProvider = "shorttoIntUnaryOpProvider")
    static void toIntArrayFloat16Vector128TestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            int[] r = av.toIntArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "shorttoLongUnaryOpProvider")
    static void toLongArrayFloat16Vector128TestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            long[] r = av.toLongArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void toDoubleArrayFloat16Vector128TestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            double[] r = av.toDoubleArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void toStringFloat16Vector128TestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            String str = av.toString();

            short subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            Assert.assertTrue(str.equals(Arrays.toString(subarr)), "at index " + i + ", string should be = " + Arrays.toString(subarr) + ", but is = " + str);
        }
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void hashCodeFloat16Vector128TestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            int hash = av.hashCode();

            short subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            int expectedHash = Objects.hash(SPECIES, Arrays.hashCode(subarr));
            Assert.assertTrue(hash == expectedHash, "at index " + i + ", hash should be = " + expectedHash + ", but is = " + hash);
        }
    }


    static long ADDReduceLong(short[] a, int idx) {
        short res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = scalar_add(res, a[i]);
        }

        return (long)res;
    }

    static long ADDReduceAllLong(short[] a) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (long)scalar_add((short)res, (short)ADDReduceLong(a, i));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ADDReduceLongFloat16Vector128Tests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD);
        }

        ra = 0;
        for (int i = 0; i < a.length; i++) {
            ra = (long)scalar_add((short)ra, (short)r[i]);
        }

        assertReductionLongArraysEquals(r, ra, a,
                Float16Vector128Tests::ADDReduceLong, Float16Vector128Tests::ADDReduceAllLong);
    }

    static long ADDReduceLongMasked(short[] a, int idx, boolean[] mask) {
        short res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if(mask[i % SPECIES.length()]) {
                res = scalar_add(res, a[i]);
            }
        }

        return (long)res;
    }

    static long ADDReduceAllLongMasked(short[] a, boolean[] mask) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (long)scalar_add((short)res, (short)ADDReduceLongMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void ADDReduceLongFloat16Vector128TestsMasked(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD, vmask);
        }

        ra = 0;
        for (int i = 0; i < a.length; i++) {
            ra = (long)scalar_add((short)ra, (short)r[i]);
        }

        assertReductionLongArraysEqualsMasked(r, ra, a, mask,
                Float16Vector128Tests::ADDReduceLongMasked, Float16Vector128Tests::ADDReduceAllLongMasked);
    }

    @Test(dataProvider = "shorttoLongUnaryOpProvider")
    static void BroadcastLongFloat16Vector128TestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector.broadcast(SPECIES, shortBitsToFloat16(a[i]).longValue()).intoArray(r, i);
        }
        assertBroadcastArraysEquals(r, a);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void blendFloat16Vector128TestsBroadcastLongSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                av.blend(shortBitsToFloat16(b[i]).longValue(), vmask).intoArray(r, i);
            }
        }
        assertBroadcastLongArraysEquals(r, a, b, mask, Float16Vector128Tests::blend);
    }


    @Test(dataProvider = "shortUnaryOpSelectFromProvider")
    static void SelectFromFloat16Vector128Tests(IntFunction<short[]> fa,
                                           BiFunction<Integer,Integer,short[]> fs) {
        short[] a = fa.apply(SPECIES.length());
        short[] order = fs.apply(a.length, SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, order, i);
            bv.selectFrom(av).intoArray(r, i);
        }

        assertSelectFromArraysEquals(r, a, order, SPECIES.length());
    }

    @Test(dataProvider = "shortSelectFromTwoVectorOpProvider")
    static void SelectFromTwoVectorFloat16Vector128Tests(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] idx = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < idx.length; i += SPECIES.length()) {
                Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
                Float16Vector bv = Float16Vector.fromArray(SPECIES, b, i);
                Float16Vector idxv = Float16Vector.fromArray(SPECIES, idx, i);
                idxv.selectFrom(av, bv).intoArray(r, i);
            }
        }
        assertSelectFromTwoVectorEquals(r, idx, a, b, SPECIES.length());
    }

    @Test(dataProvider = "shortUnaryOpSelectFromMaskProvider")
    static void SelectFromFloat16Vector128TestsMaskedSmokeTest(IntFunction<short[]> fa,
                                                           BiFunction<Integer,Integer,short[]> fs,
                                                           IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] order = fs.apply(a.length, SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            Float16Vector av = Float16Vector.fromArray(SPECIES, a, i);
            Float16Vector bv = Float16Vector.fromArray(SPECIES, order, i);
            bv.selectFrom(av, vmask).intoArray(r, i);
        }

        assertSelectFromArraysEquals(r, a, order, mask, SPECIES.length());
    }

    @Test(dataProvider = "shuffleProvider")
    static void shuffleMiscellaneousFloat16Vector128TestsSmokeTest(BiFunction<Integer,Integer,int[]> fs) {
        int[] a = fs.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var shuffle = VectorShuffle.fromArray(SPECIES, a, i);
            int hash = shuffle.hashCode();
            int length = shuffle.length();

            int subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            int expectedHash = Objects.hash(SPECIES, Arrays.hashCode(subarr));
            Assert.assertTrue(hash == expectedHash, "at index " + i + ", hash should be = " + expectedHash + ", but is = " + hash);
            AssertEquals(length, SPECIES.length());
        }
    }

    @Test(dataProvider = "shuffleProvider")
    static void shuffleToStringFloat16Vector128TestsSmokeTest(BiFunction<Integer,Integer,int[]> fs) {
        int[] a = fs.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var shuffle = VectorShuffle.fromArray(SPECIES, a, i);
            String str = shuffle.toString();

            int subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            Assert.assertTrue(str.equals("Shuffle" + Arrays.toString(subarr)), "at index " +
                i + ", string should be = " + Arrays.toString(subarr) + ", but is = " + str);
        }
    }

    @Test(dataProvider = "shuffleCompareOpProvider")
    static void shuffleEqualsFloat16Vector128TestsSmokeTest(BiFunction<Integer,Integer,int[]> fa, BiFunction<Integer,Integer,int[]> fb) {
        int[] a = fa.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());
        int[] b = fb.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = VectorShuffle.fromArray(SPECIES, a, i);
            var bv = VectorShuffle.fromArray(SPECIES, b, i);
            boolean eq = av.equals(bv);
            int to = i + SPECIES.length();
            AssertEquals(eq, Arrays.equals(a, i, to, b, i, to));
        }
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskEqualsFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            boolean equals = av.equals(bv);
            int to = i + SPECIES.length();
            AssertEquals(equals, Arrays.equals(a, i, to, b, i, to));
        }
    }

    static boolean band(boolean a, boolean b) {
        return a & b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskAndFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.and(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Float16Vector128Tests::band);
    }

    static boolean bor(boolean a, boolean b) {
        return a | b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskOrFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.or(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Float16Vector128Tests::bor);
    }

    static boolean bxor(boolean a, boolean b) {
        return a != b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskXorFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.xor(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Float16Vector128Tests::bxor);
    }

    static boolean bandNot(boolean a, boolean b) {
        return a & !b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskAndNotFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.andNot(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Float16Vector128Tests::bandNot);
    }

    static boolean beq(boolean a, boolean b) {
        return (a == b);
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskEqFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.eq(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Float16Vector128Tests::beq);
    }

    @Test(dataProvider = "maskProvider")
    static void maskHashCodeFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var vmask = SPECIES.loadMask(a, i);
            int hash = vmask.hashCode();

            boolean subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            int expectedHash = Objects.hash(SPECIES, Arrays.hashCode(subarr));
            Assert.assertTrue(hash == expectedHash, "at index " + i + ", hash should be = " + expectedHash + ", but is = " + hash);
        }
    }

    static int maskTrueCount(boolean[] a, int idx) {
        int trueCount = 0;
        for (int i = idx; i < idx + SPECIES.length(); i++) {
            trueCount += a[i] ? 1 : 0;
        }
        return trueCount;
    }

    @Test(dataProvider = "maskProvider")
    static void maskTrueCountFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.trueCount();
            }
        }

        assertMaskReductionArraysEquals(r, a, Float16Vector128Tests::maskTrueCount);
    }

    static int maskLastTrue(boolean[] a, int idx) {
        int i = idx + SPECIES.length() - 1;
        for (; i >= idx; i--) {
            if (a[i]) {
                break;
            }
        }
        return i - idx;
    }

    @Test(dataProvider = "maskProvider")
    static void maskLastTrueFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.lastTrue();
            }
        }

        assertMaskReductionArraysEquals(r, a, Float16Vector128Tests::maskLastTrue);
    }

    static int maskFirstTrue(boolean[] a, int idx) {
        int i = idx;
        for (; i < idx + SPECIES.length(); i++) {
            if (a[i]) {
                break;
            }
        }
        return i - idx;
    }

    @Test(dataProvider = "maskProvider")
    static void maskFirstTrueFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.firstTrue();
            }
        }

        assertMaskReductionArraysEquals(r, a, Float16Vector128Tests::maskFirstTrue);
    }

    @Test(dataProvider = "maskProvider")
    static void maskCompressFloat16Vector128TestsSmokeTest(IntFunction<boolean[]> fa) {
        int trueCount = 0;
        boolean[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                trueCount = vmask.trueCount();
                var rmask = vmask.compress();
                for (int j = 0; j < SPECIES.length(); j++)  {
                    AssertEquals(rmask.laneIsSet(j), j < trueCount);
                }
            }
        }
    }

    @DataProvider
    public static Object[][] longMaskProvider() {
        return new Object[][]{
                {0xFFFFFFFFFFFFFFFFL},
                {0x0000000000000000L},
                {0x5555555555555555L},
                {0x0123456789abcdefL},
        };
    }

    @Test(dataProvider = "longMaskProvider")
    static void maskFromToLongFloat16Vector128TestsSmokeTest(long inputLong) {
        var vmask = VectorMask.fromLong(SPECIES, inputLong);
        long outputLong = vmask.toLong();
        AssertEquals(outputLong, (inputLong & (((0xFFFFFFFFFFFFFFFFL >>> (64 - SPECIES.length()))))));
    }

    @DataProvider
    public static Object[][] offsetProvider() {
        return new Object[][]{
                {0},
                {-1},
                {+1},
                {+2},
                {-2},
        };
    }

    @Test(dataProvider = "offsetProvider")
    static void indexInRangeFloat16Vector128TestsSmokeTest(int offset) {
        int limit = SPECIES.length() * BUFFER_REPS;
        for (int i = 0; i < limit; i += SPECIES.length()) {
            var actualMask = SPECIES.indexInRange(i + offset, limit);
            var expectedMask = SPECIES.maskAll(true).indexInRange(i + offset, limit);
            assert(actualMask.equals(expectedMask));
            for (int j = 0; j < SPECIES.length(); j++)  {
                int index = i + j + offset;
                AssertEquals(actualMask.laneIsSet(j), index >= 0 && index < limit);
            }
        }
    }

    @Test(dataProvider = "offsetProvider")
    static void indexInRangeLongFloat16Vector128TestsSmokeTest(int offset) {
        long limit = SPECIES.length() * BUFFER_REPS;
        for (long i = 0; i < limit; i += SPECIES.length()) {
            var actualMask = SPECIES.indexInRange(i + offset, limit);
            var expectedMask = SPECIES.maskAll(true).indexInRange(i + offset, limit);
            assert(actualMask.equals(expectedMask));
            for (int j = 0; j < SPECIES.length(); j++)  {
                long index = i + j + offset;
                AssertEquals(actualMask.laneIsSet(j), index >= 0 && index < limit);
            }
        }
    }

    @DataProvider
    public static Object[][] lengthProvider() {
        return new Object[][]{
                {0},
                {1},
                {32},
                {37},
                {1024},
                {1024+1},
                {1024+5},
        };
    }

    @Test(dataProvider = "lengthProvider")
    static void loopBoundFloat16Vector128TestsSmokeTest(int length) {
        int actualLoopBound = SPECIES.loopBound(length);
        int expectedLoopBound = length - Math.floorMod(length, SPECIES.length());
        AssertEquals(actualLoopBound, expectedLoopBound);
    }

    @Test(dataProvider = "lengthProvider")
    static void loopBoundLongFloat16Vector128TestsSmokeTest(int _length) {
        long length = _length;
        long actualLoopBound = SPECIES.loopBound(length);
        long expectedLoopBound = length - Math.floorMod(length, SPECIES.length());
        AssertEquals(actualLoopBound, expectedLoopBound);
    }

    @Test
    static void ElementSizeFloat16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        int elsize = av.elementSize();
        AssertEquals(elsize, Float16.SIZE);
    }

    @Test
    static void VectorShapeFloat16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        VectorShape vsh = av.shape();
        assert(vsh.equals(VectorShape.S_128_BIT));
    }

    @Test
    static void ShapeWithLanesFloat16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        VectorShape vsh = av.shape();
        VectorSpecies species = vsh.withLanes(Float16.class);
        assert(species.equals(SPECIES));
    }

    @Test
    static void Float16Float16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        assert(av.species().elementType() == Float16.class);
    }

    @Test
    static void SpeciesElementSizeFloat16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        assert(av.species().elementSize() == Float16.SIZE);
    }

    @Test
    static void VectorTypeFloat16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        assert(av.species().vectorType() == av.getClass());
    }

    @Test
    static void WithLanesFloat16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        VectorSpecies species = av.species().withLanes(Float16.class);
        assert(species.equals(SPECIES));
    }

    @Test
    static void WithShapeFloat16Vector128TestsSmokeTest() {
        Float16Vector av = Float16Vector.zero(SPECIES);
        VectorShape vsh = av.shape();
        VectorSpecies species = av.species().withShape(vsh);
        assert(species.equals(SPECIES));
    }

    @Test
    static void MaskAllTrueFloat16Vector128TestsSmokeTest() {
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
          AssertEquals(SPECIES.maskAll(true).toLong(), -1L >>> (64 - SPECIES.length()));
        }
    }
}
