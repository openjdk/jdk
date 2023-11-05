/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm MemoryLayoutTypeRetentionTest
 */

import org.testng.annotations.*;

import java.lang.foreign.*;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class MemoryLayoutTypeRetentionTest {

    // These tests check both compile-time and runtime properties.
    // withName() et al. should return the same type as the original object.

    private static final String NAME = "a";
    private static final long BYTE_ALIGNMENT = Byte.BYTES;
    private static final ByteOrder BYTE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
            ? ByteOrder.LITTLE_ENDIAN
            : ByteOrder.BIG_ENDIAN;

    @Test
    public void testOfBoolean() {
        OfBoolean v = JAVA_BOOLEAN
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testOfByte() {
        OfByte v = JAVA_BYTE
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testOfShort() {
        OfShort v = JAVA_SHORT
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testOfInt() {
        OfInt v = JAVA_INT
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testOfChar() {
        OfChar v = JAVA_CHAR
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testOfLong() {
        OfLong v = JAVA_LONG
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testOfFloat() {
        OfFloat v = JAVA_FLOAT
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testOfDouble() {
        OfDouble v = JAVA_DOUBLE
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testValueLayout() {
        ValueLayout v = ((ValueLayout) JAVA_INT)
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withOrder(BYTE_ORDER);
        check(v);
    }

    @Test
    public void testAddressLayout() {
        AddressLayout v = ADDRESS
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME)
                .withoutTargetLayout()
                .withOrder(BYTE_ORDER);
        check(v);
        assertEquals(v.order(), BYTE_ORDER);

        assertFalse(v.targetLayout().isPresent());
        AddressLayout v2 = v.withTargetLayout(JAVA_INT);
        assertTrue(v2.targetLayout().isPresent());
        assertEquals(v2.targetLayout().get(), JAVA_INT);
        assertTrue(v2.withoutTargetLayout().targetLayout().isEmpty());
    }

    @Test
    public void testPaddingLayout() {
        PaddingLayout v = MemoryLayout.paddingLayout(1)
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME);
        check(v);
    }

    @Test
    public void testGroupLayout() {
        GroupLayout v = MemoryLayout.structLayout(
                        JAVA_INT.withByteAlignment(BYTE_ALIGNMENT),
                        JAVA_LONG.withByteAlignment(BYTE_ALIGNMENT))
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME);
        check(v);
    }

    @Test
    public void testStructLayout() {
        StructLayout v = MemoryLayout.structLayout(
                        JAVA_INT.withByteAlignment(BYTE_ALIGNMENT),
                        JAVA_LONG.withByteAlignment(BYTE_ALIGNMENT))
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME);
        check(v);
    }

    @Test
    public void testUnionLayout() {
        UnionLayout v = MemoryLayout.unionLayout(
                    JAVA_INT.withByteAlignment(BYTE_ALIGNMENT),
                    JAVA_LONG.withByteAlignment(BYTE_ALIGNMENT))
                .withByteAlignment(BYTE_ALIGNMENT)
                .withoutName()
                .withName(NAME);
        check(v);
    }

    public void check(ValueLayout v) {
        check((MemoryLayout) v);
        assertEquals(v.order(), BYTE_ORDER);
    }

    public void check(MemoryLayout v) {
        // Check name properties
        assertEquals(v.name().orElseThrow(), NAME);
        assertTrue(v.withoutName().name().isEmpty());

        assertEquals(v.byteAlignment(), BYTE_ALIGNMENT);
    }

}
