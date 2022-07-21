/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.ValueLayout;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.*;

public class TestSegments {

    @Test(dataProvider = "badSizeAndAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocateAlign(long size, long align) {
        MemorySegment.allocateNative(size, align, MemorySession.openImplicit());
    }

    @Test
    public void testZeroLengthNativeSegment() {
        try (MemorySession session = MemorySession.openConfined()) {
            var segment = MemorySegment.allocateNative(0, session);
            assertEquals(segment.byteSize(), 0);
            MemoryLayout seq = MemoryLayout.sequenceLayout(0, JAVA_INT);
            segment = MemorySegment.allocateNative(seq, session);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.address().toRawLongValue() % seq.byteAlignment(), 0);
            segment = MemorySegment.allocateNative(0, 4, session);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.address().toRawLongValue() % 4, 0);
            segment = MemorySegment.ofAddress(segment.address(), 0, session);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.address().toRawLongValue() % 4, 0);
        }
    }

    @Test(expectedExceptions = { OutOfMemoryError.class,
                                 IllegalArgumentException.class })
    public void testAllocateTooBig() {
        MemorySegment.allocateNative(Long.MAX_VALUE, MemorySession.openImplicit());
    }

    @Test(expectedExceptions = OutOfMemoryError.class)
    public void testNativeAllocationTooBig() {
        MemorySegment segment = MemorySegment.allocateNative(1024 * 1024 * 8 * 2, MemorySession.openImplicit()); // 2M
    }

    @Test
    public void testNativeSegmentIsZeroed() {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.arrayElementVarHandle();
        try (MemorySession session = MemorySession.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(1000, 1, session);
            for (long i = 0 ; i < segment.byteSize() ; i++) {
                assertEquals(0, (byte)byteHandle.get(segment, i));
            }
        }
    }

    @Test
    public void testSlices() {
        VarHandle byteHandle = ValueLayout.JAVA_BYTE.arrayElementVarHandle();
        try (MemorySession session = MemorySession.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(10, 1, session);
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
        try (MemorySession session = MemorySession.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(100, session);
            assertEquals(segment, segment.asReadOnly());
            assertEquals(segment, segment.asSlice(0, 100));
            assertNotEquals(segment, segment.asSlice(10, 90));
            assertNotEquals(segment, segment.asSlice(0, 90));
            assertEquals(segment, MemorySegment.ofAddress(segment.address(), 100, session.asNonCloseable()));
            assertNotEquals(segment, MemorySegment.ofAddress(segment.address(), 100, MemorySession.global()));
            MemorySegment segment2 = MemorySegment.allocateNative(100, session);
            assertNotEquals(segment, segment2);
        }
    }

    @Test
    public void testEqualsOnHeap() {
        MemorySegment segment = MemorySegment.ofArray(new byte[100]);
        assertEquals(segment, segment.asReadOnly());
        assertEquals(segment, segment.asSlice(0, 100));
        assertNotEquals(segment, segment.asSlice(10, 90));
        assertNotEquals(segment, segment.asSlice(0, 90));
        MemorySegment segment2 = MemorySegment.ofArray(new byte[100]);
        assertNotEquals(segment, segment2);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSmallSegmentMax() {
        long offset = (long)Integer.MAX_VALUE + (long)Integer.MAX_VALUE + 2L + 6L; // overflows to 6 when casted to int
        MemorySegment memorySegment = MemorySegment.allocateNative(10, MemorySession.openImplicit());
        memorySegment.get(JAVA_INT, offset);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSmallSegmentMin() {
        long offset = ((long)Integer.MIN_VALUE * 2L) + 6L; // underflows to 6 when casted to int
        MemorySegment memorySegment = MemorySegment.allocateNative(10, MemorySession.openImplicit());
        memorySegment.get(JAVA_INT, offset);
    }

    @Test
    public void testSegmentOOBMessage() {
        try {
            var segment = MemorySegment.allocateNative(10, MemorySession.global());
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
        tryClose(segment);
    }

    static void tryClose(MemorySegment segment) {
        if (segment.session().isCloseable()) {
            segment.session().close();
        }
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
                () -> MemorySegment.allocateNative(4, MemorySession.openImplicit()),
                () -> MemorySegment.allocateNative(4, 8, MemorySession.openImplicit()),
                () -> MemorySegment.allocateNative(JAVA_INT, MemorySession.openImplicit()),
                () -> MemorySegment.allocateNative(4, MemorySession.openImplicit()),
                () -> MemorySegment.allocateNative(4, 8, MemorySession.openImplicit()),
                () -> MemorySegment.allocateNative(JAVA_INT, MemorySession.openImplicit())

        );
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    static class SegmentFactory {
        final MemorySession session;
        final Function<MemorySession, MemorySegment> segmentFunc;

        SegmentFactory(MemorySession session, Function<MemorySession, MemorySegment> segmentFunc) {
            this.session = session;
            this.segmentFunc = segmentFunc;
        }

        public void tryClose() {
            if (session.isCloseable()) {
                session.close();
            }
        }

        public MemorySegment segment() {
            return segmentFunc.apply(session);
        }

        static SegmentFactory ofArray(Supplier<MemorySegment> segmentSupplier) {
            return new SegmentFactory(MemorySession.global(), (_ignored) -> segmentSupplier.get());
        }

        static SegmentFactory ofImplicitSession(Function<MemorySession, MemorySegment> segmentFunc) {
            return new SegmentFactory(MemorySession.openImplicit(), segmentFunc);
        }
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
            tryClose(segment);
        }
    }

    @Test(dataProvider = "segmentFactories")
    public void testFillClosed(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        tryClose(segment);
        if (!segment.session().isAlive()) {
            try {
                segment.fill((byte) 0xFF);
                fail();
            } catch (IllegalStateException ex) {
                assertTrue(true);
            }
        }
    }

    @Test(dataProvider = "segmentFactories")
    public void testNativeSegments(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        try {
            segment.address();
            assertTrue(segment.isNative());
        } catch (UnsupportedOperationException exception) {
            assertFalse(segment.isNative());
        }
        tryClose(segment);
    }

    @Test(dataProvider = "segmentFactories", expectedExceptions = UnsupportedOperationException.class)
    public void testFillIllegalAccessMode(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment segment = segmentSupplier.get();
        segment.asReadOnly().fill((byte) 0xFF);
        tryClose(segment);
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

        if (segment.session().ownerThread() != null) {
            RuntimeException e = exception.get();
            if (!(e instanceof IllegalStateException)) {
                throw e;
            }
        } else {
            assertNull(exception.get());
        }
        tryClose(segment);
    }

    @Test
    public void testFillEmpty() {
        MemorySegment.ofArray(new byte[] { }).fill((byte) 0xFF);
        MemorySegment.ofArray(new byte[2]).asSlice(0, 0).fill((byte) 0xFF);
        MemorySegment.ofBuffer(ByteBuffer.allocateDirect(0)).fill((byte) 0xFF);
    }

    @Test(dataProvider = "heapFactories")
    public void testBigHeapSegments(IntFunction<MemorySegment> heapSegmentFactory, int factor) {
        int bigSize = (Integer.MAX_VALUE / factor) + 1;
        MemorySegment segment = heapSegmentFactory.apply(bigSize);
        assertTrue(segment.byteSize() > 0);
    }

    @Test
    public void testSegmentAccessorWithWrappedLifetime() {
        MemorySession session = MemorySession.openConfined();
        MemorySession publicSession = session.asNonCloseable();
        assertEquals(session, publicSession);
        MemorySegment segment = publicSession.allocate(100);
        assertThrows(UnsupportedOperationException.class, publicSession::close);
        assertThrows(UnsupportedOperationException.class, segment.session()::close);
        session.close();
        assertFalse(publicSession.isAlive());
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
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new char[size]), 2 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new short[size]), 2 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new int[size]), 4 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new float[size]), 4 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new long[size]), 8 },
                { (IntFunction<MemorySegment>) size -> MemorySegment.ofArray(new double[size]), 8 }
        };
    }
}
