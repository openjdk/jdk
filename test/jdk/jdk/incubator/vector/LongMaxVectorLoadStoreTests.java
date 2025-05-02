/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.incubator.vector java.base/jdk.internal.vm.annotation
 * @run testng/othervm --add-opens jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED
 *      -XX:-TieredCompilation LongMaxVectorLoadStoreTests
 *
 */

// -- This file was mechanically generated: Do not edit! -- //

import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;
import jdk.internal.vm.annotation.DontInline;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteOrder;
import java.util.List;
import java.util.function.*;

@Test
public class LongMaxVectorLoadStoreTests extends AbstractVectorLoadStoreTest {
    static final VectorSpecies<Long> SPECIES =
                LongVector.SPECIES_MAX;

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);

    static final ValueLayout.OfLong ELEMENT_LAYOUT = ValueLayout.JAVA_LONG.withByteAlignment(1);

    static VectorShape getMaxBit() {
        return VectorShape.S_Max_BIT;
    }

    private static final int Max = 256;  // juts so we can do N/Max

    static final int BUFFER_REPS = Integer.getInteger("jdk.incubator.vector.test.buffer-vectors", 25000 / Max);

    static void assertArraysEquals(long[] r, long[] a, boolean[] mask) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? a[i] : (long) 0);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? a[i] : (long) 0, "at index #" + i);
        }
    }

    static final List<IntFunction<long[]>> LONG_GENERATORS = List.of(
            withToString("long[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (long)(i * 5));
            }),
            withToString("long[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (((long)(i + 1) == 0) ? 1 : (long)(i + 1)));
            })
    );

    // Relative to array.length
    static final List<IntFunction<Integer>> INDEX_GENERATORS = List.of(
            withToString("-1", (int l) -> {
                return -1;
            }),
            withToString("l", (int l) -> {
                return l;
            }),
            withToString("l - 1", (int l) -> {
                return l - 1;
            }),
            withToString("l + 1", (int l) -> {
                return l + 1;
            }),
            withToString("l - speciesl + 1", (int l) -> {
                return l - SPECIES.length() + 1;
            }),
            withToString("l + speciesl - 1", (int l) -> {
                return l + SPECIES.length() - 1;
            }),
            withToString("l + speciesl", (int l) -> {
                return l + SPECIES.length();
            }),
            withToString("l + speciesl + 1", (int l) -> {
                return l + SPECIES.length() + 1;
            })
    );

    // Relative to byte[] array.length or MemorySegment.byteSize()
    static final List<IntFunction<Integer>> BYTE_INDEX_GENERATORS = List.of(
            withToString("-1", (int l) -> {
                return -1;
            }),
            withToString("l", (int l) -> {
                return l;
            }),
            withToString("l - 1", (int l) -> {
                return l - 1;
            }),
            withToString("l + 1", (int l) -> {
                return l + 1;
            }),
            withToString("l - speciesl*ebsize + 1", (int l) -> {
                return l - SPECIES.vectorByteSize() + 1;
            }),
            withToString("l + speciesl*ebsize - 1", (int l) -> {
                return l + SPECIES.vectorByteSize() - 1;
            }),
            withToString("l + speciesl*ebsize", (int l) -> {
                return l + SPECIES.vectorByteSize();
            }),
            withToString("l + speciesl*ebsize + 1", (int l) -> {
                return l + SPECIES.vectorByteSize() + 1;
            })
    );

    @DataProvider
    public Object[][] longProvider() {
        return LONG_GENERATORS.stream().
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
    public Object[][] longProviderForIOOBE() {
        var f = LONG_GENERATORS.get(0);
        return INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi};
                }).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> LONG_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longMaskProviderForIOOBE() {
        var f = LONG_GENERATORS.get(0);
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longMemorySegmentProvider() {
        return LONG_GENERATORS.stream().
                flatMap(fa -> MEMORY_SEGMENT_GENERATORS.stream().
                        flatMap(fb -> BYTE_ORDER_VALUES.stream().map(bo -> {
                            return new Object[]{fa, fb, bo};
                        }))).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longMemorySegmentMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> LONG_GENERATORS.stream().
                        flatMap(fa -> MEMORY_SEGMENT_GENERATORS.stream().
                                flatMap(fb -> BYTE_ORDER_VALUES.stream().map(bo -> {
                            return new Object[]{fa, fb, fm, bo};
                        })))).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longByteProviderForIOOBE() {
        var f = LONG_GENERATORS.get(0);
        return BYTE_INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi};
                }).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longByteMaskProviderForIOOBE() {
        var f = LONG_GENERATORS.get(0);
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> BYTE_INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi, fm};
                })).
                toArray(Object[][]::new);
    }

    static MemorySegment toSegment(long[] a, IntFunction<MemorySegment> fb) {
        MemorySegment ms = fb.apply(a.length * SPECIES.elementSize() / 8);
        for (int i = 0; i < a.length; i++) {
            ms.set(ELEMENT_LAYOUT, i * SPECIES.elementSize() / 8 , a[i]);
        }
        return ms;
    }

    static long[] segmentToArray(MemorySegment ms) {
        return ms.toArray(ELEMENT_LAYOUT);
    }


    interface ToLongF {
        long apply(int i);
    }

    static long[] fill(int s , ToLongF f) {
        return fill(new long[s], f);
    }

    static long[] fill(long[] a, ToLongF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    @DontInline
    static LongVector fromArray(long[] a, int i) {
        // Tests the species method and the equivalent vector method it defers to
        return (LongVector) SPECIES.fromArray(a, i);
    }

    @DontInline
    static LongVector fromArray(long[] a, int i, VectorMask<Long> m) {
        return LongVector.fromArray(SPECIES, a, i, m);
    }

    @DontInline
    static void intoArray(LongVector v, long[] a, int i) {
        v.intoArray(a, i);
    }

    @DontInline
    static void intoArray(LongVector v, long[] a, int i, VectorMask<Long> m) {
        v.intoArray(a, i, m);
    }

    @DontInline
    static LongVector fromMemorySegment(MemorySegment a, int i, ByteOrder bo) {
        // Tests the species method and the equivalent vector method it defers to
        return (LongVector) SPECIES.fromMemorySegment(a, i, bo);
    }

    @DontInline
    static LongVector fromMemorySegment(MemorySegment a, int i, ByteOrder bo, VectorMask<Long> m) {
        return LongVector.fromMemorySegment(SPECIES, a, i, bo, m);
    }

    @DontInline
    static void intoMemorySegment(LongVector v, MemorySegment a, int i, ByteOrder bo) {
        v.intoMemorySegment(a, i, bo);
    }

    @DontInline
    static void intoMemorySegment(LongVector v, MemorySegment a, int i, ByteOrder bo, VectorMask<Long> m) {
        v.intoMemorySegment(a, i, bo, m);
    }

    @Test(dataProvider = "longProvider")
    static void loadStoreArray(IntFunction<long[]> fa) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i);
            }
        }
        Assert.assertEquals(r, a);
    }

    @Test(dataProvider = "longProviderForIOOBE")
    static void loadArrayIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = fromArray(a, i);
                av.intoArray(r, i);
            }
        }

        int index = fi.apply(a.length);
        boolean shouldFail = isIndexOutOfBounds(SPECIES.length(), index, a.length);
        try {
            fromArray(a, index);
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }

    @Test(dataProvider = "longProviderForIOOBE")
    static void storeArrayIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i);
                intoArray(av, r, i);
            }
        }

        int index = fi.apply(a.length);
        boolean shouldFail = isIndexOutOfBounds(SPECIES.length(), index, a.length);
        try {
            LongVector av = LongVector.fromArray(SPECIES, a, 0);
            intoArray(av, r, index);
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }


    @Test(dataProvider = "longMaskProvider")
    static void loadStoreMaskArray(IntFunction<long[]> fa,
                                   IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromValues(SPECIES, mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i, vmask);
                av.intoArray(r, i);
            }
        }
        assertArraysEquals(r, a, mask);


        r = new long[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, vmask);
            }
        }
        assertArraysEquals(r, a, mask);
    }

    @Test(dataProvider = "longMaskProviderForIOOBE")
    static void loadArrayMaskIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromValues(SPECIES, mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = fromArray(a, i, vmask);
                av.intoArray(r, i);
            }
        }

        int index = fi.apply(a.length);
        boolean shouldFail = isIndexOutOfBoundsForMask(mask, index, a.length);
        try {
            fromArray(a, index, vmask);
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }

    @Test(dataProvider = "longMaskProviderForIOOBE")
    static void storeArrayMaskIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromValues(SPECIES, mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i);
                intoArray(av, r, i, vmask);
            }
        }

        int index = fi.apply(a.length);
        boolean shouldFail = isIndexOutOfBoundsForMask(mask, index, a.length);
        try {
            LongVector av = LongVector.fromArray(SPECIES, a, 0);
            intoArray(av, a, index, vmask);
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }


    @Test(dataProvider = "longMaskProvider")
    static void loadStoreMask(IntFunction<long[]> fa,
                              IntFunction<boolean[]> fm) {
        boolean[] mask = fm.apply(SPECIES.length());
        boolean[] r = new boolean[mask.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < mask.length; i += SPECIES.length()) {
                VectorMask<Long> vmask = VectorMask.fromArray(SPECIES, mask, i);
                vmask.intoArray(r, i);
            }
        }
        Assert.assertEquals(r, mask);
    }


    @Test(dataProvider = "longMemorySegmentProvider")
    static void loadStoreMemorySegment(IntFunction<long[]> fa,
                                       IntFunction<MemorySegment> fb,
                                       ByteOrder bo) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), fb);
        MemorySegment r = fb.apply((int) a.byteSize());

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                LongVector av = LongVector.fromMemorySegment(SPECIES, a, i, bo);
                av.intoMemorySegment(r, i, bo);
            }
        }
        long m = r.mismatch(a);
        Assert.assertEquals(m, -1, "Segments not equal");
    }

    @Test(dataProvider = "longByteProviderForIOOBE")
    static void loadMemorySegmentIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> Arena.ofAuto().allocate(i, Long.SIZE));
        MemorySegment r = Arena.ofAuto().allocate(a.byteSize(), Long.SIZE);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                LongVector av = fromMemorySegment(a, i, ByteOrder.nativeOrder());
                av.intoMemorySegment(r, i, ByteOrder.nativeOrder());
            }
        }

        int index = fi.apply((int) a.byteSize());
        boolean shouldFail = isIndexOutOfBounds(SPECIES.vectorByteSize(), index, (int) a.byteSize());
        try {
            fromMemorySegment(a, index, ByteOrder.nativeOrder());
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }

    @Test(dataProvider = "longByteProviderForIOOBE")
    static void storeMemorySegmentIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> Arena.ofAuto().allocate(i, Long.SIZE));
        MemorySegment r = Arena.ofAuto().allocate(a.byteSize(), Long.SIZE);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                LongVector av = LongVector.fromMemorySegment(SPECIES, a, i, ByteOrder.nativeOrder());
                intoMemorySegment(av, r, i, ByteOrder.nativeOrder());
            }
        }

        int index = fi.apply((int) a.byteSize());
        boolean shouldFail = isIndexOutOfBounds(SPECIES.vectorByteSize(), index, (int) a.byteSize());
        try {
            LongVector av = LongVector.fromMemorySegment(SPECIES, a, 0, ByteOrder.nativeOrder());
            intoMemorySegment(av, r, index, ByteOrder.nativeOrder());
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }

    @Test(dataProvider = "longMemorySegmentMaskProvider")
    static void loadStoreMemorySegmentMask(IntFunction<long[]> fa,
                                           IntFunction<MemorySegment> fb,
                                           IntFunction<boolean[]> fm,
                                           ByteOrder bo) {
        long[] _a = fa.apply(SPECIES.length());
        MemorySegment a = toSegment(_a, fb);
        MemorySegment r = fb.apply((int) a.byteSize());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromValues(SPECIES, mask);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                LongVector av = LongVector.fromMemorySegment(SPECIES, a, i, bo, vmask);
                av.intoMemorySegment(r, i, bo);
            }
        }
        assertArraysEquals(segmentToArray(r), _a, mask);


        r = fb.apply((int) a.byteSize());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                LongVector av = LongVector.fromMemorySegment(SPECIES, a, i, bo);
                av.intoMemorySegment(r, i, bo, vmask);
            }
        }
        assertArraysEquals(segmentToArray(r), _a, mask);
    }

    @Test(dataProvider = "longByteMaskProviderForIOOBE")
    static void loadMemorySegmentMaskIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> Arena.ofAuto().allocate(i, Long.SIZE));
        MemorySegment r = Arena.ofAuto().allocate(a.byteSize(), Long.SIZE);
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromValues(SPECIES, mask);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                LongVector av = fromMemorySegment(a, i, ByteOrder.nativeOrder(), vmask);
                av.intoMemorySegment(r, i, ByteOrder.nativeOrder());
            }
        }

        int index = fi.apply((int) a.byteSize());
        boolean shouldFail = isIndexOutOfBoundsForMask(mask, index, (int) a.byteSize(), SPECIES.elementSize() / 8);
        try {
            fromMemorySegment(a, index, ByteOrder.nativeOrder(), vmask);
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }

    @Test(dataProvider = "longByteMaskProviderForIOOBE")
    static void storeMemorySegmentMaskIOOBE(IntFunction<long[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> Arena.ofAuto().allocate(i, Long.SIZE));
        MemorySegment r = Arena.ofAuto().allocate(a.byteSize(), Long.SIZE);
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromValues(SPECIES, mask);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                LongVector av = LongVector.fromMemorySegment(SPECIES, a, i, ByteOrder.nativeOrder());
                intoMemorySegment(av, r, i, ByteOrder.nativeOrder(), vmask);
            }
        }

        int index = fi.apply((int) a.byteSize());
        boolean shouldFail = isIndexOutOfBoundsForMask(mask, index, (int) a.byteSize(), SPECIES.elementSize() / 8);
        try {
            LongVector av = LongVector.fromMemorySegment(SPECIES, a, 0, ByteOrder.nativeOrder());
            intoMemorySegment(av, a, index, ByteOrder.nativeOrder(), vmask);
            if (shouldFail) {
                Assert.fail("Failed to throw IndexOutOfBoundsException");
            }
        } catch (IndexOutOfBoundsException e) {
            if (!shouldFail) {
                Assert.fail("Unexpected IndexOutOfBoundsException");
            }
        }
    }

    @Test(dataProvider = "longMemorySegmentProvider")
    static void loadStoreReadonlyMemorySegment(IntFunction<long[]> fa,
                                               IntFunction<MemorySegment> fb,
                                               ByteOrder bo) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), fb).asReadOnly();

        Assert.assertThrows(
                UnsupportedOperationException.class,
                () -> SPECIES.zero().intoMemorySegment(a, 0, bo)
        );

        Assert.assertThrows(
                UnsupportedOperationException.class,
                () -> SPECIES.zero().intoMemorySegment(a, 0, bo, SPECIES.maskAll(true))
        );

        Assert.assertThrows(
                UnsupportedOperationException.class,
                () -> SPECIES.zero().intoMemorySegment(a, 0, bo, SPECIES.maskAll(false))
        );

        VectorMask<Long> m = SPECIES.shuffleFromOp(i -> i % 2 == 0 ? 1 : -1)
                .laneIsValid();
        Assert.assertThrows(
                UnsupportedOperationException.class,
                () -> SPECIES.zero().intoMemorySegment(a, 0, bo, m)
        );
    }


    @Test(dataProvider = "maskProvider")
    static void loadStoreMask(IntFunction<boolean[]> fm) {
        boolean[] a = fm.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                VectorMask<Long> vmask = SPECIES.loadMask(a, i);
                vmask.intoArray(r, i);
            }
        }
        Assert.assertEquals(r, a);
    }


    @Test
    static void loadStoreShuffle() {
        IntUnaryOperator fn = a -> a + 5;
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            var shuffle = VectorShuffle.fromOp(SPECIES, fn);
            int [] r = shuffle.toArray();

            int [] a = expectedShuffle(SPECIES.length(), fn);
            Assert.assertEquals(r, a);
       }
    }





    // Gather/Scatter load/store tests

    static void assertGatherArraysEquals(long[] r, long[] a, int[] indexMap) {
        int i = 0;
        int j = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                j = i;
                for (; j < i + SPECIES.length(); j++) {
                    Assert.assertEquals(r[j], a[i + indexMap[j]]);
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[j], a[i + indexMap[j]], "at index #" + j);
        }
    }

    static void assertGatherArraysEquals(long[] r, long[] a, int[] indexMap, boolean[] mask) {
        int i = 0;
        int j = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                j = i;
                for (; j < i + SPECIES.length(); j++) {
                    Assert.assertEquals(r[j], mask[j % SPECIES.length()] ? a[i + indexMap[j]]: (long) 0);
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[j % SPECIES.length()] ? a[i + indexMap[j]]: (long) 0, "at index #" + j);
        }
    }

    static void assertScatterArraysEquals(long[] r, long[] a, int[] indexMap, boolean[] mask) {
        long[] expected = new long[r.length];

        // Store before checking, since the same location may be stored to more than once
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            for (int j = i; j < i + SPECIES.length(); j++) {
                if (mask[j % SPECIES.length()]) {
                    expected[i + indexMap[j]] = a[j];
                }
            }
        }

        Assert.assertEquals(r, expected);
    }

    static void assertScatterArraysEquals(long[] r, long[] a, int[] indexMap) {
        long[] expected = new long[r.length];

        // Store before checking, since the same location may be stored to more than once
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            for (int j = i; j < i + SPECIES.length(); j++) {
                expected[i + indexMap[j]] = a[j];
            }
        }

        Assert.assertEquals(r, expected);
    }

    @DataProvider
    public Object[][] gatherScatterProvider() {
        return INT_INDEX_GENERATORS.stream().
                flatMap(fs -> LONG_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] gatherScatterMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
          flatMap(fs -> INT_INDEX_GENERATORS.stream().flatMap(fm ->
            LONG_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm, fs};
            }))).
            toArray(Object[][]::new);
    }


    @Test(dataProvider = "gatherScatterProvider")
    static void gather(IntFunction<long[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        long[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        long[] r = new long[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i, b, i);
                av.intoArray(r, i);
            }
        }

        assertGatherArraysEquals(r, a, b);
    }

    @Test(dataProvider = "gatherScatterMaskProvider")
    static void gatherMask(IntFunction<long[]> fa, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i, b, i, vmask);
                av.intoArray(r, i);
            }
        }

        assertGatherArraysEquals(r, a, b, mask);
    }

    @Test(dataProvider = "gatherScatterProvider")
    static void scatter(IntFunction<long[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        long[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        long[] r = new long[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, b, i);
            }
        }

        assertScatterArraysEquals(r, a, b);
    }

    @Test(dataProvider = "gatherScatterMaskProvider")
    static void scatterMask(IntFunction<long[]> fa, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Long> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                LongVector av = LongVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, b, i, vmask);
            }
        }

        assertScatterArraysEquals(r, a, b, mask);
    }



}
