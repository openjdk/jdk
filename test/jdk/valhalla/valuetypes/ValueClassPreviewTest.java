/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test jdk.internal.value.ValueClass against preview-only things
 * @modules java.base/jdk.internal.value
 * @enablePreview
 * @run junit ValueClassPreviewTest
 */

import java.util.Optional;
import java.util.OptionalInt;

import jdk.internal.value.ValueClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueClassPreviewTest {
    @Test
    void testHasBinaryPayload() {
        assertTrue(ValueClass.hasBinaryPayload(Integer.class));
        assertTrue(ValueClass.hasBinaryPayload(OptionalInt.class));
        assertFalse(ValueClass.hasBinaryPayload(Optional.class));

        value record R1(int a, long b, float c) {}
        assertTrue(ValueClass.hasBinaryPayload(R1.class));
        value record R2(R1 a) {}
        assertTrue(ValueClass.hasBinaryPayload(R2.class));
        value record R3(String a) {}
        assertFalse(ValueClass.hasBinaryPayload(R3.class));
        value record R4(R3 a) {}
        assertFalse(ValueClass.hasBinaryPayload(R4.class));
    }

    @Test
    void testSpecialCopy() {
        Object[] original = makeArray(4);
        assertThrows(NegativeArraySizeException.class, () -> ValueClass.copyOfSpecialArray(original, -1));
        assertArrayEquals(original, ValueClass.copyOfSpecialArray(original, 4));
        Object[] padded = makeArray(5);
        padded[4] = null;
        assertArrayEquals(padded, ValueClass.copyOfSpecialArray(original, 5));
        Object[] truncated = makeArray(3);
        assertArrayEquals(truncated, ValueClass.copyOfSpecialArray(original, 3));
    }

    @Test
    void testSpecialCopyOfRange() {
        Object[] original = makeArray(4);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> ValueClass.copyOfRangeSpecialArray(original, -1, 5));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> ValueClass.copyOfRangeSpecialArray(original, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> ValueClass.copyOfRangeSpecialArray(original, 4, 2));
        assertArrayEquals(original, ValueClass.copyOfRangeSpecialArray(original, 0, 4));
        Object[] padded = makeArray(5);
        padded[4] = null;
        assertArrayEquals(padded, ValueClass.copyOfRangeSpecialArray(original, 0, 5));
        Object[] truncated = makeArray(3);
        assertArrayEquals(truncated, ValueClass.copyOfRangeSpecialArray(original, 0, 3));
    }

    private static Object[] makeArray(int l) {
        Object[] arr = ValueClass.newNullableAtomicArray(Integer.class, l);
        for (int i = 0; i < l; i++) {
            arr[i] = Integer.valueOf(i);
        }
        return arr;
    }
}
