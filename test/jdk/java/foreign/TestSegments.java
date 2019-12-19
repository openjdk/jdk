/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;

import java.awt.font.LayoutPath;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import jdk.incubator.foreign.SequenceLayout;
import org.testng.annotations.*;

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

    @Test(expectedExceptions = OutOfMemoryError.class)
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

        public SegmentMember(Method method, Object[] params) {
            this.method = method;
            this.params = params;
        }

        boolean isConfined() {
            return method.getName().startsWith("as") ||
                    method.getName().startsWith("to") ||
                    method.getName().equals("close") ||
                    method.getName().equals("slice");
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
}
