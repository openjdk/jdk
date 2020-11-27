/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm TestNativeScope
 */

import jdk.incubator.foreign.*;

import org.testng.annotations.*;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static jdk.incubator.foreign.MemorySegment.CLOSE;
import static jdk.incubator.foreign.MemorySegment.HANDOFF;
import static org.testng.Assert.*;

public class TestNativeScope {

    final static int ELEMS = 128;
    final static Class<?> ADDRESS_CARRIER = MemoryLayouts.ADDRESS.bitSize() == 64 ? long.class : int.class;

    @Test(dataProvider = "nativeScopes")
    public <Z> void testAllocation(Z value, ScopeFactory scopeFactory, ValueLayout layout, AllocationFunction<Z> allocationFunction, Function<MemoryLayout, VarHandle> handleFactory) {
        ValueLayout[] layouts = {
                layout,
                layout.withBitAlignment(layout.bitAlignment() * 2),
                layout.withBitAlignment(layout.bitAlignment() * 4),
                layout.withBitAlignment(layout.bitAlignment() * 8)
        };
        for (ValueLayout alignedLayout : layouts) {
            List<MemorySegment> addressList = new ArrayList<>();
            int elems = ELEMS / ((int)alignedLayout.byteAlignment() / (int)layout.byteAlignment());
            try (NativeScope scope = scopeFactory.make((int)alignedLayout.byteSize() * ELEMS)) {
                for (int i = 0 ; i < elems ; i++) {
                    MemorySegment address = allocationFunction.allocate(scope, alignedLayout, value);
                    assertEquals(address.byteSize(), alignedLayout.byteSize());
                    addressList.add(address);
                    VarHandle handle = handleFactory.apply(alignedLayout);
                    assertEquals(value, handle.get(address));
                    try {
                        address.close();
                        fail();
                    } catch (UnsupportedOperationException uoe) {
                        //failure is expected
                        assertTrue(true);
                    }
                }
                boolean isBound = scope.byteSize().isPresent();
                try {
                    allocationFunction.allocate(scope, alignedLayout, value); //too much, should fail if bound
                    assertFalse(isBound);
                } catch (OutOfMemoryError ex) {
                    //failure is expected if bound
                    assertTrue(isBound);
                }
            }
            // addresses should be invalid now
            for (MemorySegment address : addressList) {
                assertFalse(address.isAlive());
            }
        }
    }

    static final int SIZE_256M = 1024 * 1024 * 256;

    @Test
    public void testBigAllocationInUnboundedScope() {
        try (NativeScope scope = NativeScope.unboundedScope()) {
            for (int i = 8 ; i < SIZE_256M ; i *= 8) {
                MemorySegment address = scope.allocate(i);
                //check size
                assertEquals(address.byteSize(), i);
                //check alignment
                assertTrue(address.address().toRawLongValue() % i == 0);
            }
        }
    }

    @Test
    public void testAttachClose() {
        MemorySegment s1 = MemorySegment.ofArray(new byte[1]);
        MemorySegment s2 = MemorySegment.ofArray(new byte[1]);
        MemorySegment s3 = MemorySegment.ofArray(new byte[1]);
        assertTrue(s1.isAlive());
        assertTrue(s2.isAlive());
        assertTrue(s3.isAlive());
        try (NativeScope scope = NativeScope.boundedScope(10)) {
            MemorySegment ss1 = s1.handoff(scope);
            assertFalse(s1.isAlive());
            assertTrue(ss1.isAlive());
            s1 = ss1;
            MemorySegment ss2 = s2.handoff(scope);
            assertFalse(s2.isAlive());
            assertTrue(ss2.isAlive());
            s2 = ss2;
            MemorySegment ss3 = s3.handoff(scope);
            assertFalse(s3.isAlive());
            assertTrue(ss3.isAlive());
            s3 = ss3;
        }
        assertFalse(s1.isAlive());
        assertFalse(s2.isAlive());
        assertFalse(s3.isAlive());
    }

    @Test
    public void testNoTerminalOps() {
        try (NativeScope scope = NativeScope.boundedScope(10)) {
            MemorySegment s1 = MemorySegment.ofArray(new byte[1]);
            MemorySegment attached = s1.handoff(scope);
            int[] terminalOps = {CLOSE, HANDOFF};
            for (int mode : terminalOps) {
                if (attached.hasAccessModes(mode)) {
                    fail();
                }
            }
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testNoReattach() {
        MemorySegment s1 = MemorySegment.ofArray(new byte[1]);
        NativeScope scope1 = NativeScope.boundedScope(10);
        NativeScope scope2 = NativeScope.boundedScope(10);
        s1.handoff(scope1).handoff(scope2);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testNotAliveClaim() {
        MemorySegment segment = MemorySegment.ofArray(new byte[1]);
        segment.close();
        segment.handoff(NativeScope.boundedScope(10));
    }

    @Test
    public void testRegisterFromUnconfined() {
        MemorySegment unconfined = MemorySegment.allocateNative(10).share();
        NativeScope scope = NativeScope.boundedScope(10);
        MemorySegment registered = unconfined.handoff(scope);
        assertFalse(unconfined.isAlive());
        assertEquals(registered.ownerThread(), scope.ownerThread());
        scope.close();
        assertFalse(registered.isAlive());
    }

    @Test(dataProvider = "arrayScopes")
    public <Z> void testArray(ScopeFactory scopeFactory, ValueLayout layout, AllocationFunction<Object> allocationFunction, ToArrayHelper<Z> arrayHelper) {
        Z arr = arrayHelper.array();
        try (NativeScope scope = scopeFactory.make(100)) {
            MemorySegment address = allocationFunction.allocate(scope, layout, arr);
            Z found = arrayHelper.toArray(address, layout);
            assertEquals(found, arr);
        }
    }

    @DataProvider(name = "nativeScopes")
    static Object[][] nativeScopes() {
        return new Object[][] {
                { (byte)42, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_8_BE,
                        (AllocationFunction<Byte>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(byte.class) },
                { (short)42, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_16_BE,
                        (AllocationFunction<Short>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(short.class) },
                { (char)42, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_16_BE,
                        (AllocationFunction<Character>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(char.class) },
                { 42, (ScopeFactory) NativeScope::boundedScope,
                        MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<Integer>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(int.class) },
                { 42f, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<Float>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(float.class) },
                { 42L, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<Long>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(long.class) },
                { 42d, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<Double>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(double.class) },
                { MemoryAddress.ofLong(42), (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                        (AllocationFunction<MemoryAddress>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> MemoryHandles.asAddressVarHandle(l.varHandle(ADDRESS_CARRIER)) },

                { (byte)42, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_8_LE,
                        (AllocationFunction<Byte>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(byte.class) },
                { (short)42, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_16_LE,
                        (AllocationFunction<Short>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(short.class) },
                { (char)42, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_16_LE,
                        (AllocationFunction<Character>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(char.class) },
                { 42, (ScopeFactory) NativeScope::boundedScope,
                        MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<Integer>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(int.class) },
                { 42f, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<Float>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(float.class) },
                { 42L, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<Long>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(long.class) },
                { 42d, (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<Double>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(double.class) },
                { MemoryAddress.ofLong(42), (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.ADDRESS.withOrder(ByteOrder.LITTLE_ENDIAN),
                        (AllocationFunction<MemoryAddress>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> MemoryHandles.asAddressVarHandle(l.varHandle(ADDRESS_CARRIER)) },

                { (byte)42, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_8_BE,
                        (AllocationFunction<Byte>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(byte.class) },
                { (short)42, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_16_BE,
                        (AllocationFunction<Short>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(short.class) },
                { (char)42, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_16_BE,
                        (AllocationFunction<Character>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(char.class) },
                { 42, (ScopeFactory)size -> NativeScope.unboundedScope(),
                        MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<Integer>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(int.class) },
                { 42f, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<Float>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(float.class) },
                { 42L, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<Long>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(long.class) },
                { 42d, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<Double>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(double.class) },
                { MemoryAddress.ofLong(42), (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                        (AllocationFunction<MemoryAddress>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> MemoryHandles.asAddressVarHandle(l.varHandle(ADDRESS_CARRIER)) },

                { (byte)42, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_8_LE,
                        (AllocationFunction<Byte>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(byte.class) },
                { (short)42, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_16_LE,
                        (AllocationFunction<Short>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(short.class) },
                { (char)42, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_16_LE,
                        (AllocationFunction<Character>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(char.class) },
                { 42, (ScopeFactory)size -> NativeScope.unboundedScope(),
                        MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<Integer>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(int.class) },
                { 42f, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<Float>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(float.class) },
                { 42L, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<Long>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(long.class) },
                { 42d, (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<Double>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> l.varHandle(double.class) },
                { MemoryAddress.ofLong(42), (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.ADDRESS.withOrder(ByteOrder.LITTLE_ENDIAN),
                        (AllocationFunction<MemoryAddress>) NativeScope::allocate,
                        (Function<MemoryLayout, VarHandle>)l -> MemoryHandles.asAddressVarHandle(l.varHandle(ADDRESS_CARRIER)) },
        };
    }

    @DataProvider(name = "arrayScopes")
    static Object[][] arrayScopes() {
        return new Object[][] {
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_8_LE,
                        (AllocationFunction<byte[]>) NativeScope::allocateArray,
                        ToArrayHelper.toByteArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_16_LE,
                        (AllocationFunction<short[]>) NativeScope::allocateArray,
                        ToArrayHelper.toShortArray },
                { (ScopeFactory) NativeScope::boundedScope,
                        MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<int[]>) NativeScope::allocateArray,
                        ToArrayHelper.toIntArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<float[]>) NativeScope::allocateArray,
                        ToArrayHelper.toFloatArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<long[]>) NativeScope::allocateArray,
                        ToArrayHelper.toLongArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<double[]>) NativeScope::allocateArray,
                        ToArrayHelper.toDoubleArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.ADDRESS.withOrder(ByteOrder.LITTLE_ENDIAN),
                        (AllocationFunction<MemoryAddress[]>) NativeScope::allocateArray,
                        ToArrayHelper.toAddressArray },


                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_8_BE,
                        (AllocationFunction<byte[]>) NativeScope::allocateArray,
                        ToArrayHelper.toByteArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_16_BE,
                        (AllocationFunction<short[]>) NativeScope::allocateArray,
                        ToArrayHelper.toShortArray },
                { (ScopeFactory) NativeScope::boundedScope,
                        MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<int[]>) NativeScope::allocateArray,
                        ToArrayHelper.toIntArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<float[]>) NativeScope::allocateArray,
                        ToArrayHelper.toFloatArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<long[]>) NativeScope::allocateArray,
                        ToArrayHelper.toLongArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<double[]>) NativeScope::allocateArray,
                        ToArrayHelper.toDoubleArray },
                { (ScopeFactory) NativeScope::boundedScope, MemoryLayouts.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                        (AllocationFunction<MemoryAddress[]>) NativeScope::allocateArray,
                        ToArrayHelper.toAddressArray },

                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_8_LE,
                        (AllocationFunction<byte[]>) NativeScope::allocateArray,
                        ToArrayHelper.toByteArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_16_LE,
                        (AllocationFunction<short[]>) NativeScope::allocateArray,
                        ToArrayHelper.toShortArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(),
                        MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<int[]>) NativeScope::allocateArray,
                        ToArrayHelper.toIntArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_32_LE,
                        (AllocationFunction<float[]>) NativeScope::allocateArray,
                        ToArrayHelper.toFloatArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<long[]>) NativeScope::allocateArray,
                        ToArrayHelper.toLongArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_LE,
                        (AllocationFunction<double[]>) NativeScope::allocateArray,
                        ToArrayHelper.toDoubleArray },
                { (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.ADDRESS.withOrder(ByteOrder.LITTLE_ENDIAN),
                        (AllocationFunction<MemoryAddress[]>) NativeScope::allocateArray,
                        ToArrayHelper.toAddressArray },


                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_8_BE,
                        (AllocationFunction<byte[]>) NativeScope::allocateArray,
                        ToArrayHelper.toByteArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_16_BE,
                        (AllocationFunction<short[]>) NativeScope::allocateArray,
                        ToArrayHelper.toShortArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(),
                        MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<int[]>) NativeScope::allocateArray,
                        ToArrayHelper.toIntArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_32_BE,
                        (AllocationFunction<float[]>) NativeScope::allocateArray,
                        ToArrayHelper.toFloatArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<long[]>) NativeScope::allocateArray,
                        ToArrayHelper.toLongArray },
                { (ScopeFactory) size -> NativeScope.unboundedScope(), MemoryLayouts.BITS_64_BE,
                        (AllocationFunction<double[]>) NativeScope::allocateArray,
                        ToArrayHelper.toDoubleArray },
                { (ScopeFactory)size -> NativeScope.unboundedScope(), MemoryLayouts.ADDRESS.withOrder(ByteOrder.BIG_ENDIAN),
                        (AllocationFunction<MemoryAddress[]>) NativeScope::allocateArray,
                        ToArrayHelper.toAddressArray },
        };
    }

    interface AllocationFunction<X> {
        MemorySegment allocate(NativeScope scope, ValueLayout layout, X value);
    }

    interface ScopeFactory {
        NativeScope make(int size);
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
                return switch ((int)MemoryLayouts.ADDRESS.byteSize()) {
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
}
