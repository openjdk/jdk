/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run     junit EnumSetSpliteratorTest
 * @bug     8179918
 * @summary EnumSet spliterator should report SORTED, ORDERED, NONNULL
 */

import java.util.EnumSet;
import java.util.List;
import java.util.Spliterator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumSetSpliteratorTest {

    private enum Empty {}

    private enum Small {
        a, b, c, d
    }

    private enum Large {
        e00, e01, e02, e03, e04, e05, e06, e07,
        e08, e09, e0A, e0B, e0C, e0D, e0E, e0F,
        e10, e11, e12, e13, e14, e15, e16, e17,
        e18, e19, e1A, e1B, e1C, e1D, e1E, e1F,
        e20, e21, e22, e23, e24, e25, e26, e27,
        e28, e29, e2A, e2B, e2C, e2D, e2E, e2F,
        e30, e31, e32, e33, e34, e35, e36, e37,
        e38, e39, e3A, e3B, e3C, e3D, e3E, e3F,
        e40, e41, e42, e43, e44, e45, e46, e47,
        e48, e49, e4A, e4B, e4C, e4D, e4E, e4F
    }

    @Test
    public void testSpliteratorCharacteristics() {
        assertSpliteratorCharacteristics(EnumSet.allOf(Empty.class));
        assertSpliteratorCharacteristics(EnumSet.allOf(Small.class));
        assertSpliteratorCharacteristics(EnumSet.allOf(Large.class));
        assertSpliteratorCharacteristics(EnumSet.noneOf(Empty.class));
        assertSpliteratorCharacteristics(EnumSet.noneOf(Small.class));
        assertSpliteratorCharacteristics(EnumSet.noneOf(Large.class));
        assertSpliteratorCharacteristics(EnumSet.of(Small.a, Small.d));
        assertSpliteratorCharacteristics(EnumSet.range(Small.a, Small.c));
        assertSpliteratorCharacteristics(EnumSet.range(Large.e02, Large.e4D));
        assertSpliteratorCharacteristics(EnumSet.complementOf(EnumSet.of(Small.c)));
        assertSpliteratorCharacteristics(EnumSet.complementOf(EnumSet.of(Large.e00, Large.e4F)));
    }

    @Test
    public void testEncounterOrder() {
        assertEquals(List.of(Small.values()), EnumSet.allOf(Small.class).stream().toList());
        assertEquals(List.of(Large.values()), EnumSet.allOf(Large.class).stream().toList());
    }

    private static final int EXPECTED_CHARACTERISTICS = (
            Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED |
                    Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED);

    private static void assertSpliteratorCharacteristics(EnumSet<?> enumSet) {
        Spliterator<?> spliterator = enumSet.spliterator();
        assertTrue(spliterator.hasCharacteristics(Spliterator.DISTINCT), "Missing DISTINCT");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SORTED), "Missing SORTED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED), "Missing ORDERED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.NONNULL), "Missing NONNULL");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED), "Missing SIZED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SUBSIZED), "Missing SUBSIZED");
        assertEquals(EXPECTED_CHARACTERISTICS, spliterator.characteristics(), "Unexpected characteristics");
    }
}