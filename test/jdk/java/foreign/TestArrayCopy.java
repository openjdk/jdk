/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestArrayCopy
 */

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * These tests exercise the MemoryCopy copyFromArray(...) and copyToArray(...).
 * To make these tests more challenging the segment is a view of the given array,
 * which makes the copy operations overlapping self-copies.  Thus, this checks the claim:
 *
 * <p>If the source (destination) segment is actually a view of the destination (source) array,
 * and if the copy region of the source overlaps with the copy region of the destination,
 * the copy of the overlapping region is performed as if the data in the overlapping region
 * were first copied into a temporary segment before being copied to the destination.</p>
 */
public class TestArrayCopy {
    private static final ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();
    private static final ByteOrder NON_NATIVE_ORDER = NATIVE_ORDER == ByteOrder.LITTLE_ENDIAN
            ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

    private static final int SEG_LENGTH_BYTES = 32;
    private static final int SEG_OFFSET_BYTES = 8;

    @Test(dataProvider = "copyModesAndHelpers")
    public void testSelfCopy(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        int indexShifts = SEG_OFFSET_BYTES / bytesPerElement;
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        MemorySegment truth = truthSegment(base, helper, indexShifts, mode);
        ByteOrder bo = mode.swap ? NON_NATIVE_ORDER : NATIVE_ORDER;
        //CopyFrom
        Object srcArr = helper.toArray(base);
        int srcIndex = mode.direction ? 0 : indexShifts;
        int srcCopyLen = helper.length(srcArr) - indexShifts;
        MemorySegment dstSeg = helper.fromArray(srcArr);
        long dstOffsetBytes = mode.direction ? SEG_OFFSET_BYTES : 0;
        helper.copyFromArray(srcArr, srcIndex, srcCopyLen, dstSeg, dstOffsetBytes, bo);
        assertEquals(truth.mismatch(dstSeg), -1);
        //CopyTo
        long srcOffsetBytes = mode.direction ? 0 : SEG_OFFSET_BYTES;
        Object dstArr = helper.toArray(base);
        MemorySegment srcSeg = helper.fromArray(dstArr).asReadOnly();
        int dstIndex = mode.direction ? indexShifts : 0;
        int dstCopyLen = helper.length(dstArr) - indexShifts;
        helper.copyToArray(srcSeg, srcOffsetBytes, dstArr, dstIndex, dstCopyLen, bo);
        MemorySegment result = helper.fromArray(dstArr);
        assertEquals(truth.mismatch(result), -1);
    }

    @Test(dataProvider = "copyModesAndHelpers")
    public void testUnalignedCopy(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        int indexShifts = SEG_OFFSET_BYTES / bytesPerElement;
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        ByteOrder bo = mode.swap ? NON_NATIVE_ORDER : NATIVE_ORDER;
        //CopyFrom
        Object srcArr = helper.toArray(base);
        int srcIndex = mode.direction ? 0 : indexShifts;
        int srcCopyLen = helper.length(srcArr) - indexShifts;
        MemorySegment dstSeg = helper.fromArray(srcArr);
        long dstOffsetBytes = mode.direction ? (SEG_OFFSET_BYTES - 1) : 0;
        helper.copyFromArray(srcArr, srcIndex, srcCopyLen, dstSeg, dstOffsetBytes, bo);
        //CopyTo
        long srcOffsetBytes = mode.direction ? 0 : (SEG_OFFSET_BYTES - 1);
        Object dstArr = helper.toArray(base);
        MemorySegment srcSeg = helper.fromArray(dstArr).asReadOnly();
        int dstIndex = mode.direction ? indexShifts : 0;
        int dstCopyLen = helper.length(dstArr) - indexShifts;
        helper.copyToArray(srcSeg, srcOffsetBytes, dstArr, dstIndex, dstCopyLen, bo);
    }

    @Test(dataProvider = "copyModesAndHelpers")
    public void testCopyOobLength(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        //CopyFrom
        Object srcArr = helper.toArray(base);
        MemorySegment dstSeg = helper.fromArray(srcArr);
        try {
            helper.copyFromArray(srcArr, 0, (SEG_LENGTH_BYTES / bytesPerElement) * 2, dstSeg, 0, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
        //CopyTo
        Object dstArr = helper.toArray(base);
        MemorySegment srcSeg = helper.fromArray(dstArr).asReadOnly();
        try {
            helper.copyToArray(srcSeg, 0, dstArr, 0, (SEG_LENGTH_BYTES / bytesPerElement) * 2, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
    }

    @Test(dataProvider = "copyModesAndHelpers")
    public void testCopyNegativeIndices(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        //CopyFrom
        Object srcArr = helper.toArray(base);
        MemorySegment dstSeg = helper.fromArray(srcArr);
        try {
            helper.copyFromArray(srcArr, -1, SEG_LENGTH_BYTES / bytesPerElement, dstSeg, 0, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
        //CopyTo
        Object dstArr = helper.toArray(base);
        MemorySegment srcSeg = helper.fromArray(dstArr).asReadOnly();
        try {
            helper.copyToArray(srcSeg, 0, dstArr, -1, SEG_LENGTH_BYTES / bytesPerElement, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
    }

    @Test(dataProvider = "copyModesAndHelpers")
    public void testCopyNegativeOffsets(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        //CopyFrom
        Object srcArr = helper.toArray(base);
        MemorySegment dstSeg = helper.fromArray(srcArr);
        try {
            helper.copyFromArray(srcArr, 0, SEG_LENGTH_BYTES / bytesPerElement, dstSeg, -1, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
        //CopyTo
        Object dstArr = helper.toArray(base);
        MemorySegment srcSeg = helper.fromArray(dstArr).asReadOnly();
        try {
            helper.copyToArray(srcSeg, -1, dstArr, 0, SEG_LENGTH_BYTES / bytesPerElement, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
    }

    @Test(dataProvider = "copyModesAndHelpers")
    public void testCopyOobIndices(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        //CopyFrom
        Object srcArr = helper.toArray(base);
        MemorySegment dstSeg = helper.fromArray(srcArr);
        try {
            helper.copyFromArray(srcArr, helper.length(srcArr) + 1, SEG_LENGTH_BYTES / bytesPerElement, dstSeg, 0, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
        //CopyTo
        Object dstArr = helper.toArray(base);
        MemorySegment srcSeg = helper.fromArray(dstArr).asReadOnly();
        try {
            helper.copyToArray(srcSeg, 0, dstArr, helper.length(dstArr) + 1, SEG_LENGTH_BYTES / bytesPerElement, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
    }

    @Test(dataProvider = "copyModesAndHelpers")
    public void testCopyOobOffsets(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        //CopyFrom
        Object srcArr = helper.toArray(base);
        MemorySegment dstSeg = helper.fromArray(srcArr);
        try {
            helper.copyFromArray(srcArr, 0, SEG_LENGTH_BYTES / bytesPerElement, dstSeg, SEG_LENGTH_BYTES + 1, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
        //CopyTo
        Object dstArr = helper.toArray(base);
        MemorySegment srcSeg = helper.fromArray(dstArr).asReadOnly();
        try {
            helper.copyToArray(srcSeg, SEG_OFFSET_BYTES + 1, dstArr, 0, SEG_LENGTH_BYTES / bytesPerElement, ByteOrder.nativeOrder());
            fail();
        } catch (IndexOutOfBoundsException ex) {
            //ok
        }
    }

    @Test(dataProvider = "copyModesAndHelpers")
    public void testCopyReadOnlyDest(CopyMode mode, CopyHelper<Object, ValueLayout> helper, String helperDebugString) {
        int bytesPerElement = (int)helper.elementLayout.byteSize();
        MemorySegment base = srcSegment(SEG_LENGTH_BYTES);
        //CopyFrom
        Object srcArr = helper.toArray(base);
        MemorySegment dstSeg = helper.fromArray(srcArr).asReadOnly();
        try {
            helper.copyFromArray(srcArr, 0, SEG_LENGTH_BYTES / bytesPerElement, dstSeg, 0, ByteOrder.nativeOrder());
            fail();
        } catch (IllegalArgumentException ex) {
            //ok
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNotAnArraySrc() {
        MemorySegment segment = MemorySegment.ofArray(new int[] {1, 2, 3, 4});
        MemorySegment.copy(segment, JAVA_BYTE, 0, new String[] { "hello" }, 0, 4);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNotAnArrayDst() {
        MemorySegment segment = MemorySegment.ofArray(new int[] {1, 2, 3, 4});
        MemorySegment.copy(new String[] { "hello" }, 0, segment, JAVA_BYTE, 0, 4);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCarrierMismatchSrc() {
        MemorySegment segment = MemorySegment.ofArray(new int[] {1, 2, 3, 4});
        MemorySegment.copy(segment, JAVA_INT, 0, new byte[] { 1, 2, 3, 4 }, 0, 4);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCarrierMismatchDst() {
        MemorySegment segment = MemorySegment.ofArray(new int[] {1, 2, 3, 4});
        MemorySegment.copy(new byte[] { 1, 2, 3, 4 }, 0, segment, JAVA_INT, 0, 4);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHyperAlignedSrc() {
        MemorySegment segment = MemorySegment.ofArray(new byte[] {1, 2, 3, 4});
        MemorySegment.copy(new byte[] { 1, 2, 3, 4 }, 0, segment, JAVA_BYTE.withByteAlignment(2), 0, 4);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHyperAlignedDst() {
        MemorySegment segment = MemorySegment.ofArray(new byte[] {1, 2, 3, 4});
        MemorySegment.copy(segment, JAVA_BYTE.withByteAlignment(2), 0, new byte[] { 1, 2, 3, 4 }, 0, 4);
    }

    /***** Utilities *****/

    public static MemorySegment srcSegment(int bytesLength) {
        byte[] arr = new byte[bytesLength];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (byte)i;
        }
        return MemorySegment.ofArray(arr);
    }

    private static VarHandle arrayVarHandle(ValueLayout layout) {
        return MethodHandles.insertCoordinates(layout.arrayElementVarHandle(), 1, 0L);
    }

    public static MemorySegment truthSegment(MemorySegment srcSeg, CopyHelper<?, ?> helper, int indexShifts, CopyMode mode) {
        VarHandle indexedHandleNO = arrayVarHandle(helper.elementLayout.withOrder(NATIVE_ORDER));
        VarHandle indexedHandleNNO = arrayVarHandle(helper.elementLayout.withOrder(NON_NATIVE_ORDER));
        MemorySegment dstSeg = MemorySegment.ofArray(srcSeg.toArray(JAVA_BYTE));
        int indexLength = (int) dstSeg.byteSize() / (int)helper.elementLayout.byteSize();
        if (mode.direction) {
            if (mode.swap) {
                for (int i = indexLength - 1; i >= indexShifts; i--) {
                    Object v = indexedHandleNNO.get(dstSeg, i - indexShifts);
                    indexedHandleNO.set(dstSeg, i, v);
                }
            } else {
                for (int i = indexLength - 1; i >= indexShifts; i--) {
                    Object v = indexedHandleNO.get(dstSeg, i - indexShifts);
                    indexedHandleNO.set(dstSeg, i, v);
                }
            }
        } else { //down
            if (mode.swap) {
                for (int i = indexShifts; i < indexLength; i++) {
                    Object v = indexedHandleNNO.get(dstSeg, i);
                    indexedHandleNO.set(dstSeg, i - indexShifts, v);
                }
            } else {
                for (int i = indexShifts; i < indexLength; i++) {
                    Object v = indexedHandleNO.get(dstSeg, i);
                    indexedHandleNO.set(dstSeg, i - indexShifts, v);
                }
            }
        }
        return dstSeg;
    }

    enum CopyMode {
        UP_NO_SWAP(true, false),
        UP_SWAP(true, true),
        DOWN_NO_SWAP(false, false),
        DOWN_SWAP(false, true);

        final boolean direction;
        final boolean swap;

        CopyMode(boolean direction, boolean swap) {
            this.direction = direction;
            this.swap = swap;
        }
    }

    abstract static class CopyHelper<X, L extends ValueLayout> {

        final L elementLayout;
        final Class<?> carrier;

        @SuppressWarnings("unchecked")
        public CopyHelper(L elementLayout, Class<X> carrier) {
            this.elementLayout = (L)elementLayout.withByteAlignment(1);
            this.carrier = carrier;
        }

        abstract void copyFromArray(X srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo);
        abstract void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, X dstArr, int dstIndex, int dstCopyLen, ByteOrder bo);
        abstract X toArray(MemorySegment segment);
        abstract MemorySegment fromArray(X array);
        abstract int length(X arr);

        @Override
        public String toString() {
            return "CopyHelper{" +
                    "elementLayout=" + elementLayout +
                    ", carrier=" + carrier.getName() +
                    '}';
        }

        static final CopyHelper<byte[], ValueLayout.OfByte> BYTE = new CopyHelper<>(JAVA_BYTE, byte[].class) {
            @Override
            void copyFromArray(byte[] srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo) {
                MemorySegment.copy(srcArr, srcIndex, dstSeg, elementLayout.withOrder(bo), dstOffsetBytes, srcCopyLen);
            }

            @Override
            void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, byte[] dstArr, int dstIndex, int dstCopyLen, ByteOrder bo) {
                MemorySegment.copy(srcSeg, elementLayout.withOrder(bo), srcOffsetBytes, dstArr, dstIndex, dstCopyLen);
            }

            @Override
            byte[] toArray(MemorySegment segment) {
                return segment.toArray(elementLayout);
            }

            @Override
            MemorySegment fromArray(byte[] array) {
                return MemorySegment.ofArray(array);
            }

            @Override
            int length(byte[] arr) {
                return arr.length;
            }
        };

        static final CopyHelper<char[], ValueLayout.OfChar> CHAR = new CopyHelper<>(ValueLayout.JAVA_CHAR, char[].class) {
            @Override
            void copyFromArray(char[] srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo) {
                MemorySegment.copy(srcArr, srcIndex, dstSeg, elementLayout.withOrder(bo), dstOffsetBytes, srcCopyLen);
            }

            @Override
            void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, char[] dstArr, int dstIndex, int dstCopyLen, ByteOrder bo) {
                MemorySegment.copy(srcSeg, elementLayout.withOrder(bo), srcOffsetBytes, dstArr, dstIndex, dstCopyLen);
            }

            @Override
            char[] toArray(MemorySegment segment) {
                return segment.toArray(elementLayout);
            }

            @Override
            MemorySegment fromArray(char[] array) {
                return MemorySegment.ofArray(array);
            }

            @Override
            int length(char[] arr) {
                return arr.length;
            }
        };

        static final CopyHelper<short[], ValueLayout.OfShort> SHORT = new CopyHelper<>(ValueLayout.JAVA_SHORT, short[].class) {
            @Override
            void copyFromArray(short[] srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo) {
                MemorySegment.copy(srcArr, srcIndex, dstSeg, elementLayout.withOrder(bo), dstOffsetBytes, srcCopyLen);
            }

            @Override
            void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, short[] dstArr, int dstIndex, int dstCopyLen, ByteOrder bo) {
                MemorySegment.copy(srcSeg, elementLayout.withOrder(bo), srcOffsetBytes, dstArr, dstIndex, dstCopyLen);
            }

            @Override
            short[] toArray(MemorySegment segment) {
                return segment.toArray(elementLayout);
            }

            @Override
            MemorySegment fromArray(short[] array) {
                return MemorySegment.ofArray(array);
            }

            @Override
            int length(short[] arr) {
                return arr.length;
            }
        };

        static final CopyHelper<int[], ValueLayout.OfInt> INT = new CopyHelper<>(ValueLayout.JAVA_INT, int[].class) {
            @Override
            void copyFromArray(int[] srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo) {
                MemorySegment.copy(srcArr, srcIndex, dstSeg, elementLayout.withOrder(bo), dstOffsetBytes, srcCopyLen);
            }

            @Override
            void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, int[] dstArr, int dstIndex, int dstCopyLen, ByteOrder bo) {
                MemorySegment.copy(srcSeg, elementLayout.withOrder(bo), srcOffsetBytes, dstArr, dstIndex, dstCopyLen);
            }

            @Override
            int[] toArray(MemorySegment segment) {
                return segment.toArray(elementLayout);
            }

            @Override
            MemorySegment fromArray(int[] array) {
                return MemorySegment.ofArray(array);
            }

            @Override
            int length(int[] arr) {
                return arr.length;
            }
        };

        static final CopyHelper<float[], ValueLayout.OfFloat> FLOAT = new CopyHelper<>(ValueLayout.JAVA_FLOAT, float[].class) {
            @Override
            void copyFromArray(float[] srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo) {
                MemorySegment.copy(srcArr, srcIndex, dstSeg, elementLayout.withOrder(bo), dstOffsetBytes, srcCopyLen);
            }

            @Override
            void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, float[] dstArr, int dstIndex, int dstCopyLen, ByteOrder bo) {
                MemorySegment.copy(srcSeg, elementLayout.withOrder(bo), srcOffsetBytes, dstArr, dstIndex, dstCopyLen);
            }

            @Override
            float[] toArray(MemorySegment segment) {
                return segment.toArray(elementLayout);
            }

            @Override
            MemorySegment fromArray(float[] array) {
                return MemorySegment.ofArray(array);
            }

            @Override
            int length(float[] arr) {
                return arr.length;
            }
        };

        static final CopyHelper<long[], ValueLayout.OfLong> LONG = new CopyHelper<>(ValueLayout.JAVA_LONG, long[].class) {
            @Override
            void copyFromArray(long[] srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo) {
                MemorySegment.copy(srcArr, srcIndex, dstSeg, elementLayout.withOrder(bo), dstOffsetBytes, srcCopyLen);
            }

            @Override
            void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, long[] dstArr, int dstIndex, int dstCopyLen, ByteOrder bo) {
                MemorySegment.copy(srcSeg, elementLayout.withOrder(bo), srcOffsetBytes, dstArr, dstIndex, dstCopyLen);
            }

            @Override
            long[] toArray(MemorySegment segment) {
                return segment.toArray(elementLayout);
            }

            @Override
            MemorySegment fromArray(long[] array) {
                return MemorySegment.ofArray(array);
            }

            @Override
            int length(long[] arr) {
                return arr.length;
            }
        };

        static final CopyHelper<double[], ValueLayout.OfDouble> DOUBLE = new CopyHelper<>(ValueLayout.JAVA_DOUBLE, double[].class) {
            @Override
            void copyFromArray(double[] srcArr, int srcIndex, int srcCopyLen, MemorySegment dstSeg, long dstOffsetBytes, ByteOrder bo) {
                MemorySegment.copy(srcArr, srcIndex, dstSeg, elementLayout.withOrder(bo), dstOffsetBytes, srcCopyLen);
            }

            @Override
            void copyToArray(MemorySegment srcSeg, long srcOffsetBytes, double[] dstArr, int dstIndex, int dstCopyLen, ByteOrder bo) {
                MemorySegment.copy(srcSeg, elementLayout.withOrder(bo), srcOffsetBytes, dstArr, dstIndex, dstCopyLen);
            }

            @Override
            double[] toArray(MemorySegment segment) {
                return segment.toArray(elementLayout);
            }

            @Override
            MemorySegment fromArray(double[] array) {
                return MemorySegment.ofArray(array);
            }

            @Override
            int length(double[] arr) {
                return arr.length;
            }
        };
    }

    @DataProvider
    Object[][] copyModesAndHelpers() {
        CopyHelper<?, ?>[] helpers = { CopyHelper.BYTE, CopyHelper.CHAR, CopyHelper.SHORT, CopyHelper.INT,
                                    CopyHelper.FLOAT, CopyHelper.LONG, CopyHelper.DOUBLE };
        List<Object[]> results = new ArrayList<>();
        for (CopyHelper<?, ?> helper : helpers) {
            for (CopyMode mode : CopyMode.values()) {
                results.add(new Object[] { mode, helper, helper.toString() });
            }
        }
        return results.stream().toArray(Object[][]::new);
    }
}
