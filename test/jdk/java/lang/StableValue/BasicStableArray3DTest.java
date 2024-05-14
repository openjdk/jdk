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
 * @compile Util.java
 * @run junit/othervm --enable-preview BasicStableArray3DTest
 */

import jdk.internal.lang.StableArray2D;
import jdk.internal.lang.StableArray3D;
import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class BasicStableArray3DTest {

    @Test
    void empty() {
        StableArray3D<Integer> arr = StableArray3D.of(0, 0, 0);
        assertEquals("[]", arr.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(0, 0, 0));
        assertEquals(0, arr.length(0));
        assertEquals(0, arr.length(1));
        assertEquals(0, arr.length(2));
    }

    @Test
    void oneTimesTwoTimesThree() {
        StableArray3D<Integer> arr = StableArray3D.of(2, 3, 4);
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
        assertTrue(arr.get(1, 2, 3).trySet(42));
        StableValue<Integer> stable = StableValue.of(); // Separate stable
        stable.trySet(42);
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
        assertTrue(arr.get(1, 2, 3).isSet());
        assertFalse(arr.get(1, 2, 3).isError());

        assertEquals(42, arr.get(1, 2, 3).orThrow());

        assertFalse(arr.get(1, 2, 3).trySet(13));

        // No change
        assertEquals(42, arr.get(1, 2, 3).computeIfUnset(() -> 13));
        assertEquals(42, arr.get(1, 2, 3).orThrow());
    }

    @Test
    void computeThrows() {
        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };

        StableArray3D<Integer> arr = StableArray3D.of(2, 3, 4);
        assertThrows(UnsupportedOperationException.class, () ->
                arr.get(1, 2, 3).computeIfUnset(throwingSupplier));
        assertTrue(arr.get(1, 2, 3).isError());
        assertFalse(arr.get(1, 2, 3).isSet());

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
