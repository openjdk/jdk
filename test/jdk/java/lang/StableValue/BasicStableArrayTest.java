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
 * @compile --enable-preview -source ${jdk.version} BasicStableArrayTest.java
 * @compile Util.java
 * @run junit/othervm --enable-preview BasicStableArrayTest
 */

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class BasicStableArrayTest {

    @Test
    void empty() {
        StableArray<Integer> arr = StableArray.of(0);
        assertEquals("[]", arr.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(0));
    }

    @Test
    void one() {
        StableArray<Integer> arr = StableArray.of(1);
        StableValue<Integer> stable = StableValue.of(); // Separate stable
        assertEquals("[" + stable + "]", arr.toString());
        assertTrue(arr.get(0).trySet(42));
        stable.trySet(42);
        assertEquals("[" + stable + "]", arr.toString());
        assertTrue(arr.get(0).isSet());
        assertFalse(arr.get(0).isError());

        assertEquals(42, arr.get(0).orThrow());

        assertFalse(arr.get(0).trySet(13));

        // No change
        assertEquals(42, arr.get(0).computeIfUnset(() -> 13));
        assertEquals(42, arr.get(0).orThrow());
    }

    @Test
    void computeThrows() {
        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };

        StableArray<Integer> arr = StableArray.of(1);
        assertThrows(UnsupportedOperationException.class, () ->
                arr.get(0).computeIfUnset(throwingSupplier));
        assertTrue(arr.get(0).isError());
        assertFalse(arr.get(0).isSet());

        StableValue<Integer> stable = StableValue.of();
        try {
            stable.computeIfUnset(throwingSupplier);
        } catch (UnsupportedOperationException _) {
            // Happy path
        }
        assertEquals("[" + stable.toString() + "]", arr.toString());
    }

}
