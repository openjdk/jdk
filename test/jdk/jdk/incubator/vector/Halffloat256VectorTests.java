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
 * @run testng/othervm/timeout=300 -ea -esa -Xbatch -XX:TieredStopAtLevel=3 Halffloat256VectorTests
 */

// -- This file was mechanically generated: Do not edit! -- //

import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.Vector;

import jdk.incubator.vector.Float16;
import jdk.incubator.vector.HalffloatVector;

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
public class Halffloat256VectorTests extends AbstractVectorTest {

    static final VectorSpecies<Float16> SPECIES =
                HalffloatVector.SPECIES_256;

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);

    static HalffloatVector bcast_vec = HalffloatVector.broadcast(SPECIES, (short)10);


    // for floating point addition reduction ops that may introduce rounding errors
    private static final short RELATIVE_ROUNDING_ERROR_FACTOR_ADD = (short)10.0;

    // for floating point multiplication reduction ops that may introduce rounding errors
    private static final short RELATIVE_ROUNDING_ERROR_FACTOR_MUL = (short)50.0;

    static final int BUFFER_REPS = Integer.getInteger("jdk.incubator.vector.test.buffer-vectors", 25000 / 256);

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
                Assert.assertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i]), "at index #" + i + ", input = " + a[i]);
        }
    }

    interface FUnArrayOp {
        short[] apply(short a);
    }

    static void assertArraysEquals(short[] r, short[] a, FUnArrayOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a[i]));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a[i]);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, boolean[] mask, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], "at index #" + i + ", input = " + a[i] + ", mask = " + mask[i % SPECIES.length()]);
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
            Assert.assertEquals(rc, fa.apply(a), Math.ulp(rc) * relativeErrorFactor);
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(r[i], f.apply(a, i), Math.ulp(r[i]) * relativeErrorFactor);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(rc, fa.apply(a), Math.ulp(rc) * relativeErrorFactor, "Final result is incorrect!");
            Assert.assertEquals(r[i], f.apply(a, i), Math.ulp(r[i]) * relativeErrorFactor, "at index #" + i);
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
            Assert.assertEquals(rc, fa.apply(a, mask), Math.abs(rc * relativeError));
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(r[i], f.apply(a, i, mask), Math.abs(r[i] *
relativeError));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(rc, fa.apply(a, mask), Math.abs(rc * relativeError), "Final result is incorrect!");
            Assert.assertEquals(r[i], f.apply(a, i, mask), Math.abs(r[i] * relativeError), "at index #" + i);
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
            Assert.assertEquals(rc, fa.apply(a));
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(rc, fa.apply(a), "Final result is incorrect!");
            Assert.assertEquals(r[i], f.apply(a, i), "at index #" + i);
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
            Assert.assertEquals(rc, fa.apply(a, mask));
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(r[i], f.apply(a, i, mask));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(rc, fa.apply(a, mask), "Final result is incorrect!");
            Assert.assertEquals(r[i], f.apply(a, i, mask), "at index #" + i);
        }
    }

    interface FBoolReductionOp {
        boolean apply(boolean[] a, int idx);
    }

    static void assertReductionBoolArraysEquals(boolean[] r, boolean[] a, FBoolReductionOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FMaskReductionOp {
        int apply(boolean[] a, int idx);
    }

    static void assertMaskReductionArraysEquals(int[] r, boolean[] a, FMaskReductionOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    static void assertRearrangeArraysEquals(short[] r, short[] a, int[] order, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    Assert.assertEquals(r[i+j], a[i+order[i+j]]);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            Assert.assertEquals(r[i+j], a[i+order[i+j]], "at index #" + idx + ", input = " + a[i+order[i+j]]);
        }
    }

    static void assertcompressArraysEquals(short[] r, short[] a, boolean[] m, int vector_len) {
        int i = 0, j = 0, k = 0;
        try {
            for (; i < a.length; i += vector_len) {
                k = 0;
                for (j = 0; j < vector_len; j++) {
                    if (m[(i + j) % SPECIES.length()]) {
                        Assert.assertEquals(r[i + k], a[i + j]);
                        k++;
                    }
                }
                for (; k < vector_len; k++) {
                    Assert.assertEquals(r[i + k], (short)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + k;
            if (m[(i + j) % SPECIES.length()]) {
                Assert.assertEquals(r[idx], a[i + j], "at index #" + idx);
            } else {
                Assert.assertEquals(r[idx], (short)0, "at index #" + idx);
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
                        Assert.assertEquals(r[i + j], a[i + k]);
                        k++;
                    } else {
                        Assert.assertEquals(r[i + j], (short)0);
                    }
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (m[idx % SPECIES.length()]) {
                Assert.assertEquals(r[idx], a[i + k], "at index #" + idx);
            } else {
                Assert.assertEquals(r[idx], (short)0, "at index #" + idx);
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
                    wrapped_index = Math.floorMod(Float16.shortBitsToFloat16(order[idx]).intValue(), 2 * vector_len);
                    is_exceptional_idx = wrapped_index >= vector_len;
                    oidx = is_exceptional_idx ? (wrapped_index - vector_len) : wrapped_index;
                    Assert.assertEquals(r[idx], (is_exceptional_idx ? b[i + oidx] : a[i + oidx]));
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[idx], (is_exceptional_idx ? b[i + oidx] : a[i + oidx]), "at index #" + idx + ", order = " + order[idx] + ", a = " + a[i + oidx] + ", b = " + b[i + oidx]);
        }
    }

    static void assertSelectFromArraysEquals(short[] r, short[] a, short[] order, int vector_len) {
        int i = 0, j = 0;
        int idx = 0, wrapped_index = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    idx = Float16.shortBitsToFloat16(order[i+j]).intValue();
                    wrapped_index = Integer.remainderUnsigned(idx, vector_len);
                    Assert.assertEquals(r[i+j], a[i+wrapped_index]);
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i+j], a[i+wrapped_index], "at index #" + idx + ", input = " + a[i+wrapped_index]);
        }
    }

    static void assertRearrangeArraysEquals(short[] r, short[] a, int[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    if (mask[j % SPECIES.length()])
                         Assert.assertEquals(r[i+j], a[i+order[i+j]]);
                    else
                         Assert.assertEquals(r[i+j], (short)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (mask[j % SPECIES.length()])
                Assert.assertEquals(r[i+j], a[i+order[i+j]], "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
            else
                Assert.assertEquals(r[i+j], (short)0, "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
        }
    }

    static void assertSelectFromArraysEquals(short[] r, short[] a, short[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        int idx = 0, wrapped_index = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    idx = Float16.shortBitsToFloat16(order[i+j]).intValue();
                    wrapped_index = Integer.remainderUnsigned(idx, vector_len);
                    if (mask[j % SPECIES.length()])
                         Assert.assertEquals(r[i+j], a[i+wrapped_index]);
                    else
                         Assert.assertEquals(r[i+j], (short)0);
                }
            }
        } catch (AssertionError e) {
            if (mask[j % SPECIES.length()])
                Assert.assertEquals(r[i+j], a[i+wrapped_index], "at index #" + idx + ", input = " + a[i+wrapped_index] + ", mask = " + mask[j % SPECIES.length()]);
            else
                Assert.assertEquals(r[i+j], (short)0, "at index #" + idx + ", input = " + a[i+wrapped_index] + ", mask = " + mask[j % SPECIES.length()]);
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
                Assert.assertEquals(r[i], a[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], a[i], "at index #" + i + ", input = " + a[i]);
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
                Assert.assertEquals(rl[i], f.apply(f.apply(a[i], b[i]), c[i]));

                //Right associative
                Assert.assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i])));

                //Results equal sanity check
                Assert.assertEquals(rl[i], rr[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(rl[i], f.apply(f.apply(a[i], b[i]), c[i]), "left associative test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
            Assert.assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i])), "right associative test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
            Assert.assertEquals(rl[i], rr[i], "Result checks not equal at index #" + i + "leftRes = " + rl[i] + ", rightRes = " + rr[i]);
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
                Assert.assertEquals(rl[i], f.apply(f.apply(a[i], b[i], mask_bit), c[i], mask_bit));

                //Right associative
                Assert.assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i], mask_bit), mask_bit));

                //Results equal sanity check
                Assert.assertEquals(rl[i], rr[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(rl[i], f.apply(f.apply(a[i], b[i], mask_bit), c[i], mask_bit), "left associative masked test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i] + ", mask = " + mask_bit);
            Assert.assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i], mask_bit), mask_bit), "right associative masked test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i] + ", mask = " + mask_bit);
            Assert.assertEquals(rl[i], rr[i], "Result checks not equal at index #" + i + "leftRes = " + rl[i] + ", rightRes = " + rr[i]);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i]), "(" + a[i] + ", " + b[i] + ") at index #" + i);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b), "(" + a[i] + ", " + b + ") at index #" + i);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]),
                                "(" + a[i] + ", " + b[(i / SPECIES.length()) * SPECIES.length()] + ") at index #" + i);
        }
    }

    static void assertBroadcastLongArraysEquals(short[] r, short[] a, short[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], (short)((long)b[(i / SPECIES.length()) * SPECIES.length()])));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], (short)((long)b[(i / SPECIES.length()) * SPECIES.length()])),
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
                Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short b, boolean[] mask, FBinOp f) {
        assertArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(short[] r, short[] a, short b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b, mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b, mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinOp f) {
        assertBroadcastArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
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
                Assert.assertEquals(r[i], f.apply(a[i], (short)((long)b[(i / SPECIES.length()) * SPECIES.length()]), mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], (short)((long)b[(i / SPECIES.length()) * SPECIES.length()]),
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
                    Assert.assertEquals(r[i+j], f.apply(a[i+j], b[j]));
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i+j], f.apply(a[i+j], b[j]), "at index #" + i + ", " + j);
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
                    Assert.assertEquals(r[i+j], f.apply(a[i+j], b[j], mask[i]));
                }
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i+j], f.apply(a[i+j], b[j], mask[i]), "at index #" + i + ", input1 = " + a[i+j] + ", input2 = " + b[j] + ", mask = " + mask[i]);
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
                    Assert.assertEquals(r[i+j], f.apply(a[i+j]));
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i+j], f.apply(a[i+j]), "at index #" + i + ", " + j);
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
                    Assert.assertEquals(r[i+j], f.apply(a[i+j], mask[i]));
                }
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i+j], f.apply(a[i+j], mask[i]), "at index #" + i + ", input1 = " + a[i+j] + ", mask = " + mask[i]);
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
                Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
        }
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask, FTernOp f) {
        assertArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertArraysEquals(short[] r, short[] a, short[] b, short[] c, boolean[] mask, FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = "
              + b[i] + ", input3 = " + c[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()]), "at index #" +
                                i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " +
                                c[(i / SPECIES.length()) * SPECIES.length()]);
        }
    }

    static void assertAltBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i]), "at index #" +
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
                Assert.assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()],
                                    mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()],
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
                Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i],
                                    mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i],
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] +
                                ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] +
                                ", input3 = " + c[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertDoubleBroadcastArraysEquals(short[] r, short[] a, short[] b, short[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                    c[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
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
                Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                    c[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                c[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]), "at index #"
                                + i + ", input1 = " + a[i] + ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] +
                                ", input3 = " + c[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }


    static boolean isWithin1Ulp(short actual, short expected) {
        Float16 act = Float16.shortBitsToFloat16(actual);
        Float16 exp = Float16.shortBitsToFloat16(expected);
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
                Assert.assertTrue(Float16.compare(Float16.shortBitsToFloat16(r[i]), Float16.shortBitsToFloat16(mathf.apply(a[i]))) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float16.compare(Float16.shortBitsToFloat16(r[i]), Float16.shortBitsToFloat16(mathf.apply(a[i]))) == 0, "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected = " + mathf.apply(a[i]));
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i])), "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected (within 1 ulp) = " + strictmathf.apply(a[i]));
        }
    }

    static void assertArraysEqualsWithinOneUlp(short[] r, short[] a, short[] b, FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float16.compare(Float16.shortBitsToFloat16(r[i]), Float16.shortBitsToFloat16(mathf.apply(a[i], b[i]))) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float16.compare(Float16.shortBitsToFloat16(r[i]), Float16.shortBitsToFloat16(mathf.apply(a[i], b[i]))) == 0, "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected = " + mathf.apply(a[i], b[i]));
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", actual = " + r[i] + ", expected (within 1 ulp) = " + strictmathf.apply(a[i], b[i]));
        }
    }

    static void assertBroadcastArraysEqualsWithinOneUlp(short[] r, short[] a, short[] b,
                                                        FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float16.compare(Float16.shortBitsToFloat16(r[i]),
                                  Float16.shortBitsToFloat16(mathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]))) == 0 ||
                                  isWithin1Ulp(r[i],
                                  strictmathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float16.compare(Float16.shortBitsToFloat16(r[i]),
                              Float16.shortBitsToFloat16(mathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]))) == 0,
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, b, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, i, b, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref,
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, i, mask, b, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref,
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(r, a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(r, a, i, mask, b, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref,
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, origin, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, origin, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, mask, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, mask, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, part, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
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
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, mask, i));
            }
        } catch (AssertionError e) {
            short[] ref = f.apply(a, b, origin, part, mask, i);
            short[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin
              + ", with part #" + part);
        }
    }

    static short genValue(int i) {
        return Float16.float16ToRawShortBits(Float16.valueOf(i));
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

    static final List<IntFunction<short[]>> INT_HALFFLOAT_GENERATORS = List.of(
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
                Assert.assertEquals(r[i], (int)Float.float16ToFloat(a[i+offs]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], (int)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
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
        return Float16.float16ToRawShortBits(Float16.valueOf(i));
    }

    static final List<IntFunction<short[]>> LONG_HALFFLOAT_GENERATORS = List.of(
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
                            i -> (short)longCornerCaseValue(i));
            })
    );


    static void assertArraysEquals(long[] r, short[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                Assert.assertEquals(r[i], (long)Float.float16ToFloat(a[i+offs]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], (long)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }

    static void assertArraysEquals(double[] r, short[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                Assert.assertEquals(r[i], (double)Float.float16ToFloat(a[i+offs]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], (double)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }

    static short bits(short e) {
        return e;
    }

    static final List<IntFunction<short[]>> HALFFLOAT_GENERATORS = List.of(
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
    static final List<List<IntFunction<short[]>>> HALFFLOAT_GENERATOR_PAIRS =
        Stream.of(HALFFLOAT_GENERATORS.get(0)).
                flatMap(fa -> HALFFLOAT_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] boolUnaryOpProvider() {
        return BOOL_ARRAY_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<short[]>>> HALFFLOAT_GENERATOR_TRIPLES =
        HALFFLOAT_GENERATOR_PAIRS.stream().
                flatMap(pair -> HALFFLOAT_GENERATORS.stream().map(f -> List.of(pair.get(0), pair.get(1), f))).
                collect(Collectors.toList());

    static final List<IntFunction<short[]>> SELECT_FROM_INDEX_GENERATORS = List.of(
            withToString("short[0..VECLEN*2)", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (short)(RAND.nextInt()));
            })
    );

    static final List<List<IntFunction<short[]>>> HALFFLOAT_GENERATOR_SELECT_FROM_TRIPLES =
        HALFFLOAT_GENERATOR_PAIRS.stream().
                flatMap(pair -> SELECT_FROM_INDEX_GENERATORS.stream().map(f -> List.of(pair.get(0), pair.get(1), f))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortBinaryOpProvider() {
        return HALFFLOAT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortIndexedOpProvider() {
        return HALFFLOAT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> HALFFLOAT_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortTernaryOpProvider() {
        return HALFFLOAT_GENERATOR_TRIPLES.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortSelectFromTwoVectorOpProvider() {
        return HALFFLOAT_GENERATOR_SELECT_FROM_TRIPLES.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortTernaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> HALFFLOAT_GENERATOR_TRIPLES.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpProvider() {
        return HALFFLOAT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> HALFFLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shorttoIntUnaryOpProvider() {
        return INT_HALFFLOAT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shorttoLongUnaryOpProvider() {
        return LONG_HALFFLOAT_GENERATORS.stream().
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
                flatMap(fs -> HALFFLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpShuffleMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_SHUFFLE_GENERATORS.stream().
                    flatMap(fs -> HALFFLOAT_GENERATORS.stream().map(fa -> {
                        return new Object[] {fa, fs, fm};
                }))).
                toArray(Object[][]::new);
    }

    static final List<BiFunction<Integer,Integer,short[]>> HALFFLOAT_SHUFFLE_GENERATORS = List.of(
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
        return HALFFLOAT_SHUFFLE_GENERATORS.stream().
                flatMap(fs -> HALFFLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpSelectFromMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> HALFFLOAT_SHUFFLE_GENERATORS.stream().
                    flatMap(fs -> HALFFLOAT_GENERATORS.stream().map(fa -> {
                        return new Object[] {fa, fs, fm};
                }))).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<short[]>> HALFFLOAT_COMPARE_GENERATORS = List.of(
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

    static final List<List<IntFunction<short[]>>> HALFFLOAT_TEST_GENERATOR_ARGS =
        HALFFLOAT_COMPARE_GENERATORS.stream().
                map(fa -> List.of(fa)).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortTestOpProvider() {
        return HALFFLOAT_TEST_GENERATOR_ARGS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortTestOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> HALFFLOAT_TEST_GENERATOR_ARGS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<short[]>>> HALFFLOAT_COMPARE_GENERATOR_PAIRS =
        HALFFLOAT_COMPARE_GENERATORS.stream().
                flatMap(fa -> HALFFLOAT_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortCompareOpProvider() {
        return HALFFLOAT_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortCompareOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> HALFFLOAT_COMPARE_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    interface ToHalffloatF {
        short apply(int i);
    }

    static short[] fill(int s , ToHalffloatF f) {
        return fill(new short[s], f);
    }

    static short[] fill(short[] a, ToHalffloatF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    static short cornerCaseValue(int i) {
        return switch(i % 8) {
            case 0  -> Float16.float16ToRawShortBits(Float16.MAX_VALUE);
            case 1  -> Float16.float16ToRawShortBits(Float16.MIN_VALUE);
            case 2  -> Float16.float16ToRawShortBits(Float16.NEGATIVE_INFINITY);
            case 3  -> Float16.float16ToRawShortBits(Float16.POSITIVE_INFINITY);
            case 4  -> Float16.float16ToRawShortBits(Float16.NaN);
            case 5  -> Float16.float16ToRawShortBits(Float16.shortBitsToFloat16((short)0x7FFA));
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
        Float16 at = Float16.shortBitsToFloat16(a);
        Float16 bt = Float16.shortBitsToFloat16(b);
        return at.floatValue() == bt.floatValue();
    }

    static boolean neq(short a, short b) {
        Float16 at = Float16.shortBitsToFloat16(a);
        Float16 bt = Float16.shortBitsToFloat16(b);
        return at.floatValue() != bt.floatValue();
    }

    static boolean lt(short a, short b) {
        Float16 at = Float16.shortBitsToFloat16(a);
        Float16 bt = Float16.shortBitsToFloat16(b);
        return at.floatValue() < bt.floatValue();
    }

    static boolean le(short a, short b) {
        Float16 at = Float16.shortBitsToFloat16(a);
        Float16 bt = Float16.shortBitsToFloat16(b);
        return at.floatValue() <= bt.floatValue();
    }

    static boolean gt(short a, short b) {
        Float16 at = Float16.shortBitsToFloat16(a);
        Float16 bt = Float16.shortBitsToFloat16(b);
        return at.floatValue() > bt.floatValue();
    }

    static boolean ge(short a, short b) {
        Float16 at = Float16.shortBitsToFloat16(a);
        Float16 bt = Float16.shortBitsToFloat16(b);
        return at.floatValue() >= bt.floatValue();
    }

    static short firstNonZero(short a, short b) {
        return Short.compare(a, (short) 0) != 0 ? a : b;
    }

    @Test
    static void smokeTest1() {
        HalffloatVector three = HalffloatVector.broadcast(SPECIES, Float16.float16ToRawShortBits(Float16.valueOf(-3)));
        HalffloatVector three2 = (HalffloatVector) SPECIES.broadcast(Float16.float16ToRawShortBits(Float16.valueOf(-3)));
        assert(three.eq(three2).allTrue());
        HalffloatVector three3 = three2.broadcast(Float16.float16ToRawShortBits(Float16.valueOf(1))).broadcast(Float16.float16ToRawShortBits(Float16.valueOf(-3)));
        assert(three.eq(three3).allTrue());
        int scale = 2;
        HalffloatVector higher = three.addIndex(scale);
        VectorMask<Float16> m = three.compare(VectorOperators.LE, higher);
        assert(m.allTrue());
        m = higher.min((Float16.float16ToRawShortBits(Float16.valueOf(-1)))).test(VectorOperators.IS_NEGATIVE);
        assert(m.allTrue());
        m = higher.test(VectorOperators.IS_FINITE);
        assert(m.allTrue());
        short max = higher.reduceLanes(VectorOperators.MAX);
        assert((short) Float.float16ToFloat(max) == -3 + scale * (SPECIES.length()-1));
    }

    private static short[]
    bothToArray(HalffloatVector a, HalffloatVector b) {
        short[] r = new short[a.length() + b.length()];
        a.intoArray(r, 0);
        b.intoArray(r, a.length());
        return r;
    }

    @Test
    static void smokeTest2() {
        // Do some zipping and shuffling.
        HalffloatVector io = (HalffloatVector) SPECIES.broadcast(0).addIndex(1);
        HalffloatVector io2 = (HalffloatVector) VectorShuffle.iota(SPECIES,0,1,false).toVector();
        Assert.assertEquals(io, io2);
        HalffloatVector a = io.add((short)1); //[1,2]
        HalffloatVector b = a.neg();  //[-1,-2]
        short[] abValues = bothToArray(a,b); //[1,2,-1,-2]
        VectorShuffle<Float16> zip0 = VectorShuffle.makeZip(SPECIES, 0);
        VectorShuffle<Float16> zip1 = VectorShuffle.makeZip(SPECIES, 1);
        HalffloatVector zab0 = a.rearrange(zip0,b); //[1,-1]
        HalffloatVector zab1 = a.rearrange(zip1,b); //[2,-2]
        short[] zabValues = bothToArray(zab0, zab1); //[1,-1,2,-2]
        // manually zip
        short[] manual = new short[zabValues.length];
        for (int i = 0; i < manual.length; i += 2) {
            manual[i+0] = abValues[i/2];
            manual[i+1] = abValues[a.length() + i/2];
        }
        Assert.assertEquals(Arrays.toString(zabValues), Arrays.toString(manual));
        VectorShuffle<Float16> unz0 = VectorShuffle.makeUnzip(SPECIES, 0);
        VectorShuffle<Float16> unz1 = VectorShuffle.makeUnzip(SPECIES, 1);
        HalffloatVector uab0 = zab0.rearrange(unz0,zab1);
        HalffloatVector uab1 = zab0.rearrange(unz1,zab1);
        short[] abValues1 = bothToArray(uab0, uab1);
        Assert.assertEquals(Arrays.toString(abValues), Arrays.toString(abValues1));
    }

    static void iotaShuffle() {
        HalffloatVector io = (HalffloatVector) SPECIES.broadcast(0).addIndex(1);
        HalffloatVector io2 = (HalffloatVector) VectorShuffle.iota(SPECIES, 0 , 1, false).toVector();
        Assert.assertEquals(io, io2);
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
        Assert.assertEquals(asIntegralSpecies.vectorShape(), SPECIES.vectorShape());
        Assert.assertEquals(asIntegralSpecies.length(), SPECIES.length());
        Assert.assertEquals(asIntegral.viewAsFloatingLanes().species(), SPECIES);
    }

    @Test
    void viewAsFloatingLanesTest() {
        Vector<?> asFloating = SPECIES.zero().viewAsFloatingLanes();
        Assert.assertEquals(asFloating.species(), SPECIES);
    }

    static short ADD(short a, short b) {
        return (short)(Float.floatToFloat16(Float.float16ToFloat(a) + Float.float16ToFloat(b)));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void ADDHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Halffloat256VectorTests::ADD);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void ADDHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Halffloat256VectorTests::ADD);
    }

    static short SUB(short a, short b) {
        return (short)(Float.floatToFloat16(Float.float16ToFloat(a) - Float.float16ToFloat(b)));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void SUBHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Halffloat256VectorTests::SUB);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void SUBHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Halffloat256VectorTests::SUB);
    }

    static short MUL(short a, short b) {
        return (short)(Float.floatToFloat16(Float.float16ToFloat(a) * Float.float16ToFloat(b)));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MULHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Halffloat256VectorTests::MUL);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void MULHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Halffloat256VectorTests::MUL);
    }

    static short DIV(short a, short b) {
        return (short)(Float.floatToFloat16(Float.float16ToFloat(a) / Float.float16ToFloat(b)));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void DIVHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Halffloat256VectorTests::DIV);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void DIVHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Halffloat256VectorTests::DIV);
    }

    static short MAX(short a, short b) {
        return (short)(Float.floatToFloat16(Math.max(Float.float16ToFloat(a), Float.float16ToFloat(b))));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MAXHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MAX, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Halffloat256VectorTests::MAX);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void MAXHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MAX, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Halffloat256VectorTests::MAX);
    }

    static short MIN(short a, short b) {
        return (short)(Float.floatToFloat16(Math.min(Float.float16ToFloat(a), Float.float16ToFloat(b))));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void MINHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MIN, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, Halffloat256VectorTests::MIN);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void MINHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MIN, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Halffloat256VectorTests::MIN);
    }

    static short ABS(short a) {
        return (short)(Math.abs(a));
    }

    static short abs(short a) {
        return (short)(Math.abs(a));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ABSHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Halffloat256VectorTests::ABS);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void absHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.abs().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Halffloat256VectorTests::abs);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void ABSMaskedHalffloat256VectorTests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, Halffloat256VectorTests::ABS);
    }

    static short NEG(short a) {
        return (short)(-a);
    }

    static short neg(short a) {
        return (short)(-a);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void NEGHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Halffloat256VectorTests::NEG);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void negHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.neg().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Halffloat256VectorTests::neg);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void NEGMaskedHalffloat256VectorTests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, Halffloat256VectorTests::NEG);
    }

    static short FMA(short a, short b, short c) {
        return (short)(Float.floatToFloat16(Math.fma(Float.float16ToFloat(a), Float.float16ToFloat(b), Float.float16ToFloat(c))));
    }

    static short fma(short a, short b, short c) {
        return (short)(Float.floatToFloat16(Math.fma(Float.float16ToFloat(a), Float.float16ToFloat(b), Float.float16ToFloat(c))));
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void FMAHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                HalffloatVector cv = HalffloatVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.FMA, bv, cv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, c, Halffloat256VectorTests::FMA);
    }

    @Test(dataProvider = "shortTernaryOpProvider")
    static void fmaHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
            HalffloatVector cv = HalffloatVector.fromArray(SPECIES, c, i);
            av.fma(bv, cv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, c, Halffloat256VectorTests::fma);
    }

    @Test(dataProvider = "shortTernaryOpMaskProvider")
    static void FMAHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<short[]> fc, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] c = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                HalffloatVector cv = HalffloatVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.FMA, bv, cv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, c, mask, Halffloat256VectorTests::FMA);
    }

    static short SQRT(short a) {
        return (short)(Float.floatToFloat16((float) Math.sqrt(Float.float16ToFloat(a))));
    }

    static short sqrt(short a) {
        return (short)(Float.floatToFloat16((float) Math.sqrt(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void SQRTHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SQRT).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Halffloat256VectorTests::SQRT);
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void sqrtHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.sqrt().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, Halffloat256VectorTests::sqrt);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void SQRTMaskedHalffloat256VectorTests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SQRT, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, Halffloat256VectorTests::SQRT);
    }

    static short SIN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.sin(Float.float16ToFloat(a))));
    }

    static short strictSIN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.sin(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void SINHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SIN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::SIN, Halffloat256VectorTests::strictSIN);
    }

    static short EXP(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.exp(Float.float16ToFloat(a))));
    }

    static short strictEXP(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.exp(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void EXPHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.EXP).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::EXP, Halffloat256VectorTests::strictEXP);
    }

    static short LOG1P(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.log1p(Float.float16ToFloat(a))));
    }

    static short strictLOG1P(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.log1p(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void LOG1PHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG1P).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::LOG1P, Halffloat256VectorTests::strictLOG1P);
    }

    static short LOG(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.log(Float.float16ToFloat(a))));
    }

    static short strictLOG(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.log(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void LOGHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::LOG, Halffloat256VectorTests::strictLOG);
    }

    static short LOG10(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.log10(Float.float16ToFloat(a))));
    }

    static short strictLOG10(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.log10(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void LOG10Halffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG10).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::LOG10, Halffloat256VectorTests::strictLOG10);
    }

    static short EXPM1(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.expm1(Float.float16ToFloat(a))));
    }

    static short strictEXPM1(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.expm1(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void EXPM1Halffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.EXPM1).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::EXPM1, Halffloat256VectorTests::strictEXPM1);
    }

    static short COS(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.cos(Float.float16ToFloat(a))));
    }

    static short strictCOS(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.cos(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void COSHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.COS).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::COS, Halffloat256VectorTests::strictCOS);
    }

    static short TAN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.tan(Float.float16ToFloat(a))));
    }

    static short strictTAN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.tan(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void TANHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TAN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::TAN, Halffloat256VectorTests::strictTAN);
    }

    static short SINH(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.sinh(Float.float16ToFloat(a))));
    }

    static short strictSINH(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.sinh(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void SINHHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SINH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::SINH, Halffloat256VectorTests::strictSINH);
    }

    static short COSH(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.cosh(Float.float16ToFloat(a))));
    }

    static short strictCOSH(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.cosh(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void COSHHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.COSH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::COSH, Halffloat256VectorTests::strictCOSH);
    }

    static short TANH(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.tanh(Float.float16ToFloat(a))));
    }

    static short strictTANH(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.tanh(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void TANHHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TANH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::TANH, Halffloat256VectorTests::strictTANH);
    }

    static short ASIN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.asin(Float.float16ToFloat(a))));
    }

    static short strictASIN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.asin(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ASINHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ASIN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::ASIN, Halffloat256VectorTests::strictASIN);
    }

    static short ACOS(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.acos(Float.float16ToFloat(a))));
    }

    static short strictACOS(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.acos(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ACOSHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ACOS).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::ACOS, Halffloat256VectorTests::strictACOS);
    }

    static short ATAN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.atan(Float.float16ToFloat(a))));
    }

    static short strictATAN(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.atan(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ATANHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ATAN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::ATAN, Halffloat256VectorTests::strictATAN);
    }

    static short CBRT(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) Math.cbrt(Float.float16ToFloat(a))));
    }

    static short strictCBRT(short a) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.cbrt(Float.float16ToFloat(a))));
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void CBRTHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.CBRT).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, Halffloat256VectorTests::CBRT, Halffloat256VectorTests::strictCBRT);
    }

    static short HYPOT(short a, short b) {
        return Float16.float16ToRawShortBits((Float16.valueOf((float) Math.hypot(Float.float16ToFloat(a), Float.float16ToFloat(b)))));
    }

    static short strictHYPOT(short a, short b) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.hypot(Float.float16ToFloat(a), Float.float16ToFloat(b))));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void HYPOTHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.HYPOT, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Halffloat256VectorTests::HYPOT, Halffloat256VectorTests::strictHYPOT);
    }

    static short POW(short a, short b) {
        return Float16.float16ToRawShortBits((Float16.valueOf((float) Math.pow(Float.float16ToFloat(a), Float.float16ToFloat(b)))));
    }

    static short strictPOW(short a, short b) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.pow(Float.float16ToFloat(a), Float.float16ToFloat(b))));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void POWHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.POW, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Halffloat256VectorTests::POW, Halffloat256VectorTests::strictPOW);
    }

    static short pow(short a, short b) {
        return Float16.float16ToRawShortBits((Float16.valueOf((float) Math.pow(Float.float16ToFloat(a), Float.float16ToFloat(b)))));
    }

    static short strictpow(short a, short b) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.pow(Float.float16ToFloat(a), Float.float16ToFloat(b))));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void powHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.pow(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Halffloat256VectorTests::pow, Halffloat256VectorTests::strictpow);
    }

    static short ATAN2(short a, short b) {
        return Float16.float16ToRawShortBits((Float16.valueOf((float) Math.atan2(Float.float16ToFloat(a), Float.float16ToFloat(b)))));
    }

    static short strictATAN2(short a, short b) {
        return Float16.float16ToRawShortBits(Float16.valueOf((float) StrictMath.atan2(Float.float16ToFloat(a), Float.float16ToFloat(b))));
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void ATAN2Halffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ATAN2, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(r, a, b, Halffloat256VectorTests::ATAN2, Halffloat256VectorTests::strictATAN2);
    }

    @Test(dataProvider = "shortBinaryOpProvider")
    static void POWHalffloat256VectorTestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.POW, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEqualsWithinOneUlp(r, a, b, Halffloat256VectorTests::POW, Halffloat256VectorTests::strictPOW);
    }


    @Test(dataProvider = "shortBinaryOpProvider")
    static void powHalffloat256VectorTestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            av.pow(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEqualsWithinOneUlp(r, a, b, Halffloat256VectorTests::pow, Halffloat256VectorTests::strictpow);
    }


    static short blend(short a, short b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void blendHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                av.blend(bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, Halffloat256VectorTests::blend);
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void ltHalffloat256VectorTestsBroadcastSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.lt(b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), lt(a[i + j], b[i]));
            }
        }
    }

    @Test(dataProvider = "shortCompareOpProvider")
    static void eqHalffloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float16> mv = av.eq(b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), eq(a[i + j], b[i]));
            }
        }
    }

    @Test(dataProvider = "shorttoIntUnaryOpProvider")
    static void toIntArrayHalffloat256VectorTestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            int[] r = av.toIntArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "shorttoLongUnaryOpProvider")
    static void toLongArrayHalffloat256VectorTestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            long[] r = av.toLongArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void toDoubleArrayHalffloat256VectorTestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            double[] r = av.toDoubleArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void toStringHalffloat256VectorTestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            String str = av.toString();

            short subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            Assert.assertTrue(str.equals(Arrays.toString(subarr)), "at index " + i + ", string should be = " + Arrays.toString(subarr) + ", but is = " + str);
        }
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void hashCodeHalffloat256VectorTestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            int hash = av.hashCode();

            short subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            int expectedHash = Objects.hash(SPECIES, Arrays.hashCode(subarr));
            Assert.assertTrue(hash == expectedHash, "at index " + i + ", hash should be = " + expectedHash + ", but is = " + hash);
        }
    }


    static long ADDReduceLong(short[] a, int idx) {
        short res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = Float.floatToFloat16(Float.float16ToFloat(res) + Float.float16ToFloat(a[i]));
        }

        return (long)res;
    }

    static long ADDReduceAllLong(short[] a) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = Float.floatToFloat16(Float.float16ToFloat((short)res) + Float.float16ToFloat((short)ADDReduceLong(a, i)));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpProvider")
    static void ADDReduceLongHalffloat256VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD);
        }

        ra = 0;
        for (int i = 0; i < a.length; i++) {
            ra = (long)(Float16.float16ToRawShortBits(Float16.add(Float16.shortBitsToFloat16((short)ra), Float16.shortBitsToFloat16((short)r[i]))));
        }

        assertReductionLongArraysEquals(r, ra, a,
                Halffloat256VectorTests::ADDReduceLong, Halffloat256VectorTests::ADDReduceAllLong);
    }

    static long ADDReduceLongMasked(short[] a, int idx, boolean[] mask) {
        short res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if(mask[i % SPECIES.length()])
                res = Float.floatToFloat16(Float.float16ToFloat(res) + Float.float16ToFloat(a[i]));
        }

        return (long)res;
    }

    static long ADDReduceAllLongMasked(short[] a, boolean[] mask) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = Float.floatToFloat16(Float.float16ToFloat((short)res) + Float.float16ToFloat((short)ADDReduceLongMasked(a, i, mask)));
        }

        return res;
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void ADDReduceLongHalffloat256VectorTestsMasked(IntFunction<short[]> fa, IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD, vmask);
        }

        ra = 0;
        for (int i = 0; i < a.length; i++) {
            ra = (long)(Float16.float16ToRawShortBits(Float16.add(Float16.shortBitsToFloat16((short)ra), Float16.shortBitsToFloat16((short)r[i]))));
        }

        assertReductionLongArraysEqualsMasked(r, ra, a, mask,
                Halffloat256VectorTests::ADDReduceLongMasked, Halffloat256VectorTests::ADDReduceAllLongMasked);
    }

    @Test(dataProvider = "shorttoLongUnaryOpProvider")
    static void BroadcastLongHalffloat256VectorTestsSmokeTest(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector.broadcast(SPECIES, (long)a[i]).intoArray(r, i);
        }
        assertBroadcastArraysEquals(r, a);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider")
    static void blendHalffloat256VectorTestsBroadcastLongSmokeTest(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                av.blend((long)b[i], vmask).intoArray(r, i);
            }
        }
        assertBroadcastLongArraysEquals(r, a, b, mask, Halffloat256VectorTests::blend);
    }


    @Test(dataProvider = "shortUnaryOpSelectFromProvider")
    static void SelectFromHalffloat256VectorTests(IntFunction<short[]> fa,
                                           BiFunction<Integer,Integer,short[]> fs) {
        short[] a = fa.apply(SPECIES.length());
        short[] order = fs.apply(a.length, SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            HalffloatVector bv = HalffloatVector.fromArray(SPECIES, order, i);
            bv.selectFrom(av).intoArray(r, i);
        }

        assertSelectFromArraysEquals(r, a, order, SPECIES.length());
    }

    @Test(dataProvider = "shortSelectFromTwoVectorOpProvider")
    static void SelectFromTwoVectorHalffloat256VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb, IntFunction<short[]> fc) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] idx = fc.apply(SPECIES.length());
        short[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < idx.length; i += SPECIES.length()) {
                HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
                HalffloatVector bv = HalffloatVector.fromArray(SPECIES, b, i);
                HalffloatVector idxv = HalffloatVector.fromArray(SPECIES, idx, i);
                idxv.selectFrom(av, bv).intoArray(r, i);
            }
        }
        assertSelectFromTwoVectorEquals(r, idx, a, b, SPECIES.length());
    }

    @Test(dataProvider = "shortUnaryOpSelectFromMaskProvider")
    static void SelectFromHalffloat256VectorTestsMaskedSmokeTest(IntFunction<short[]> fa,
                                                           BiFunction<Integer,Integer,short[]> fs,
                                                           IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] order = fs.apply(a.length, SPECIES.length());
        short[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float16> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            HalffloatVector av = HalffloatVector.fromArray(SPECIES, a, i);
            HalffloatVector bv = HalffloatVector.fromArray(SPECIES, order, i);
            bv.selectFrom(av, vmask).intoArray(r, i);
        }

        assertSelectFromArraysEquals(r, a, order, mask, SPECIES.length());
    }

    @Test(dataProvider = "shuffleProvider")
    static void shuffleMiscellaneousHalffloat256VectorTestsSmokeTest(BiFunction<Integer,Integer,int[]> fs) {
        int[] a = fs.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var shuffle = VectorShuffle.fromArray(SPECIES, a, i);
            int hash = shuffle.hashCode();
            int length = shuffle.length();

            int subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            int expectedHash = Objects.hash(SPECIES, Arrays.hashCode(subarr));
            Assert.assertTrue(hash == expectedHash, "at index " + i + ", hash should be = " + expectedHash + ", but is = " + hash);
            Assert.assertEquals(length, SPECIES.length());
        }
    }

    @Test(dataProvider = "shuffleProvider")
    static void shuffleToStringHalffloat256VectorTestsSmokeTest(BiFunction<Integer,Integer,int[]> fs) {
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
    static void shuffleEqualsHalffloat256VectorTestsSmokeTest(BiFunction<Integer,Integer,int[]> fa, BiFunction<Integer,Integer,int[]> fb) {
        int[] a = fa.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());
        int[] b = fb.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = VectorShuffle.fromArray(SPECIES, a, i);
            var bv = VectorShuffle.fromArray(SPECIES, b, i);
            boolean eq = av.equals(bv);
            int to = i + SPECIES.length();
            Assert.assertEquals(eq, Arrays.equals(a, i, to, b, i, to));
        }
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskEqualsHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            boolean equals = av.equals(bv);
            int to = i + SPECIES.length();
            Assert.assertEquals(equals, Arrays.equals(a, i, to, b, i, to));
        }
    }

    static boolean band(boolean a, boolean b) {
        return a & b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskAndHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.and(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Halffloat256VectorTests::band);
    }

    static boolean bor(boolean a, boolean b) {
        return a | b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskOrHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.or(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Halffloat256VectorTests::bor);
    }

    static boolean bxor(boolean a, boolean b) {
        return a != b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskXorHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.xor(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Halffloat256VectorTests::bxor);
    }

    static boolean bandNot(boolean a, boolean b) {
        return a & !b;
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskAndNotHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.andNot(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Halffloat256VectorTests::bandNot);
    }

    static boolean beq(boolean a, boolean b) {
        return (a == b);
    }

    @Test(dataProvider = "maskCompareOpProvider")
    static void maskEqHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = SPECIES.loadMask(a, i);
            var bv = SPECIES.loadMask(b, i);
            var cv = av.eq(bv);
            cv.intoArray(r, i);
        }
        assertArraysEquals(r, a, b, Halffloat256VectorTests::beq);
    }

    @Test(dataProvider = "maskProvider")
    static void maskHashCodeHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa) {
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
    static void maskTrueCountHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.trueCount();
            }
        }

        assertMaskReductionArraysEquals(r, a, Halffloat256VectorTests::maskTrueCount);
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
    static void maskLastTrueHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.lastTrue();
            }
        }

        assertMaskReductionArraysEquals(r, a, Halffloat256VectorTests::maskLastTrue);
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
    static void maskFirstTrueHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.firstTrue();
            }
        }

        assertMaskReductionArraysEquals(r, a, Halffloat256VectorTests::maskFirstTrue);
    }

    @Test(dataProvider = "maskProvider")
    static void maskCompressHalffloat256VectorTestsSmokeTest(IntFunction<boolean[]> fa) {
        int trueCount = 0;
        boolean[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                trueCount = vmask.trueCount();
                var rmask = vmask.compress();
                for (int j = 0; j < SPECIES.length(); j++)  {
                    Assert.assertEquals(rmask.laneIsSet(j), j < trueCount);
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
    static void maskFromToLongHalffloat256VectorTestsSmokeTest(long inputLong) {
        var vmask = VectorMask.fromLong(SPECIES, inputLong);
        long outputLong = vmask.toLong();
        Assert.assertEquals(outputLong, (inputLong & (((0xFFFFFFFFFFFFFFFFL >>> (64 - SPECIES.length()))))));
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
    static void indexInRangeHalffloat256VectorTestsSmokeTest(int offset) {
        int limit = SPECIES.length() * BUFFER_REPS;
        for (int i = 0; i < limit; i += SPECIES.length()) {
            var actualMask = SPECIES.indexInRange(i + offset, limit);
            var expectedMask = SPECIES.maskAll(true).indexInRange(i + offset, limit);
            assert(actualMask.equals(expectedMask));
            for (int j = 0; j < SPECIES.length(); j++)  {
                int index = i + j + offset;
                Assert.assertEquals(actualMask.laneIsSet(j), index >= 0 && index < limit);
            }
        }
    }

    @Test(dataProvider = "offsetProvider")
    static void indexInRangeLongHalffloat256VectorTestsSmokeTest(int offset) {
        long limit = SPECIES.length() * BUFFER_REPS;
        for (long i = 0; i < limit; i += SPECIES.length()) {
            var actualMask = SPECIES.indexInRange(i + offset, limit);
            var expectedMask = SPECIES.maskAll(true).indexInRange(i + offset, limit);
            assert(actualMask.equals(expectedMask));
            for (int j = 0; j < SPECIES.length(); j++)  {
                long index = i + j + offset;
                Assert.assertEquals(actualMask.laneIsSet(j), index >= 0 && index < limit);
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
    static void loopBoundHalffloat256VectorTestsSmokeTest(int length) {
        int actualLoopBound = SPECIES.loopBound(length);
        int expectedLoopBound = length - Math.floorMod(length, SPECIES.length());
        Assert.assertEquals(actualLoopBound, expectedLoopBound);
    }

    @Test(dataProvider = "lengthProvider")
    static void loopBoundLongHalffloat256VectorTestsSmokeTest(int _length) {
        long length = _length;
        long actualLoopBound = SPECIES.loopBound(length);
        long expectedLoopBound = length - Math.floorMod(length, SPECIES.length());
        Assert.assertEquals(actualLoopBound, expectedLoopBound);
    }

    @Test
    static void ElementSizeHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        int elsize = av.elementSize();
        Assert.assertEquals(elsize, Float16.SIZE);
    }

    @Test
    static void VectorShapeHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        VectorShape vsh = av.shape();
        assert(vsh.equals(VectorShape.S_256_BIT));
    }

    @Test
    static void ShapeWithLanesHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        VectorShape vsh = av.shape();
        VectorSpecies species = vsh.withLanes(Float16.class);
        assert(species.equals(SPECIES));
    }

    @Test
    static void ElementTypeHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        assert(av.species().elementType() == Float16.class);
    }

    @Test
    static void SpeciesElementSizeHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        assert(av.species().elementSize() == Float16.SIZE);
    }

    @Test
    static void VectorTypeHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        assert(av.species().vectorType() == av.getClass());
    }

    @Test
    static void WithLanesHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        VectorSpecies species = av.species().withLanes(Float16.class);
        assert(species.equals(SPECIES));
    }

    @Test
    static void WithShapeHalffloat256VectorTestsSmokeTest() {
        HalffloatVector av = HalffloatVector.zero(SPECIES);
        VectorShape vsh = av.shape();
        VectorSpecies species = av.species().withShape(vsh);
        assert(species.equals(SPECIES));
    }

    @Test
    static void MaskAllTrueHalffloat256VectorTestsSmokeTest() {
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
          Assert.assertEquals(SPECIES.maskAll(true).toLong(), -1L >>> (64 - SPECIES.length()));
        }
    }
}
