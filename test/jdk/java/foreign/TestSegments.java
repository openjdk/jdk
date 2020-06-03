/*
 *  Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestSegments
 */

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static jdk.incubator.foreign.MemorySegment.*;
import static org.testng.Assert.*;

public class TestSegments {

    @Test(dataProvider = "badSizeAndAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadAllocateAlign(long size, long align) {
        MemorySegment.allocateNative(size, align);
    }

    @Test(dataProvider = "badLayouts", expectedExceptions = UnsupportedOperationException.class)
    public void testBadAllocateLayout(MemoryLayout layout) {
        MemorySegment.allocateNative(layout);
    }

    @Test(expectedExceptions = { OutOfMemoryError.class,
                                 IllegalArgumentException.class })
    public void testAllocateTooBig() {
        MemorySegment.allocateNative(Long.MAX_VALUE);
    }

    @Test(dataProvider = "segmentOperations")
    public void testOpOutsideConfinement(SegmentMember member) throws Throwable {
        try (MemorySegment segment = MemorySegment.allocateNative(4)) {
            AtomicBoolean failed = new AtomicBoolean(false);
            Thread t = new Thread(() -> {
                try {
                    Object o = member.method.invoke(segment, member.params);
                    if (member.method.getName().equals("acquire")) {
                        ((MemorySegment)o).close();
                    }
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            t.setUncaughtExceptionHandler((thread, ex) -> failed.set(true));
            t.start();
            t.join();
            assertEquals(failed.get(), member.isConfined());
        }
    }

    @Test
    public void testNativeSegmentIsZeroed() {
        VarHandle byteHandle = MemoryLayout.ofSequence(MemoryLayouts.JAVA_BYTE)
                .varHandle(byte.class, MemoryLayout.PathElement.sequenceElement());
        try (MemorySegment segment = MemorySegment.allocateNative(1000)) {
            for (long i = 0 ; i < segment.byteSize() ; i++) {
                assertEquals(0, (byte)byteHandle.get(segment.baseAddress(), i));
            }
        }
    }

    @Test
    public void testNothingSegmentAccess() {
        VarHandle longHandle = MemoryLayouts.JAVA_LONG.varHandle(long.class);
        long[] values = { 0L, Integer.MAX_VALUE - 1, (long) Integer.MAX_VALUE + 1 };
        for (long value : values) {
            MemoryAddress addr = MemoryAddress.ofLong(value);
            try {
                longHandle.get(addr);
            } catch (UnsupportedOperationException ex) {
                assertTrue(ex.getMessage().contains("Required access mode"));
            }
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testNothingSegmentOffset() {
        MemoryAddress addr = MemoryAddress.ofLong(42);
        assertNull(addr.segment());
        addr.segmentOffset();
    }

    @Test
    public void testSlices() {
        VarHandle byteHandle = MemoryLayout.ofSequence(MemoryLayouts.JAVA_BYTE)
                .varHandle(byte.class, MemoryLayout.PathElement.sequenceElement());
        try (MemorySegment segment = MemorySegment.allocateNative(10)) {
            //init
            for (byte i = 0 ; i < segment.byteSize() ; i++) {
                byteHandle.set(segment.baseAddress(), (long)i, i);
            }
            long start = 0;
            MemoryAddress base = segment.baseAddress();
            MemoryAddress last = base.addOffset(10);
            while (!base.equals(last)) {
                MemorySegment slice = segment.asSlice(base.segmentOffset(), 10 - start);
                for (long i = start ; i < 10 ; i++) {
                    assertEquals(
                            byteHandle.get(segment.baseAddress(), i),
                            byteHandle.get(slice.baseAddress(), i - start)
                    );
                }
                base = base.addOffset(1);
                start++;
            }
        }
    }

    @Test(dataProvider = "segmentFactories")
    public void testAccessModesOfFactories(Supplier<MemorySegment> memorySegmentSupplier) {
        try (MemorySegment segment = memorySegmentSupplier.get()) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS));
            assertEquals(segment.accessModes(), ALL_ACCESS);
        }
    }

    @Test(dataProvider = "accessModes")
    public void testAccessModes(int accessModes) {
        int[] arr = new int[1];
        for (AccessActions action : AccessActions.values()) {
            MemorySegment segment = MemorySegment.ofArray(arr);
            MemorySegment restrictedSegment = segment.withAccessModes(accessModes);
            assertEquals(restrictedSegment.accessModes(), accessModes);
            boolean shouldFail = !restrictedSegment.hasAccessModes(action.accessMode);
            try {
                action.run(restrictedSegment);
                assertFalse(shouldFail);
            } catch (UnsupportedOperationException ex) {
                assertTrue(shouldFail);
            }
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
                () -> MemorySegment.allocateNative(4),
                () -> MemorySegment.allocateNative(4, 8),
                () -> MemorySegment.allocateNative(MemoryLayout.ofValueBits(32, ByteOrder.nativeOrder()))
        );
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "segmentFactories")
    public void testFill(Supplier<MemorySegment> memorySegmentSupplier) {
        VarHandle byteHandle = MemoryLayout.ofSequence(MemoryLayouts.JAVA_BYTE)
                .varHandle(byte.class, MemoryLayout.PathElement.sequenceElement());

        for (byte value : new byte[] {(byte) 0xFF, (byte) 0x00, (byte) 0x45}) {
            try (MemorySegment segment = memorySegmentSupplier.get()) {
                segment.fill(value);
                for (long l = 0; l < segment.byteSize(); l++) {
                    assertEquals((byte) byteHandle.get(segment.baseAddress(), l), value);
                }

                // fill a slice
                var sliceSegment = segment.asSlice(1, segment.byteSize() - 2).fill((byte) ~value);
                for (long l = 0; l < sliceSegment.byteSize(); l++) {
                    assertEquals((byte) byteHandle.get(sliceSegment.baseAddress(), l), ~value);
                }
                // assert enclosing slice
                assertEquals((byte) byteHandle.get(segment.baseAddress(), 0L), value);
                for (long l = 1; l < segment.byteSize() - 2; l++) {
                    assertEquals((byte) byteHandle.get(segment.baseAddress(), l), (byte) ~value);
                }
                assertEquals((byte) byteHandle.get(segment.baseAddress(), segment.byteSize() - 1L), value);
            }
        }
    }

    @Test(dataProvider = "segmentFactories", expectedExceptions = IllegalStateException.class)
    public void testFillClosed(Supplier<MemorySegment> memorySegmentSupplier) {
        MemorySegment segment = memorySegmentSupplier.get();
        segment.close();
        segment.fill((byte) 0xFF);
    }

    @Test(dataProvider = "segmentFactories", expectedExceptions = UnsupportedOperationException.class)
    public void testFillIllegalAccessMode(Supplier<MemorySegment> memorySegmentSupplier) {
        try (MemorySegment segment = memorySegmentSupplier.get()) {
            segment.withAccessModes(segment.accessModes() & ~WRITE).fill((byte) 0xFF);
        }
    }

    @Test(dataProvider = "segmentFactories")
    public void testFillThread(Supplier<MemorySegment> memorySegmentSupplier) throws Exception {
        try (MemorySegment segment = memorySegmentSupplier.get()) {
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

            RuntimeException e = exception.get();
            if (!(e instanceof IllegalStateException)) {
                throw e;
            }
        }
    }

    @Test
    public void testFillEmpty() {
        MemorySegment.ofArray(new byte[] { }).fill((byte) 0xFF);
        MemorySegment.ofArray(new byte[2]).asSlice(0, 0).fill((byte) 0xFF);
        MemorySegment.ofByteBuffer(ByteBuffer.allocateDirect(0)).fill((byte) 0xFF);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWithAccessModesBadUnsupportedMode() {
        int[] arr = new int[1];
        MemorySegment segment = MemorySegment.ofArray(arr);
        segment.withAccessModes((1 << AccessActions.values().length) + 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadWithAccessModesBadStrongerMode() {
        int[] arr = new int[1];
        MemorySegment segment = MemorySegment.ofArray(arr).withAccessModes(READ);
        segment.withAccessModes(WRITE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadHasAccessModes() {
        int[] arr = new int[1];
        MemorySegment segment = MemorySegment.ofArray(arr);
        segment.hasAccessModes((1 << AccessActions.values().length) + 1);
    }

    @DataProvider(name = "badSizeAndAlignments")
    public Object[][] sizesAndAlignments() {
        return new Object[][] {
                { -1, 8 },
                { 1, 15 },
                { 1, -15 }
        };
    }

    @DataProvider(name = "badLayouts")
    public Object[][] layouts() {
        SizedLayoutFactory[] layoutFactories = SizedLayoutFactory.values();
        Object[][] values = new Object[layoutFactories.length * 2][2];
        for (int i = 0; i < layoutFactories.length ; i++) {
            values[i * 2] = new Object[] { MemoryLayout.ofStruct(layoutFactories[i].make(7), MemoryLayout.ofPaddingBits(9)) }; // good size, bad align
            values[(i * 2) + 1] = new Object[] { layoutFactories[i].make(15).withBitAlignment(16) }; // bad size, good align
        }
        return values;
    }

    enum SizedLayoutFactory {
        VALUE_BE(size -> MemoryLayout.ofValueBits(size, ByteOrder.BIG_ENDIAN)),
        VALUE_LE(size -> MemoryLayout.ofValueBits(size, ByteOrder.LITTLE_ENDIAN)),
        PADDING(MemoryLayout::ofPaddingBits);

        private final LongFunction<MemoryLayout> factory;

        SizedLayoutFactory(LongFunction<MemoryLayout> factory) {
            this.factory = factory;
        }

        MemoryLayout make(long size) {
            return factory.apply(size);
        }
    }

    @DataProvider(name = "segmentOperations")
    static Object[][] segmentMembers() {
        List<SegmentMember> members = new ArrayList<>();
        for (Method m : MemorySegment.class.getDeclaredMethods()) {
            //skip statics and method declared in j.l.Object
            if (m.getDeclaringClass().equals(Object.class) ||
                    (m.getModifiers() & Modifier.STATIC) != 0) continue;
            Object[] args = Stream.of(m.getParameterTypes())
                    .map(TestSegments::defaultValue)
                    .toArray();
            members.add(new SegmentMember(m, args));
        }
        return members.stream().map(ms -> new Object[] { ms }).toArray(Object[][]::new);
    }

    static class SegmentMember {
        final Method method;
        final Object[] params;

        final static List<String> CONFINED_NAMES = List.of(
                "close",
                "fill",
                "copyFrom",
                "mismatch",
                "toByteArray",
                "withOwnerThread"
        );

        public SegmentMember(Method method, Object[] params) {
            this.method = method;
            this.params = params;
        }

        boolean isConfined() {
            return CONFINED_NAMES.contains(method.getName());
        }

        @Override
        public String toString() {
            return method.getName();
        }
    }

    static Object defaultValue(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == char.class) {
                return (char)0;
            } else if (c == boolean.class) {
                return false;
            } else if (c == byte.class) {
                return (byte)0;
            } else if (c == short.class) {
                return (short)0;
            } else if (c == int.class) {
                return 0;
            } else if (c == long.class) {
                return 0L;
            } else if (c == float.class) {
                return 0f;
            } else if (c == double.class) {
                return 0d;
            } else {
                throw new IllegalStateException();
            }
        } else {
            return null;
        }
    }

    @DataProvider(name = "accessModes")
    public Object[][] accessModes() {
        int nActions = AccessActions.values().length;
        Object[][] results = new Object[1 << nActions][];
        for (int accessModes = 0 ; accessModes < results.length ; accessModes++) {
            results[accessModes] = new Object[] { accessModes };
        }
        return results;
    }

    enum AccessActions {
        ACQUIRE(MemorySegment.ACQUIRE) {
            @Override
            void run(MemorySegment segment) {
                Spliterator<MemorySegment> spliterator =
                        MemorySegment.spliterator(segment, MemoryLayout.ofSequence(segment.byteSize(), MemoryLayouts.JAVA_BYTE));
                AtomicReference<RuntimeException> exception = new AtomicReference<>();
                Runnable action = () -> {
                    try {
                        spliterator.tryAdvance(s -> { });
                    } catch (RuntimeException e) {
                        exception.set(e);
                    }
                };
                Thread thread = new Thread(action);
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException ex) {
                    throw new AssertionError(ex);
                }
                RuntimeException e = exception.get();
                if (e != null) {
                    throw e;
                }
            }
        },
        CLOSE(MemorySegment.CLOSE) {
            @Override
            void run(MemorySegment segment) {
                segment.close();
            }
        },
        READ(MemorySegment.READ) {
            @Override
            void run(MemorySegment segment) {
                INT_HANDLE.get(segment.baseAddress());
            }
        },
        WRITE(MemorySegment.WRITE) {
            @Override
            void run(MemorySegment segment) {
                INT_HANDLE.set(segment.baseAddress(), 42);
            }
        },
        HANDOFF(MemorySegment.HANDOFF) {
            @Override
            void run(MemorySegment segment) {
                segment.withOwnerThread(new Thread());
            }
        };

        final int accessMode;

        static VarHandle INT_HANDLE = MemoryLayouts.JAVA_INT.varHandle(int.class);

        AccessActions(int accessMode) {
            this.accessMode = accessMode;
        }

        abstract void run(MemorySegment segment);
    }
}
