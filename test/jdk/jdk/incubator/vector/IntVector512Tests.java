/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm/timeout=300 -ea -esa -Xbatch -XX:-TieredCompilation IntVector512Tests
 */

// -- This file was mechanically generated: Do not edit! -- //

import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorMath;

import jdk.incubator.vector.IntVector;

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
public class IntVector512Tests extends AbstractVectorTest {

    static final VectorSpecies<Integer> SPECIES =
                IntVector.SPECIES_512;

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);
    static void assertEquals(int actual, int expected) {
        Assert.assertEquals(actual, expected);
    }
    static void assertEquals(int actual, int expected, String msg) {
        Assert.assertEquals(actual, expected, msg);
    }
    static void assertEquals(int actual, int expected, int delta) {
        Assert.assertEquals(actual, expected, delta);
    }
    static void assertEquals(int actual, int expected, int delta, String msg) {
        Assert.assertEquals(actual, expected, delta, msg);
    }
    static void assertEquals(int [] actual, int [] expected) {
        Assert.assertEquals(actual, expected);
    }
    static void assertEquals(int [] actual, int [] expected, String msg) {
        Assert.assertEquals(actual, expected, msg);
    }
    static void assertEquals(long actual, long expected) {
        Assert.assertEquals(actual, expected);
    }
    static void assertEquals(long actual, long expected, String msg) {
        Assert.assertEquals(actual, expected, msg);
    }
    static void assertEquals(String actual, String expected) {
        Assert.assertEquals(actual, expected);
    }
    static void assertEquals(Object actual, Object expected) {
        Assert.assertEquals(actual, expected);
    }
    static void assertEquals(double actual, double expected) {
        Assert.assertEquals(actual, expected);
    }
    static void assertEquals(double actual, double expected, String msg) {
        Assert.assertEquals(actual, expected, msg);
    }
    static void assertEquals(boolean actual, boolean expected) {
        Assert.assertEquals(actual, expected);
    }
    static void assertEquals(boolean actual, boolean expected, String msg) {
        Assert.assertEquals(actual, expected, msg);
    }


    private static final int CONST_SHIFT = Integer.SIZE / 2;

    // Identity values for reduction operations
    private static final int ADD_IDENTITY = (int)0;
    private static final int AND_IDENTITY = (int)-1;
    private static final int FIRST_NONZERO_IDENTITY = (int)0;
    private static final int MAX_IDENTITY = Integer.MIN_VALUE;
    private static final int MIN_IDENTITY = Integer.MAX_VALUE;
    private static final int MUL_IDENTITY = (int)1;
    private static final int OR_IDENTITY = (int)0;
    private static final int SUADD_IDENTITY = (int)0;
    private static final int UMAX_IDENTITY = (int)0;   // Minimum unsigned value
    private static final int UMIN_IDENTITY = (int)-1;  // Maximum unsigned value
    private static final int XOR_IDENTITY = (int)0;

    static final int BUFFER_REPS = Integer.getInteger("jdk.incubator.vector.test.buffer-vectors", 25000 / 512);

    static void assertArraysStrictlyEquals(int[] r, int[] a) {
        for (int i = 0; i < a.length; i++) {
            if (r[i] != a[i]) {
                Assert.fail("at index #" + i + ", expected = " + a[i] + ", actual = " + r[i]);
            }
        }
    }

    interface FUnOp {
        int apply(int a);
    }

    static void assertArraysEquals(int[] r, int[] a, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i]), "at index #" + i + ", input = " + a[i]);
        }
    }

    interface FUnArrayOp {
        int[] apply(int a);
    }

    static void assertArraysEquals(int[] r, int[] a, FUnArrayOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a[i]));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a[i]);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i);
        }
    }

    static void assertArraysEquals(int[] r, int[] a, boolean[] mask, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i]);
            }
        } catch (AssertionError e) {
            assertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], "at index #" + i + ", input = " + a[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    interface FReductionOp {
        int apply(int[] a, int idx);
    }

    interface FReductionAllOp {
        int apply(int[] a);
    }

    static void assertReductionArraysEquals(int[] r, int rc, int[] a,
                                            FReductionOp f, FReductionAllOp fa) {
        int i = 0;
        try {
            assertEquals(rc, fa.apply(a));
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            assertEquals(rc, fa.apply(a), "Final result is incorrect!");
            assertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FReductionMaskedOp {
        int apply(int[] a, int idx, boolean[] mask);
    }

    interface FReductionAllMaskedOp {
        int apply(int[] a, boolean[] mask);
    }

    static void assertReductionArraysEqualsMasked(int[] r, int rc, int[] a, boolean[] mask,
                                            FReductionMaskedOp f, FReductionAllMaskedOp fa) {
        int i = 0;
        try {
            assertEquals(rc, fa.apply(a, mask));
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(r[i], f.apply(a, i, mask));
            }
        } catch (AssertionError e) {
            assertEquals(rc, fa.apply(a, mask), "Final result is incorrect!");
            assertEquals(r[i], f.apply(a, i, mask), "at index #" + i);
        }
    }

    interface FReductionOpLong {
        long apply(int[] a, int idx);
    }

    interface FReductionAllOpLong {
        long apply(int[] a);
    }

    static void assertReductionLongArraysEquals(long[] r, long rc, int[] a,
                                            FReductionOpLong f, FReductionAllOpLong fa) {
        int i = 0;
        try {
            assertEquals(rc, fa.apply(a));
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            assertEquals(rc, fa.apply(a), "Final result is incorrect!");
            assertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FReductionMaskedOpLong {
        long apply(int[] a, int idx, boolean[] mask);
    }

    interface FReductionAllMaskedOpLong {
        long apply(int[] a, boolean[] mask);
    }

    static void assertReductionLongArraysEqualsMasked(long[] r, long rc, int[] a, boolean[] mask,
                                            FReductionMaskedOpLong f, FReductionAllMaskedOpLong fa) {
        int i = 0;
        try {
            assertEquals(rc, fa.apply(a, mask));
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(r[i], f.apply(a, i, mask));
            }
        } catch (AssertionError e) {
            assertEquals(rc, fa.apply(a, mask), "Final result is incorrect!");
            assertEquals(r[i], f.apply(a, i, mask), "at index #" + i);
        }
    }

    interface FBoolReductionOp {
        boolean apply(boolean[] a, int idx);
    }

    static void assertReductionBoolArraysEquals(boolean[] r, boolean[] a, FBoolReductionOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FMaskReductionOp {
        int apply(boolean[] a, int idx);
    }

    static void assertMaskReductionArraysEquals(int[] r, boolean[] a, FMaskReductionOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a, i), "at index #" + i);
        }
    }

    static void assertRearrangeArraysEquals(int[] r, int[] a, int[] order, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    assertEquals(r[i+j], a[i+order[i+j]]);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            assertEquals(r[i+j], a[i+order[i+j]], "at index #" + idx + ", input = " + a[i+order[i+j]]);
        }
    }

    static void assertcompressArraysEquals(int[] r, int[] a, boolean[] m, int vector_len) {
        int i = 0, j = 0, k = 0;
        try {
            for (; i < a.length; i += vector_len) {
                k = 0;
                for (j = 0; j < vector_len; j++) {
                    if (m[(i + j) % SPECIES.length()]) {
                        assertEquals(r[i + k], a[i + j]);
                        k++;
                    }
                }
                for (; k < vector_len; k++) {
                    assertEquals(r[i + k], (int)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + k;
            if (m[(i + j) % SPECIES.length()]) {
                assertEquals(r[idx], a[i + j], "at index #" + idx);
            } else {
                assertEquals(r[idx], (int)0, "at index #" + idx);
            }
        }
    }

    static void assertexpandArraysEquals(int[] r, int[] a, boolean[] m, int vector_len) {
        int i = 0, j = 0, k = 0;
        try {
            for (; i < a.length; i += vector_len) {
                k = 0;
                for (j = 0; j < vector_len; j++) {
                    if (m[(i + j) % SPECIES.length()]) {
                        assertEquals(r[i + j], a[i + k]);
                        k++;
                    } else {
                        assertEquals(r[i + j], (int)0);
                    }
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (m[idx % SPECIES.length()]) {
                assertEquals(r[idx], a[i + k], "at index #" + idx);
            } else {
                assertEquals(r[idx], (int)0, "at index #" + idx);
            }
        }
    }

    static void assertSelectFromTwoVectorEquals(int[] r, int[] order, int[] a, int[] b, int vector_len) {
        int i = 0, j = 0;
        boolean is_exceptional_idx = false;
        int idx = 0, wrapped_index = 0, oidx = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    idx = i + j;
                    wrapped_index = Math.floorMod((int)order[idx], 2 * vector_len);
                    is_exceptional_idx = wrapped_index >= vector_len;
                    oidx = is_exceptional_idx ? (wrapped_index - vector_len) : wrapped_index;
                    assertEquals(r[idx], (is_exceptional_idx ? b[i + oidx] : a[i + oidx]));
                }
            }
        } catch (AssertionError e) {
            assertEquals(r[idx], (is_exceptional_idx ? b[i + oidx] : a[i + oidx]), "at index #" + idx + ", order = " + order[idx] + ", a = " + a[i + oidx] + ", b = " + b[i + oidx]);
        }
    }

    static void assertSelectFromArraysEquals(int[] r, int[] a, int[] order, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    assertEquals(r[i+j], a[i+(int)order[i+j]]);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            assertEquals(r[i+j], a[i+(int)order[i+j]], "at index #" + idx + ", input = " + a[i+(int)order[i+j]]);
        }
    }

    static void assertRearrangeArraysEquals(int[] r, int[] a, int[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    if (mask[j % SPECIES.length()])
                         assertEquals(r[i+j], a[i+order[i+j]]);
                    else
                         assertEquals(r[i+j], (int)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (mask[j % SPECIES.length()])
                assertEquals(r[i+j], a[i+order[i+j]], "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
            else
                assertEquals(r[i+j], (int)0, "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
        }
    }

    static void assertSelectFromArraysEquals(int[] r, int[] a, int[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    if (mask[j % SPECIES.length()])
                         assertEquals(r[i+j], a[i+(int)order[i+j]]);
                    else
                         assertEquals(r[i+j], (int)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (mask[j % SPECIES.length()])
                assertEquals(r[i+j], a[i+(int)order[i+j]], "at index #" + idx + ", input = " + a[i+(int)order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
            else
                assertEquals(r[i+j], (int)0, "at index #" + idx + ", input = " + a[i+(int)order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(int[] r, int[] a) {
        int i = 0;
        for (; i < a.length; i += SPECIES.length()) {
            int idx = i;
            for (int j = idx; j < (idx + SPECIES.length()); j++)
                a[j]=a[idx];
        }

        try {
            for (i = 0; i < a.length; i++) {
                assertEquals(r[i], a[i]);
            }
        } catch (AssertionError e) {
            assertEquals(r[i], a[i], "at index #" + i + ", input = " + a[i]);
        }
    }

    interface FBoolUnOp {
        boolean apply(boolean a);
    }

    static void assertArraysEquals(boolean[] r, boolean[] a, FBoolUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i]), "(" + a[i] + ") at index #" + i);
        }
    }

    interface FBoolBinOp {
        boolean apply(boolean a, boolean b);
    }

    static void assertArraysEquals(boolean[] r, boolean[] a, boolean[] b, FBoolBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b[i]), "(" + a[i] + ", " + b[i] + ") at index #" + i);
        }
    }

    interface FBinOp {
        int apply(int a, int b);
    }

    interface FBinMaskOp {
        int apply(int a, int b, boolean m);

        static FBinMaskOp lift(FBinOp f) {
            return (a, b, m) -> m ? f.apply(a, b) : a;
        }
    }

    static void assertArraysEqualsAssociative(int[] rl, int[] rr, int[] a, int[] b, int[] c, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                //Left associative
                assertEquals(rl[i], f.apply(f.apply(a[i], b[i]), c[i]));

                //Right associative
                assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i])));

                //Results equal sanity check
                assertEquals(rl[i], rr[i]);
            }
        } catch (AssertionError e) {
            assertEquals(rl[i], f.apply(f.apply(a[i], b[i]), c[i]), "left associative test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
            assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i])), "right associative test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
            assertEquals(rl[i], rr[i], "Result checks not equal at index #" + i + "leftRes = " + rl[i] + ", rightRes = " + rr[i]);
        }
    }

   static void assertArraysEqualsAssociative(int[] rl, int[] rr, int[] a, int[] b, int[] c, boolean[] mask, FBinOp f) {
       assertArraysEqualsAssociative(rl, rr, a, b, c, mask, FBinMaskOp.lift(f));
   }

    static void assertArraysEqualsAssociative(int[] rl, int[] rr, int[] a, int[] b, int[] c, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        boolean mask_bit = false;
        try {
            for (; i < a.length; i++) {
                mask_bit = mask[i % SPECIES.length()];
                //Left associative
                assertEquals(rl[i], f.apply(f.apply(a[i], b[i], mask_bit), c[i], mask_bit));

                //Right associative
                assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i], mask_bit), mask_bit));

                //Results equal sanity check
                assertEquals(rl[i], rr[i]);
            }
        } catch (AssertionError e) {
            assertEquals(rl[i], f.apply(f.apply(a[i], b[i], mask_bit), c[i], mask_bit), "left associative masked test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i] + ", mask = " + mask_bit);
            assertEquals(rr[i], f.apply(a[i], f.apply(b[i], c[i], mask_bit), mask_bit), "right associative masked test at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i] + ", mask = " + mask_bit);
            assertEquals(rl[i], rr[i], "Result checks not equal at index #" + i + "leftRes = " + rl[i] + ", rightRes = " + rr[i]);
        }
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b[i]), "(" + a[i] + ", " + b[i] + ") at index #" + i);
        }
    }

    static void assertArraysEquals(int[] r, int[] a, int b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b), "(" + a[i] + ", " + b + ") at index #" + i);
        }
    }

    static void assertBroadcastArraysEquals(int[] r, int[] a, int[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()]),
                                "(" + a[i] + ", " + b[(i / SPECIES.length()) * SPECIES.length()] + ") at index #" + i);
        }
    }

    static void assertBroadcastLongArraysEquals(int[] r, int[] a, int[] b, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], (int)((long)b[(i / SPECIES.length()) * SPECIES.length()])));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], (int)((long)b[(i / SPECIES.length()) * SPECIES.length()])),
                                "(" + a[i] + ", " + b[(i / SPECIES.length()) * SPECIES.length()] + ") at index #" + i);
        }
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinOp f) {
        assertArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertArraysEquals(int[] r, int[] a, int b, boolean[] mask, FBinOp f) {
        assertArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(int[] r, int[] a, int b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b, mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], b, mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinOp f) {
        assertBroadcastArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertBroadcastArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] +
                                ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastLongArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinOp f) {
        assertBroadcastLongArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertBroadcastLongArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], (int)((long)b[(i / SPECIES.length()) * SPECIES.length()]), mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], (int)((long)b[(i / SPECIES.length()) * SPECIES.length()]),
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] +
                                ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }

    static void assertShiftArraysEquals(int[] r, int[] a, int[] b, FBinOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    assertEquals(r[i+j], f.apply(a[i+j], b[j]));
                }
            }
        } catch (AssertionError e) {
            assertEquals(r[i+j], f.apply(a[i+j], b[j]), "at index #" + i + ", " + j);
        }
    }

    static void assertShiftArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinOp f) {
        assertShiftArraysEquals(r, a, b, mask, FBinMaskOp.lift(f));
    }

    static void assertShiftArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    assertEquals(r[i+j], f.apply(a[i+j], b[j], mask[i]));
                }
            }
        } catch (AssertionError err) {
            assertEquals(r[i+j], f.apply(a[i+j], b[j], mask[i]), "at index #" + i + ", input1 = " + a[i+j] + ", input2 = " + b[j] + ", mask = " + mask[i]);
        }
    }

    interface FBinConstOp {
        int apply(int a);
    }

    interface FBinConstMaskOp {
        int apply(int a, boolean m);

        static FBinConstMaskOp lift(FBinConstOp f) {
            return (a, m) -> m ? f.apply(a) : a;
        }
    }

    static void assertShiftConstEquals(int[] r, int[] a, FBinConstOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    assertEquals(r[i+j], f.apply(a[i+j]));
                }
            }
        } catch (AssertionError e) {
            assertEquals(r[i+j], f.apply(a[i+j]), "at index #" + i + ", " + j);
        }
    }

    static void assertShiftConstEquals(int[] r, int[] a, boolean[] mask, FBinConstOp f) {
        assertShiftConstEquals(r, a, mask, FBinConstMaskOp.lift(f));
    }

    static void assertShiftConstEquals(int[] r, int[] a, boolean[] mask, FBinConstMaskOp f) {
        int i = 0;
        int j = 0;
        try {
            for (; j < a.length; j += SPECIES.length()) {
                for (i = 0; i < SPECIES.length(); i++) {
                    assertEquals(r[i+j], f.apply(a[i+j], mask[i]));
                }
            }
        } catch (AssertionError err) {
            assertEquals(r[i+j], f.apply(a[i+j], mask[i]), "at index #" + i + ", input1 = " + a[i+j] + ", mask = " + mask[i]);
        }
    }

    interface FTernOp {
        int apply(int a, int b, int c);
    }

    interface FTernMaskOp {
        int apply(int a, int b, int c, boolean m);

        static FTernMaskOp lift(FTernOp f) {
            return (a, b, c, m) -> m ? f.apply(a, b, c) : a;
        }
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, int[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[i], c[i]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b[i], c[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
        }
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask, FTernOp f) {
        assertArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask, FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[i], c[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], b[i], c[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = "
              + b[i] + ", input3 = " + c[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()]), "at index #" +
                                i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " +
                                c[(i / SPECIES.length()) * SPECIES.length()]);
        }
    }

    static void assertAltBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i]), "at index #" +
                                i + ", input1 = " + a[i] + ", input2 = " +
                                b[(i / SPECIES.length()) * SPECIES.length()] + ",  input3 = " + c[i]);
        }
    }

    static void assertBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask,
                                            FTernOp f) {
        assertBroadcastArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask,
                                            FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()],
                                    mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], b[i], c[(i / SPECIES.length()) * SPECIES.length()],
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " +
                                b[i] + ", input3 = " + c[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }

    static void assertAltBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask,
                                            FTernOp f) {
        assertAltBroadcastArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertAltBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask,
                                            FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i],
                                    mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()], c[i],
                                mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] +
                                ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] +
                                ", input3 = " + c[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertDoubleBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                    c[(i / SPECIES.length()) * SPECIES.length()]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                c[(i / SPECIES.length()) * SPECIES.length()]), "at index #" + i + ", input1 = " + a[i]
                                + ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] + ", input3 = " +
                                c[(i / SPECIES.length()) * SPECIES.length()]);
        }
    }

    static void assertDoubleBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask,
                                                  FTernOp f) {
        assertDoubleBroadcastArraysEquals(r, a, b, c, mask, FTernMaskOp.lift(f));
    }

    static void assertDoubleBroadcastArraysEquals(int[] r, int[] a, int[] b, int[] c, boolean[] mask,
                                                  FTernMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                    c[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            assertEquals(r[i], f.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()],
                                c[(i / SPECIES.length()) * SPECIES.length()], mask[i % SPECIES.length()]), "at index #"
                                + i + ", input1 = " + a[i] + ", input2 = " + b[(i / SPECIES.length()) * SPECIES.length()] +
                                ", input3 = " + c[(i / SPECIES.length()) * SPECIES.length()] + ", mask = " +
                                mask[i % SPECIES.length()]);
        }
    }



    interface FGatherScatterOp {
        int[] apply(int[] a, int ix, int[] b, int iy);
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, FGatherScatterOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, b, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a, i, b, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref,
              "(ref: " + Arrays.toString(ref) + ", res: " + Arrays.toString(res) + ", a: "
              + Arrays.toString(Arrays.copyOfRange(a, i, i+SPECIES.length()))
              + ", b: "
              + Arrays.toString(Arrays.copyOfRange(b, i, i+SPECIES.length()))
              + " at index #" + i);
        }
    }

    interface FGatherMaskedOp {
        int[] apply(int[] a, int ix, boolean[] mask, int[] b, int iy);
    }

    interface FScatterMaskedOp {
        int[] apply(int[] r, int[] a, int ix, boolean[] mask, int[] b, int iy);
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FGatherMaskedOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a, i, mask, b, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref,
              "(ref: " + Arrays.toString(ref) + ", res: " + Arrays.toString(res) + ", a: "
              + Arrays.toString(Arrays.copyOfRange(a, i, i+SPECIES.length()))
              + ", b: "
              + Arrays.toString(Arrays.copyOfRange(b, i, i+SPECIES.length()))
              + ", mask: "
              + Arrays.toString(mask)
              + " at index #" + i);
        }
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, boolean[] mask, FScatterMaskedOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(r, a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(r, a, i, mask, b, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref,
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
        int[] apply(int[] a, int origin, int idx);
    }

    static void assertArraysEquals(int[] r, int[] a, int origin, FLaneOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, origin, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a, origin, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i);
        }
    }

    interface FLaneBop {
        int[] apply(int[] a, int[] b, int origin, int idx);
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, int origin, FLaneBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a, b, origin, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin);
        }
    }

    interface FLaneMaskedBop {
        int[] apply(int[] a, int[] b, int origin, boolean[] mask, int idx);
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, int origin, boolean[] mask, FLaneMaskedBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, mask, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a, b, origin, mask, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin);
        }
    }

    interface FLanePartBop {
        int[] apply(int[] a, int[] b, int origin, int part, int idx);
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, int origin, int part, FLanePartBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a, b, origin, part, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin
              + ", with part #" + part);
        }
    }

    interface FLanePartMaskedBop {
        int[] apply(int[] a, int[] b, int origin, int part, boolean[] mask, int idx);
    }

    static void assertArraysEquals(int[] r, int[] a, int[] b, int origin, int part, boolean[] mask, FLanePartMaskedBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, mask, i));
            }
        } catch (AssertionError e) {
            int[] ref = f.apply(a, b, origin, part, mask, i);
            int[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            assertEquals(res, ref, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin
              + ", with part #" + part);
        }
    }


    static void assertArraysEquals(int[] r, int[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                assertEquals(r[i], (int)(a[i+offs]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], (int)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }



    static void assertArraysEquals(long[] r, int[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                assertEquals(r[i], (long)(a[i+offs]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], (long)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }

    static void assertArraysEquals(double[] r, int[] a, int offs) {
        int i = 0;
        try {
            for (; i < r.length; i++) {
                assertEquals(r[i], (double)(a[i+offs]));
            }
        } catch (AssertionError e) {
            assertEquals(r[i], (double)(a[i+offs]), "at index #" + i + ", input = " + a[i+offs]);
        }
    }

    static int bits(int e) {
        return  e;
    }

    static final List<IntFunction<int[]>> INT_GENERATORS = List.of(
            withToString("int[-i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(-i * 5));
            }),
            withToString("int[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(i * 5));
            }),
            withToString("int[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (((int)(i + 1) == 0) ? 1 : (int)(i + 1)));
            }),
            withToString("int[cornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> cornerCaseValue(i));
            })
    );

    static final List<IntFunction<int[]>> INT_SATURATING_GENERATORS = List.of(
            withToString("int[Integer.MIN_VALUE]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(Integer.MIN_VALUE));
            }),
            withToString("int[Integer.MAX_VALUE]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(Integer.MAX_VALUE));
            }),
            withToString("int[Integer.MAX_VALUE - 100]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(Integer.MAX_VALUE - 100));
            }),
            withToString("int[Integer.MIN_VALUE + 100]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(Integer.MIN_VALUE + 100));
            }),
            withToString("int[-i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(-i * 5));
            }),
            withToString("int[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(i * 5));
            })
    );

    static final List<IntFunction<int[]>> INT_SATURATING_GENERATORS_ASSOC = List.of(
            withToString("int[Integer.MAX_VALUE]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(Integer.MAX_VALUE));
            }),
            withToString("int[Integer.MAX_VALUE - 100]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(Integer.MAX_VALUE - 100));
            }),
            withToString("int[-1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(-1));
            })
    );

    // Create combinations of pairs
    // @@@ Might be sensitive to order e.g. div by 0
    static final List<List<IntFunction<int[]>>> INT_GENERATOR_PAIRS =
        Stream.of(INT_GENERATORS.get(0)).
                flatMap(fa -> INT_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    static final List<List<IntFunction<int[]>>> INT_SATURATING_GENERATOR_PAIRS =
        Stream.of(INT_GENERATORS.get(0)).
                flatMap(fa -> INT_SATURATING_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    static final List<List<IntFunction<int[]>>> INT_SATURATING_GENERATOR_TRIPLETS =
            Stream.of(INT_GENERATORS.get(1))
                    .flatMap(fa -> INT_SATURATING_GENERATORS_ASSOC.stream().map(fb -> List.of(fa, fb)))
                    .flatMap(pair -> INT_SATURATING_GENERATORS_ASSOC.stream().map(f -> List.of(pair.get(0), pair.get(1), f)))
                    .collect(Collectors.toList());

    @DataProvider
    public Object[][] boolUnaryOpProvider() {
        return BOOL_ARRAY_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<int[]>>> INT_GENERATOR_TRIPLES =
        INT_GENERATOR_PAIRS.stream().
                flatMap(pair -> INT_GENERATORS.stream().map(f -> List.of(pair.get(0), pair.get(1), f))).
                collect(Collectors.toList());

    static final List<IntFunction<int[]>> SELECT_FROM_INDEX_GENERATORS = List.of(
            withToString("int[0..VECLEN*2)", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(RAND.nextInt()));
            })
    );

    static final List<List<IntFunction<int[]>>> INT_GENERATOR_SELECT_FROM_TRIPLES =
        INT_GENERATOR_PAIRS.stream().
                flatMap(pair -> SELECT_FROM_INDEX_GENERATORS.stream().map(f -> List.of(pair.get(0), pair.get(1), f))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] intBinaryOpProvider() {
        return INT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intSaturatingBinaryOpProvider() {
        return INT_SATURATING_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intSaturatingBinaryOpAssocProvider() {
        return INT_SATURATING_GENERATOR_TRIPLETS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intSaturatingBinaryOpAssocMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_SATURATING_GENERATOR_TRIPLETS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }


    @DataProvider
    public Object[][] intIndexedOpProvider() {
        return INT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intSaturatingBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_SATURATING_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

   @DataProvider
   public Object[][] intSaturatingUnaryOpProvider() {
       return INT_SATURATING_GENERATORS.stream().
                    map(f -> new Object[]{f}).
                    toArray(Object[][]::new);
   }

   @DataProvider
   public Object[][] intSaturatingUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_SATURATING_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
   }

    @DataProvider
    public Object[][] intBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intTernaryOpProvider() {
        return INT_GENERATOR_TRIPLES.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intSelectFromTwoVectorOpProvider() {
        return INT_GENERATOR_SELECT_FROM_TRIPLES.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intTernaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_GENERATOR_TRIPLES.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intUnaryOpProvider() {
        return INT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] maskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longMaskProvider() {
        return LONG_MASK_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] boolMaskBinaryOpProvider() {
        return BOOLEAN_MASK_COMPARE_GENERATOR_PAIRS.stream().
                map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] boolMaskUnaryOpProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                map(f -> new Object[]{f}).
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
    public Object[][] intUnaryOpShuffleProvider() {
        return INT_SHUFFLE_GENERATORS.stream().
                flatMap(fs -> INT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intUnaryOpShuffleMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_SHUFFLE_GENERATORS.stream().
                    flatMap(fs -> INT_GENERATORS.stream().map(fa -> {
                        return new Object[] {fa, fs, fm};
                }))).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<int[]>> INT_COMPARE_GENERATORS = List.of(
            withToString("int[i]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)i);
            }),
            withToString("int[i - length / 2]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(i - (s * BUFFER_REPS / 2)));
            }),
            withToString("int[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(i + 1));
            }),
            withToString("int[i - 2]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (int)(i - 2));
            }),
            withToString("int[zigZag(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> i%3 == 0 ? (int)i : (i%3 == 1 ? (int)(i + 1) : (int)(i - 2)));
            }),
            withToString("int[cornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> cornerCaseValue(i));
            })
    );

    static final List<List<IntFunction<int[]>>> INT_TEST_GENERATOR_ARGS =
        INT_COMPARE_GENERATORS.stream().
                map(fa -> List.of(fa)).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] intTestOpProvider() {
        return INT_TEST_GENERATOR_ARGS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intTestOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_TEST_GENERATOR_ARGS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<int[]>>> INT_COMPARE_GENERATOR_PAIRS =
        INT_COMPARE_GENERATORS.stream().
                flatMap(fa -> INT_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] intCompareOpProvider() {
        return INT_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] intCompareOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_COMPARE_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    interface ToIntF {
        int apply(int i);
    }

    static int[] fill(int s , ToIntF f) {
        return fill(new int[s], f);
    }

    static int[] fill(int[] a, ToIntF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    static int cornerCaseValue(int i) {
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

    static final IntFunction<int[]> fr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new int[length];
    };

    static final IntFunction<boolean[]> fmr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new boolean[length];
    };

    static final IntFunction<long[]> lfr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new long[length];
    };

    static void replaceZero(int[] a, int v) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                a[i] = v;
            }
        }
    }

    static void replaceZero(int[] a, boolean[] mask, int v) {
        for (int i = 0; i < a.length; i++) {
            if (mask[i % mask.length] && a[i] == 0) {
                a[i] = v;
            }
        }
    }

    static int ROL_scalar(int a, int b) {
        return Integer.rotateLeft(a, ((int)b));
    }

    static int ROR_scalar(int a, int b) {
        return Integer.rotateRight(a, ((int)b));
    }

    static int TRAILING_ZEROS_COUNT_scalar(int a) {
        return Integer.numberOfTrailingZeros(a);
    }

    static int LEADING_ZEROS_COUNT_scalar(int a) {
        return Integer.numberOfLeadingZeros(a);
    }

    static int REVERSE_scalar(int a) {
        return Integer.reverse(a);
    }

    static boolean eq(int a, int b) {
        return a == b;
    }

    static boolean neq(int a, int b) {
        return a != b;
    }

    static boolean lt(int a, int b) {
        return a < b;
    }

    static boolean le(int a, int b) {
        return a <= b;
    }

    static boolean gt(int a, int b) {
        return a > b;
    }

    static boolean ge(int a, int b) {
        return a >= b;
    }

    static boolean ult(int a, int b) {
        return Integer.compareUnsigned(a, b) < 0;
    }

    static boolean ule(int a, int b) {
        return Integer.compareUnsigned(a, b) <= 0;
    }

    static boolean ugt(int a, int b) {
        return Integer.compareUnsigned(a, b) > 0;
    }

    static boolean uge(int a, int b) {
        return Integer.compareUnsigned(a, b) >= 0;
    }

    static int firstNonZero(int a, int b) {
        return Integer.compare(a, (int) 0) != 0 ? a : b;
    }

    @Test
    static void smokeTest1() {
        IntVector three = IntVector.broadcast(SPECIES, (byte)-3);
        IntVector three2 = (IntVector) SPECIES.broadcast(-3);
        assert(three.eq(three2).allTrue());
        IntVector three3 = three2.broadcast(1).broadcast(-3);
        assert(three.eq(three3).allTrue());
        int scale = 2;
        Class<?> ETYPE = int.class;
        if (ETYPE == double.class || ETYPE == long.class)
            scale = 1000000;
        else if (ETYPE == byte.class && SPECIES.length() >= 64)
            scale = 1;
        IntVector higher = three.addIndex(scale);
        VectorMask<Integer> m = three.compare(VectorOperators.LE, higher);
        assert(m.allTrue());
        m = higher.min((int)-1).test(VectorOperators.IS_NEGATIVE);
        assert(m.allTrue());
        int max = higher.reduceLanes(VectorOperators.MAX);
        assert(max == -3 + scale * (SPECIES.length()-1));
    }

    private static int[]
    bothToArray(IntVector a, IntVector b) {
        int[] r = new int[a.length() + b.length()];
        a.intoArray(r, 0);
        b.intoArray(r, a.length());
        return r;
    }

    @Test
    static void smokeTest2() {
        // Do some zipping and shuffling.
        IntVector io = (IntVector) SPECIES.broadcast(0).addIndex(1);
        IntVector io2 = (IntVector) VectorShuffle.iota(SPECIES,0,1,false).toVector();
        assertEquals(io, io2);
        IntVector a = io.add((int)1); //[1,2]
        IntVector b = a.neg();  //[-1,-2]
        int[] abValues = bothToArray(a,b); //[1,2,-1,-2]
        VectorShuffle<Integer> zip0 = VectorShuffle.makeZip(SPECIES, 0);
        VectorShuffle<Integer> zip1 = VectorShuffle.makeZip(SPECIES, 1);
        IntVector zab0 = a.rearrange(zip0,b); //[1,-1]
        IntVector zab1 = a.rearrange(zip1,b); //[2,-2]
        int[] zabValues = bothToArray(zab0, zab1); //[1,-1,2,-2]
        // manually zip
        int[] manual = new int[zabValues.length];
        for (int i = 0; i < manual.length; i += 2) {
            manual[i+0] = abValues[i/2];
            manual[i+1] = abValues[a.length() + i/2];
        }
        assertEquals(Arrays.toString(zabValues), Arrays.toString(manual));
        VectorShuffle<Integer> unz0 = VectorShuffle.makeUnzip(SPECIES, 0);
        VectorShuffle<Integer> unz1 = VectorShuffle.makeUnzip(SPECIES, 1);
        IntVector uab0 = zab0.rearrange(unz0,zab1);
        IntVector uab1 = zab0.rearrange(unz1,zab1);
        int[] abValues1 = bothToArray(uab0, uab1);
        assertEquals(Arrays.toString(abValues), Arrays.toString(abValues1));
    }

    static void iotaShuffle() {
        IntVector io = (IntVector) SPECIES.broadcast(0).addIndex(1);
        IntVector io2 = (IntVector) VectorShuffle.iota(SPECIES, 0 , 1, false).toVector();
        assertEquals(io, io2);
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
        assertEquals(asIntegral.species(), SPECIES);
    }

    @Test
    void viewAsFloatingLanesTest() {
        Vector<?> asFloating = SPECIES.zero().viewAsFloatingLanes();
        VectorSpecies<?> asFloatingSpecies = asFloating.species();
        Assert.assertNotEquals(asFloatingSpecies.elementType(), SPECIES.elementType());
        assertEquals(asFloatingSpecies.vectorShape(), SPECIES.vectorShape());
        assertEquals(asFloatingSpecies.length(), SPECIES.length());
        assertEquals(asFloating.viewAsIntegralLanes().species(), SPECIES);
    }

    @Test
    // Test div by 0.
    static void bitwiseDivByZeroSmokeTest() {
        try {
            IntVector a = (IntVector) SPECIES.broadcast(0).addIndex(1);
            IntVector b = (IntVector) SPECIES.broadcast(0);
            a.div(b);
            Assert.fail();
        } catch (ArithmeticException e) {
        }

        try {
            IntVector a = (IntVector) SPECIES.broadcast(0).addIndex(1);
            IntVector b = (IntVector) SPECIES.broadcast(0);
            VectorMask<Integer> m = a.lt((int) 1);
            a.div(b, m);
            Assert.fail();
        } catch (ArithmeticException e) {
        }
    }

    static int ADD(int a, int b) {
        return (int)(a + b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ADDIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::ADD);
    }

    static int add(int a, int b) {
        return (int)(a + b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void addIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.add(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, IntVector512Tests::add);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ADDIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::ADD);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void addIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.add(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::add);
    }

    static int SUB(int a, int b) {
        return (int)(a - b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void SUBIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::SUB);
    }

    static int sub(int a, int b) {
        return (int)(a - b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void subIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.sub(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, IntVector512Tests::sub);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void SUBIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::SUB);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void subIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.sub(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::sub);
    }

    static int MUL(int a, int b) {
        return (int)(a * b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void MULIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::MUL);
    }

    static int mul(int a, int b) {
        return (int)(a * b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void mulIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.mul(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, IntVector512Tests::mul);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void MULIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::MUL);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void mulIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.mul(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::mul);
    }

    static int DIV(int a, int b) {
        return (int)(a / b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void DIVIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        replaceZero(b, (int) 1);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::DIV);
    }

    static int div(int a, int b) {
        return (int)(a / b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void divIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        replaceZero(b, (int) 1);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.div(bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::div);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void DIVIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        replaceZero(b, mask, (int) 1);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::DIV);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void divIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        replaceZero(b, mask, (int) 1);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.div(bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::div);
    }

    static int FIRST_NONZERO(int a, int b) {
        return (int)((a)!=0?a:b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void FIRST_NONZEROIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.FIRST_NONZERO, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::FIRST_NONZERO);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void FIRST_NONZEROIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.FIRST_NONZERO, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::FIRST_NONZERO);
    }

    static int AND(int a, int b) {
        return (int)(a & b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ANDIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.AND, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::AND);
    }

    static int and(int a, int b) {
        return (int)(a & b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void andIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.and(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, IntVector512Tests::and);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ANDIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.AND, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::AND);
    }

    static int AND_NOT(int a, int b) {
        return (int)(a & ~b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void AND_NOTIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.AND_NOT, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::AND_NOT);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void AND_NOTIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.AND_NOT, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::AND_NOT);
    }

    static int OR(int a, int b) {
        return (int)(a | b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ORIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.OR, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::OR);
    }

    static int or(int a, int b) {
        return (int)(a | b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void orIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.or(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, IntVector512Tests::or);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ORIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.OR, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::OR);
    }

    static int XOR(int a, int b) {
        return (int)(a ^ b);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void XORIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.XOR, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::XOR);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void XORIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.XOR, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::XOR);
    }

    static int COMPRESS_BITS(int a, int b) {
        return (int)(Integer.compress(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void COMPRESS_BITSIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.COMPRESS_BITS, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::COMPRESS_BITS);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void COMPRESS_BITSIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.COMPRESS_BITS, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::COMPRESS_BITS);
    }

    static int EXPAND_BITS(int a, int b) {
        return (int)(Integer.expand(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void EXPAND_BITSIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.EXPAND_BITS, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::EXPAND_BITS);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void EXPAND_BITSIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.EXPAND_BITS, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::EXPAND_BITS);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void addIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.add(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::add);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void addIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.add(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, IntVector512Tests::add);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void subIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.sub(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::sub);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void subIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.sub(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, IntVector512Tests::sub);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void mulIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.mul(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::mul);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void mulIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.mul(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, IntVector512Tests::mul);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void divIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        replaceZero(b, (int) 1);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.div(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::div);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void divIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        replaceZero(b, (int) 1);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.div(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, IntVector512Tests::div);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ORIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.OR, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::OR);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void orIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.or(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::or);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ORIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.OR, b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, IntVector512Tests::OR);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ANDIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.AND, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::AND);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void andIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.and(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::and);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ANDIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.AND, b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, mask, IntVector512Tests::AND);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ORIntVector512TestsBroadcastLongSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.OR, (long)b[i]).intoArray(r, i);
        }

        assertBroadcastLongArraysEquals(r, a, b, IntVector512Tests::OR);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ORIntVector512TestsBroadcastMaskedLongSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.OR, (long)b[i], vmask).intoArray(r, i);
        }

        assertBroadcastLongArraysEquals(r, a, b, mask, IntVector512Tests::OR);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ADDIntVector512TestsBroadcastLongSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.ADD, (long)b[i]).intoArray(r, i);
        }

        assertBroadcastLongArraysEquals(r, a, b, IntVector512Tests::ADD);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ADDIntVector512TestsBroadcastMaskedLongSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.ADD, (long)b[i], vmask).intoArray(r, i);
        }

        assertBroadcastLongArraysEquals(r, a, b, mask, IntVector512Tests::ADD);
    }

    static int LSHL(int a, int b) {
        return (int)((a << b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void LSHLIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.LSHL, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::LSHL);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void LSHLIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.LSHL, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::LSHL);
    }

    static int ASHR(int a, int b) {
        return (int)((a >> b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ASHRIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ASHR, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::ASHR);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ASHRIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ASHR, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::ASHR);
    }

    static int LSHR(int a, int b) {
        return (int)((a >>> b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void LSHRIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.LSHR, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::LSHR);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void LSHRIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.LSHR, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::LSHR);
    }

    static int LSHL_unary(int a, int b) {
        return (int)((a << b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void LSHLIntVector512TestsScalarShift(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHL, (int)b[i]).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, IntVector512Tests::LSHL_unary);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void LSHLIntVector512TestsScalarShiftMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHL, (int)b[i], vmask).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, mask, IntVector512Tests::LSHL_unary);
    }

    static int LSHR_unary(int a, int b) {
        return (int)((a >>> b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void LSHRIntVector512TestsScalarShift(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHR, (int)b[i]).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, IntVector512Tests::LSHR_unary);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void LSHRIntVector512TestsScalarShiftMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHR, (int)b[i], vmask).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, mask, IntVector512Tests::LSHR_unary);
    }

    static int ASHR_unary(int a, int b) {
        return (int)((a >> b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ASHRIntVector512TestsScalarShift(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ASHR, (int)b[i]).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, IntVector512Tests::ASHR_unary);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ASHRIntVector512TestsScalarShiftMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ASHR, (int)b[i], vmask).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, mask, IntVector512Tests::ASHR_unary);
    }

    static int ROR(int a, int b) {
        return (int)(ROR_scalar(a,b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void RORIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ROR, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::ROR);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void RORIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ROR, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::ROR);
    }

    static int ROL(int a, int b) {
        return (int)(ROL_scalar(a,b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ROLIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ROL, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::ROL);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ROLIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ROL, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::ROL);
    }

    static int ROR_unary(int a, int b) {
        return (int)(ROR_scalar(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void RORIntVector512TestsScalarShift(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROR, (int)b[i]).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, IntVector512Tests::ROR_unary);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void RORIntVector512TestsScalarShiftMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROR, (int)b[i], vmask).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, mask, IntVector512Tests::ROR_unary);
    }

    static int ROL_unary(int a, int b) {
        return (int)(ROL_scalar(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void ROLIntVector512TestsScalarShift(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROL, (int)b[i]).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, IntVector512Tests::ROL_unary);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void ROLIntVector512TestsScalarShiftMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROL, (int)b[i], vmask).intoArray(r, i);
            }
        }

        assertShiftArraysEquals(r, a, b, mask, IntVector512Tests::ROL_unary);
    }
    static int LSHR_binary_const(int a) {
        return (int)((a >>> CONST_SHIFT));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void LSHRIntVector512TestsScalarShiftConst(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHR, CONST_SHIFT).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, IntVector512Tests::LSHR_binary_const);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void LSHRIntVector512TestsScalarShiftMaskedConst(IntFunction<int[]> fa,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHR, CONST_SHIFT, vmask).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, mask, IntVector512Tests::LSHR_binary_const);
    }

    static int LSHL_binary_const(int a) {
        return (int)((a << CONST_SHIFT));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void LSHLIntVector512TestsScalarShiftConst(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHL, CONST_SHIFT).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, IntVector512Tests::LSHL_binary_const);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void LSHLIntVector512TestsScalarShiftMaskedConst(IntFunction<int[]> fa,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LSHL, CONST_SHIFT, vmask).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, mask, IntVector512Tests::LSHL_binary_const);
    }

    static int ASHR_binary_const(int a) {
        return (int)((a >> CONST_SHIFT));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ASHRIntVector512TestsScalarShiftConst(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ASHR, CONST_SHIFT).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, IntVector512Tests::ASHR_binary_const);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ASHRIntVector512TestsScalarShiftMaskedConst(IntFunction<int[]> fa,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ASHR, CONST_SHIFT, vmask).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, mask, IntVector512Tests::ASHR_binary_const);
    }

    static int ROR_binary_const(int a) {
        return (int)(ROR_scalar(a, CONST_SHIFT));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void RORIntVector512TestsScalarShiftConst(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROR, CONST_SHIFT).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, IntVector512Tests::ROR_binary_const);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void RORIntVector512TestsScalarShiftMaskedConst(IntFunction<int[]> fa,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROR, CONST_SHIFT, vmask).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, mask, IntVector512Tests::ROR_binary_const);
    }

    static int ROL_binary_const(int a) {
        return (int)(ROL_scalar(a, CONST_SHIFT));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ROLIntVector512TestsScalarShiftConst(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROL, CONST_SHIFT).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, IntVector512Tests::ROL_binary_const);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ROLIntVector512TestsScalarShiftMaskedConst(IntFunction<int[]> fa,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ROL, CONST_SHIFT, vmask).intoArray(r, i);
            }
        }

        assertShiftConstEquals(r, a, mask, IntVector512Tests::ROL_binary_const);
    }


    static IntVector bv_MIN = IntVector.broadcast(SPECIES, (int)10);

    @Test(dataProvider = "intUnaryOpProvider")
    static void MINIntVector512TestsWithMemOp(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MIN, bv_MIN).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (int)10, IntVector512Tests::MIN);
    }

    static IntVector bv_min = IntVector.broadcast(SPECIES, (int)10);

    @Test(dataProvider = "intUnaryOpProvider")
    static void minIntVector512TestsWithMemOp(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.min(bv_min).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (int)10, IntVector512Tests::min);
    }

    static IntVector bv_MIN_M = IntVector.broadcast(SPECIES, (int)10);

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void MINIntVector512TestsMaskedWithMemOp(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MIN, bv_MIN_M, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (int)10, mask, IntVector512Tests::MIN);
    }

    static IntVector bv_MAX = IntVector.broadcast(SPECIES, (int)10);

    @Test(dataProvider = "intUnaryOpProvider")
    static void MAXIntVector512TestsWithMemOp(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MAX, bv_MAX).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (int)10, IntVector512Tests::MAX);
    }

    static IntVector bv_max = IntVector.broadcast(SPECIES, (int)10);

    @Test(dataProvider = "intUnaryOpProvider")
    static void maxIntVector512TestsWithMemOp(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.max(bv_max).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (int)10, IntVector512Tests::max);
    }

    static IntVector bv_MAX_M = IntVector.broadcast(SPECIES, (int)10);

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void MAXIntVector512TestsMaskedWithMemOp(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.MAX, bv_MAX_M, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, (int)10, mask, IntVector512Tests::MAX);
    }

    static int MIN(int a, int b) {
        return (int)(Math.min(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void MINIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MIN, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::MIN);
    }

    static int min(int a, int b) {
        return (int)(Math.min(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void minIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.min(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, IntVector512Tests::min);
    }

    static int MAX(int a, int b) {
        return (int)(Math.max(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void MAXIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MAX, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::MAX);
    }

    static int max(int a, int b) {
        return (int)(Math.max(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void maxIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.max(bv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, IntVector512Tests::max);
    }

    static int UMIN(int a, int b) {
        return (int)(VectorMath.minUnsigned(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void UMINIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.UMIN, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::UMIN);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void UMINIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.UMIN, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::UMIN);
    }

    static int UMAX(int a, int b) {
        return (int)(VectorMath.maxUnsigned(a, b));
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void UMAXIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.UMAX, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::UMAX);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void UMAXIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.UMAX, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::UMAX);
    }

    static int SADD(int a, int b) {
        return (int)(VectorMath.addSaturating(a, b));
    }

    @Test(dataProvider = "intSaturatingBinaryOpProvider")
    static void SADDIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SADD, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::SADD);
    }

    @Test(dataProvider = "intSaturatingBinaryOpMaskProvider")
    static void SADDIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SADD, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::SADD);
    }

    static int SSUB(int a, int b) {
        return (int)(VectorMath.subSaturating(a, b));
    }

    @Test(dataProvider = "intSaturatingBinaryOpProvider")
    static void SSUBIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SSUB, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::SSUB);
    }

    @Test(dataProvider = "intSaturatingBinaryOpMaskProvider")
    static void SSUBIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SSUB, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::SSUB);
    }

    static int SUADD(int a, int b) {
        return (int)(VectorMath.addSaturatingUnsigned(a, b));
    }

    @Test(dataProvider = "intSaturatingBinaryOpProvider")
    static void SUADDIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUADD, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::SUADD);
    }

    @Test(dataProvider = "intSaturatingBinaryOpMaskProvider")
    static void SUADDIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUADD, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::SUADD);
    }

    static int SUSUB(int a, int b) {
        return (int)(VectorMath.subSaturatingUnsigned(a, b));
    }

    @Test(dataProvider = "intSaturatingBinaryOpProvider")
    static void SUSUBIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUSUB, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::SUSUB);
    }

    @Test(dataProvider = "intSaturatingBinaryOpMaskProvider")
    static void SUSUBIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUSUB, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::SUSUB);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void MINIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.MIN, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::MIN);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void minIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.min(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::min);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void MAXIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.MAX, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::MAX);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void maxIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.max(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, IntVector512Tests::max);
    }
    @Test(dataProvider = "intSaturatingBinaryOpAssocProvider")
    static void SUADDAssocIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] rl = fr.apply(SPECIES.length());
        int[] rr = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                IntVector cv = IntVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.SUADD, bv).lanewise(VectorOperators.SUADD, cv).intoArray(rl, i);
                av.lanewise(VectorOperators.SUADD, bv.lanewise(VectorOperators.SUADD, cv)).intoArray(rr, i);
            }
        }

        assertArraysEqualsAssociative(rl, rr, a, b, c, IntVector512Tests::SUADD);
    }

    @Test(dataProvider = "intSaturatingBinaryOpAssocMaskProvider")
    static void SUADDAssocIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                     IntFunction<int[]> fc, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        int[] rl = fr.apply(SPECIES.length());
        int[] rr = fr.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                IntVector cv = IntVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.SUADD, bv, vmask).lanewise(VectorOperators.SUADD, cv, vmask).intoArray(rl, i);
                av.lanewise(VectorOperators.SUADD, bv.lanewise(VectorOperators.SUADD, cv, vmask), vmask).intoArray(rr, i);
            }
        }

        assertArraysEqualsAssociative(rl, rr, a, b, c, mask, IntVector512Tests::SUADD);
    }

    static int ANDReduce(int[] a, int idx) {
        int res = AND_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res &= a[i];
        }

        return res;
    }

    static int ANDReduceAll(int[] a) {
        int res = AND_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res &= ANDReduce(a, i);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ANDReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = AND_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.AND);
                r[i] = v;
                ra &= v;
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::ANDReduce, IntVector512Tests::ANDReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ANDReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = AND_IDENTITY;

        assertEquals((int) (id & id), id,
                            "AND(AND_IDENTITY, AND_IDENTITY) != AND_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) (id & x), x);
                assertEquals((int) (x & id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) (id & x), x,
                                "AND(AND_IDENTITY, " + x + ") != " + x);
            assertEquals((int) (x & id), x,
                                "AND(" + x + ", AND_IDENTITY) != " + x);
        }
    }

    static int ANDReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = AND_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res &= a[i];
        }

        return res;
    }

    static int ANDReduceAllMasked(int[] a, boolean[] mask) {
        int res = AND_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res &= ANDReduceMasked(a, i, mask);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ANDReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = AND_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.AND, vmask);
                r[i] = v;
                ra &= v;
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::ANDReduceMasked, IntVector512Tests::ANDReduceAllMasked);
    }

    static int ORReduce(int[] a, int idx) {
        int res = OR_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res |= a[i];
        }

        return res;
    }

    static int ORReduceAll(int[] a) {
        int res = OR_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res |= ORReduce(a, i);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ORReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = OR_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.OR);
                r[i] = v;
                ra |= v;
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::ORReduce, IntVector512Tests::ORReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ORReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = OR_IDENTITY;

        assertEquals((int) (id | id), id,
                            "OR(OR_IDENTITY, OR_IDENTITY) != OR_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) (id | x), x);
                assertEquals((int) (x | id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) (id | x), x,
                                "OR(OR_IDENTITY, " + x + ") != " + x);
            assertEquals((int) (x | id), x,
                                "OR(" + x + ", OR_IDENTITY) != " + x);
        }
    }

    static int ORReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = OR_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res |= a[i];
        }

        return res;
    }

    static int ORReduceAllMasked(int[] a, boolean[] mask) {
        int res = OR_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res |= ORReduceMasked(a, i, mask);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ORReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = OR_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.OR, vmask);
                r[i] = v;
                ra |= v;
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::ORReduceMasked, IntVector512Tests::ORReduceAllMasked);
    }

    static int XORReduce(int[] a, int idx) {
        int res = XOR_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res ^= a[i];
        }

        return res;
    }

    static int XORReduceAll(int[] a) {
        int res = XOR_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res ^= XORReduce(a, i);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void XORReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = XOR_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.XOR);
                r[i] = v;
                ra ^= v;
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::XORReduce, IntVector512Tests::XORReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void XORReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = XOR_IDENTITY;

        assertEquals((int) (id ^ id), id,
                            "XOR(XOR_IDENTITY, XOR_IDENTITY) != XOR_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) (id ^ x), x);
                assertEquals((int) (x ^ id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) (id ^ x), x,
                                "XOR(XOR_IDENTITY, " + x + ") != " + x);
            assertEquals((int) (x ^ id), x,
                                "XOR(" + x + ", XOR_IDENTITY) != " + x);
        }
    }

    static int XORReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = XOR_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res ^= a[i];
        }

        return res;
    }

    static int XORReduceAllMasked(int[] a, boolean[] mask) {
        int res = XOR_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res ^= XORReduceMasked(a, i, mask);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void XORReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = XOR_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.XOR, vmask);
                r[i] = v;
                ra ^= v;
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::XORReduceMasked, IntVector512Tests::XORReduceAllMasked);
    }

    static int ADDReduce(int[] a, int idx) {
        int res = ADD_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res += a[i];
        }

        return res;
    }

    static int ADDReduceAll(int[] a) {
        int res = ADD_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduce(a, i);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ADDReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = ADD_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.ADD);
                r[i] = v;
                ra += v;
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::ADDReduce, IntVector512Tests::ADDReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ADDReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = ADD_IDENTITY;

        assertEquals((int) (id + id), id,
                            "ADD(ADD_IDENTITY, ADD_IDENTITY) != ADD_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) (id + x), x);
                assertEquals((int) (x + id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) (id + x), x,
                                "ADD(ADD_IDENTITY, " + x + ") != " + x);
            assertEquals((int) (x + id), x,
                                "ADD(" + x + ", ADD_IDENTITY) != " + x);
        }
    }

    static int ADDReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = ADD_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res += a[i];
        }

        return res;
    }

    static int ADDReduceAllMasked(int[] a, boolean[] mask) {
        int res = ADD_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduceMasked(a, i, mask);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ADDReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = ADD_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.ADD, vmask);
                r[i] = v;
                ra += v;
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::ADDReduceMasked, IntVector512Tests::ADDReduceAllMasked);
    }

    static int MULReduce(int[] a, int idx) {
        int res = MUL_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res *= a[i];
        }

        return res;
    }

    static int MULReduceAll(int[] a) {
        int res = MUL_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res *= MULReduce(a, i);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void MULReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = MUL_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.MUL);
                r[i] = v;
                ra *= v;
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::MULReduce, IntVector512Tests::MULReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void MULReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = MUL_IDENTITY;

        assertEquals((int) (id * id), id,
                            "MUL(MUL_IDENTITY, MUL_IDENTITY) != MUL_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) (id * x), x);
                assertEquals((int) (x * id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) (id * x), x,
                                "MUL(MUL_IDENTITY, " + x + ") != " + x);
            assertEquals((int) (x * id), x,
                                "MUL(" + x + ", MUL_IDENTITY) != " + x);
        }
    }

    static int MULReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = MUL_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res *= a[i];
        }

        return res;
    }

    static int MULReduceAllMasked(int[] a, boolean[] mask) {
        int res = MUL_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res *= MULReduceMasked(a, i, mask);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void MULReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = MUL_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.MUL, vmask);
                r[i] = v;
                ra *= v;
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::MULReduceMasked, IntVector512Tests::MULReduceAllMasked);
    }

    static int MINReduce(int[] a, int idx) {
        int res = MIN_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = (int) Math.min(res, a[i]);
        }

        return res;
    }

    static int MINReduceAll(int[] a) {
        int res = MIN_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) Math.min(res, MINReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void MINReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = MIN_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.MIN);
                r[i] = v;
                ra = (int) Math.min(ra, v);
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::MINReduce, IntVector512Tests::MINReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void MINReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = MIN_IDENTITY;

        assertEquals((int) Math.min(id, id), id,
                            "MIN(MIN_IDENTITY, MIN_IDENTITY) != MIN_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) Math.min(id, x), x);
                assertEquals((int) Math.min(x, id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) Math.min(id, x), x,
                                "MIN(MIN_IDENTITY, " + x + ") != " + x);
            assertEquals((int) Math.min(x, id), x,
                                "MIN(" + x + ", MIN_IDENTITY) != " + x);
        }
    }

    static int MINReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = MIN_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = (int) Math.min(res, a[i]);
        }

        return res;
    }

    static int MINReduceAllMasked(int[] a, boolean[] mask) {
        int res = MIN_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) Math.min(res, MINReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void MINReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = MIN_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.MIN, vmask);
                r[i] = v;
                ra = (int) Math.min(ra, v);
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::MINReduceMasked, IntVector512Tests::MINReduceAllMasked);
    }

    static int MAXReduce(int[] a, int idx) {
        int res = MAX_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = (int) Math.max(res, a[i]);
        }

        return res;
    }

    static int MAXReduceAll(int[] a) {
        int res = MAX_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) Math.max(res, MAXReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void MAXReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = MAX_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.MAX);
                r[i] = v;
                ra = (int) Math.max(ra, v);
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::MAXReduce, IntVector512Tests::MAXReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void MAXReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = MAX_IDENTITY;

        assertEquals((int) Math.max(id, id), id,
                            "MAX(MAX_IDENTITY, MAX_IDENTITY) != MAX_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) Math.max(id, x), x);
                assertEquals((int) Math.max(x, id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) Math.max(id, x), x,
                                "MAX(MAX_IDENTITY, " + x + ") != " + x);
            assertEquals((int) Math.max(x, id), x,
                                "MAX(" + x + ", MAX_IDENTITY) != " + x);
        }
    }

    static int MAXReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = MAX_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = (int) Math.max(res, a[i]);
        }

        return res;
    }

    static int MAXReduceAllMasked(int[] a, boolean[] mask) {
        int res = MAX_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) Math.max(res, MAXReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void MAXReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = MAX_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.MAX, vmask);
                r[i] = v;
                ra = (int) Math.max(ra, v);
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::MAXReduceMasked, IntVector512Tests::MAXReduceAllMasked);
    }

    static int UMINReduce(int[] a, int idx) {
        int res = UMIN_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = (int) VectorMath.minUnsigned(res, a[i]);
        }

        return res;
    }

    static int UMINReduceAll(int[] a) {
        int res = UMIN_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) VectorMath.minUnsigned(res, UMINReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void UMINReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = UMIN_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.UMIN);
                r[i] = v;
                ra = (int) VectorMath.minUnsigned(ra, v);
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::UMINReduce, IntVector512Tests::UMINReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void UMINReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = UMIN_IDENTITY;

        assertEquals((int) VectorMath.minUnsigned(id, id), id,
                            "UMIN(UMIN_IDENTITY, UMIN_IDENTITY) != UMIN_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) VectorMath.minUnsigned(id, x), x);
                assertEquals((int) VectorMath.minUnsigned(x, id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) VectorMath.minUnsigned(id, x), x,
                                "UMIN(UMIN_IDENTITY, " + x + ") != " + x);
            assertEquals((int) VectorMath.minUnsigned(x, id), x,
                                "UMIN(" + x + ", UMIN_IDENTITY) != " + x);
        }
    }

    static int UMINReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = UMIN_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = (int) VectorMath.minUnsigned(res, a[i]);
        }

        return res;
    }

    static int UMINReduceAllMasked(int[] a, boolean[] mask) {
        int res = UMIN_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) VectorMath.minUnsigned(res, UMINReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void UMINReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = UMIN_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.UMIN, vmask);
                r[i] = v;
                ra = (int) VectorMath.minUnsigned(ra, v);
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::UMINReduceMasked, IntVector512Tests::UMINReduceAllMasked);
    }

    static int UMAXReduce(int[] a, int idx) {
        int res = UMAX_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = (int) VectorMath.maxUnsigned(res, a[i]);
        }

        return res;
    }

    static int UMAXReduceAll(int[] a) {
        int res = UMAX_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) VectorMath.maxUnsigned(res, UMAXReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void UMAXReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = UMAX_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.UMAX);
                r[i] = v;
                ra = (int) VectorMath.maxUnsigned(ra, v);
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::UMAXReduce, IntVector512Tests::UMAXReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void UMAXReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = UMAX_IDENTITY;

        assertEquals((int) VectorMath.maxUnsigned(id, id), id,
                            "UMAX(UMAX_IDENTITY, UMAX_IDENTITY) != UMAX_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) VectorMath.maxUnsigned(id, x), x);
                assertEquals((int) VectorMath.maxUnsigned(x, id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) VectorMath.maxUnsigned(id, x), x,
                                "UMAX(UMAX_IDENTITY, " + x + ") != " + x);
            assertEquals((int) VectorMath.maxUnsigned(x, id), x,
                                "UMAX(" + x + ", UMAX_IDENTITY) != " + x);
        }
    }

    static int UMAXReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = UMAX_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = (int) VectorMath.maxUnsigned(res, a[i]);
        }

        return res;
    }

    static int UMAXReduceAllMasked(int[] a, boolean[] mask) {
        int res = UMAX_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) VectorMath.maxUnsigned(res, UMAXReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void UMAXReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = UMAX_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.UMAX, vmask);
                r[i] = v;
                ra = (int) VectorMath.maxUnsigned(ra, v);
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::UMAXReduceMasked, IntVector512Tests::UMAXReduceAllMasked);
    }

    static int FIRST_NONZEROReduce(int[] a, int idx) {
        int res = FIRST_NONZERO_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = firstNonZero(res, a[i]);
        }

        return res;
    }

    static int FIRST_NONZEROReduceAll(int[] a) {
        int res = FIRST_NONZERO_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = firstNonZero(res, FIRST_NONZEROReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void FIRST_NONZEROReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = FIRST_NONZERO_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.FIRST_NONZERO);
                r[i] = v;
                ra = firstNonZero(ra, v);
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::FIRST_NONZEROReduce, IntVector512Tests::FIRST_NONZEROReduceAll);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void FIRST_NONZEROReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = FIRST_NONZERO_IDENTITY;

        assertEquals(firstNonZero(id, id), id,
                            "FIRST_NONZERO(FIRST_NONZERO_IDENTITY, FIRST_NONZERO_IDENTITY) != FIRST_NONZERO_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals(firstNonZero(id, x), x);
                assertEquals(firstNonZero(x, id), x);
            }
        } catch (AssertionError e) {
            assertEquals(firstNonZero(id, x), x,
                                "FIRST_NONZERO(FIRST_NONZERO_IDENTITY, " + x + ") != " + x);
            assertEquals(firstNonZero(x, id), x,
                                "FIRST_NONZERO(" + x + ", FIRST_NONZERO_IDENTITY) != " + x);
        }
    }

    static int FIRST_NONZEROReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = FIRST_NONZERO_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = firstNonZero(res, a[i]);
        }

        return res;
    }

    static int FIRST_NONZEROReduceAllMasked(int[] a, boolean[] mask) {
        int res = FIRST_NONZERO_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = firstNonZero(res, FIRST_NONZEROReduceMasked(a, i, mask));
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void FIRST_NONZEROReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = FIRST_NONZERO_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.FIRST_NONZERO, vmask);
                r[i] = v;
                ra = firstNonZero(ra, v);
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::FIRST_NONZEROReduceMasked, IntVector512Tests::FIRST_NONZEROReduceAllMasked);
    }

    static boolean anyTrue(boolean[] a, int idx) {
        boolean res = false;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res |= a[i];
        }

        return res;
    }

    @Test(dataProvider = "boolUnaryOpProvider")
    static void anyTrueIntVector512Tests(IntFunction<boolean[]> fm) {
        boolean[] mask = fm.apply(SPECIES.length());
        boolean[] r = fmr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < mask.length; i += SPECIES.length()) {
                VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, i);
                r[i] = vmask.anyTrue();
            }
        }

        assertReductionBoolArraysEquals(r, mask, IntVector512Tests::anyTrue);
    }

    static boolean allTrue(boolean[] a, int idx) {
        boolean res = true;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res &= a[i];
        }

        return res;
    }

    @Test(dataProvider = "boolUnaryOpProvider")
    static void allTrueIntVector512Tests(IntFunction<boolean[]> fm) {
        boolean[] mask = fm.apply(SPECIES.length());
        boolean[] r = fmr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < mask.length; i += SPECIES.length()) {
                VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, i);
                r[i] = vmask.allTrue();
            }
        }

        assertReductionBoolArraysEquals(r, mask, IntVector512Tests::allTrue);
    }

    static int SUADDReduce(int[] a, int idx) {
        int res = SUADD_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = (int) VectorMath.addSaturatingUnsigned(res, a[i]);
        }

        return res;
    }

    static int SUADDReduceAll(int[] a) {
        int res = SUADD_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) VectorMath.addSaturatingUnsigned(res, SUADDReduce(a, i));
        }

        return res;
    }

    @Test(dataProvider = "intSaturatingUnaryOpProvider")
    static void SUADDReduceIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = SUADD_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.SUADD);
                r[i] = v;
                ra = (int) VectorMath.addSaturatingUnsigned(ra, v);
            }
        }

        assertReductionArraysEquals(r, ra, a,
                IntVector512Tests::SUADDReduce, IntVector512Tests::SUADDReduceAll);
    }

    @Test(dataProvider = "intSaturatingUnaryOpProvider")
    static void SUADDReduceIdentityValueTests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int id = SUADD_IDENTITY;

        assertEquals((int) VectorMath.addSaturatingUnsigned(id, id), id,
                            "SUADD(SUADD_IDENTITY, SUADD_IDENTITY) != SUADD_IDENTITY");

        int x = 0;
        try {
            for (int i = 0; i < a.length; i++) {
                x = a[i];
                assertEquals((int) VectorMath.addSaturatingUnsigned(id, x), x);
                assertEquals((int) VectorMath.addSaturatingUnsigned(x, id), x);
            }
        } catch (AssertionError e) {
            assertEquals((int) VectorMath.addSaturatingUnsigned(id, x), x,
                                "SUADD(SUADD_IDENTITY, " + x + ") != " + x);
            assertEquals((int) VectorMath.addSaturatingUnsigned(x, id), x,
                                "SUADD(" + x + ", SUADD_IDENTITY) != " + x);
        }
    }

    static int SUADDReduceMasked(int[] a, int idx, boolean[] mask) {
        int res = SUADD_IDENTITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res = (int) VectorMath.addSaturatingUnsigned(res, a[i]);
        }

        return res;
    }

    static int SUADDReduceAllMasked(int[] a, boolean[] mask) {
        int res = SUADD_IDENTITY;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res = (int) VectorMath.addSaturatingUnsigned(res, SUADDReduceMasked(a, i, mask));
        }

        return res;
    }
    @Test(dataProvider = "intSaturatingUnaryOpMaskProvider")
    static void SUADDReduceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = SUADD_IDENTITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                int v = av.reduceLanes(VectorOperators.SUADD, vmask);
                r[i] = v;
                ra = (int) VectorMath.addSaturatingUnsigned(ra, v);
            }
        }

        assertReductionArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::SUADDReduceMasked, IntVector512Tests::SUADDReduceAllMasked);
    }

    @Test(dataProvider = "intBinaryOpProvider")
    static void withIntVector512Tests(IntFunction<int []> fa, IntFunction<int []> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0, j = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.withLane(j, b[i + j]).intoArray(r, i);
                a[i + j] = b[i + j];
                j = (j + 1) & (SPECIES.length() - 1);
            }
        }


        assertArraysStrictlyEquals(r, a);
    }

    static boolean testIS_DEFAULT(int a) {
        return bits(a)==0;
    }

    @Test(dataProvider = "intTestOpProvider")
    static void IS_DEFAULTIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                VectorMask<Integer> mv = av.test(VectorOperators.IS_DEFAULT);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), testIS_DEFAULT(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intTestOpMaskProvider")
    static void IS_DEFAULTMaskedIntVector512Tests(IntFunction<int[]> fa,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                VectorMask<Integer> mv = av.test(VectorOperators.IS_DEFAULT, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j),  vmask.laneIsSet(j) && testIS_DEFAULT(a[i + j]));
                }
            }
        }
    }

    static boolean testIS_NEGATIVE(int a) {
        return bits(a)<0;
    }

    @Test(dataProvider = "intTestOpProvider")
    static void IS_NEGATIVEIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                VectorMask<Integer> mv = av.test(VectorOperators.IS_NEGATIVE);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), testIS_NEGATIVE(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intTestOpMaskProvider")
    static void IS_NEGATIVEMaskedIntVector512Tests(IntFunction<int[]> fa,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                VectorMask<Integer> mv = av.test(VectorOperators.IS_NEGATIVE, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j),  vmask.laneIsSet(j) && testIS_NEGATIVE(a[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void LTIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.LT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), lt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void ltIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.lt(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), lt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void LTIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.LT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && lt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void GTIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.GT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), gt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void GTIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.GT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && gt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void EQIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.EQ, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), eq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void eqIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.eq(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), eq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void EQIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.EQ, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && eq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void NEIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.NE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), neq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void NEIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.NE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && neq(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void LEIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.LE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), le(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void LEIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.LE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && le(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void GEIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.GE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), ge(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void GEIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.GE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && ge(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void ULTIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.ULT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), ult(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void ULTIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.ULT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && ult(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void UGTIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.UGT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), ugt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void UGTIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.UGT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && ugt(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void ULEIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.ULE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), ule(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void ULEIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.ULE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && ule(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void UGEIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.UGE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), uge(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void UGEIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                VectorMask<Integer> mv = av.compare(VectorOperators.UGE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    assertEquals(mv.laneIsSet(j), mask[j] && uge(a[i + j], b[i + j]));
                }
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void LTIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.LT, b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), a[i + j] < b[i]);
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void LTIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa,
                                IntFunction<int[]> fb, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.LT, b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] < b[i]));
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void LTIntVector512TestsBroadcastLongSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.LT, (long)b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), a[i + j] < (int)((long)b[i]));
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void LTIntVector512TestsBroadcastLongMaskedSmokeTest(IntFunction<int[]> fa,
                                IntFunction<int[]> fb, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.LT, (long)b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] < (int)((long)b[i])));
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void EQIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.EQ, b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), a[i + j] == b[i]);
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void EQIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa,
                                IntFunction<int[]> fb, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.EQ, b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] == b[i]));
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void EQIntVector512TestsBroadcastLongSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.EQ, (long)b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), a[i + j] == (int)((long)b[i]));
            }
        }
    }

    @Test(dataProvider = "intCompareOpMaskProvider")
    static void EQIntVector512TestsBroadcastLongMaskedSmokeTest(IntFunction<int[]> fa,
                                IntFunction<int[]> fb, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.compare(VectorOperators.EQ, (long)b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] == (int)((long)b[i])));
            }
        }
    }

    static int blend(int a, int b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void blendIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.blend(bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, mask, IntVector512Tests::blend);
    }

    @Test(dataProvider = "intUnaryOpShuffleProvider")
    static void RearrangeIntVector512Tests(IntFunction<int[]> fa,
                                           BiFunction<Integer,Integer,int[]> fs) {
        int[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.rearrange(VectorShuffle.fromArray(SPECIES, order, i)).intoArray(r, i);
            }
        }

        assertRearrangeArraysEquals(r, a, order, SPECIES.length());
    }

    @Test(dataProvider = "intUnaryOpShuffleMaskProvider")
    static void RearrangeIntVector512TestsMaskedSmokeTest(IntFunction<int[]> fa,
                                                          BiFunction<Integer,Integer,int[]> fs,
                                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.rearrange(VectorShuffle.fromArray(SPECIES, order, i), vmask).intoArray(r, i);
        }

        assertRearrangeArraysEquals(r, a, order, mask, SPECIES.length());
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void compressIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.compress(vmask).intoArray(r, i);
            }
        }

        assertcompressArraysEquals(r, a, mask, SPECIES.length());
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void expandIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.expand(vmask).intoArray(r, i);
            }
        }

        assertexpandArraysEquals(r, a, mask, SPECIES.length());
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void getIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
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

    @Test(dataProvider = "intUnaryOpProvider")
    static void BroadcastIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector.broadcast(SPECIES, a[i]).intoArray(r, i);
            }
        }

        assertBroadcastArraysEquals(r, a);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ZeroIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector.zero(SPECIES).intoArray(a, i);
            }
        }

        assertEquals(a, r);
    }

    static int[] sliceUnary(int[] a, int origin, int idx) {
        int[] res = new int[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = a[idx+i+origin];
            else
                res[i] = (int)0;
        }
        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void sliceUnaryIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.slice(origin).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, origin, IntVector512Tests::sliceUnary);
    }

    static int[] sliceBinary(int[] a, int[] b, int origin, int idx) {
        int[] res = new int[SPECIES.length()];
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

    @Test(dataProvider = "intBinaryOpProvider")
    static void sliceBinaryIntVector512TestsBinary(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = new int[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.slice(origin, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, IntVector512Tests::sliceBinary);
    }

    static int[] slice(int[] a, int[] b, int origin, boolean[] mask, int idx) {
        int[] res = new int[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = mask[i] ? a[idx+i+origin] : (int)0;
            else {
                res[i] = mask[i] ? b[idx+j] : (int)0;
                j++;
            }
        }
        return res;
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void sliceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
    IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        int[] r = new int[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.slice(origin, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, mask, IntVector512Tests::slice);
    }

    static int[] unsliceUnary(int[] a, int origin, int idx) {
        int[] res = new int[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i < origin)
                res[i] = (int)0;
            else {
                res[i] = a[idx+j];
                j++;
            }
        }
        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void unsliceUnaryIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.unslice(origin).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, origin, IntVector512Tests::unsliceUnary);
    }

    static int[] unsliceBinary(int[] a, int[] b, int origin, int part, int idx) {
        int[] res = new int[SPECIES.length()];
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

    @Test(dataProvider = "intBinaryOpProvider")
    static void unsliceBinaryIntVector512TestsBinary(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = new int[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        int part = RAND.nextInt(2);
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.unslice(origin, bv, part).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, part, IntVector512Tests::unsliceBinary);
    }

    static int[] unslice(int[] a, int[] b, int origin, int part, boolean[] mask, int idx) {
        int[] res = new int[SPECIES.length()];
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
        int[] res1 = new int[SPECIES.length()];
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

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void unsliceIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
    IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        int[] r = new int[a.length];
        int origin = RAND.nextInt(SPECIES.length());
        int part = RAND.nextInt(2);
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                av.unslice(origin, bv, part, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, origin, part, mask, IntVector512Tests::unslice);
    }

    static int BITWISE_BLEND(int a, int b, int c) {
        return (int)((a&~(c))|(b&c));
    }

    static int bitwiseBlend(int a, int b, int c) {
        return (int)((a&~(c))|(b&c));
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void BITWISE_BLENDIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                IntVector cv = IntVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.BITWISE_BLEND, bv, cv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, c, IntVector512Tests::BITWISE_BLEND);
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void bitwiseBlendIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            IntVector cv = IntVector.fromArray(SPECIES, c, i);
            av.bitwiseBlend(bv, cv).intoArray(r, i);
        }

        assertArraysEquals(r, a, b, c, IntVector512Tests::bitwiseBlend);
    }

    @Test(dataProvider = "intTernaryOpMaskProvider")
    static void BITWISE_BLENDIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<int[]> fc, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                IntVector cv = IntVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.BITWISE_BLEND, bv, cv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, c, mask, IntVector512Tests::BITWISE_BLEND);
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void BITWISE_BLENDIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.lanewise(VectorOperators.BITWISE_BLEND, bv, c[i]).intoArray(r, i);
        }
        assertBroadcastArraysEquals(r, a, b, c, IntVector512Tests::BITWISE_BLEND);
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void BITWISE_BLENDIntVector512TestsAltBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector cv = IntVector.fromArray(SPECIES, c, i);
            av.lanewise(VectorOperators.BITWISE_BLEND, b[i], cv).intoArray(r, i);
        }
        assertAltBroadcastArraysEquals(r, a, b, c, IntVector512Tests::BITWISE_BLEND);
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void bitwiseBlendIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.bitwiseBlend(bv, c[i]).intoArray(r, i);
        }
        assertBroadcastArraysEquals(r, a, b, c, IntVector512Tests::bitwiseBlend);
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void bitwiseBlendIntVector512TestsAltBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector cv = IntVector.fromArray(SPECIES, c, i);
            av.bitwiseBlend(b[i], cv).intoArray(r, i);
        }
        assertAltBroadcastArraysEquals(r, a, b, c, IntVector512Tests::bitwiseBlend);
    }

    @Test(dataProvider = "intTernaryOpMaskProvider")
    static void BITWISE_BLENDIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<int[]> fc, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, b, i);
            av.lanewise(VectorOperators.BITWISE_BLEND, bv, c[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(r, a, b, c, mask, IntVector512Tests::BITWISE_BLEND);
    }

    @Test(dataProvider = "intTernaryOpMaskProvider")
    static void BITWISE_BLENDIntVector512TestsAltBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<int[]> fc, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector cv = IntVector.fromArray(SPECIES, c, i);
            av.lanewise(VectorOperators.BITWISE_BLEND, b[i], cv, vmask).intoArray(r, i);
        }

        assertAltBroadcastArraysEquals(r, a, b, c, mask, IntVector512Tests::BITWISE_BLEND);
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void BITWISE_BLENDIntVector512TestsDoubleBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.BITWISE_BLEND, b[i], c[i]).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(r, a, b, c, IntVector512Tests::BITWISE_BLEND);
    }

    @Test(dataProvider = "intTernaryOpProvider")
    static void bitwiseBlendIntVector512TestsDoubleBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.bitwiseBlend(b[i], c[i]).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(r, a, b, c, IntVector512Tests::bitwiseBlend);
    }

    @Test(dataProvider = "intTernaryOpMaskProvider")
    static void BITWISE_BLENDIntVector512TestsDoubleBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<int[]> fc, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] c = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.BITWISE_BLEND, b[i], c[i], vmask).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(r, a, b, c, mask, IntVector512Tests::BITWISE_BLEND);
    }

    static int NEG(int a) {
        return (int)(-((int)a));
    }

    static int neg(int a) {
        return (int)(-((int)a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void NEGIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::NEG);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void negIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.neg().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::neg);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void NEGMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::NEG);
    }

    static int ABS(int a) {
        return (int)(Math.abs((int)a));
    }

    static int abs(int a) {
        return (int)(Math.abs((int)a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ABSIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::ABS);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void absIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.abs().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::abs);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ABSMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::ABS);
    }

    static int NOT(int a) {
        return (int)(~((int)a));
    }

    static int not(int a) {
        return (int)(~((int)a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void NOTIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NOT).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::NOT);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void notIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.not().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::not);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void NOTMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NOT, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::NOT);
    }

    static int ZOMO(int a) {
        return (int)((a==0?0:-1));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ZOMOIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ZOMO).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::ZOMO);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ZOMOMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ZOMO, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::ZOMO);
    }

    static int BIT_COUNT(int a) {
        return (int)(Integer.bitCount(a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void BIT_COUNTIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.BIT_COUNT).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::BIT_COUNT);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void BIT_COUNTMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.BIT_COUNT, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::BIT_COUNT);
    }

    static int TRAILING_ZEROS_COUNT(int a) {
        return (int)(TRAILING_ZEROS_COUNT_scalar(a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void TRAILING_ZEROS_COUNTIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TRAILING_ZEROS_COUNT).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::TRAILING_ZEROS_COUNT);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void TRAILING_ZEROS_COUNTMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TRAILING_ZEROS_COUNT, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::TRAILING_ZEROS_COUNT);
    }

    static int LEADING_ZEROS_COUNT(int a) {
        return (int)(LEADING_ZEROS_COUNT_scalar(a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void LEADING_ZEROS_COUNTIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LEADING_ZEROS_COUNT).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::LEADING_ZEROS_COUNT);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void LEADING_ZEROS_COUNTMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LEADING_ZEROS_COUNT, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::LEADING_ZEROS_COUNT);
    }

    static int REVERSE(int a) {
        return (int)(REVERSE_scalar(a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void REVERSEIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.REVERSE).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::REVERSE);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void REVERSEMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.REVERSE, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::REVERSE);
    }

    static int REVERSE_BYTES(int a) {
        return (int)(Integer.reverseBytes(a));
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void REVERSE_BYTESIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.REVERSE_BYTES).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::REVERSE_BYTES);
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void REVERSE_BYTESMaskedIntVector512Tests(IntFunction<int[]> fa,
                                                IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.REVERSE_BYTES, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, mask, IntVector512Tests::REVERSE_BYTES);
    }

    static boolean band(boolean a, boolean b) {
        return a & b;
    }

    @Test(dataProvider = "boolMaskBinaryOpProvider")
    static void maskandIntVector512Tests(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                VectorMask av = SPECIES.loadMask(a, i);
                VectorMask bv = SPECIES.loadMask(b, i);
                av.and(bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::band);
    }

    static boolean bor(boolean a, boolean b) {
        return a | b;
    }

    @Test(dataProvider = "boolMaskBinaryOpProvider")
    static void maskorIntVector512Tests(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                VectorMask av = SPECIES.loadMask(a, i);
                VectorMask bv = SPECIES.loadMask(b, i);
                av.or(bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::bor);
    }

    static boolean bxor(boolean a, boolean b) {
        return a != b;
    }

    @Test(dataProvider = "boolMaskBinaryOpProvider")
    static void maskxorIntVector512Tests(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                VectorMask av = SPECIES.loadMask(a, i);
                VectorMask bv = SPECIES.loadMask(b, i);
                av.xor(bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::bxor);
    }

    static boolean bandNot(boolean a, boolean b) {
        return a & !b;
    }

    @Test(dataProvider = "boolMaskBinaryOpProvider")
    static void maskandNotIntVector512Tests(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                VectorMask av = SPECIES.loadMask(a, i);
                VectorMask bv = SPECIES.loadMask(b, i);
                av.andNot(bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::bandNot);
    }

    static boolean beq(boolean a, boolean b) {
        return a == b;
    }

    @Test(dataProvider = "boolMaskBinaryOpProvider")
    static void maskeqIntVector512Tests(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                VectorMask av = SPECIES.loadMask(a, i);
                VectorMask bv = SPECIES.loadMask(b, i);
                av.eq(bv).intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, b, IntVector512Tests::beq);
    }

    static boolean unot(boolean a) {
        return !a;
    }

    @Test(dataProvider = "boolMaskUnaryOpProvider")
    static void masknotIntVector512Tests(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                VectorMask av = SPECIES.loadMask(a, i);
                av.not().intoArray(r, i);
            }
        }

        assertArraysEquals(r, a, IntVector512Tests::unot);
    }

    private static final long LONG_MASK_BITS = 0xFFFFFFFFFFFFFFFFL >>> (64 - SPECIES.length());

    static void assertArraysEquals(long[] r, long[] a, long bits) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                assertEquals(r[i], a[i] & bits);
            }
        } catch (AssertionError e) {
            assertEquals(r[i], a[i] & bits, "(" + a[i] + ") at index #" + i);
        }
    }

    @Test(dataProvider = "longMaskProvider")
    static void maskFromToLongIntVector512Tests(IntFunction<long[]> fa) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i++) {
                VectorMask vmask = VectorMask.fromLong(SPECIES, a[i]);
                r[i] = vmask.toLong();
            }
        }
        assertArraysEquals(r, a, LONG_MASK_BITS);
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void ltIntVector512TestsBroadcastSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.lt(b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), a[i + j] < b[i]);
            }
        }
    }

    @Test(dataProvider = "intCompareOpProvider")
    static void eqIntVector512TestsBroadcastMaskedSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            VectorMask<Integer> mv = av.eq(b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                assertEquals(mv.laneIsSet(j), a[i + j] == b[i]);
            }
        }
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void toIntArrayIntVector512TestsSmokeTest(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            int[] r = av.toIntArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void toLongArrayIntVector512TestsSmokeTest(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            long[] r = av.toLongArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void toDoubleArrayIntVector512TestsSmokeTest(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            double[] r = av.toDoubleArray();
            assertArraysEquals(r, a, i);
        }
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void toStringIntVector512TestsSmokeTest(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            String str = av.toString();

            int subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            Assert.assertTrue(str.equals(Arrays.toString(subarr)), "at index " + i + ", string should be = " + Arrays.toString(subarr) + ", but is = " + str);
        }
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void hashCodeIntVector512TestsSmokeTest(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            int hash = av.hashCode();

            int subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            int expectedHash = Objects.hash(SPECIES, Arrays.hashCode(subarr));
            Assert.assertTrue(hash == expectedHash, "at index " + i + ", hash should be = " + expectedHash + ", but is = " + hash);
        }
    }


    static long ADDReduceLong(int[] a, int idx) {
        int res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res += a[i];
        }

        return (long)res;
    }

    static long ADDReduceAllLong(int[] a) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduceLong(a, i);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void ADDReduceLongIntVector512Tests(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD);
        }

        ra = 0;
        for (int i = 0; i < a.length; i ++) {
            ra += r[i];
        }

        assertReductionLongArraysEquals(r, ra, a,
                IntVector512Tests::ADDReduceLong, IntVector512Tests::ADDReduceAllLong);
    }

    static long ADDReduceLongMasked(int[] a, int idx, boolean[] mask) {
        int res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if(mask[i % SPECIES.length()])
                res += a[i];
        }

        return (long)res;
    }

    static long ADDReduceAllLongMasked(int[] a, boolean[] mask) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduceLongMasked(a, i, mask);
        }

        return res;
    }

    @Test(dataProvider = "intUnaryOpMaskProvider")
    static void ADDReduceLongIntVector512TestsMasked(IntFunction<int[]> fa, IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD, vmask);
        }

        ra = 0;
        for (int i = 0; i < a.length; i ++) {
            ra += r[i];
        }

        assertReductionLongArraysEqualsMasked(r, ra, a, mask,
                IntVector512Tests::ADDReduceLongMasked, IntVector512Tests::ADDReduceAllLongMasked);
    }

    @Test(dataProvider = "intUnaryOpProvider")
    static void BroadcastLongIntVector512TestsSmokeTest(IntFunction<int[]> fa) {
        int[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector.broadcast(SPECIES, (long)a[i]).intoArray(r, i);
        }
        assertBroadcastArraysEquals(r, a);
    }

    @Test(dataProvider = "intBinaryOpMaskProvider")
    static void blendIntVector512TestsBroadcastLongSmokeTest(IntFunction<int[]> fa, IntFunction<int[]> fb,
                                          IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                av.blend((long)b[i], vmask).intoArray(r, i);
            }
        }
        assertBroadcastLongArraysEquals(r, a, b, mask, IntVector512Tests::blend);
    }


    @Test(dataProvider = "intUnaryOpShuffleProvider")
    static void SelectFromIntVector512Tests(IntFunction<int[]> fa,
                                           BiFunction<Integer,Integer,int[]> fs) {
        int[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, order, i);
            bv.selectFrom(av).intoArray(r, i);
        }

        assertSelectFromArraysEquals(r, a, order, SPECIES.length());
    }

    @Test(dataProvider = "intSelectFromTwoVectorOpProvider")
    static void SelectFromTwoVectorIntVector512Tests(IntFunction<int[]> fa, IntFunction<int[]> fb, IntFunction<int[]> fc) {
        int[] a = fa.apply(SPECIES.length());
        int[] b = fb.apply(SPECIES.length());
        int[] idx = fc.apply(SPECIES.length());
        int[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < idx.length; i += SPECIES.length()) {
                IntVector av = IntVector.fromArray(SPECIES, a, i);
                IntVector bv = IntVector.fromArray(SPECIES, b, i);
                IntVector idxv = IntVector.fromArray(SPECIES, idx, i);
                idxv.selectFrom(av, bv).intoArray(r, i);
            }
        }
        assertSelectFromTwoVectorEquals(r, idx, a, b, SPECIES.length());
    }

    @Test(dataProvider = "intUnaryOpShuffleMaskProvider")
    static void SelectFromIntVector512TestsMaskedSmokeTest(IntFunction<int[]> fa,
                                                           BiFunction<Integer,Integer,int[]> fs,
                                                           IntFunction<boolean[]> fm) {
        int[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        int[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            IntVector av = IntVector.fromArray(SPECIES, a, i);
            IntVector bv = IntVector.fromArray(SPECIES, order, i);
            bv.selectFrom(av, vmask).intoArray(r, i);
        }

        assertSelectFromArraysEquals(r, a, order, mask, SPECIES.length());
    }

    @Test(dataProvider = "shuffleProvider")
    static void shuffleMiscellaneousIntVector512TestsSmokeTest(BiFunction<Integer,Integer,int[]> fs) {
        int[] a = fs.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var shuffle = VectorShuffle.fromArray(SPECIES, a, i);
            int hash = shuffle.hashCode();
            int length = shuffle.length();

            int subarr[] = Arrays.copyOfRange(a, i, i + SPECIES.length());
            int expectedHash = Objects.hash(SPECIES, Arrays.hashCode(subarr));
            Assert.assertTrue(hash == expectedHash, "at index " + i + ", hash should be = " + expectedHash + ", but is = " + hash);
            assertEquals(length, SPECIES.length());
        }
    }

    @Test(dataProvider = "shuffleProvider")
    static void shuffleToStringIntVector512TestsSmokeTest(BiFunction<Integer,Integer,int[]> fs) {
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
    static void shuffleEqualsIntVector512TestsSmokeTest(BiFunction<Integer,Integer,int[]> fa, BiFunction<Integer,Integer,int[]> fb) {
        int[] a = fa.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());
        int[] b = fb.apply(SPECIES.length() * BUFFER_REPS, SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var av = VectorShuffle.fromArray(SPECIES, a, i);
            var bv = VectorShuffle.fromArray(SPECIES, b, i);
            boolean eq = av.equals(bv);
            int to = i + SPECIES.length();
            assertEquals(eq, Arrays.equals(a, i, to, b, i, to));
        }
    }

    @Test(dataProvider = "boolMaskBinaryOpProvider")
    static void maskEqualsIntVector512Tests(IntFunction<boolean[]> fa, IntFunction<boolean[]> fb) {
        boolean[] a = fa.apply(SPECIES.length());
        boolean[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var av = SPECIES.loadMask(a, i);
                var bv = SPECIES.loadMask(b, i);
                boolean equals = av.equals(bv);
                int to = i + SPECIES.length();
                assertEquals(equals, Arrays.equals(a, i, to, b, i, to));
            }
        }
    }

    @Test(dataProvider = "maskProvider")
    static void maskHashCodeIntVector512TestsSmokeTest(IntFunction<boolean[]> fa) {
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
    static void maskTrueCountIntVector512TestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.trueCount();
            }
        }

        assertMaskReductionArraysEquals(r, a, IntVector512Tests::maskTrueCount);
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
    static void maskLastTrueIntVector512TestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.lastTrue();
            }
        }

        assertMaskReductionArraysEquals(r, a, IntVector512Tests::maskLastTrue);
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
    static void maskFirstTrueIntVector512TestsSmokeTest(IntFunction<boolean[]> fa) {
        boolean[] a = fa.apply(SPECIES.length());
        int[] r = new int[a.length];

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                r[i] = vmask.firstTrue();
            }
        }

        assertMaskReductionArraysEquals(r, a, IntVector512Tests::maskFirstTrue);
    }

    @Test(dataProvider = "maskProvider")
    static void maskCompressIntVector512TestsSmokeTest(IntFunction<boolean[]> fa) {
        int trueCount = 0;
        boolean[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                var vmask = SPECIES.loadMask(a, i);
                trueCount = vmask.trueCount();
                var rmask = vmask.compress();
                for (int j = 0; j < SPECIES.length(); j++)  {
                    assertEquals(rmask.laneIsSet(j), j < trueCount);
                }
            }
        }
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
    static void indexInRangeIntVector512TestsSmokeTest(int offset) {
        int limit = SPECIES.length() * BUFFER_REPS;
        for (int i = 0; i < limit; i += SPECIES.length()) {
            var actualMask = SPECIES.indexInRange(i + offset, limit);
            var expectedMask = SPECIES.maskAll(true).indexInRange(i + offset, limit);
            assert(actualMask.equals(expectedMask));
            for (int j = 0; j < SPECIES.length(); j++)  {
                int index = i + j + offset;
                assertEquals(actualMask.laneIsSet(j), index >= 0 && index < limit);
            }
        }
    }

    @Test(dataProvider = "offsetProvider")
    static void indexInRangeLongIntVector512TestsSmokeTest(int offset) {
        long limit = SPECIES.length() * BUFFER_REPS;
        for (long i = 0; i < limit; i += SPECIES.length()) {
            var actualMask = SPECIES.indexInRange(i + offset, limit);
            var expectedMask = SPECIES.maskAll(true).indexInRange(i + offset, limit);
            assert(actualMask.equals(expectedMask));
            for (int j = 0; j < SPECIES.length(); j++)  {
                long index = i + j + offset;
                assertEquals(actualMask.laneIsSet(j), index >= 0 && index < limit);
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
    static void loopBoundIntVector512TestsSmokeTest(int length) {
        int actualLoopBound = SPECIES.loopBound(length);
        int expectedLoopBound = length - Math.floorMod(length, SPECIES.length());
        assertEquals(actualLoopBound, expectedLoopBound);
    }

    @Test(dataProvider = "lengthProvider")
    static void loopBoundLongIntVector512TestsSmokeTest(int _length) {
        long length = _length;
        long actualLoopBound = SPECIES.loopBound(length);
        long expectedLoopBound = length - Math.floorMod(length, SPECIES.length());
        assertEquals(actualLoopBound, expectedLoopBound);
    }

    @Test
    static void ElementSizeIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        int elsize = av.elementSize();
        assertEquals(elsize, Integer.SIZE);
    }

    @Test
    static void VectorShapeIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        VectorShape vsh = av.shape();
        assert(vsh.equals(VectorShape.S_512_BIT));
    }

    @Test
    static void ShapeWithLanesIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        VectorShape vsh = av.shape();
        VectorSpecies species = vsh.withLanes(int.class);
        assert(species.equals(SPECIES));
    }

    @Test
    static void ElementTypeIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        assert(av.species().elementType() == int.class);
    }

    @Test
    static void SpeciesElementSizeIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        assert(av.species().elementSize() == Integer.SIZE);
    }

    @Test
    static void VectorTypeIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        assert(av.species().vectorType() == av.getClass());
    }

    @Test
    static void WithLanesIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        VectorSpecies species = av.species().withLanes(int.class);
        assert(species.equals(SPECIES));
    }

    @Test
    static void WithShapeIntVector512TestsSmokeTest() {
        IntVector av = IntVector.zero(SPECIES);
        VectorShape vsh = av.shape();
        VectorSpecies species = av.species().withShape(vsh);
        assert(species.equals(SPECIES));
    }

    @Test
    static void MaskAllTrueIntVector512TestsSmokeTest() {
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
          assertEquals(SPECIES.maskAll(true).toLong(), -1L >>> (64 - SPECIES.length()));
        }
    }
}
