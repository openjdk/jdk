/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm -Xmx4G -XX:MaxDirectMemorySize=1M --enable-native-access=ALL-UNNAMED TestSegments
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.ValueLayout;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.*;

public class TestSegments {

    @Test(dataProvider = "badSizeAndAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocateAlign(long size, long align) {
        MemorySegment.allocateNative(size, align, SegmentScope.auto());
    }

    @Test
    public void testZeroLengthNativeSegment() {
        try (Arena arena = Arena.openConfined()) {
            SegmentScope session = arena.scope();
            var segment = MemorySegment.allocateNative(0, session);
            assertEquals(segment.byteSize(), 0);
            MemoryLayout seq = MemoryLayout.sequenceLayout(0, JAVA_INT);
            segment = MemorySegment.allocateNative(seq, session);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.address() % seq.byteAlignment(), 0);
            segment = MemorySegment.allocateNative(0, 4, session);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.address() % 4, 0);
            MemorySegment rawAddress = MemorySegment.ofAddress(segment.address(), 0, session);
            assertEquals(rawAddress.byteSize(), 0);
            assertEquals(rawAddress.address() % 4, 0);
        }
    }

    @Test(expectedExceptions = { OutOfMemoryError.class,
                                 IllegalArgumentException.class })
    public void testAllocateTooBig() {
        MemorySegment.allocateNative(Long.MAX_VALUE, SegmentScope.auto());
    }

    @Test(expectedExceptions = OutOfMemoryError.class)
    public void testNativeAllocationTooBig() {
        MemorySegment segment = MemorySegment.allocateNative(1024L * 1024 * 8 * 2, SegmentScope.auto()); // 2M
    }

    @Test
    public void testNativeSegmentIsZeroed() {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.arrayElementVarHandle();
        try (Arena arena = Arena.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(1000, 1, arena.scope());
            for (long i = 0 ; i < segment.byteSize() ; i++) {
                assertEquals(0, (byte)byteHandle.get(segment, i));
            }
        }
    }

    @Test
    public void testSlices() {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.arrayElementVarHandle();
        try (Arena arena = Arena.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(10, 1, arena.scope());
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

    @Test
    public void testEqualsOffHeap() {
        try (Arena arena = Arena.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(100, arena.scope());
            assertEquals(segment, segment.asReadOnly());
            assertEquals(segment, segment.asSlice(0, 100));
            assertNotEquals(segment, segment.asSlice(10, 90));
            assertEquals(segment, segment.asSlice(0, 90));
            assertEquals(segment, MemorySegment.ofAddress(segment.address(), 100, SegmentScope.global()));
            MemorySegment segment2 = MemorySegment.allocateNative(100, arena.scope());
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
        try (Arena arena = Arena.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(100, arena.scope());
            assertEquals(segment.hashCode(), segment.asReadOnly().hashCode());
            assertEquals(segment.hashCode(), segment.asSlice(0, 100).hashCode());
            assertEquals(segment.hashCode(), segment.asSlice(0, 90).hashCode());
            assertEquals(segment.hashCode(), MemorySegment.ofAddress(segment.address(), 100, SegmentScope.global()).hashCode());
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
        MemorySegment memorySegment = MemorySegment.allocateNative(10, SegmentScope.auto());
        memorySegment.get(JAVA_INT, offset);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSmallSegmentMin() {
        long offset = ((long)Integer.MIN_VALUE * 2L) + 6L; // underflows to 6 when cast to int
        MemorySegment memorySegment = MemorySegment.allocateNative(10L, SegmentScope.auto());
        memorySegment.get(JAVA_INT, offset);
    }

    @Test
    public void testSegmentOOBMessage() {
        try {
            var segment = MemorySegment.allocateNative(10, SegmentScope.global());
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
                { SegmentScope.auto(), false },
                { SegmentScope.global(), false },
                { Arena.openConfined().scope(), true },
                { Arena.openShared().scope(), false }
        };
    }

    @Test(dataProvider = "scopes")
    public void testIsAccessibleBy(SegmentScope scope, boolean isConfined) {
        assertTrue(scope.isAccessibleBy(Thread.currentThread()));
        assertTrue(scope.isAccessibleBy(new Thread()) != isConfined);
        MemorySegment segment = MemorySegment.ofAddress(0, 0, scope);
        assertTrue(segment.scope().isAccessibleBy(Thread.currentThread()));
        assertTrue(segment.scope().isAccessibleBy(new Thread()) != isConfined);
    }

    @DataProvider(name = "segmentFactories")
    public Object[][] segmentFactories() {
        List<Supplier<MemorySegment>> l = List.of(
                () -> MemorySegment.ofArray(new byte[] { 0x00, 0x01, 0x02, 0x03 }),
                () -> MemorySegment.ofArray(new char[] {'a', 'b', 'c', 'd' }),
                () -> MemorySegment.ofArray(new double[] { 1d, 2d, 3d, 4d} ),
                () -> MemorySegment.ofArray(new float[] { 1.0f, 2.0f, 3.0f, 4.0f }),
                () -> MemorySegment.ofArray(new int[] { 1, 2, 3, 4 }),
                () -> MemorySegment.ofArray(new long[] { 1l, 2l, 3l, 4l } ),
                () -> MemorySegment.ofArray(new short[] { 1, 2, 3, 4 } ),
                () -> MemorySegment.allocateNative(4L, SegmentScope.auto()),
                () -> MemorySegment.allocateNative(4L, 8, SegmentScope.auto()),
                () -> MemorySegment.allocateNative(JAVA_INT, SegmentScope.auto()),
                () -> MemorySegment.allocateNative(4L, SegmentScope.auto()),
                () -> MemorySegment.allocateNative(4L, 8, SegmentScope.auto()),
                () -> MemorySegment.allocateNative(JAVA_INT, SegmentScope.auto())

        );
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "segmentFactories")
    public void testFill(Supplier<MemorySegment> segmentSupplier) {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.arrayElementVarHandle();

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
    public void testNativeSegments(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        assertEquals(segment.isNative(), !segment.array().isPresent());
    }

    @Test(dataProvider = "segmentFactories", expectedExceptions = UnsupportedOperationException.class)
    public void testFillIllegalAccessMode(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        segment.asReadOnly().fill((byte) 0xFF);
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

        if (!segment.scope().isAccessibleBy(Thread.currentThread())) {
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
