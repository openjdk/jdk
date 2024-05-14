/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic tests for StableArray implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} BasicStableArray3DTest.java
 * @run junit/othervm --enable-preview BasicStableArray3DTest
 */

import jdk.internal.lang.StableArray2D;
import jdk.internal.lang.StableArray3D;
import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class BasicStableArray3DTest {

    private static final int DIM0 = 2;
    private static final int DIM1 = 3;
    private static final int DIM2 = 4;
    private static final int I0 = 1;
    private static final int I1 = 2;
    private static final int I2 = 3;
    private static final int VALUE = 42;
    private static final int VALUE2 = 13;

    private StableArray2D<Integer> arr;

    @BeforeEach
    void setup() {
        arr =  StableArray2D.of(DIM0, DIM1);
    }

    @Test
    void empty() {
        StableArray3D<Integer> empty = StableArray3D.of(0, 0, 0);
        assertEquals("[]", empty.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> empty.get(0, 0, 0));
        assertEquals(0, empty.length(0));
        assertEquals(0, empty.length(1));
        assertEquals(0, empty.length(2));
    }

    @Test
    void length() {
        assertEquals(DIM0, arr.length(0));
        assertEquals(DIM1, arr.length(1));
        assertEquals(DIM2, arr.length(2));
        assertThrows(IllegalArgumentException.class, () -> arr.length(-1));
        assertThrows(IllegalArgumentException.class, () -> arr.length(3));
    }

    @Test
    void oneTimesTwoTimesThree() {
        StableArray3D<Integer> arr = StableArray3D.of(I0, I1, I2);
        assertEquals(2, arr.length(0));
        assertEquals(3, arr.length(1));
        assertEquals(4, arr.length(2));
        assertEquals("[" +
                        "[" +
                        "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                        "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                        "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset]" +
                        "], [" +
                        "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                        "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                        "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset]" +
                        "]" +
                        "]"
        , arr.toString());
        assertTrue(arr.get(I0, I1, I2).trySet(VALUE));
        StableValue<Integer> stable = StableValue.of(); // Separate stable
        stable.trySet(VALUE);
        assertEquals("[" +
                "[" +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset]" +
                "], [" +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, " + stable + "]" +
                "]" +
                "]", arr.toString());
        assertTrue(arr.get(I0, I1, I2).isSet());
        assertFalse(arr.get(I0, I1, I2).isError());

        assertEquals(VALUE, arr.get(I0, I1, I2).orThrow());

        assertFalse(arr.get(I0, I1, I2).trySet(VALUE2));

        // No change
        assertEquals(VALUE, arr.get(I0, I1, I2).computeIfUnset(() -> VALUE2));
        assertEquals(VALUE, arr.get(I0, I1, I2).orThrow());
    }

    @Test
    void computeThrows() {
        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };

        StableArray3D<Integer> arr = StableArray3D.of(2, 3, 4);
        assertThrows(UnsupportedOperationException.class, () ->
                arr.get(I0, I1, I2).computeIfUnset(throwingSupplier));
        assertTrue(arr.get(I0, I1, I2).isError());
        assertFalse(arr.get(I0, I1, I2).isSet());

        StableValue<Integer> stable = StableValue.of();
        try {
            stable.computeIfUnset(throwingSupplier);
        } catch (UnsupportedOperationException _) {
            // Happy path
        }

        assertEquals("[" +
                "[" +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset]" +
                "], [" +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, StableValue.unset], " +
                "[StableValue.unset, StableValue.unset, StableValue.unset, " + stable + "]" +
                "]" +
                "]", arr.toString());
    }

}
