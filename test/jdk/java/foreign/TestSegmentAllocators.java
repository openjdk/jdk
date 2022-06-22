/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @enablePreview
 * @run testng/othervm TestSegmentAllocators
 */

import java.lang.foreign.*;

import org.testng.annotations.*;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.testng.Assert.*;

public class TestSegmentAllocators {

    final static int ELEMS = 128;
    final static Class<?> ADDRESS_CARRIER = ValueLayout.ADDRESS.bitSize() == 64 ? long.class : int.class;

    @Test(dataProvider = "scalarAllocations")
    @SuppressWarnings("unchecked")
    public <Z, L extends ValueLayout> void testAllocation(Z value, AllocationFactory allocationFactory, L layout, AllocationFunction<Z, L> allocationFunction, Function<MemoryLayout, VarHandle> handleFactory) {
        layout = (L)layout.withBitAlignment(layout.bitSize());
        L[] layouts = (L[])new ValueLayout[] {
                layout,
                layout.withBitAlignment(layout.bitAlignment() * 2),
                layout.withBitAlignment(layout.bitAlignment() * 4),
                layout.withBitAlignment(layout.bitAlignment() * 8)
        };
        for (L alignedLayout : layouts) {
            List<MemorySegment> addressList = new ArrayList<>();
            int elems = ELEMS / ((int)alignedLayout.byteAlignment() / (int)layout.byteAlignment());
            MemorySession[] sessions = {
                    MemorySession.openConfined(),
                    MemorySession.openShared()
            };
            for (MemorySession session : sessions) {
                try (session) {
                    SegmentAllocator allocator = allocationFactory.allocator(alignedLayout.byteSize() * ELEMS, session);
                    for (int i = 0; i < elems; i++) {
                        MemorySegment address = allocationFunction.allocate(allocator, alignedLayout, value);
                        assertEquals(address.byteSize(), alignedLayout.byteSize());
                        addressList.add(address);
                        VarHandle handle = handleFactory.apply(alignedLayout);
                        assertEquals(value, handle.get(address));
                    }
                    boolean isBound = allocationFactory.isBound();
                    try {
                        allocationFunction.allocate(allocator, alignedLayout, value);
                        assertFalse(isBound);
                    } catch (OutOfMemoryError ex) {
                        //failure is expected if bound
                        assertTrue(isBound);
                    }
                }
                if (allocationFactory != AllocationFactory.IMPLICIT_ALLOCATOR) {
                    // addresses should be invalid now
                    for (MemorySegment address : addressList) {
                        assertFalse(address.session().isAlive());
                    }
                }
            }
        }
    }

    static final int SIZE_256M = 1024 * 1024 * 256;

    @Test
    public void testBigAllocationInUnboundedSession() {
        try (MemorySession session = MemorySession.openConfined()) {
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(session);
            for (int i = 8 ; i < SIZE_256M ; i *= 8) {
                MemorySegment address = allocator.allocate(i, i);
                //check size
                assertEquals(address.byteSize(), i);
                //check alignment
                assertEquals(address.address().toRawLongValue() % i, 0);
            }
        }
    }

    @Test
    public void testTooBigForBoundedArena() {
        try (MemorySession session = MemorySession.openConfined()) {
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(10, session);
            assertThrows(OutOfMemoryError.class, () -> allocator.allocate(12));
            allocator.allocate(5); // ok
        }
    }

    @Test
    public void testBiggerThanBlockForBoundedArena() {
        try (MemorySession session = MemorySession.openConfined()) {
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(4 * 1024 * 2, session);
            allocator.allocate(4 * 1024 + 1); // should be ok
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadUnboundedArenaSize() {
        SegmentAllocator.newNativeArena( -1, MemorySession.global());
    }

    @Test(dataProvider = "allocators", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocationSize(SegmentAllocator allocator) {
        allocator.allocate(-1);
    }

    @Test(dataProvider = "allocators", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocationAlignZero(SegmentAllocator allocator) {
        allocator.allocate(1, 0);
    }

    @Test(dataProvider = "allocators", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocationAlignNeg(SegmentAllocator allocator) {
        allocator.allocate(1, -1);
    }

    @Test(dataProvider = "allocators", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocationAlignNotPowerTwo(SegmentAllocator allocator) {
        allocator.allocate(1, 3);
    }

    @Test(dataProvider = "arrayAllocations")
    public <Z> void testArray(AllocationFactory allocationFactory, ValueLayout layout, AllocationFunction<Object, ValueLayout> allocationFunction, ToArrayHelper<Z> arrayHelper) {
        Z arr = arrayHelper.array();
        MemorySession[] sessions = {
                MemorySession.openConfined(),
                MemorySession.openShared()
        };
        for (MemorySession session : sessions) {
            try (session) {
                SegmentAllocator allocator = allocationFactory.allocator(100, session);
                MemorySegment address = allocationFunction.allocate(allocator, layout, arr);
                Z found = arrayHelper.toArray(address, layout);
                assertEquals(found, arr);
            }
        }
    }

    @DataProvider(name = "scalarAllocations")
    static Object[][] scalarAllocations() {
        List<Object[]> scalarAllocations = new ArrayList<>();
        for (AllocationFactory factory : AllocationFactory.values()) {
            scalarAllocations.add(new Object[] { (byte)42, factory, ValueLayout.JAVA_BYTE,
                    (AllocationFunction.OfByte) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { (short)42, factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfShort) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { (char)42, factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfChar) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42, factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfInt) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42f, factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfFloat) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42L, factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfLong) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42d, factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfDouble) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { MemoryAddress.ofLong(42), factory, ValueLayout.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfAddress) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });

            scalarAllocations.add(new Object[] { (short)42, factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfShort) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { (char)42, factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfChar) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42, factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfInt) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42f, factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfFloat) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42L, factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfLong) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42d, factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfDouble) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { MemoryAddress.ofLong(42), factory, ValueLayout.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfAddress) SegmentAllocator::allocate,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
        }
        return scalarAllocations.toArray(Object[][]::new);
    }

    @DataProvider(name = "arrayAllocations")
    static Object[][] arrayAllocations() {
        List<Object[]> arrayAllocations = new ArrayList<>();
        for (AllocationFactory factory : AllocationFactory.values()) {
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_BYTE,
                    (AllocationFunction.OfByteArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toByteArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfCharArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toCharArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfShortArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toShortArray });
            arrayAllocations.add(new Object[] { factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfIntArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toIntArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfFloatArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toFloatArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfLongArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toLongArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfDoubleArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toDoubleArray });

            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfCharArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toCharArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfShortArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toShortArray });
            arrayAllocations.add(new Object[] { factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfIntArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toIntArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfFloatArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toFloatArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfLongArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toLongArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfDoubleArray) SegmentAllocator::allocateArray,
                    ToArrayHelper.toDoubleArray });
        };
        return arrayAllocations.toArray(Object[][]::new);
    }

    interface AllocationFunction<X, L extends ValueLayout> {
        MemorySegment allocate(SegmentAllocator allocator, L layout, X value);

        interface OfByte extends AllocationFunction<Byte, ValueLayout.OfByte> { }
        interface OfBoolean extends AllocationFunction<Boolean, ValueLayout.OfBoolean> { }
        interface OfChar extends AllocationFunction<Character, ValueLayout.OfChar> { }
        interface OfShort extends AllocationFunction<Short, ValueLayout.OfShort> { }
        interface OfInt extends AllocationFunction<Integer, ValueLayout.OfInt> { }
        interface OfFloat extends AllocationFunction<Float, ValueLayout.OfFloat> { }
        interface OfLong extends AllocationFunction<Long, ValueLayout.OfLong> { }
        interface OfDouble extends AllocationFunction<Double, ValueLayout.OfDouble> { }
        interface OfAddress extends AllocationFunction<MemoryAddress, ValueLayout.OfAddress> { }

        interface OfByteArray extends AllocationFunction<byte[], ValueLayout.OfByte> { }
        interface OfCharArray extends AllocationFunction<char[], ValueLayout.OfChar> { }
        interface OfShortArray extends AllocationFunction<short[], ValueLayout.OfShort> { }
        interface OfIntArray extends AllocationFunction<int[], ValueLayout.OfInt> { }
        interface OfFloatArray extends AllocationFunction<float[], ValueLayout.OfFloat> { }
        interface OfLongArray extends AllocationFunction<long[], ValueLayout.OfLong> { }
        interface OfDoubleArray extends AllocationFunction<double[], ValueLayout.OfDouble> { }
    }

    enum AllocationFactory {
        ARENA_BOUNDED(true, SegmentAllocator::newNativeArena),
        ARENA_UNBOUNDED(false, (size, session) -> SegmentAllocator.newNativeArena(session)),
        NATIVE_ALLOCATOR(false, (size, session) -> session),
        IMPLICIT_ALLOCATOR(false, (size, session) -> SegmentAllocator.implicitAllocator());

        private final boolean isBound;
        private final BiFunction<Long, MemorySession, SegmentAllocator> factory;

        AllocationFactory(boolean isBound, BiFunction<Long, MemorySession, SegmentAllocator> factory) {
            this.isBound = isBound;
            this.factory = factory;
        }

        SegmentAllocator allocator(long size, MemorySession session) {
            return factory.apply(size, session);
        }

        public boolean isBound() {
            return isBound;
        }
    }

    interface ToArrayHelper<T> {
        T array();
        T toArray(MemorySegment segment, ValueLayout layout);

        ToArrayHelper<byte[]> toByteArray = new ToArrayHelper<>() {
            @Override
            public byte[] array() {
                return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            }

            @Override
            public byte[] toArray(MemorySegment segment, ValueLayout layout) {
                ByteBuffer buffer = segment.asByteBuffer().order(layout.order());
                byte[] found = new byte[buffer.limit()];
                buffer.get(found);
                return found;
            }
        };

        ToArrayHelper<char[]> toCharArray = new ToArrayHelper<>() {
            @Override
            public char[] array() {
                return new char[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            }

            @Override
            public char[] toArray(MemorySegment segment, ValueLayout layout) {
                CharBuffer buffer = segment.asByteBuffer().order(layout.order()).asCharBuffer();
                char[] found = new char[buffer.limit()];
                buffer.get(found);
                return found;
            }
        };

        ToArrayHelper<short[]> toShortArray = new ToArrayHelper<>() {
            @Override
            public short[] array() {
                return new short[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            }

            @Override
            public short[] toArray(MemorySegment segment, ValueLayout layout) {
                ShortBuffer buffer = segment.asByteBuffer().order(layout.order()).asShortBuffer();
                short[] found = new short[buffer.limit()];
                buffer.get(found);
                return found;
            }
        };

        ToArrayHelper<int[]> toIntArray = new ToArrayHelper<>() {
            @Override
            public int[] array() {
                return new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            }

            @Override
            public int[] toArray(MemorySegment segment, ValueLayout layout) {
                IntBuffer buffer = segment.asByteBuffer().order(layout.order()).asIntBuffer();
                int[] found = new int[buffer.limit()];
                buffer.get(found);
                return found;
            }
        };

        ToArrayHelper<float[]> toFloatArray = new ToArrayHelper<>() {
            @Override
            public float[] array() {
                return new float[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            }

            @Override
            public float[] toArray(MemorySegment segment, ValueLayout layout) {
                FloatBuffer buffer = segment.asByteBuffer().order(layout.order()).asFloatBuffer();
                float[] found = new float[buffer.limit()];
                buffer.get(found);
                return found;
            }
        };

        ToArrayHelper<long[]> toLongArray = new ToArrayHelper<>() {
            @Override
            public long[] array() {
                return new long[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            }

            @Override
            public long[] toArray(MemorySegment segment, ValueLayout layout) {
                LongBuffer buffer = segment.asByteBuffer().order(layout.order()).asLongBuffer();
                long[] found = new long[buffer.limit()];
                buffer.get(found);
                return found;
            }
        };

        ToArrayHelper<double[]> toDoubleArray = new ToArrayHelper<>() {
            @Override
            public double[] array() {
                return new double[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
            }

            @Override
            public double[] toArray(MemorySegment segment, ValueLayout layout) {
                DoubleBuffer buffer = segment.asByteBuffer().order(layout.order()).asDoubleBuffer();
                double[] found = new double[buffer.limit()];
                buffer.get(found);
                return found;
            }
        };

        ToArrayHelper<MemoryAddress[]> toAddressArray = new ToArrayHelper<>() {
            @Override
            public MemoryAddress[] array() {
                return switch ((int) ValueLayout.ADDRESS.byteSize()) {
                    case 4 -> wrap(toIntArray.array());
                    case 8 -> wrap(toLongArray.array());
                    default -> throw new IllegalStateException("Cannot get here");
                };
            }

            @Override
            public MemoryAddress[] toArray(MemorySegment segment, ValueLayout layout) {
                return switch ((int)layout.byteSize()) {
                    case 4 -> wrap(toIntArray.toArray(segment, layout));
                    case 8 -> wrap(toLongArray.toArray(segment, layout));
                    default -> throw new IllegalStateException("Cannot get here");
                };
            }

            private MemoryAddress[] wrap(int[] ints) {
                return IntStream.of(ints).mapToObj(MemoryAddress::ofLong).toArray(MemoryAddress[]::new);
            }

            private MemoryAddress[] wrap(long[] ints) {
                return LongStream.of(ints).mapToObj(MemoryAddress::ofLong).toArray(MemoryAddress[]::new);
            }
        };
    }

    @DataProvider(name = "allocators")
    static Object[][] allocators() {
        return new Object[][] {
                { SegmentAllocator.implicitAllocator() },
                { SegmentAllocator.newNativeArena(MemorySession.global()) },
                { SegmentAllocator.prefixAllocator(MemorySegment.allocateNative(10, MemorySession.global())) },
        };
    }
}
