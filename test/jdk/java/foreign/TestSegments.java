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
 * @requires vm.bits == 64
 * @run testng/othervm -Xmx4G -XX:MaxDirectMemorySize=1M --enable-native-access=ALL-UNNAMED TestSegments
 */

import java.lang.foreign.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.testng.Assert.*;

public class TestSegments {

    @Test(dataProvider = "badSizeAndAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocateAlign(long size, long align) {
        Arena.ofAuto().allocate(size, align);
    }

    @Test
    public void testZeroLengthNativeSegment() {
        try (Arena arena = Arena.ofConfined()) {
            var segment = arena.allocate(0, 1);
            assertEquals(segment.byteSize(), 0);
            MemoryLayout seq = MemoryLayout.sequenceLayout(0, JAVA_INT);
            segment = arena.allocate(seq);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.address() % seq.byteAlignment(), 0);
            segment = arena.allocate(0, 4);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.address() % 4, 0);
            MemorySegment rawAddress = MemorySegment.ofAddress(segment.address());
            assertEquals(rawAddress.byteSize(), 0);
            assertEquals(rawAddress.address() % 4, 0);
        }
    }

    @Test(expectedExceptions = { OutOfMemoryError.class,
                                 IllegalArgumentException.class })
    public void testAllocateTooBig() {
        Arena.ofAuto().allocate(Long.MAX_VALUE, 1);
    }

    @Test(expectedExceptions = OutOfMemoryError.class)
    public void testNativeAllocationTooBig() {
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(1024L * 1024 * 8 * 2, 1); // 2M
    }

    @Test
    public void testNativeSegmentIsZeroed() {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.varHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(1000, 1);
            for (long i = 0 ; i < segment.byteSize() ; i++) {
                assertEquals(0, (byte)byteHandle.get(segment, i));
            }
        }
    }

    @Test
    public void testSlices() {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.varHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(10, 1);
            //init
            for (byte i = 0 ; i < segment.byteSize() ; i++) {
                byteHandle.set(segment, (long)i, i);
            }
            for (int offset = 0 ; offset < 10 ; offset++) {
                MemorySegment slice = segment.asSlice(offset);
                for (long i = offset ; i < 10 ; i++) {
                    assertEquals(
                            byteHandle.get(segment, i),
                            byteHandle.get(slice, i - offset)
                    );
                }
            }
        }
    }

    @Test(dataProvider = "segmentFactories")
    public void testDerivedScopes(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        assertEquals(segment.scope(), segment.scope());
        // one level
        assertEquals(segment.asSlice(0).scope(), segment.scope());
        assertEquals(segment.asReadOnly().scope(), segment.scope());
        // two levels
        assertEquals(segment.asSlice(0).asReadOnly().scope(), segment.scope());
        assertEquals(segment.asReadOnly().asSlice(0).scope(), segment.scope());
        // check fresh every time
        MemorySegment another = segmentSupplier.get();
        assertNotEquals(segment.scope(), another.scope());
    }

    @Test
    public void testEqualsOffHeap() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(100, 1);
            assertEquals(segment, segment.asReadOnly());
            assertEquals(segment, segment.asSlice(0, 100));
            assertNotEquals(segment, segment.asSlice(10, 90));
            assertEquals(segment, segment.asSlice(0, 90));
            assertEquals(segment, MemorySegment.ofAddress(segment.address()));
            MemorySegment segment2 = arena.allocate(100, 1);
            assertNotEquals(segment, segment2);
        }
    }

    @Test
    public void testEqualsOnHeap() {
        MemorySegment segment = MemorySegment.ofArray(new byte[100]);
        assertEquals(segment, segment.asReadOnly());
        assertEquals(segment, segment.asSlice(0, 100));
        assertNotEquals(segment, segment.asSlice(10, 90));
        assertEquals(segment, segment.asSlice(0, 90));
        MemorySegment segment2 = MemorySegment.ofArray(new byte[100]);
        assertNotEquals(segment, segment2);
    }

    @Test
    public void testHashCodeOffHeap() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(100, 1);
            assertEquals(segment.hashCode(), segment.asReadOnly().hashCode());
            assertEquals(segment.hashCode(), segment.asSlice(0, 100).hashCode());
            assertEquals(segment.hashCode(), segment.asSlice(0, 90).hashCode());
            assertEquals(segment.hashCode(), MemorySegment.ofAddress(segment.address()).hashCode());
        }
    }

    @Test
    public void testHashCodeOnHeap() {
        MemorySegment segment = MemorySegment.ofArray(new byte[100]);
        assertEquals(segment.hashCode(), segment.asReadOnly().hashCode());
        assertEquals(segment.hashCode(), segment.asSlice(0, 100).hashCode());
        assertEquals(segment.hashCode(), segment.asSlice(0, 90).hashCode());
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSmallSegmentMax() {
        long offset = (long)Integer.MAX_VALUE + (long)Integer.MAX_VALUE + 2L + 6L; // overflows to 6 when cast to int
        Arena scope = Arena.ofAuto();
        MemorySegment memorySegment = scope.allocate(10, 1);
        memorySegment.get(JAVA_INT, offset);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSmallSegmentMin() {
        long offset = ((long)Integer.MIN_VALUE * 2L) + 6L; // underflows to 6 when cast to int
        Arena scope = Arena.ofAuto();
        MemorySegment memorySegment = scope.allocate(10L, 1);
        memorySegment.get(JAVA_INT, offset);
    }

    @Test
    public void testSegmentOOBMessage() {
        try {
            var segment = Arena.global().allocate(10, 1);
            segment.getAtIndex(ValueLayout.JAVA_INT, 2);
        } catch (IndexOutOfBoundsException ex) {
            assertTrue(ex.getMessage().contains("Out of bound access"));
            assertTrue(ex.getMessage().contains("offset = 8"));
            assertTrue(ex.getMessage().contains("length = 4"));
        }
    }

    @Test(dataProvider = "segmentFactories")
    public void testAccessModesOfFactories(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        assertFalse(segment.isReadOnly());
    }

    @DataProvider(name = "scopes")
    public Object[][] scopes() {
        return new Object[][] {
                { Arena.ofAuto(), false },
                { Arena.global(), false },
                { Arena.ofConfined(), true },
                { Arena.ofShared(), false }
        };
    }

    @Test(dataProvider = "scopes")
    public void testIsAccessibleBy(Arena arena, boolean isConfined) {
        MemorySegment segment = MemorySegment.NULL.reinterpret(arena, null);
        assertTrue(segment.isAccessibleBy(Thread.currentThread()));
        assertTrue(segment.isAccessibleBy(new Thread()) != isConfined);
    }

    @Test(dataProvider = "segmentFactories")
    public void testToString(Supplier<MemorySegment> segmentSupplier) {
        var segment = segmentSupplier.get();
        String s = segment.toString();
        assertTrue(s.startsWith("MemorySegment{"));
        assertTrue(s.contains("address: 0x"));
        assertTrue(s.contains("byteSize: "));
        if (segment.heapBase().isPresent()) {
            assertTrue(s.contains("heapBase: ["));
        } else {
            assertFalse(s.contains("heapBase: "));
        }
        assertFalse(s.contains("Optional"));
    }

    @DataProvider(name = "segmentFactories")
    public Object[][] segmentFactories() {
        List<Supplier<MemorySegment>> l = List.of(
                () -> MemorySegment.ofArray(new byte[] { 0x00, 0x01, 0x02, 0x03 }),
                () -> MemorySegment.ofArray(new char[] {'a', 'b', 'c', 'd' }),
                () -> MemorySegment.ofArray(new double[] { 1d, 2d, 3d, 4d} ),
                () -> MemorySegment.ofArray(new float[] { 1.0f, 2.0f, 3.0f, 4.0f }),
                () -> MemorySegment.ofArray(new int[] { 1, 2, 3, 4 }),
                () -> MemorySegment.ofArray(new long[] { 1L, 2L, 3L, 4L } ),
                () -> MemorySegment.ofArray(new short[] { 1, 2, 3, 4 } ),
                () -> Arena.ofAuto().allocate(4L, 1),
                () -> Arena.ofAuto().allocate(4L, 8),
                () -> Arena.ofAuto().allocate(JAVA_INT),
                () -> Arena.ofAuto().allocate(4L, 1),
                () -> Arena.ofAuto().allocate(4L, 8),
                () -> Arena.ofAuto().allocate(JAVA_INT)

        );
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "segmentFactories")
    public void testFill(Supplier<MemorySegment> segmentSupplier) {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.varHandle();

        for (byte value : new byte[] {(byte) 0xFF, (byte) 0x00, (byte) 0x45}) {
            MemorySegment segment = segmentSupplier.get();
            segment.fill(value);
            for (long l = 0; l < segment.byteSize(); l++) {
                assertEquals((byte) byteHandle.get(segment, l), value);
            }

            // fill a slice
            var sliceSegment = segment.asSlice(1, segment.byteSize() - 2).fill((byte) ~value);
            for (long l = 0; l < sliceSegment.byteSize(); l++) {
                assertEquals((byte) byteHandle.get(sliceSegment, l), ~value);
            }
            // assert enclosing slice
            assertEquals((byte) byteHandle.get(segment, 0L), value);
            for (long l = 1; l < segment.byteSize() - 2; l++) {
                assertEquals((byte) byteHandle.get(segment, l), (byte) ~value);
            }
            assertEquals((byte) byteHandle.get(segment, segment.byteSize() - 1L), value);
        }
    }

    @Test(dataProvider = "segmentFactories")
    public void testHeapBase(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        assertEquals(segment.isNative(), !segment.heapBase().isPresent());
        segment = segment.asReadOnly();
        assertTrue(segment.heapBase().isEmpty());
    }

    @Test
    public void testScopeConfinedArena() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(100);
            assertEquals(segment.scope(), arena.scope());
        }
    }

    @Test
    public void testScopeSharedArena() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(100);
            assertEquals(segment.scope(), arena.scope());
        }
    }

    @Test
    public void testScopeAutoArena() {
        Arena arena = Arena.ofAuto();
        MemorySegment segment = arena.allocate(100);
        assertEquals(segment.scope(), arena.scope());
    }

    @Test
    public void testScopeGlobalArena() {
        Arena arena = Arena.global();
        MemorySegment segment = arena.allocate(100);
        assertEquals(segment.scope(), arena.scope());
    }

    @Test(dataProvider = "segmentFactories", expectedExceptions = IllegalArgumentException.class)
    public void testFillIllegalAccessMode(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        segment.asReadOnly().fill((byte) 0xFF);
    }

    @Test(dataProvider = "segmentFactories", expectedExceptions = IllegalArgumentException.class)
    public void testFromStringIllegalAccessMode(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        segment.asReadOnly().setString(0, "a");
    }

    @Test(dataProvider = "segmentFactories")
    public void testFillThread(Supplier<MemorySegment> segmentSupplier) throws Exception {
        MemorySegment segment = segmentSupplier.get();
        AtomicReference<RuntimeException> exception = new AtomicReference<>();
        Runnable action = () -> {
            try {
                segment.fill((byte) 0xBA);
            } catch (RuntimeException e) {
                exception.set(e);
            }
        };
        Thread thread = new Thread(action);
        thread.start();
        thread.join();

        if (!segment.isAccessibleBy(Thread.currentThread())) {
            RuntimeException e = exception.get();
            throw e;
        } else {
            assertNull(exception.get());
        }
    }

    @Test
    public void testFillEmpty() {
        MemorySegment.ofArray(new byte[] { }).fill((byte) 0xFF);
        MemorySegment.ofArray(new byte[2]).asSlice(0, 0).fill((byte) 0xFF);
        MemorySegment.ofBuffer(ByteBuffer.allocateDirect(0)).fill((byte) 0xFF);
    }

    @Test(dataProvider = "heapFactories")
    public void testVirtualizedBaseAddress(IntFunction<MemorySegment> heapSegmentFactory, int factor) {
        MemorySegment segment = heapSegmentFactory.apply(10);
        assertEquals(segment.address(), 0); // base address should be zero (no leaking of impl details)
        MemorySegment end = segment.asSlice(segment.byteSize(), 0);
        assertEquals(end.address(), segment.byteSize()); // end address should be equal to segment byte size
    }

    @Test
    void testReinterpret() {
        AtomicInteger counter = new AtomicInteger();
        try (Arena arena = Arena.ofConfined()){
            // check size
            assertEquals(MemorySegment.ofAddress(42).reinterpret(100).byteSize(), 100);
            assertEquals(MemorySegment.ofAddress(42).reinterpret(100, Arena.ofAuto(), null).byteSize(), 100);
            // check scope and cleanup
            assertEquals(MemorySegment.ofAddress(42).reinterpret(100, arena, s -> counter.incrementAndGet()).scope(), arena.scope());
            assertEquals(MemorySegment.ofAddress(42).reinterpret(arena, s -> counter.incrementAndGet()).scope(), arena.scope());
        }
        assertEquals(counter.get(), 2);
    }

    @Test
    void testThrowInCleanup() {
        AtomicInteger counter = new AtomicInteger();
        RuntimeException thrown = null;
        Set<String> expected = new HashSet<>();
        try (Arena arena = Arena.ofConfined()) {
            for (int i = 0 ; i < 10 ; i++) {
                String msg = "exception#" + i;
                expected.add(msg);
                MemorySegment.ofAddress(42).reinterpret(arena, seg -> {
                    throw new IllegalArgumentException(msg);
                });
            }
            for (int i = 10 ; i < 20 ; i++) {
                String msg = "exception#" + i;
                expected.add(msg);
                MemorySegment.ofAddress(42).reinterpret(100, arena, seg -> {
                    throw new IllegalArgumentException(msg);
                });
            }
            MemorySegment.ofAddress(42).reinterpret(arena, seg -> counter.incrementAndGet());
        } catch (RuntimeException ex) {
            thrown = ex;
        }
        assertNotNull(thrown);
        assertEquals(counter.get(), 1);
        assertEquals(thrown.getSuppressed().length, 19);
        Throwable[] errors = new IllegalArgumentException[20];
        assertTrue(thrown instanceof IllegalArgumentException);
        errors[0] = thrown;
        for (int i = 0 ; i < 19 ; i++) {
            assertTrue(thrown.getSuppressed()[i] instanceof IllegalArgumentException);
            errors[i + 1] = thrown.getSuppressed()[i];
        }
        for (Throwable t : errors) {
            assertTrue(expected.remove(t.getMessage()));
        }
        assertTrue(expected.isEmpty());
    }

    @Test
    void testThrowInCleanupSame() {
        AtomicInteger counter = new AtomicInteger();
        Throwable thrown = null;
        IllegalArgumentException iae = new IllegalArgumentException();
        try (Arena arena = Arena.ofConfined()) {
            for (int i = 0 ; i < 10 ; i++) {
                MemorySegment.ofAddress(42).reinterpret(arena, seg -> {
                    throw iae;
                });
            }
            for (int i = 10 ; i < 20 ; i++) {
                MemorySegment.ofAddress(42).reinterpret(100, arena, seg -> {
                    throw iae;
                });
            }
            MemorySegment.ofAddress(42).reinterpret(arena, seg -> counter.incrementAndGet());
        } catch (RuntimeException ex) {
            thrown = ex;
        }
        assertEquals(thrown, iae);
        assertEquals(counter.get(), 1);
        assertEquals(thrown.getSuppressed().length, 0);
    }

    @DataProvider(name = "badSizeAndAlignments")
    public Object[][] sizesAndAlignments() {
        return new Object[][] {
                { -1, 8 },
                { 1, 15 },
                { 1, -15 }
        };
    }

    @DataProvider(name = "heapFactories")
    public Object[][] heapFactories() {
        return new Object[][] {
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new byte[size]), 1 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new char[size]), 2 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new short[size]), 2 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new int[size]), 4 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new float[size]), 4 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new long[size]), 8 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new double[size]), 8 }
        };
    }
}
