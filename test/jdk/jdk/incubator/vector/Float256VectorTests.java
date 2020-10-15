/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.incubator.vector
 * @run testng/othervm -ea -esa -Xbatch Float256VectorTests
 */

// -- This file was mechanically generated: Do not edit! -- //

import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.Vector;

import jdk.incubator.vector.FloatVector;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.Integer;
import java.util.List;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Test
public class Float256VectorTests extends AbstractVectorTest {

    static final VectorSpecies<Float> SPECIES =
                FloatVector.SPECIES_256;

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);


    static final int BUFFER_REPS = Integer.getInteger("jdk.incubator.vector.test.buffer-vectors", 25000 / 256);

    static final int BUFFER_SIZE = Integer.getInteger("jdk.incubator.vector.test.buffer-size", BUFFER_REPS * (256 / 8));

    interface FUnOp {
        float apply(float a);
    }

    static void assertArraysEquals(float[] a, float[] r, FUnOp f) {
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
        float[] apply(float a);
    }

    static void assertArraysEquals(float[] a, float[] r, FUnArrayOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a[i]));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a[i]);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i);
        }
    }

    static void assertArraysEquals(float[] a, float[] r, boolean[] mask, FUnOp f) {
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
        float apply(float[] a, int idx);
    }

    interface FReductionAllOp {
        float apply(float[] a);
    }

    static void assertReductionArraysEquals(float[] a, float[] b, float c,
                                            FReductionOp f, FReductionAllOp fa) {
        int i = 0;
        try {
            Assert.assertEquals(c, fa.apply(a));
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(b[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(c, fa.apply(a), "Final result is incorrect!");
            Assert.assertEquals(b[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FReductionMaskedOp {
        float apply(float[] a, int idx, boolean[] mask);
    }

    interface FReductionAllMaskedOp {
        float apply(float[] a, boolean[] mask);
    }

    static void assertReductionArraysEqualsMasked(float[] a, float[] b, float c, boolean[] mask,
                                            FReductionMaskedOp f, FReductionAllMaskedOp fa) {
        int i = 0;
        try {
            Assert.assertEquals(c, fa.apply(a, mask));
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(b[i], f.apply(a, i, mask));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(c, fa.apply(a, mask), "Final result is incorrect!");
            Assert.assertEquals(b[i], f.apply(a, i, mask), "at index #" + i);
        }
    }

    interface FReductionOpLong {
        long apply(float[] a, int idx);
    }

    interface FReductionAllOpLong {
        long apply(float[] a);
    }

    static void assertReductionLongArraysEquals(float[] a, long[] b, long c,
                                            FReductionOpLong f, FReductionAllOpLong fa) {
        int i = 0;
        try {
            Assert.assertEquals(c, fa.apply(a));
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(b[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(c, fa.apply(a), "Final result is incorrect!");
            Assert.assertEquals(b[i], f.apply(a, i), "at index #" + i);
        }
    }

    interface FReductionMaskedOpLong {
        long apply(float[] a, int idx, boolean[] mask);
    }

    interface FReductionAllMaskedOpLong {
        long apply(float[] a, boolean[] mask);
    }

    static void assertReductionLongArraysEqualsMasked(float[] a, long[] b, long c, boolean[] mask,
                                            FReductionMaskedOpLong f, FReductionAllMaskedOpLong fa) {
        int i = 0;
        try {
            Assert.assertEquals(c, fa.apply(a, mask));
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(b[i], f.apply(a, i, mask));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(c, fa.apply(a, mask), "Final result is incorrect!");
            Assert.assertEquals(b[i], f.apply(a, i, mask), "at index #" + i);
        }
    }

    interface FBoolReductionOp {
        boolean apply(boolean[] a, int idx);
    }

    static void assertReductionBoolArraysEquals(boolean[] a, boolean[] b, FBoolReductionOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(b[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(b[i], f.apply(a, i), "at index #" + i);
        }
    }

    static void assertInsertArraysEquals(float[] a, float[] b, float element, int index) {
        int i = 0;
        try {
            for (; i < a.length; i += 1) {
                if(i%SPECIES.length() == index) {
                    Assert.assertEquals(b[i], element);
                } else {
                    Assert.assertEquals(b[i], a[i]);
                }
            }
        } catch (AssertionError e) {
            if (i%SPECIES.length() == index) {
                Assert.assertEquals(b[i], element, "at index #" + i);
            } else {
                Assert.assertEquals(b[i], a[i], "at index #" + i);
            }
        }
    }

    static void assertRearrangeArraysEquals(float[] a, float[] r, int[] order, int vector_len) {
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

    static void assertSelectFromArraysEquals(float[] a, float[] r, float[] order, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    Assert.assertEquals(r[i+j], a[i+(int)order[i+j]]);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            Assert.assertEquals(r[i+j], a[i+(int)order[i+j]], "at index #" + idx + ", input = " + a[i+(int)order[i+j]]);
        }
    }

    static void assertRearrangeArraysEquals(float[] a, float[] r, int[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    if (mask[j % SPECIES.length()])
                         Assert.assertEquals(r[i+j], a[i+order[i+j]]);
                    else
                         Assert.assertEquals(r[i+j], (float)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (mask[j % SPECIES.length()])
                Assert.assertEquals(r[i+j], a[i+order[i+j]], "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
            else
                Assert.assertEquals(r[i+j], (float)0, "at index #" + idx + ", input = " + a[i+order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
        }
    }

    static void assertSelectFromArraysEquals(float[] a, float[] r, float[] order, boolean[] mask, int vector_len) {
        int i = 0, j = 0;
        try {
            for (; i < a.length; i += vector_len) {
                for (j = 0; j < vector_len; j++) {
                    if (mask[j % SPECIES.length()])
                         Assert.assertEquals(r[i+j], a[i+(int)order[i+j]]);
                    else
                         Assert.assertEquals(r[i+j], (float)0);
                }
            }
        } catch (AssertionError e) {
            int idx = i + j;
            if (mask[j % SPECIES.length()])
                Assert.assertEquals(r[i+j], a[i+(int)order[i+j]], "at index #" + idx + ", input = " + a[i+(int)order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
            else
                Assert.assertEquals(r[i+j], (float)0, "at index #" + idx + ", input = " + a[i+(int)order[i+j]] + ", mask = " + mask[j % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(float[]a, float[]r) {
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
        float apply(float a, float b);
    }

    interface FBinMaskOp {
        float apply(float a, float b, boolean m);

        static FBinMaskOp lift(FBinOp f) {
            return (a, b, m) -> m ? f.apply(a, b) : a;
        }
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i]), "(" + a[i] + ", " + b[i] + ") at index #" + i);
        }
    }

    static void assertBroadcastArraysEquals(float[] a, float[] b, float[] r, FBinOp f) {
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


    static void assertArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinOp f) {
        assertArraysEquals(a, b, r, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    static void assertBroadcastArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinOp f) {
        assertBroadcastArraysEquals(a, b, r, mask, FBinMaskOp.lift(f));
    }

    static void assertBroadcastArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinMaskOp f) {
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


    static void assertShiftArraysEquals(float[] a, float[] b, float[] r, FBinOp f) {
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

    static void assertShiftArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinOp f) {
        assertShiftArraysEquals(a, b, r, mask, FBinMaskOp.lift(f));
    }

    static void assertShiftArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinMaskOp f) {
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

    interface FTernOp {
        float apply(float a, float b, float c);
    }

    interface FTernMaskOp {
        float apply(float a, float b, float c, boolean m);

        static FTernMaskOp lift(FTernOp f) {
            return (a, b, c, m) -> m ? f.apply(a, b, c) : a;
        }
    }

    static void assertArraysEquals(float[] a, float[] b, float[] c, float[] r, FTernOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], c[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", input3 = " + c[i]);
        }
    }

    static void assertArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask, FTernOp f) {
        assertArraysEquals(a, b, c, r, mask, FTernMaskOp.lift(f));
    }

    static void assertArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask, FTernMaskOp f) {
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

    static void assertBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, FTernOp f) {
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

    static void assertAltBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, FTernOp f) {
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

    static void assertBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask,
                                            FTernOp f) {
        assertBroadcastArraysEquals(a, b, c, r, mask, FTernMaskOp.lift(f));
    }

    static void assertBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask,
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

    static void assertAltBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask,
                                            FTernOp f) {
        assertAltBroadcastArraysEquals(a, b, c, r, mask, FTernMaskOp.lift(f));
    }

    static void assertAltBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask,
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

    static void assertDoubleBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, FTernOp f) {
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

    static void assertDoubleBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask,
                                                  FTernOp f) {
        assertDoubleBroadcastArraysEquals(a, b, c, r, mask, FTernMaskOp.lift(f));
    }

    static void assertDoubleBroadcastArraysEquals(float[] a, float[] b, float[] c, float[] r, boolean[] mask,
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


    static boolean isWithin1Ulp(float actual, float expected) {
        if (Float.isNaN(expected) && !Float.isNaN(actual)) {
            return false;
        } else if (!Float.isNaN(expected) && Float.isNaN(actual)) {
            return false;
        }

        float low = Math.nextDown(expected);
        float high = Math.nextUp(expected);

        if (Float.compare(low, expected) > 0) {
            return false;
        }

        if (Float.compare(high, expected) < 0) {
            return false;
        }

        return true;
    }

    static void assertArraysEqualsWithinOneUlp(float[] a, float[] r, FUnOp mathf, FUnOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i])) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i])) == 0, "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected = " + mathf.apply(a[i]));
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i])), "at index #" + i + ", input = " + a[i] + ", actual = " + r[i] + ", expected (within 1 ulp) = " + strictmathf.apply(a[i]));
        }
    }

    static void assertArraysEqualsWithinOneUlp(float[] a, float[] b, float[] r, FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i], b[i])) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i], b[i])) == 0, "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", actual = " + r[i] + ", expected = " + mathf.apply(a[i], b[i]));
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", actual = " + r[i] + ", expected (within 1 ulp) = " + strictmathf.apply(a[i], b[i]));
        }
    }

    static void assertBroadcastArraysEqualsWithinOneUlp(float[] a, float[] b, float[] r,
                                                        FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float.compare(r[i],
                                  mathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()])) == 0 ||
                                  isWithin1Ulp(r[i],
                                  strictmathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float.compare(r[i],
                              mathf.apply(a[i], b[(i / SPECIES.length()) * SPECIES.length()])) == 0,
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

    interface FBinArrayOp {
        float apply(float[] a, int b);
    }

    static void assertArraysEquals(float[] a, float[] r, FBinArrayOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a, i));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a,i), "at index #" + i);
        }
    }

    interface FGatherScatterOp {
        float[] apply(float[] a, int ix, int[] b, int iy);
    }

    static void assertArraysEquals(float[] a, int[] b, float[] r, FGatherScatterOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, b, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a, i, b, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(res, ref,
              "(ref: " + Arrays.toString(ref) + ", res: " + Arrays.toString(res) + ", a: "
              + Arrays.toString(Arrays.copyOfRange(a, i, i+SPECIES.length()))
              + ", b: "
              + Arrays.toString(Arrays.copyOfRange(b, i, i+SPECIES.length()))
              + " at index #" + i);
        }
    }

    interface FGatherMaskedOp {
        float[] apply(float[] a, int ix, boolean[] mask, int[] b, int iy);
    }

    interface FScatterMaskedOp {
        float[] apply(float[] r, float[] a, int ix, boolean[] mask, int[] b, int iy);
    }

    static void assertArraysEquals(float[] a, int[] b, float[] r, boolean[] mask, FGatherMaskedOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a, i, mask, b, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res,
              "(ref: " + Arrays.toString(ref) + ", res: " + Arrays.toString(res) + ", a: "
              + Arrays.toString(Arrays.copyOfRange(a, i, i+SPECIES.length()))
              + ", b: "
              + Arrays.toString(Arrays.copyOfRange(b, i, i+SPECIES.length()))
              + ", mask: "
              + Arrays.toString(mask)
              + " at index #" + i);
        }
    }

    static void assertArraysEquals(float[] a, int[] b, float[] r, boolean[] mask, FScatterMaskedOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(r, a, i, mask, b, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(r, a, i, mask, b, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res,
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
        float[] apply(float[] a, int origin, int idx);
    }

    static void assertArraysEquals(float[] a, float[] r, int origin, FLaneOp f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, origin, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a, origin, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i);
        }
    }

    interface FLaneBop {
        float[] apply(float[] a, float[] b, int origin, int idx);
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, int origin, FLaneBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a, b, origin, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin);
        }
    }

    interface FLaneMaskedBop {
        float[] apply(float[] a, float[] b, int origin, boolean[] mask, int idx);
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, int origin, boolean[] mask, FLaneMaskedBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, mask, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a, b, origin, mask, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin);
        }
    }

    interface FLanePartBop {
        float[] apply(float[] a, float[] b, int origin, int part, int idx);
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, int origin, int part, FLanePartBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a, b, origin, part, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin
              + ", with part #" + part);
        }
    }

    interface FLanePartMaskedBop {
        float[] apply(float[] a, float[] b, int origin, int part, boolean[] mask, int idx);
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, int origin, int part, boolean[] mask, FLanePartMaskedBop f) {
        int i = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                Assert.assertEquals(Arrays.copyOfRange(r, i, i+SPECIES.length()),
                  f.apply(a, b, origin, part, mask, i));
            }
        } catch (AssertionError e) {
            float[] ref = f.apply(a, b, origin, part, mask, i);
            float[] res = Arrays.copyOfRange(r, i, i+SPECIES.length());
            Assert.assertEquals(ref, res, "(ref: " + Arrays.toString(ref)
              + ", res: " + Arrays.toString(res)
              + "), at index #" + i
              + ", at origin #" + origin
              + ", with part #" + part);
        }
    }

    static int bits(float e) {
        return  Float.floatToIntBits(e);
    }

    static final List<IntFunction<float[]>> FLOAT_GENERATORS = List.of(
            withToString("float[-i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (float)(-i * 5));
            }),
            withToString("float[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (float)(i * 5));
            }),
            withToString("float[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (((float)(i + 1) == 0) ? 1 : (float)(i + 1)));
            }),
            withToString("float[cornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> cornerCaseValue(i));
            })
    );

    // Create combinations of pairs
    // @@@ Might be sensitive to order e.g. div by 0
    static final List<List<IntFunction<float[]>>> FLOAT_GENERATOR_PAIRS =
        Stream.of(FLOAT_GENERATORS.get(0)).
                flatMap(fa -> FLOAT_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] boolUnaryOpProvider() {
        return BOOL_ARRAY_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<float[]>>> FLOAT_GENERATOR_TRIPLES =
        FLOAT_GENERATOR_PAIRS.stream().
                flatMap(pair -> FLOAT_GENERATORS.stream().map(f -> List.of(pair.get(0), pair.get(1), f))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] floatBinaryOpProvider() {
        return FLOAT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatIndexedOpProvider() {
        return FLOAT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatTernaryOpProvider() {
        return FLOAT_GENERATOR_TRIPLES.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatTernaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT_GENERATOR_TRIPLES.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryOpProvider() {
        return FLOAT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryOpShuffleProvider() {
        return INT_SHUFFLE_GENERATORS.stream().
                flatMap(fs -> FLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryOpShuffleMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INT_SHUFFLE_GENERATORS.stream().
                    flatMap(fs -> FLOAT_GENERATORS.stream().map(fa -> {
                        return new Object[] {fa, fs, fm};
                }))).
                toArray(Object[][]::new);
    }

    static final List<BiFunction<Integer,Integer,float[]>> FLOAT_SHUFFLE_GENERATORS = List.of(
            withToStringBi("shuffle[random]", (Integer l, Integer m) -> {
                float[] a = new float[l];
                for (int i = 0; i < 1; i++) {
                    a[i] = (float)RAND.nextInt(m);
                }
                return a;
            })
    );

    @DataProvider
    public Object[][] floatUnaryOpSelectFromProvider() {
        return FLOAT_SHUFFLE_GENERATORS.stream().
                flatMap(fs -> FLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryOpSelectFromMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT_SHUFFLE_GENERATORS.stream().
                    flatMap(fs -> FLOAT_GENERATORS.stream().map(fa -> {
                        return new Object[] {fa, fs, fm};
                }))).
                toArray(Object[][]::new);
    }


    @DataProvider
    public Object[][] floatUnaryOpIndexProvider() {
        return INT_INDEX_GENERATORS.stream().
                flatMap(fs -> FLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryMaskedOpIndexProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
          flatMap(fs -> INT_INDEX_GENERATORS.stream().flatMap(fm ->
            FLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm, fs};
            }))).
            toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] scatterMaskedOpIndexProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
          flatMap(fs -> INT_INDEX_GENERATORS.stream().flatMap(fm ->
            FLOAT_GENERATORS.stream().flatMap(fn ->
              FLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fn, fm, fs};
            })))).
            toArray(Object[][]::new);
    }

    static final List<IntFunction<float[]>> FLOAT_COMPARE_GENERATORS = List.of(
            withToString("float[i]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (float)i);
            }),
            withToString("float[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (float)(i + 1));
            }),
            withToString("float[i - 2]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (float)(i - 2));
            }),
            withToString("float[zigZag(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> i%3 == 0 ? (float)i : (i%3 == 1 ? (float)(i + 1) : (float)(i - 2)));
            }),
            withToString("float[cornerCaseValue(i)]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> cornerCaseValue(i));
            })
    );

    static final List<List<IntFunction<float[]>>> FLOAT_TEST_GENERATOR_ARGS =
        FLOAT_COMPARE_GENERATORS.stream().
                map(fa -> List.of(fa)).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] floatTestOpProvider() {
        return FLOAT_TEST_GENERATOR_ARGS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    static final List<List<IntFunction<float[]>>> FLOAT_COMPARE_GENERATOR_PAIRS =
        FLOAT_COMPARE_GENERATORS.stream().
                flatMap(fa -> FLOAT_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] floatCompareOpProvider() {
        return FLOAT_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatCompareOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT_COMPARE_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    interface ToFloatF {
        float apply(int i);
    }

    static float[] fill(int s , ToFloatF f) {
        return fill(new float[s], f);
    }

    static float[] fill(float[] a, ToFloatF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    static float cornerCaseValue(int i) {
        switch(i % 7) {
            case 0:
                return Float.MAX_VALUE;
            case 1:
                return Float.MIN_VALUE;
            case 2:
                return Float.NEGATIVE_INFINITY;
            case 3:
                return Float.POSITIVE_INFINITY;
            case 4:
                return Float.NaN;
            case 5:
                return (float)0.0;
            default:
                return (float)-0.0;
        }
    }
    static float get(float[] a, int i) {
        return (float) a[i];
    }

    static final IntFunction<float[]> fr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new float[length];
    };

    static final IntFunction<boolean[]> fmr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new boolean[length];
    };

    static final IntFunction<long[]> lfr = (vl) -> {
        int length = BUFFER_REPS * vl;
        return new long[length];
    };


    @Test
    static void smokeTest1() {
        FloatVector three = FloatVector.broadcast(SPECIES, (byte)-3);
        FloatVector three2 = (FloatVector) SPECIES.broadcast(-3);
        assert(three.eq(three2).allTrue());
        FloatVector three3 = three2.broadcast(1).broadcast(-3);
        assert(three.eq(three3).allTrue());
        int scale = 2;
        Class<?> ETYPE = float.class;
        if (ETYPE == double.class || ETYPE == long.class)
            scale = 1000000;
        else if (ETYPE == byte.class && SPECIES.length() >= 64)
            scale = 1;
        FloatVector higher = three.addIndex(scale);
        VectorMask<Float> m = three.compare(VectorOperators.LE, higher);
        assert(m.allTrue());
        m = higher.min((float)-1).test(VectorOperators.IS_NEGATIVE);
        assert(m.allTrue());
        m = higher.test(VectorOperators.IS_FINITE);
        assert(m.allTrue());
        float max = higher.reduceLanes(VectorOperators.MAX);
        assert(max == -3 + scale * (SPECIES.length()-1));
    }

    private static float[]
    bothToArray(FloatVector a, FloatVector b) {
        float[] r = new float[a.length() + b.length()];
        a.intoArray(r, 0);
        b.intoArray(r, a.length());
        return r;
    }

    @Test
    static void smokeTest2() {
        // Do some zipping and shuffling.
        FloatVector io = (FloatVector) SPECIES.broadcast(0).addIndex(1);
        FloatVector io2 = (FloatVector) VectorShuffle.iota(SPECIES,0,1,false).toVector();
        Assert.assertEquals(io, io2);
        FloatVector a = io.add((float)1); //[1,2]
        FloatVector b = a.neg();  //[-1,-2]
        float[] abValues = bothToArray(a,b); //[1,2,-1,-2]
        VectorShuffle<Float> zip0 = VectorShuffle.makeZip(SPECIES, 0);
        VectorShuffle<Float> zip1 = VectorShuffle.makeZip(SPECIES, 1);
        FloatVector zab0 = a.rearrange(zip0,b); //[1,-1]
        FloatVector zab1 = a.rearrange(zip1,b); //[2,-2]
        float[] zabValues = bothToArray(zab0, zab1); //[1,-1,2,-2]
        // manually zip
        float[] manual = new float[zabValues.length];
        for (int i = 0; i < manual.length; i += 2) {
            manual[i+0] = abValues[i/2];
            manual[i+1] = abValues[a.length() + i/2];
        }
        Assert.assertEquals(Arrays.toString(zabValues), Arrays.toString(manual));
        VectorShuffle<Float> unz0 = VectorShuffle.makeUnzip(SPECIES, 0);
        VectorShuffle<Float> unz1 = VectorShuffle.makeUnzip(SPECIES, 1);
        FloatVector uab0 = zab0.rearrange(unz0,zab1);
        FloatVector uab1 = zab0.rearrange(unz1,zab1);
        float[] abValues1 = bothToArray(uab0, uab1);
        Assert.assertEquals(Arrays.toString(abValues), Arrays.toString(abValues1));
    }

    static void iotaShuffle() {
        FloatVector io = (FloatVector) SPECIES.broadcast(0).addIndex(1);
        FloatVector io2 = (FloatVector) VectorShuffle.iota(SPECIES, 0 , 1, false).toVector();
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

    static float ADD(float a, float b) {
        return (float)(a + b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void ADDFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::ADD);
    }
    static float add(float a, float b) {
        return (float)(a + b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void addFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.add(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Float256VectorTests::add);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void ADDFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ADD, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::ADD);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void addFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.add(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::add);
    }
    static float SUB(float a, float b) {
        return (float)(a - b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void SUBFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::SUB);
    }
    static float sub(float a, float b) {
        return (float)(a - b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void subFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.sub(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Float256VectorTests::sub);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void SUBFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.SUB, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::SUB);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void subFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.sub(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::sub);
    }
    static float MUL(float a, float b) {
        return (float)(a * b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void MULFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::MUL);
    }
    static float mul(float a, float b) {
        return (float)(a * b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void mulFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.mul(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Float256VectorTests::mul);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void MULFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MUL, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::MUL);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void mulFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.mul(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::mul);
    }

    static float DIV(float a, float b) {
        return (float)(a / b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void DIVFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::DIV);
    }
    static float div(float a, float b) {
        return (float)(a / b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void divFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.div(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Float256VectorTests::div);
    }



    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void DIVFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.DIV, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::DIV);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void divFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.div(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::div);
    }



    static float FIRST_NONZERO(float a, float b) {
        return (float)(Double.doubleToLongBits(a)!=0?a:b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void FIRST_NONZEROFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.FIRST_NONZERO, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::FIRST_NONZERO);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void FIRST_NONZEROFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.FIRST_NONZERO, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::FIRST_NONZERO);
    }









    @Test(dataProvider = "floatBinaryOpProvider")
    static void addFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.add(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::add);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void addFloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.add(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, mask, Float256VectorTests::add);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void subFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.sub(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::sub);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void subFloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.sub(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, mask, Float256VectorTests::sub);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void mulFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.mul(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::mul);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void mulFloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.mul(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, mask, Float256VectorTests::mul);
    }


    @Test(dataProvider = "floatBinaryOpProvider")
    static void divFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.div(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::div);
    }



    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void divFloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.div(b[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, mask, Float256VectorTests::div);
    }











































    static float MIN(float a, float b) {
        return (float)(Math.min(a, b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void MINFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MIN, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::MIN);
    }
    static float min(float a, float b) {
        return (float)(Math.min(a, b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void minFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.min(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Float256VectorTests::min);
    }
    static float MAX(float a, float b) {
        return (float)(Math.max(a, b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void MAXFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.MAX, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::MAX);
    }
    static float max(float a, float b) {
        return (float)(Math.max(a, b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void maxFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.max(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Float256VectorTests::max);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void MINFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.MIN, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::MIN);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void minFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.min(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::min);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void MAXFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.MAX, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::MAX);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void maxFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.max(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, r, Float256VectorTests::max);
    }












    static float ADDReduce(float[] a, int idx) {
        float res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res += a[i];
        }

        return res;
    }

    static float ADDReduceAll(float[] a) {
        float res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduce(a, i);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void ADDReduceFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        float ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.ADD);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = 0;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra += av.reduceLanes(VectorOperators.ADD);
            }
        }

        assertReductionArraysEquals(a, r, ra,
                Float256VectorTests::ADDReduce, Float256VectorTests::ADDReduceAll);
    }
    static float ADDReduceMasked(float[] a, int idx, boolean[] mask) {
        float res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res += a[i];
        }

        return res;
    }

    static float ADDReduceAllMasked(float[] a, boolean[] mask) {
        float res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduceMasked(a, i, mask);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void ADDReduceFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        float ra = 0;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.ADD, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = 0;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra += av.reduceLanes(VectorOperators.ADD, vmask);
            }
        }

        assertReductionArraysEqualsMasked(a, r, ra, mask,
                Float256VectorTests::ADDReduceMasked, Float256VectorTests::ADDReduceAllMasked);
    }
    static float MULReduce(float[] a, int idx) {
        float res = 1;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res *= a[i];
        }

        return res;
    }

    static float MULReduceAll(float[] a) {
        float res = 1;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res *= MULReduce(a, i);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void MULReduceFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        float ra = 1;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MUL);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = 1;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra *= av.reduceLanes(VectorOperators.MUL);
            }
        }

        assertReductionArraysEquals(a, r, ra,
                Float256VectorTests::MULReduce, Float256VectorTests::MULReduceAll);
    }
    static float MULReduceMasked(float[] a, int idx, boolean[] mask) {
        float res = 1;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if (mask[i % SPECIES.length()])
                res *= a[i];
        }

        return res;
    }

    static float MULReduceAllMasked(float[] a, boolean[] mask) {
        float res = 1;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res *= MULReduceMasked(a, i, mask);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void MULReduceFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        float ra = 1;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MUL, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = 1;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra *= av.reduceLanes(VectorOperators.MUL, vmask);
            }
        }

        assertReductionArraysEqualsMasked(a, r, ra, mask,
                Float256VectorTests::MULReduceMasked, Float256VectorTests::MULReduceAllMasked);
    }
    static float MINReduce(float[] a, int idx) {
        float res = Float.POSITIVE_INFINITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = (float)Math.min(res, a[i]);
        }

        return res;
    }

    static float MINReduceAll(float[] a) {
        float res = Float.POSITIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            res = (float)Math.min(res, a[i]);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void MINReduceFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        float ra = Float.POSITIVE_INFINITY;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MIN);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = Float.POSITIVE_INFINITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra = (float)Math.min(ra, av.reduceLanes(VectorOperators.MIN));
            }
        }

        assertReductionArraysEquals(a, r, ra,
                Float256VectorTests::MINReduce, Float256VectorTests::MINReduceAll);
    }
    static float MINReduceMasked(float[] a, int idx, boolean[] mask) {
        float res = Float.POSITIVE_INFINITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if(mask[i % SPECIES.length()])
                res = (float)Math.min(res, a[i]);
        }

        return res;
    }

    static float MINReduceAllMasked(float[] a, boolean[] mask) {
        float res = Float.POSITIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            if(mask[i % SPECIES.length()])
                res = (float)Math.min(res, a[i]);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void MINReduceFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        float ra = Float.POSITIVE_INFINITY;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MIN, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = Float.POSITIVE_INFINITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra = (float)Math.min(ra, av.reduceLanes(VectorOperators.MIN, vmask));
            }
        }

        assertReductionArraysEqualsMasked(a, r, ra, mask,
                Float256VectorTests::MINReduceMasked, Float256VectorTests::MINReduceAllMasked);
    }
    static float MAXReduce(float[] a, int idx) {
        float res = Float.NEGATIVE_INFINITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res = (float)Math.max(res, a[i]);
        }

        return res;
    }

    static float MAXReduceAll(float[] a) {
        float res = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            res = (float)Math.max(res, a[i]);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void MAXReduceFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        float ra = Float.NEGATIVE_INFINITY;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MAX);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra = (float)Math.max(ra, av.reduceLanes(VectorOperators.MAX));
            }
        }

        assertReductionArraysEquals(a, r, ra,
                Float256VectorTests::MAXReduce, Float256VectorTests::MAXReduceAll);
    }
    static float MAXReduceMasked(float[] a, int idx, boolean[] mask) {
        float res = Float.NEGATIVE_INFINITY;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if(mask[i % SPECIES.length()])
                res = (float)Math.max(res, a[i]);
        }

        return res;
    }

    static float MAXReduceAllMasked(float[] a, boolean[] mask) {
        float res = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            if(mask[i % SPECIES.length()])
                res = (float)Math.max(res, a[i]);
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void MAXReduceFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        float ra = Float.NEGATIVE_INFINITY;

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                r[i] = av.reduceLanes(VectorOperators.MAX, vmask);
            }
        }

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ra = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                ra = (float)Math.max(ra, av.reduceLanes(VectorOperators.MAX, vmask));
            }
        }

        assertReductionArraysEqualsMasked(a, r, ra, mask,
                Float256VectorTests::MAXReduceMasked, Float256VectorTests::MAXReduceAllMasked);
    }





    @Test(dataProvider = "floatUnaryOpProvider")
    static void withFloat256VectorTests(IntFunction<float []> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.withLane(0, (float)4).intoArray(r, i);
            }
        }

        assertInsertArraysEquals(a, r, (float)4, 0);
    }
    static boolean testIS_DEFAULT(float a) {
        return bits(a)==0;
    }

    @Test(dataProvider = "floatTestOpProvider")
    static void IS_DEFAULTFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                VectorMask<Float> mv = av.test(VectorOperators.IS_DEFAULT);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), testIS_DEFAULT(a[i + j]));
                }
            }
        }
    }

    static boolean testIS_NEGATIVE(float a) {
        return bits(a)<0;
    }

    @Test(dataProvider = "floatTestOpProvider")
    static void IS_NEGATIVEFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                VectorMask<Float> mv = av.test(VectorOperators.IS_NEGATIVE);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), testIS_NEGATIVE(a[i + j]));
                }
            }
        }
    }


    static boolean testIS_FINITE(float a) {
        return Float.isFinite(a);
    }

    @Test(dataProvider = "floatTestOpProvider")
    static void IS_FINITEFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                VectorMask<Float> mv = av.test(VectorOperators.IS_FINITE);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), testIS_FINITE(a[i + j]));
                }
            }
        }
    }



    static boolean testIS_NAN(float a) {
        return Float.isNaN(a);
    }

    @Test(dataProvider = "floatTestOpProvider")
    static void IS_NANFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                VectorMask<Float> mv = av.test(VectorOperators.IS_NAN);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), testIS_NAN(a[i + j]));
                }
            }
        }
    }



    static boolean testIS_INFINITE(float a) {
        return Float.isInfinite(a);
    }

    @Test(dataProvider = "floatTestOpProvider")
    static void IS_INFINITEFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                VectorMask<Float> mv = av.test(VectorOperators.IS_INFINITE);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), testIS_INFINITE(a[i + j]));
                }
            }
        }
    }



    @Test(dataProvider = "floatCompareOpProvider")
    static void LTFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.LT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] < b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void ltFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.lt(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] < b[i + j]);
                }
            }
        }
    }

    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void LTFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.LT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] < b[i + j]));
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void GTFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.GT, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] > b[i + j]);
                }
            }
        }
    }

    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void GTFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.GT, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] > b[i + j]));
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void EQFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.EQ, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] == b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void eqFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.eq(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] == b[i + j]);
                }
            }
        }
    }

    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void EQFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.EQ, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] == b[i + j]));
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void NEFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.NE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] != b[i + j]);
                }
            }
        }
    }

    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void NEFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.NE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] != b[i + j]));
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void LEFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.LE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] <= b[i + j]);
                }
            }
        }
    }

    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void LEFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.LE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] <= b[i + j]));
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void GEFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.GE, bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), a[i + j] >= b[i + j]);
                }
            }
        }
    }

    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void GEFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                VectorMask<Float> mv = av.compare(VectorOperators.GE, bv, vmask);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] >= b[i + j]));
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void LTFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.LT, b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), a[i + j] < b[i]);
            }
        }
    }


    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void LTFloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<float[]> fa,
                                IntFunction<float[]> fb, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.LT, b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] < b[i]));
            }
        }
    }

    @Test(dataProvider = "floatCompareOpProvider")
    static void LTFloat256VectorTestsBroadcastLongSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.LT, (long)b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), a[i + j] < (float)((long)b[i]));
            }
        }
    }


    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void LTFloat256VectorTestsBroadcastLongMaskedSmokeTest(IntFunction<float[]> fa,
                                IntFunction<float[]> fb, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.LT, (long)b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] < (float)((long)b[i])));
            }
        }
    }

    @Test(dataProvider = "floatCompareOpProvider")
    static void EQFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.EQ, b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), a[i + j] == b[i]);
            }
        }
    }


    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void EQFloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<float[]> fa,
                                IntFunction<float[]> fb, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.EQ, b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] == b[i]));
            }
        }
    }

    @Test(dataProvider = "floatCompareOpProvider")
    static void EQFloat256VectorTestsBroadcastLongSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.EQ, (long)b[i]);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), a[i + j] == (float)((long)b[i]));
            }
        }
    }


    @Test(dataProvider = "floatCompareOpMaskProvider")
    static void EQFloat256VectorTestsBroadcastLongMaskedSmokeTest(IntFunction<float[]> fa,
                                IntFunction<float[]> fb, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());

        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            VectorMask<Float> mv = av.compare(VectorOperators.EQ, (long)b[i], vmask);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.laneIsSet(j), mask[j] && (a[i + j] == (float)((long)b[i])));
            }
        }
    }

    static float blend(float a, float b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void blendFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.blend(bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::blend);
    }

    @Test(dataProvider = "floatUnaryOpShuffleProvider")
    static void RearrangeFloat256VectorTests(IntFunction<float[]> fa,
                                           BiFunction<Integer,Integer,int[]> fs) {
        float[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.rearrange(VectorShuffle.fromArray(SPECIES, order, i)).intoArray(r, i);
            }
        }

        assertRearrangeArraysEquals(a, r, order, SPECIES.length());
    }

    @Test(dataProvider = "floatUnaryOpShuffleMaskProvider")
    static void RearrangeFloat256VectorTestsMaskedSmokeTest(IntFunction<float[]> fa,
                                                          BiFunction<Integer,Integer,int[]> fs,
                                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        int[] order = fs.apply(a.length, SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.rearrange(VectorShuffle.fromArray(SPECIES, order, i), vmask).intoArray(r, i);
        }

        assertRearrangeArraysEquals(a, r, order, mask, SPECIES.length());
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void getFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
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

        assertArraysEquals(a, r, Float256VectorTests::get);
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void BroadcastFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector.broadcast(SPECIES, a[i]).intoArray(r, i);
            }
        }

        assertBroadcastArraysEquals(a, r);
    }





    @Test(dataProvider = "floatUnaryOpProvider")
    static void ZeroFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector.zero(SPECIES).intoArray(a, i);
            }
        }

        Assert.assertEquals(a, r);
    }




    static float[] sliceUnary(float[] a, int origin, int idx) {
        float[] res = new float[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = a[idx+i+origin];
            else
                res[i] = (float)0;
        }
        return res;
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void sliceUnaryFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        int origin = (new java.util.Random()).nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.slice(origin).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, origin, Float256VectorTests::sliceUnary);
    }
    static float[] sliceBinary(float[] a, float[] b, int origin, int idx) {
        float[] res = new float[SPECIES.length()];
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

    @Test(dataProvider = "floatBinaryOpProvider")
    static void sliceBinaryFloat256VectorTestsBinary(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = new float[a.length];
        int origin = (new java.util.Random()).nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.slice(origin, bv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, origin, Float256VectorTests::sliceBinary);
    }
    static float[] slice(float[] a, float[] b, int origin, boolean[] mask, int idx) {
        float[] res = new float[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i+origin < SPECIES.length())
                res[i] = mask[i] ? a[idx+i+origin] : (float)0;
            else {
                res[i] = mask[i] ? b[idx+j] : (float)0;
                j++;
            }
        }
        return res;
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void sliceFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
    IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        float[] r = new float[a.length];
        int origin = (new java.util.Random()).nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.slice(origin, bv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, origin, mask, Float256VectorTests::slice);
    }
    static float[] unsliceUnary(float[] a, int origin, int idx) {
        float[] res = new float[SPECIES.length()];
        for (int i = 0, j = 0; i < SPECIES.length(); i++){
            if(i < origin)
                res[i] = (float)0;
            else {
                res[i] = a[idx+j];
                j++;
            }
        }
        return res;
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void unsliceUnaryFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        int origin = (new java.util.Random()).nextInt(SPECIES.length());
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.unslice(origin).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, origin, Float256VectorTests::unsliceUnary);
    }
    static float[] unsliceBinary(float[] a, float[] b, int origin, int part, int idx) {
        float[] res = new float[SPECIES.length()];
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

    @Test(dataProvider = "floatBinaryOpProvider")
    static void unsliceBinaryFloat256VectorTestsBinary(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = new float[a.length];
        int origin = (new java.util.Random()).nextInt(SPECIES.length());
        int part = (new java.util.Random()).nextInt(2);
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.unslice(origin, bv, part).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, origin, part, Float256VectorTests::unsliceBinary);
    }
    static float[] unslice(float[] a, float[] b, int origin, int part, boolean[] mask, int idx) {
        float[] res = new float[SPECIES.length()];
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
        float[] res1 = new float[SPECIES.length()];
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

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void unsliceFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
    IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        float[] r = new float[a.length];
        int origin = (new java.util.Random()).nextInt(SPECIES.length());
        int part = (new java.util.Random()).nextInt(2);
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.unslice(origin, bv, part, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, origin, part, mask, Float256VectorTests::unslice);
    }

    static float SIN(float a) {
        return (float)(Math.sin((double)a));
    }

    static float strictSIN(float a) {
        return (float)(StrictMath.sin((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void SINFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SIN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::SIN, Float256VectorTests::strictSIN);
    }


    static float EXP(float a) {
        return (float)(Math.exp((double)a));
    }

    static float strictEXP(float a) {
        return (float)(StrictMath.exp((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void EXPFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.EXP).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::EXP, Float256VectorTests::strictEXP);
    }


    static float LOG1P(float a) {
        return (float)(Math.log1p((double)a));
    }

    static float strictLOG1P(float a) {
        return (float)(StrictMath.log1p((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void LOG1PFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG1P).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::LOG1P, Float256VectorTests::strictLOG1P);
    }


    static float LOG(float a) {
        return (float)(Math.log((double)a));
    }

    static float strictLOG(float a) {
        return (float)(StrictMath.log((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void LOGFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::LOG, Float256VectorTests::strictLOG);
    }


    static float LOG10(float a) {
        return (float)(Math.log10((double)a));
    }

    static float strictLOG10(float a) {
        return (float)(StrictMath.log10((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void LOG10Float256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.LOG10).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::LOG10, Float256VectorTests::strictLOG10);
    }


    static float EXPM1(float a) {
        return (float)(Math.expm1((double)a));
    }

    static float strictEXPM1(float a) {
        return (float)(StrictMath.expm1((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void EXPM1Float256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.EXPM1).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::EXPM1, Float256VectorTests::strictEXPM1);
    }


    static float COS(float a) {
        return (float)(Math.cos((double)a));
    }

    static float strictCOS(float a) {
        return (float)(StrictMath.cos((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void COSFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.COS).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::COS, Float256VectorTests::strictCOS);
    }


    static float TAN(float a) {
        return (float)(Math.tan((double)a));
    }

    static float strictTAN(float a) {
        return (float)(StrictMath.tan((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void TANFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TAN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::TAN, Float256VectorTests::strictTAN);
    }


    static float SINH(float a) {
        return (float)(Math.sinh((double)a));
    }

    static float strictSINH(float a) {
        return (float)(StrictMath.sinh((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void SINHFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SINH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::SINH, Float256VectorTests::strictSINH);
    }


    static float COSH(float a) {
        return (float)(Math.cosh((double)a));
    }

    static float strictCOSH(float a) {
        return (float)(StrictMath.cosh((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void COSHFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.COSH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::COSH, Float256VectorTests::strictCOSH);
    }


    static float TANH(float a) {
        return (float)(Math.tanh((double)a));
    }

    static float strictTANH(float a) {
        return (float)(StrictMath.tanh((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void TANHFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.TANH).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::TANH, Float256VectorTests::strictTANH);
    }


    static float ASIN(float a) {
        return (float)(Math.asin((double)a));
    }

    static float strictASIN(float a) {
        return (float)(StrictMath.asin((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void ASINFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ASIN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::ASIN, Float256VectorTests::strictASIN);
    }


    static float ACOS(float a) {
        return (float)(Math.acos((double)a));
    }

    static float strictACOS(float a) {
        return (float)(StrictMath.acos((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void ACOSFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ACOS).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::ACOS, Float256VectorTests::strictACOS);
    }


    static float ATAN(float a) {
        return (float)(Math.atan((double)a));
    }

    static float strictATAN(float a) {
        return (float)(StrictMath.atan((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void ATANFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ATAN).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::ATAN, Float256VectorTests::strictATAN);
    }


    static float CBRT(float a) {
        return (float)(Math.cbrt((double)a));
    }

    static float strictCBRT(float a) {
        return (float)(StrictMath.cbrt((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void CBRTFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.CBRT).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::CBRT, Float256VectorTests::strictCBRT);
    }


    static float HYPOT(float a, float b) {
        return (float)(Math.hypot((double)a, (double)b));
    }

    static float strictHYPOT(float a, float b) {
        return (float)(StrictMath.hypot((double)a, (double)b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void HYPOTFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.HYPOT, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::HYPOT, Float256VectorTests::strictHYPOT);
    }



    static float POW(float a, float b) {
        return (float)(Math.pow((double)a, (double)b));
    }

    static float strictPOW(float a, float b) {
        return (float)(StrictMath.pow((double)a, (double)b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void POWFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.POW, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::POW, Float256VectorTests::strictPOW);
    }

    static float pow(float a, float b) {
        return (float)(Math.pow((double)a, (double)b));
    }

    static float strictpow(float a, float b) {
        return (float)(StrictMath.pow((double)a, (double)b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void powFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.pow(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::pow, Float256VectorTests::strictpow);
    }



    static float ATAN2(float a, float b) {
        return (float)(Math.atan2((double)a, (double)b));
    }

    static float strictATAN2(float a, float b) {
        return (float)(StrictMath.atan2((double)a, (double)b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void ATAN2Float256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                av.lanewise(VectorOperators.ATAN2, bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::ATAN2, Float256VectorTests::strictATAN2);
    }



    @Test(dataProvider = "floatBinaryOpProvider")
    static void POWFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.POW, b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::POW, Float256VectorTests::strictPOW);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void powFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.pow(b[i]).intoArray(r, i);
        }

        assertBroadcastArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::pow, Float256VectorTests::strictpow);
    }



    static float FMA(float a, float b, float c) {
        return (float)(Math.fma(a, b, c));
    }
    static float fma(float a, float b, float c) {
        return (float)(Math.fma(a, b, c));
    }


    @Test(dataProvider = "floatTernaryOpProvider")
    static void FMAFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb, IntFunction<float[]> fc) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                FloatVector cv = FloatVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.FMA, bv, cv).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, c, r, Float256VectorTests::FMA);
    }
    @Test(dataProvider = "floatTernaryOpProvider")
    static void fmaFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb, IntFunction<float[]> fc) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            FloatVector cv = FloatVector.fromArray(SPECIES, c, i);
            av.fma(bv, cv).intoArray(r, i);
        }

        assertArraysEquals(a, b, c, r, Float256VectorTests::fma);
    }


    @Test(dataProvider = "floatTernaryOpMaskProvider")
    static void FMAFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<float[]> fc, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
                FloatVector cv = FloatVector.fromArray(SPECIES, c, i);
                av.lanewise(VectorOperators.FMA, bv, cv, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, c, r, mask, Float256VectorTests::FMA);
    }





    @Test(dataProvider = "floatTernaryOpProvider")
    static void FMAFloat256VectorTestsBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb, IntFunction<float[]> fc) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.lanewise(VectorOperators.FMA, bv, c[i]).intoArray(r, i);
        }
        assertBroadcastArraysEquals(a, b, c, r, Float256VectorTests::FMA);
    }

    @Test(dataProvider = "floatTernaryOpProvider")
    static void FMAFloat256VectorTestsAltBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb, IntFunction<float[]> fc) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector cv = FloatVector.fromArray(SPECIES, c, i);
            av.lanewise(VectorOperators.FMA, b[i], cv).intoArray(r, i);
        }
        assertAltBroadcastArraysEquals(a, b, c, r, Float256VectorTests::FMA);
    }


    @Test(dataProvider = "floatTernaryOpMaskProvider")
    static void FMAFloat256VectorTestsBroadcastMaskedSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<float[]> fc, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, b, i);
            av.lanewise(VectorOperators.FMA, bv, c[i], vmask).intoArray(r, i);
        }

        assertBroadcastArraysEquals(a, b, c, r, mask, Float256VectorTests::FMA);
    }

    @Test(dataProvider = "floatTernaryOpMaskProvider")
    static void FMAFloat256VectorTestsAltBroadcastMaskedSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<float[]> fc, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector cv = FloatVector.fromArray(SPECIES, c, i);
            av.lanewise(VectorOperators.FMA, b[i], cv, vmask).intoArray(r, i);
        }

        assertAltBroadcastArraysEquals(a, b, c, r, mask, Float256VectorTests::FMA);
    }




    @Test(dataProvider = "floatTernaryOpProvider")
    static void FMAFloat256VectorTestsDoubleBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb, IntFunction<float[]> fc) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.FMA, b[i], c[i]).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(a, b, c, r, Float256VectorTests::FMA);
    }
    @Test(dataProvider = "floatTernaryOpProvider")
    static void fmaFloat256VectorTestsDoubleBroadcastSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb, IntFunction<float[]> fc) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.fma(b[i], c[i]).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(a, b, c, r, Float256VectorTests::fma);
    }


    @Test(dataProvider = "floatTernaryOpMaskProvider")
    static void FMAFloat256VectorTestsDoubleBroadcastMaskedSmokeTest(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<float[]> fc, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] c = fc.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            av.lanewise(VectorOperators.FMA, b[i], c[i], vmask).intoArray(r, i);
        }

        assertDoubleBroadcastArraysEquals(a, b, c, r, mask, Float256VectorTests::FMA);
    }




    static float NEG(float a) {
        return (float)(-((float)a));
    }

    static float neg(float a) {
        return (float)(-((float)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void NEGFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::NEG);
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void negFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.neg().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::neg);
    }

    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void NEGMaskedFloat256VectorTests(IntFunction<float[]> fa,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.NEG, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Float256VectorTests::NEG);
    }

    static float ABS(float a) {
        return (float)(Math.abs((float)a));
    }

    static float abs(float a) {
        return (float)(Math.abs((float)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void ABSFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::ABS);
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void absFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.abs().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::abs);
    }

    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void ABSMaskedFloat256VectorTests(IntFunction<float[]> fa,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.ABS, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Float256VectorTests::ABS);
    }








    static float SQRT(float a) {
        return (float)(Math.sqrt((double)a));
    }

    static float sqrt(float a) {
        return (float)(Math.sqrt((double)a));
    }



    @Test(dataProvider = "floatUnaryOpProvider")
    static void SQRTFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SQRT).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::SQRT);
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void sqrtFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.sqrt().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::sqrt);
    }



    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void SQRTMaskedFloat256VectorTests(IntFunction<float[]> fa,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.lanewise(VectorOperators.SQRT, vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Float256VectorTests::SQRT);
    }

    static float[] gather(float a[], int ix, int[] b, int iy) {
        float[] res = new float[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            int bi = iy + i;
            res[i] = a[b[bi] + ix];
        }
        return res;
    }

    @Test(dataProvider = "floatUnaryOpIndexProvider")
    static void gatherFloat256VectorTests(IntFunction<float[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        float[] a = fa.apply(SPECIES.length());
        int[] b    = fs.apply(a.length, SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i, b, i);
                av.intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::gather);
    }
    static float[] gatherMasked(float a[], int ix, boolean[] mask, int[] b, int iy) {
        float[] res = new float[SPECIES.length()];
        for (int i = 0; i < SPECIES.length(); i++) {
            int bi = iy + i;
            if (mask[i]) {
              res[i] = a[b[bi] + ix];
            }
        }
        return res;
    }

    @Test(dataProvider = "floatUnaryMaskedOpIndexProvider")
    static void gatherMaskedFloat256VectorTests(IntFunction<float[]> fa, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        int[] b    = fs.apply(a.length, SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i, b, i, vmask);
                av.intoArray(r, i);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::gatherMasked);
    }

    static float[] scatter(float a[], int ix, int[] b, int iy) {
      float[] res = new float[SPECIES.length()];
      for (int i = 0; i < SPECIES.length(); i++) {
        int bi = iy + i;
        res[b[bi]] = a[i + ix];
      }
      return res;
    }

    @Test(dataProvider = "floatUnaryOpIndexProvider")
    static void scatterFloat256VectorTests(IntFunction<float[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        float[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, b, i);
            }
        }

        assertArraysEquals(a, b, r, Float256VectorTests::scatter);
    }

    static float[] scatterMasked(float r[], float a[], int ix, boolean[] mask, int[] b, int iy) {
      // First, gather r.
      float[] oldVal = gather(r, ix, b, iy);
      float[] newVal = new float[SPECIES.length()];

      // Second, blending it with a.
      for (int i = 0; i < SPECIES.length(); i++) {
        newVal[i] = blend(oldVal[i], a[i+ix], mask[i]);
      }

      // Third, scatter: copy old value of r, and scatter it manually.
      float[] res = Arrays.copyOfRange(r, ix, ix+SPECIES.length());
      for (int i = 0; i < SPECIES.length(); i++) {
        int bi = iy + i;
        res[b[bi]] = newVal[i];
      }

      return res;
    }

    @Test(dataProvider = "scatterMaskedOpIndexProvider")
    static void scatterMaskedFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        float[] r = fb.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, b, i, vmask);
            }
        }

        assertArraysEquals(a, b, r, mask, Float256VectorTests::scatterMasked);
    }


    static long ADDReduceLong(float[] a, int idx) {
        float res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            res += a[i];
        }

        return (long)res;
    }

    static long ADDReduceAllLong(float[] a) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduceLong(a, i);
        }

        return res;
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void ADDReduceLongFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD);
        }

        ra = 0;
        for (int i = 0; i < a.length; i ++) {
            ra += r[i];
        }

        assertReductionLongArraysEquals(a, r, ra,
                Float256VectorTests::ADDReduceLong, Float256VectorTests::ADDReduceAllLong);
    }

    static long ADDReduceLongMasked(float[] a, int idx, boolean[] mask) {
        float res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
            if(mask[i % SPECIES.length()])
                res += a[i];
        }

        return (long)res;
    }

    static long ADDReduceAllLongMasked(float[] a, boolean[] mask) {
        long res = 0;
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            res += ADDReduceLongMasked(a, i, mask);
        }

        return res;
    }

    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void ADDReduceLongFloat256VectorTestsMasked(IntFunction<float[]> fa, IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        long[] r = lfr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);
        long ra = 0;

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            r[i] = av.reduceLanesToLong(VectorOperators.ADD, vmask);
        }

        ra = 0;
        for (int i = 0; i < a.length; i ++) {
            ra += r[i];
        }

        assertReductionLongArraysEqualsMasked(a, r, ra, mask,
                Float256VectorTests::ADDReduceLongMasked, Float256VectorTests::ADDReduceAllLongMasked);
    }

    @Test(dataProvider = "floatUnaryOpSelectFromProvider")
    static void SelectFromFloat256VectorTests(IntFunction<float[]> fa,
                                           BiFunction<Integer,Integer,float[]> fs) {
        float[] a = fa.apply(SPECIES.length());
        float[] order = fs.apply(a.length, SPECIES.length());
        float[] r = fr.apply(SPECIES.length());

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, order, i);
            bv.selectFrom(av).intoArray(r, i);
        }

        assertSelectFromArraysEquals(a, r, order, SPECIES.length());
    }

    @Test(dataProvider = "floatUnaryOpSelectFromMaskProvider")
    static void SelectFromFloat256VectorTestsMaskedSmokeTest(IntFunction<float[]> fa,
                                                           BiFunction<Integer,Integer,float[]> fs,
                                                           IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] order = fs.apply(a.length, SPECIES.length());
        float[] r = fr.apply(SPECIES.length());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Float> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int i = 0; i < a.length; i += SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(SPECIES, a, i);
            FloatVector bv = FloatVector.fromArray(SPECIES, order, i);
            bv.selectFrom(av, vmask).intoArray(r, i);
        }

        assertSelectFromArraysEquals(a, r, order, mask, SPECIES.length());
    }

    @Test
    static void ElementSizeFloat256VectorTests() {
        FloatVector av = FloatVector.zero(SPECIES);
        int elsize = av.elementSize();
        Assert.assertEquals(elsize, Float.SIZE);
    }

    @Test
    static void VectorShapeFloat256VectorTests() {
        FloatVector av = FloatVector.zero(SPECIES);
        VectorShape vsh = av.shape();
        assert(vsh.equals(VectorShape.S_256_BIT));
    }
}

