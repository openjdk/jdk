/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.foreign
 * @run testng/othervm TestSegmentAllocators
 */

import java.lang.foreign.*;

import jdk.internal.foreign.NativeMemorySegmentImpl;
import org.testng.annotations.*;

import java.lang.foreign.Arena;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.testng.Assert.*;

public class TestSegmentAllocators {

    final static int ELEMS = 128;

    @Test(dataProvider = "scalarAllocations")
    @SuppressWarnings("unchecked")
    public <Z, L extends ValueLayout> void testAllocation(Z value, AllocationFactory allocationFactory, L layout, AllocationFunction<Z, L> allocationFunction, Function<MemoryLayout, VarHandle> handleFactory) {
        layout = (L)layout.withByteAlignment(layout.byteSize());
        L[] layouts = (L[])new ValueLayout[] {
                layout,
                layout.withByteAlignment(layout.byteAlignment() * 2),
                layout.withByteAlignment(layout.byteAlignment() * 4),
                layout.withByteAlignment(layout.byteAlignment() * 8)
        };
        for (L alignedLayout : layouts) {
            List<MemorySegment> addressList = new ArrayList<>();
            int elems = ELEMS / ((int)alignedLayout.byteAlignment() / (int)layout.byteAlignment());
            Arena[] arenas = {
                    Arena.ofConfined(),
                    Arena.ofShared()
            };
            for (Arena arena : arenas) {
                try (arena) {
                    SegmentAllocator allocator = allocationFactory.allocator(alignedLayout.byteSize() * ELEMS, arena);
                    for (int i = 0; i < elems; i++) {
                        MemorySegment address = allocationFunction.allocate(allocator, alignedLayout, value);
                        assertEquals(address.byteSize(), alignedLayout.byteSize());
                        addressList.add(address);
                        VarHandle handle = handleFactory.apply(alignedLayout);
                        assertEquals(value, handle.get(address, 0L));
                    }
                    boolean isBound = allocationFactory.isBound();
                    try {
                        allocationFunction.allocate(allocator, alignedLayout, value);
                        assertFalse(isBound);
                    } catch (IndexOutOfBoundsException ex) {
                        //failure is expected if bound
                        assertTrue(isBound);
                    }
                }
                // addresses should be invalid now
                for (MemorySegment address : addressList) {
                    assertFalse(address.scope().isAlive());
                }
            }
        }
    }

    static final int SIZE_256M = 1024 * 1024 * 256;

    @Test
    public void testBigAllocationInUnboundedSession() {
        try (Arena arena = Arena.ofConfined()) {
            for (int i = 8 ; i < SIZE_256M ; i *= 8) {
                SegmentAllocator allocator = SegmentAllocator.slicingAllocator(arena.allocate(i * 2 + 1));
                MemorySegment address = allocator.allocate(i, i);
                //check size
                assertEquals(address.byteSize(), i);
                //check alignment
                assertEquals(address.address() % i, 0);
            }
        }
    }

    @Test
    public void testTooBigForBoundedArena() {
        try (Arena arena = Arena.ofConfined()) {
            SegmentAllocator allocator = SegmentAllocator.slicingAllocator(arena.allocate(10));
            assertThrows(IndexOutOfBoundsException.class, () -> allocator.allocate(12));
            allocator.allocate(5);
        }
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

    @Test(dataProvider = "allocators", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocationArrayNegSize(SegmentAllocator allocator) {
        allocator.allocate(ValueLayout.JAVA_BYTE, -1);
    }

    @Test(dataProvider = "allocators", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocationArrayOverflow(SegmentAllocator allocator) {
        allocator.allocate(ValueLayout.JAVA_LONG,  Long.MAX_VALUE);
    }

    @Test(expectedExceptions = OutOfMemoryError.class)
    public void testBadArenaNullReturn() {
        try (Arena arena = Arena.ofConfined()) {
            arena.allocate(Long.MAX_VALUE, 2);
        }
    }

    @Test
    public void testArrayAllocateDelegation() {
        AtomicInteger calls = new AtomicInteger();
        SegmentAllocator allocator = new SegmentAllocator() {
            @Override
            public MemorySegment allocate(long bytesSize, long byteAlignment) {
                return MemorySegment.NULL;
            }

            @Override
            public MemorySegment allocateFrom(ValueLayout elementLayout, MemorySegment source, ValueLayout sourceElementLayout, long sourceOffset, long elementCount) {
                calls.incrementAndGet();
                return MemorySegment.NULL;
            }
        };
        allocator.allocateFrom(ValueLayout.JAVA_BYTE);
        allocator.allocateFrom(ValueLayout.JAVA_SHORT);
        allocator.allocateFrom(ValueLayout.JAVA_CHAR);
        allocator.allocateFrom(ValueLayout.JAVA_INT);
        allocator.allocateFrom(ValueLayout.JAVA_FLOAT);
        allocator.allocateFrom(ValueLayout.JAVA_LONG);
        allocator.allocateFrom(ValueLayout.JAVA_DOUBLE);
        assertEquals(calls.get(), 7);
    }

    @Test
    public void testStringAllocateDelegation() {
        AtomicInteger calls = new AtomicInteger();
        SegmentAllocator allocator = new SegmentAllocator() {
            @Override
            public MemorySegment allocate(long byteSize, long byteAlignment) {
                return Arena.ofAuto().allocate(byteSize, byteAlignment);
            }

            @Override
            public MemorySegment allocate(long size) {
                calls.incrementAndGet();
                return allocate(size, 1);
            };
        };
        allocator.allocateFrom("Hello");
        assertEquals(calls.get(), 1);
    }


    @Test(dataProvider = "arrayAllocations")
    public <Z> void testArray(AllocationFactory allocationFactory, ValueLayout layout, AllocationFunction<Object, ValueLayout> allocationFunction, ToArrayHelper<Z> arrayHelper) {
        Z arr = arrayHelper.array();
        Arena[] arenas = {
                Arena.ofConfined(),
                Arena.ofShared()
        };
        for (Arena arena : arenas) {
            try (arena) {
                SegmentAllocator allocator = allocationFactory.allocator(100, arena);
                MemorySegment address = allocationFunction.allocate(allocator, layout, arr);
                Z found = arrayHelper.toArray(address, layout);
                assertEquals(found, arr);
            }
        }
    }

    @Test(dataProvider = "arrayAllocations")
    public <Z> void testPredicatesAndCommands(AllocationFactory allocationFactory, ValueLayout layout, AllocationFunction<Object, ValueLayout> allocationFunction, ToArrayHelper<Z> arrayHelper) {
        Z arr = arrayHelper.array();
        Arena[] arenas = {
                Arena.ofConfined(),
                Arena.ofShared()
        };
        for (Arena arena : arenas) {
            try (arena) {
                SegmentAllocator allocator = allocationFactory.allocator(100, arena);
                MemorySegment segment = allocationFunction.allocate(allocator, layout, arr);
                assertThrows(UnsupportedOperationException.class, segment::load);
                assertThrows(UnsupportedOperationException.class, segment::unload);
                assertThrows(UnsupportedOperationException.class, segment::isLoaded);
                assertThrows(UnsupportedOperationException.class, segment::force);
                assertFalse(segment.isMapped());
                assertEquals(segment.isNative(), segment instanceof NativeMemorySegmentImpl);
            }
        }
    }

    @DataProvider(name = "scalarAllocations")
    static Object[][] scalarAllocations() {
        List<Object[]> scalarAllocations = new ArrayList<>();
        for (AllocationFactory factory : AllocationFactory.values()) {
            scalarAllocations.add(new Object[] { (byte)42, factory, ValueLayout.JAVA_BYTE,
                    (AllocationFunction.OfByte) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { (short)42, factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfShort) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { (char)42, factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfChar) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42, factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfInt) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42f, factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfFloat) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42L, factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfLong) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42d, factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfDouble) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { MemorySegment.ofAddress(42), factory, ValueLayout.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfAddress) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });

            scalarAllocations.add(new Object[] { (short)42, factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfShort) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { (char)42, factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfChar) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42, factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfInt) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42f, factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfFloat) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42L, factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfLong) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { 42d, factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfDouble) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
            scalarAllocations.add(new Object[] { MemorySegment.ofAddress(42), factory, ValueLayout.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfAddress) SegmentAllocator::allocateFrom,
                    (Function<MemoryLayout, VarHandle>)l -> l.varHandle() });
        }
        return scalarAllocations.toArray(Object[][]::new);
    }

    @DataProvider(name = "arrayAllocations")
    static Object[][] arrayAllocations() {
        List<Object[]> arrayAllocations = new ArrayList<>();
        for (AllocationFactory factory : AllocationFactory.values()) {
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_BYTE,
                    (AllocationFunction.OfByteArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toByteArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfCharArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toCharArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfShortArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toShortArray });
            arrayAllocations.add(new Object[] { factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfIntArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toIntArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfFloatArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toFloatArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfLongArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toLongArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN),
                    (AllocationFunction.OfDoubleArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toDoubleArray });

            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfCharArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toCharArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfShortArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toShortArray });
            arrayAllocations.add(new Object[] { factory,
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfIntArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toIntArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfFloatArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toFloatArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfLongArray) SegmentAllocator::allocateFrom,
                    ToArrayHelper.toLongArray });
            arrayAllocations.add(new Object[] { factory, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN),
                    (AllocationFunction.OfDoubleArray) SegmentAllocator::allocateFrom,
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
        interface OfAddress extends AllocationFunction<MemorySegment, AddressLayout> { }

        interface OfByteArray extends AllocationFunction<byte[], ValueLayout.OfByte> { }
        interface OfCharArray extends AllocationFunction<char[], ValueLayout.OfChar> { }
        interface OfShortArray extends AllocationFunction<short[], ValueLayout.OfShort> { }
        interface OfIntArray extends AllocationFunction<int[], ValueLayout.OfInt> { }
        interface OfFloatArray extends AllocationFunction<float[], ValueLayout.OfFloat> { }
        interface OfLongArray extends AllocationFunction<long[], ValueLayout.OfLong> { }
        interface OfDoubleArray extends AllocationFunction<double[], ValueLayout.OfDouble> { }
    }

    enum AllocationFactory {
        SLICING(true, (size, arena) -> {
            return SegmentAllocator.slicingAllocator(arena.allocate(size, 1));
        });

        private final boolean isBound;
        private final BiFunction<Long, Arena, SegmentAllocator> factory;

        AllocationFactory(boolean isBound, BiFunction<Long, Arena, SegmentAllocator> factory) {
            this.isBound = isBound;
            this.factory = factory;
        }

        SegmentAllocator allocator(long size, Arena arena) {
            return factory.apply(size, arena);
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
    }

    @DataProvider(name = "allocators")
    static Object[][] allocators() {
        return new Object[][] {
                { SegmentAllocator.prefixAllocator(Arena.global().allocate(10, 1)) },
        };
    }
}
