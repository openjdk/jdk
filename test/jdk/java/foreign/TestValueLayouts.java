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
 * @modules java.base/jdk.internal.misc
 * @run testng TestValueLayouts
 */

import org.testng.annotations.*;

import java.lang.foreign.*;
import java.nio.ByteOrder;
import jdk.internal.misc.Unsafe;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class TestValueLayouts {

    @Test
    public void testByte() {
        testAligned(JAVA_BYTE, byte.class, Byte.BYTES);
    }

    @Test
    public void testBoolean() {
        testAligned(JAVA_BOOLEAN, boolean.class, Byte.BYTES);
    }

    @Test
    public void testShort() {
        testAligned(JAVA_SHORT, short.class, Short.BYTES);
    }

    @Test
    public void testShortUnaligned() {
        testUnaligned(JAVA_SHORT_UNALIGNED, short.class, Short.BYTES);
    }

    @Test
    public void testInt() {
        testAligned(JAVA_INT, int.class, Integer.BYTES);
    }

    @Test
    public void testIntUnaligned() {
        testUnaligned(JAVA_INT_UNALIGNED, int.class, Integer.BYTES);
    }

    @Test
    public void testLong() {
        testAligned(JAVA_LONG, long.class, Long.BYTES, Long.BYTES);
    }

    @Test
    public void testLongUnaligned() {
        testUnaligned(JAVA_LONG_UNALIGNED, long.class, Long.BYTES);
    }

    @Test
    public void testFloat() {
        testAligned(JAVA_FLOAT, float.class, Float.BYTES);
    }

    @Test
    public void testFloatUnaligned() {
        testUnaligned(JAVA_FLOAT_UNALIGNED, float.class, Float.BYTES);
    }

    @Test
    public void testDouble() {
        testAligned(JAVA_DOUBLE, double.class, Double.BYTES, Double.BYTES);
    }

    @Test
    public void testDoubleUnaligned() {
        testUnaligned(JAVA_DOUBLE_UNALIGNED, double.class, Double.BYTES);
    }

    @Test
    public void testChar() {
        testAligned(JAVA_CHAR, char.class, Character.BYTES);
    }

    @Test
    public void testCharUnaligned() {
        testUnaligned(JAVA_CHAR_UNALIGNED, char.class, Character.BYTES);
    }

    @Test
    public void testAddress() {
        testAligned(ADDRESS, MemorySegment.class, Unsafe.ADDRESS_SIZE);
    }

    @Test
    public void testAddressUnaligned() {
        testUnaligned(ADDRESS_UNALIGNED, MemorySegment.class, Unsafe.ADDRESS_SIZE);
    }

    void testAligned(ValueLayout layout,
                     Class<?> carrier,
                     long byteSize) {
        test(layout, carrier, byteSize, byteSize);
    }

    void testAligned(ValueLayout layout,
                     Class<?> carrier,
                     long byteSize,
                     long byteAlignment) {
        test(layout, carrier, byteSize, byteAlignment);
    }

    void testUnaligned(ValueLayout layout,
                       Class<?> carrier,
                       long byteSize) {
        test(layout, carrier, byteSize, Byte.BYTES);
    }

    void test(ValueLayout layout,
              Class<?> carrier,
              long byteSize,
              long byteAlignment) {
        assertEquals(layout.carrier(), carrier);
        assertEquals(layout.byteSize(), byteSize);
        assertEquals(layout.order(), ByteOrder.nativeOrder());
        assertEquals(layout.byteAlignment(), byteAlignment);
        assertTrue(layout.name().isEmpty());

    }

}
