/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.incubator.foreign jdk.incubator.vector java.base/jdk.internal.vm.annotation
 * @run testng/othervm -XX:-TieredCompilation Byte128VectorLoadStoreTests
 *
 */

// -- This file was mechanically generated: Do not edit! -- //

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
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
public class Byte128VectorLoadStoreTests extends AbstractVectorLoadStoreTest {
    static final VectorSpecies<Byte> SPECIES =
                ByteVector.SPECIES_128;

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);


    static final int BUFFER_REPS = Integer.getInteger("jdk.incubator.vector.test.buffer-vectors", 25000 / 128);

    static void assertArraysEquals(byte[] r, byte[] a, boolean[] mask) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? a[i] : (byte) 0);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? a[i] : (byte) 0, "at index #" + i);
        }
    }

    static final List<IntFunction<byte[]>> BYTE_GENERATORS = List.of(
            withToString("byte[i * 5]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (byte)(i * 5));
            }),
            withToString("byte[i + 1]", (int s) -> {
                return fill(s * BUFFER_REPS,
                            i -> (((byte)(i + 1) == 0) ? 1 : (byte)(i + 1)));
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
    public Object[][] byteProvider() {
        return BYTE_GENERATORS.stream().
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
    public Object[][] byteProviderForIOOBE() {
        var f = BYTE_GENERATORS.get(0);
        return INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi};
                }).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] byteMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> BYTE_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] byteMaskProviderForIOOBE() {
        var f = BYTE_GENERATORS.get(0);
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi, fm};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] byteMemorySegmentProvider() {
        return BYTE_GENERATORS.stream().
                flatMap(fa -> MEMORY_SEGMENT_GENERATORS.stream().
                        flatMap(fb -> BYTE_ORDER_VALUES.stream().map(bo -> {
                            return new Object[]{fa, fb, bo};
                        }))).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] byteMemorySegmentMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> BYTE_GENERATORS.stream().
                        flatMap(fa -> MEMORY_SEGMENT_GENERATORS.stream().
                                flatMap(fb -> BYTE_ORDER_VALUES.stream().map(bo -> {
                            return new Object[]{fa, fb, fm, bo};
                        })))).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] byteByteProviderForIOOBE() {
        var f = BYTE_GENERATORS.get(0);
        return BYTE_INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi};
                }).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] byteByteMaskProviderForIOOBE() {
        var f = BYTE_GENERATORS.get(0);
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> BYTE_INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi, fm};
                })).
                toArray(Object[][]::new);
    }

    static MemorySegment toSegment(byte[] a, IntFunction<MemorySegment> fb) {
        MemorySegment ms = fb.apply(a.length * SPECIES.elementSize() / 8);
        for (int i = 0; i < a.length; i++) {
            ms.set(ValueLayout.JAVA_BYTE, i * SPECIES.elementSize() / 8 , a[i]);
        }
        return ms;
    }

    static byte[] segmentToArray(MemorySegment ms) {
        return ms.toArray(ValueLayout.JAVA_BYTE);
    }


    interface ToByteF {
        byte apply(int i);
    }

    static byte[] fill(int s , ToByteF f) {
        return fill(new byte[s], f);
    }

    static byte[] fill(byte[] a, ToByteF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    @DontInline
    static ByteVector fromArray(byte[] a, int i) {
        return ByteVector.fromArray(SPECIES, a, i);
    }

    @DontInline
    static ByteVector fromArray(byte[] a, int i, VectorMask<Byte> m) {
        return ByteVector.fromArray(SPECIES, a, i, m);
    }

    @DontInline
    static void intoArray(ByteVector v, byte[] a, int i) {
        v.intoArray(a, i);
    }

    @DontInline
    static void intoArray(ByteVector v, byte[] a, int i, VectorMask<Byte> m) {
        v.intoArray(a, i, m);
    }

    @DontInline
    static ByteVector fromMemorySegment(MemorySegment a, int i, ByteOrder bo) {
        return ByteVector.fromMemorySegment(SPECIES, a, i, bo);
    }

    @DontInline
    static ByteVector fromMemorySegment(MemorySegment a, int i, ByteOrder bo, VectorMask<Byte> m) {
        return ByteVector.fromMemorySegment(SPECIES, a, i, bo, m);
    }

    @DontInline
    static void intoMemorySegment(ByteVector v, MemorySegment a, int i, ByteOrder bo) {
        v.intoMemorySegment(a, i, bo);
    }

    @DontInline
    static void intoMemorySegment(ByteVector v, MemorySegment a, int i, ByteOrder bo, VectorMask<Byte> m) {
        v.intoMemorySegment(a, i, bo, m);
    }

    @Test(dataProvider = "byteProvider")
    static void loadStoreArray(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(SPECIES.length());
        byte[] r = new byte[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i);
            }
        }
        Assert.assertEquals(r, a);
    }

    @Test(dataProvider = "byteProviderForIOOBE")
    static void loadArrayIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi) {
        byte[] a = fa.apply(SPECIES.length());
        byte[] r = new byte[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = fromArray(a, i);
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

    @Test(dataProvider = "byteProviderForIOOBE")
    static void storeArrayIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi) {
        byte[] a = fa.apply(SPECIES.length());
        byte[] r = new byte[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i);
                intoArray(av, r, i);
            }
        }

        int index = fi.apply(a.length);
        boolean shouldFail = isIndexOutOfBounds(SPECIES.length(), index, a.length);
        try {
            ByteVector av = ByteVector.fromArray(SPECIES, a, 0);
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


    @Test(dataProvider = "byteMaskProvider")
    static void loadStoreMaskArray(IntFunction<byte[]> fa,
                                   IntFunction<boolean[]> fm) {
        byte[] a = fa.apply(SPECIES.length());
        byte[] r = new byte[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromValues(SPECIES, mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i, vmask);
                av.intoArray(r, i);
            }
        }
        assertArraysEquals(r, a, mask);


        r = new byte[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, vmask);
            }
        }
        assertArraysEquals(r, a, mask);
    }

    @Test(dataProvider = "byteMaskProviderForIOOBE")
    static void loadArrayMaskIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        byte[] a = fa.apply(SPECIES.length());
        byte[] r = new byte[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromValues(SPECIES, mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = fromArray(a, i, vmask);
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

    @Test(dataProvider = "byteMaskProviderForIOOBE")
    static void storeArrayMaskIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        byte[] a = fa.apply(SPECIES.length());
        byte[] r = new byte[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromValues(SPECIES, mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i);
                intoArray(av, r, i, vmask);
            }
        }

        int index = fi.apply(a.length);
        boolean shouldFail = isIndexOutOfBoundsForMask(mask, index, a.length);
        try {
            ByteVector av = ByteVector.fromArray(SPECIES, a, 0);
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


    @Test(dataProvider = "byteMaskProvider")
    static void loadStoreMask(IntFunction<byte[]> fa,
                              IntFunction<boolean[]> fm) {
        boolean[] mask = fm.apply(SPECIES.length());
        boolean[] r = new boolean[mask.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < mask.length; i += SPECIES.length()) {
                VectorMask<Byte> vmask = VectorMask.fromArray(SPECIES, mask, i);
                vmask.intoArray(r, i);
            }
        }
        Assert.assertEquals(r, mask);
    }


    @Test(dataProvider = "byteMemorySegmentProvider")
    static void loadStoreMemorySegment(IntFunction<byte[]> fa,
                                       IntFunction<MemorySegment> fb,
                                       ByteOrder bo) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), fb);
        MemorySegment r = fb.apply((int) a.byteSize());

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                ByteVector av = ByteVector.fromMemorySegment(SPECIES, a, i, bo);
                av.intoMemorySegment(r, i, bo);
            }
        }
        long m = r.mismatch(a);
        Assert.assertEquals(m, -1, "Segments not equal");
    }

    @Test(dataProvider = "byteByteProviderForIOOBE")
    static void loadMemorySegmentIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> MemorySegment.allocateNative(i, ResourceScope.newImplicitScope()));
        MemorySegment r = MemorySegment.allocateNative(a.byteSize(), ResourceScope.newImplicitScope());

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                ByteVector av = fromMemorySegment(a, i, ByteOrder.nativeOrder());
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

    @Test(dataProvider = "byteByteProviderForIOOBE")
    static void storeMemorySegmentIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> MemorySegment.allocateNative(i, ResourceScope.newImplicitScope()));
        MemorySegment r = MemorySegment.allocateNative(a.byteSize(), ResourceScope.newImplicitScope());

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                ByteVector av = ByteVector.fromMemorySegment(SPECIES, a, i, ByteOrder.nativeOrder());
                intoMemorySegment(av, r, i, ByteOrder.nativeOrder());
            }
        }

        int index = fi.apply((int) a.byteSize());
        boolean shouldFail = isIndexOutOfBounds(SPECIES.vectorByteSize(), index, (int) a.byteSize());
        try {
            ByteVector av = ByteVector.fromMemorySegment(SPECIES, a, 0, ByteOrder.nativeOrder());
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

    @Test(dataProvider = "byteMemorySegmentMaskProvider")
    static void loadStoreMemorySegmentMask(IntFunction<byte[]> fa,
                                           IntFunction<MemorySegment> fb,
                                           IntFunction<boolean[]> fm,
                                           ByteOrder bo) {
        byte[] _a = fa.apply(SPECIES.length());
        MemorySegment a = toSegment(_a, fb);
        MemorySegment r = fb.apply((int) a.byteSize());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromValues(SPECIES, mask);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                ByteVector av = ByteVector.fromMemorySegment(SPECIES, a, i, bo, vmask);
                av.intoMemorySegment(r, i, bo);
            }
        }
        assertArraysEquals(segmentToArray(r), _a, mask);


        r = fb.apply((int) a.byteSize());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                ByteVector av = ByteVector.fromMemorySegment(SPECIES, a, i, bo);
                av.intoMemorySegment(r, i, bo, vmask);
            }
        }
        assertArraysEquals(segmentToArray(r), _a, mask);
    }

    @Test(dataProvider = "byteByteMaskProviderForIOOBE")
    static void loadMemorySegmentMaskIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> MemorySegment.allocateNative(i, ResourceScope.newImplicitScope()));
        MemorySegment r = MemorySegment.allocateNative(a.byteSize(), ResourceScope.newImplicitScope());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromValues(SPECIES, mask);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                ByteVector av = fromMemorySegment(a, i, ByteOrder.nativeOrder(), vmask);
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

    @Test(dataProvider = "byteByteMaskProviderForIOOBE")
    static void storeMemorySegmentMaskIOOBE(IntFunction<byte[]> fa, IntFunction<Integer> fi, IntFunction<boolean[]> fm) {
        MemorySegment a = toSegment(fa.apply(SPECIES.length()), i -> MemorySegment.allocateNative(i, ResourceScope.newImplicitScope()));
        MemorySegment r = MemorySegment.allocateNative(a.byteSize(), ResourceScope.newImplicitScope());
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromValues(SPECIES, mask);

        int l = (int) a.byteSize();
        int s = SPECIES.vectorByteSize();

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < l; i += s) {
                ByteVector av = ByteVector.fromMemorySegment(SPECIES, a, i, ByteOrder.nativeOrder());
                intoMemorySegment(av, r, i, ByteOrder.nativeOrder(), vmask);
            }
        }

        int index = fi.apply((int) a.byteSize());
        boolean shouldFail = isIndexOutOfBoundsForMask(mask, index, (int) a.byteSize(), SPECIES.elementSize() / 8);
        try {
            ByteVector av = ByteVector.fromMemorySegment(SPECIES, a, 0, ByteOrder.nativeOrder());
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

    @Test(dataProvider = "byteMemorySegmentProvider")
    static void loadStoreReadonlyMemorySegment(IntFunction<byte[]> fa,
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

        VectorMask<Byte> m = SPECIES.shuffleFromOp(i -> i % 2 == 0 ? 1 : -1)
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
                VectorMask<Byte> vmask = SPECIES.loadMask(a, i);
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



    static void assertArraysEquals(boolean[] r, byte[] a) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], (a[i] & 1) == 1);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], (a[i] & 1) == 1, "at index #" + i);
        }
    }

    static void assertArraysEquals(boolean[] r, boolean[] a, boolean[] mask) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], mask[i % SPECIES.length()] && a[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[i % SPECIES.length()] && a[i], "at index #" + i);
        }
    }

    static boolean[] convertToBooleanArray(byte[] a) {
        boolean[] r = new boolean[a.length];

        for (int i = 0; i < a.length; i++) {
            r[i] = (a[i] & 1) == 1;
        }

        return r;
    }

    @Test(dataProvider = "byteProvider")
    static void loadByteStoreBooleanArray(IntFunction<byte[]> fa) {
        byte[] a = fa.apply(SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i);
                av.intoBooleanArray(r, i);
            }
        }
        assertArraysEquals(r, a);
    }

    @Test(dataProvider = "byteProvider")
    static void loadStoreBooleanArray(IntFunction<byte[]> fa) {
        boolean[] a = convertToBooleanArray(fa.apply(SPECIES.length()));
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromBooleanArray(SPECIES, a, i);
                av.intoBooleanArray(r, i);
            }
        }
        Assert.assertEquals(r, a);
    }

    @Test(dataProvider = "byteMaskProvider")
    static void loadStoreMaskBooleanArray(IntFunction<byte[]> fa,
                                          IntFunction<boolean[]> fm) {
        boolean[] a = convertToBooleanArray(fa.apply(SPECIES.length()));
        boolean[] r = new boolean[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromValues(SPECIES, mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromBooleanArray(SPECIES, a, i, vmask);
                av.intoBooleanArray(r, i);
            }
        }
        assertArraysEquals(r, a, mask);


        r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromBooleanArray(SPECIES, a, i);
                av.intoBooleanArray(r, i, vmask);
            }
        }
        assertArraysEquals(r, a, mask);
    }


    // Gather/Scatter load/store tests

    static void assertGatherArraysEquals(byte[] r, byte[] a, int[] indexMap) {
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

    static void assertGatherArraysEquals(byte[] r, byte[] a, int[] indexMap, boolean[] mask) {
        int i = 0;
        int j = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                j = i;
                for (; j < i + SPECIES.length(); j++) {
                    Assert.assertEquals(r[j], mask[j % SPECIES.length()] ? a[i + indexMap[j]]: (byte) 0);
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[j % SPECIES.length()] ? a[i + indexMap[j]]: (byte) 0, "at index #" + j);
        }
    }

    static void assertScatterArraysEquals(byte[] r, byte[] a, int[] indexMap, boolean[] mask) {
        byte[] expected = new byte[r.length];

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

    static void assertScatterArraysEquals(byte[] r, byte[] a, int[] indexMap) {
        byte[] expected = new byte[r.length];

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
                flatMap(fs -> BYTE_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fs};
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] gatherScatterMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
          flatMap(fs -> INT_INDEX_GENERATORS.stream().flatMap(fm ->
            BYTE_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm, fs};
            }))).
            toArray(Object[][]::new);
    }


    @Test(dataProvider = "gatherScatterProvider")
    static void gather(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        byte[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        byte[] r = new byte[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i, b, i);
                av.intoArray(r, i);
            }
        }

        assertGatherArraysEquals(r, a, b);
    }

    @Test(dataProvider = "gatherScatterMaskProvider")
    static void gatherMask(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        byte[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        byte[] r = new byte[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i, b, i, vmask);
                av.intoArray(r, i);
            }
        }

        assertGatherArraysEquals(r, a, b, mask);
    }

    @Test(dataProvider = "gatherScatterProvider")
    static void scatter(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        byte[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        byte[] r = new byte[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, b, i);
            }
        }

        assertScatterArraysEquals(r, a, b);
    }

    @Test(dataProvider = "gatherScatterMaskProvider")
    static void scatterMask(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        byte[] a = fa.apply(SPECIES.length());
        int[] b = fs.apply(a.length, SPECIES.length());
        byte[] r = new byte[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(SPECIES, a, i);
                av.intoArray(r, i, b, i, vmask);
            }
        }

        assertScatterArraysEquals(r, a, b, mask);
    }


    static void assertGatherArraysEquals(boolean[] r, boolean[] a, int[] indexMap) {
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

    static void assertGatherArraysEquals(boolean[] r, boolean[] a, int[] indexMap, boolean[] mask) {
        int i = 0;
        int j = 0;
        try {
            for (; i < a.length; i += SPECIES.length()) {
                j = i;
                for (; j < i + SPECIES.length(); j++) {
                    Assert.assertEquals(r[j], mask[j % SPECIES.length()] ? a[i + indexMap[j]]: false);
                }
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[j % SPECIES.length()] ? a[i + indexMap[j]]: false, "at index #" + j);
        }
    }

    static void assertScatterArraysEquals(boolean[] r, boolean[] a, int[] indexMap, boolean[] mask) {
        boolean[] expected = new boolean[r.length];

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

    static void assertScatterArraysEquals(boolean[] r, boolean[] a, int[] indexMap) {
        boolean[] expected = new boolean[r.length];

        // Store before checking, since the same location may be stored to more than once
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            for (int j = i; j < i + SPECIES.length(); j++) {
                expected[i + indexMap[j]] = a[j];
            }
        }

        Assert.assertEquals(r, expected);
    }

    @Test(dataProvider = "gatherScatterProvider")
    static void booleanGather(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        boolean[] a = convertToBooleanArray(fa.apply(SPECIES.length()));
        int[] b = fs.apply(a.length, SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromBooleanArray(SPECIES, a, i, b, i);
                av.intoBooleanArray(r, i);
            }
        }

        assertGatherArraysEquals(r, a, b);
    }

    @Test(dataProvider = "gatherScatterMaskProvider")
    static void booleanGatherMask(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        boolean[] a = convertToBooleanArray(fa.apply(SPECIES.length()));
        int[] b = fs.apply(a.length, SPECIES.length());
        boolean[] r = new boolean[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromBooleanArray(SPECIES, a, i, b, i, vmask);
                av.intoBooleanArray(r, i);
            }
        }

        assertGatherArraysEquals(r, a, b, mask);
    }

    @Test(dataProvider = "gatherScatterProvider")
    static void booleanScatter(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs) {
        boolean[] a = convertToBooleanArray(fa.apply(SPECIES.length()));
        int[] b = fs.apply(a.length, SPECIES.length());
        boolean[] r = new boolean[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromBooleanArray(SPECIES, a, i);
                av.intoBooleanArray(r, i, b, i);
            }
        }

        assertScatterArraysEquals(r, a, b);
    }

    @Test(dataProvider = "gatherScatterMaskProvider")
    static void booleanScatterMask(IntFunction<byte[]> fa, BiFunction<Integer,Integer,int[]> fs, IntFunction<boolean[]> fm) {
        boolean[] a = convertToBooleanArray(fa.apply(SPECIES.length()));
        int[] b = fs.apply(a.length, SPECIES.length());
        boolean[] r = new boolean[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        VectorMask<Byte> vmask = VectorMask.fromArray(SPECIES, mask, 0);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                ByteVector av = ByteVector.fromBooleanArray(SPECIES, a, i);
                av.intoBooleanArray(r, i, b, i, vmask);
            }
        }

        assertScatterArraysEquals(r, a, b, mask);
    }

}
