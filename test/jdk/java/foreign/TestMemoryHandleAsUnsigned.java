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

import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.*;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.testng.Assert.*;

/*
 * @test
 * @run testng TestMemoryHandleAsUnsigned
 */

public class TestMemoryHandleAsUnsigned {

    @DataProvider(name = "unsignedIntToByteData")
    public Object[][] unsignedIntToByteData() {
        return IntStream.range(0, 256)
                .mapToObj(v -> new Object[] { v }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "unsignedIntToByteData")
    public void testUnsignedIntToByte(int intValue) {
        byte byteValue = (byte) (intValue & 0xFF);

        MemoryLayout layout = ValueLayout.JAVA_BYTE;
        VarHandle byteHandle = layout.varHandle();
        VarHandle intHandle = MemoryHandles.asUnsigned(byteHandle, int.class);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(layout, scope);
            intHandle.set(segment, intValue);
            int expectedIntValue = Byte.toUnsignedInt(byteValue);
            assertEquals((int) intHandle.get(segment), expectedIntValue);
            assertEquals((byte) byteHandle.get(segment), byteValue);
        }
    }

    @DataProvider(name = "unsignedLongToByteData")
    public Object[][] unsignedLongToByteData() {
        return LongStream.range(0L, 256L)
                .mapToObj(v -> new Object[] { v }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "unsignedLongToByteData")
    public void testUnsignedLongToByte(long longValue) {
        byte byteValue = (byte) (longValue & 0xFFL);

        MemoryLayout layout = ValueLayout.JAVA_BYTE;
        VarHandle byteHandle = layout.varHandle();
        VarHandle longHandle = MemoryHandles.asUnsigned(byteHandle, long.class);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(layout, scope);
            longHandle.set(segment, longValue);
            long expectedLongValue = Byte.toUnsignedLong(byteValue);
            assertEquals((long) longHandle.get(segment), expectedLongValue);
            assertEquals((byte) byteHandle.get(segment), byteValue);
        }
    }

    @DataProvider(name = "unsignedIntToShortData")
    public Object[][] unsignedIntToShortData() {
        return IntStream.range(0, 65_536).filter(i -> i % 99 == 0)
                .mapToObj(v -> new Object[] { v }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "unsignedIntToShortData")
    public void testUnsignedIntToShort(int intValue) {
        short shortValue = (short) (intValue & 0xFFFF);

        MemoryLayout layout = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN);
        VarHandle shortHandle = layout.varHandle();
        VarHandle intHandle = MemoryHandles.asUnsigned(shortHandle, int.class);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(layout, scope);
            intHandle.set(segment, intValue);
            int expectedIntValue = Short.toUnsignedInt(shortValue);
            assertEquals((int) intHandle.get(segment), expectedIntValue);
            assertEquals((short) shortHandle.get(segment), shortValue);
        }
    }

    @DataProvider(name = "unsignedLongToShortData")
    public Object[][] unsignedLongToShortData() {
        return LongStream.range(0L, 65_536L).filter(i -> i % 99 == 0)
                .mapToObj(v -> new Object[] { v }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "unsignedLongToShortData")
    public void testUnsignedLongToShort(long longValue) {
        short shortValue = (short) (longValue & 0xFFFFL);

        MemoryLayout layout = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN);
        VarHandle shortHandle = layout.varHandle();
        VarHandle longHandle = MemoryHandles.asUnsigned(shortHandle, long.class);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(layout, scope);
            longHandle.set(segment, longValue);
            long expectedLongValue = Short.toUnsignedLong(shortValue);
            assertEquals((long) longHandle.get(segment), expectedLongValue);
            assertEquals((short) shortHandle.get(segment), shortValue);
        }
    }

    @DataProvider(name = "unsignedLongToIntData")
    public Object[][] unsignedLongToIntData() {
        // some boundary values
        long[] l = new long[] { Long.MAX_VALUE, Long.MIN_VALUE,
                Short.MAX_VALUE - 1L, Short.MAX_VALUE, Short.MAX_VALUE + 1L,
                Short.MIN_VALUE - 1L, Short.MIN_VALUE, Short.MIN_VALUE + 1L, };
        return LongStream.concat(LongStream.range(-256L, 256L), Arrays.stream(l))
                .mapToObj(v -> new Object[] { v }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "unsignedLongToIntData")
    public void testUnsignedLongToInt(long longValue) {
        int intValue = (int) (longValue & 0xFFFF_FFFFL);

        MemoryLayout layout = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
        VarHandle intHandle = layout.varHandle();
        VarHandle longHandle = MemoryHandles.asUnsigned(intHandle, long.class);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(layout, scope);
            longHandle.set(segment, longValue);
            long expectedLongValue = Integer.toUnsignedLong(intValue);
            assertEquals((long) longHandle.get(segment), expectedLongValue);
            assertEquals((int) intHandle.get(segment), intValue);
        }
    }

    @Test
    public void testCoordinatesSequenceLayout() {
        MemoryLayout layout = MemoryLayout.sequenceLayout(2, ValueLayout.JAVA_BYTE);
        VarHandle byteHandle = layout.varHandle(PathElement.sequenceElement());
        VarHandle intHandle = MemoryHandles.asUnsigned(byteHandle, int.class);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(layout, scope);
            intHandle.set(segment, 0L, (int) -1);
            assertEquals((int) intHandle.get(segment, 0L), 255);
            intHandle.set(segment, 1L, (int) 200);
            assertEquals((int) intHandle.get(segment, 1L), 200);
        }
    }

    @Test
    public void testCoordinatesStride() {
        byte[] arr = { 0, 0, (byte) 129, 0 };
        MemorySegment segment = MemorySegment.ofArray(arr);

        {
            VarHandle byteHandle = MemoryLayout.sequenceLayout(ValueLayout.JAVA_BYTE)
                    .varHandle(PathElement.sequenceElement());
            VarHandle intHandle = MemoryHandles.asUnsigned(byteHandle, int.class);
            assertEquals((int) intHandle.get(segment, 2L), 129);
        }
        {
            VarHandle byteHandle = MemoryLayout.sequenceLayout(ValueLayout.JAVA_BYTE)
                    .varHandle(PathElement.sequenceElement());
            VarHandle intHandle = MemoryHandles.asUnsigned(byteHandle, int.class);
            assertEquals((int) intHandle.get(segment, 2L), 129);
        }
    }

    static final Class<NullPointerException> NPE = NullPointerException.class;

    @Test
    public void testNull() {
        VarHandle handle = MemoryHandles.varHandle(ValueLayout.JAVA_BYTE.withOrder(BIG_ENDIAN));
        assertThrows(NPE, () -> MemoryHandles.asUnsigned(handle, null));
        assertThrows(NPE, () -> MemoryHandles.asUnsigned(null, short.class));
        assertThrows(NPE, () -> MemoryHandles.asUnsigned(null, null));
    }

    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    static void assertIllegalArgumentExceptionIllegalCarrier(ValueLayout layout, Class<?> adaptedType) {
        var vh = MemoryHandles.varHandle(layout.withOrder(BIG_ENDIAN));
        var exception = expectThrows(IAE, () -> MemoryHandles.asUnsigned(vh, adaptedType));
        var msg = exception.getMessage();
        assertTrue(msg.contains("illegal carrier"), "Expected \"illegal carrier\" in:[" + msg +"]");
    }

    static void assertIllegalArgumentExceptionIllegalAdapter(ValueLayout layout, Class<?> adaptedType) {
        var vh = MemoryHandles.varHandle(layout.withOrder(BIG_ENDIAN));
        var exception = expectThrows(IAE, () -> MemoryHandles.asUnsigned(vh, adaptedType));
        var msg = exception.getMessage();
        assertTrue(msg.contains("illegal adapter type"), "Expected \"illegal adapter type\" in:[" + msg +"]");
    }

    static void assertIllegalArgumentExceptionIsNotWiderThan(ValueLayout layout, Class<?> adaptedType) {
        var vh = MemoryHandles.varHandle(layout.withOrder(BIG_ENDIAN));
        var exception = expectThrows(IAE, () -> MemoryHandles.asUnsigned(vh, adaptedType));
        var msg = exception.getMessage();
        assertTrue(msg.contains("is not wider than"), "Expected \"is not wider than\" in:[" + msg +"]");
    }

    @Test
    public void testIllegalArgumentException() {
        assertIllegalArgumentExceptionIllegalCarrier(ValueLayout.JAVA_CHAR,   long.class);
        assertIllegalArgumentExceptionIllegalCarrier(ValueLayout.JAVA_DOUBLE, long.class);
        assertIllegalArgumentExceptionIllegalCarrier(ValueLayout.JAVA_FLOAT,  long.class);
        assertIllegalArgumentExceptionIllegalCarrier(ValueLayout.JAVA_LONG,   long.class);

        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, void.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, byte.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, short.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, char.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, double.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, float.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, Object.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, Integer.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, Long.class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, long[].class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, int[].class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, Integer[].class);
        assertIllegalArgumentExceptionIllegalAdapter(ValueLayout.JAVA_BYTE, Long[].class);

        assertIllegalArgumentExceptionIsNotWiderThan(ValueLayout.JAVA_INT, int.class);
    }
}
