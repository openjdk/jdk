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
 * @run     junit EnumMapSpliteratorTest
 * @bug     8373288
 * @summary EnumMap spliterators should include more specific characteristics
 */

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumMapSpliteratorTest {

    enum TestEnum { A, B, C, D, E }

    private final EnumMap<TestEnum, Integer> map = new EnumMap<>(TestEnum.class);

    {{
        map.put(TestEnum.A, 0);
        map.put(TestEnum.B, 1);
        map.put(TestEnum.C, 2);
        map.put(TestEnum.D, 3);
        map.put(TestEnum.E, null);
    }}

    @Test
    public void testKeySetSpliteratorCharacteristics() {
        Spliterator<TestEnum> spliterator = map.keySet().spliterator();
        assertTrue(spliterator.hasCharacteristics(Spliterator.DISTINCT), "Missing DISTINCT");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SORTED), "Missing SORTED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED), "Missing ORDERED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.NONNULL), "Missing NONNULL");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED), "Missing SIZED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SUBSIZED), "Missing SUBSIZED");
        int expectedCharacteristics = Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.SORTED
                | Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED;
        assertEquals(expectedCharacteristics, spliterator.characteristics(), "Unexpected additional characteristics");
        List<TestEnum> expectedEncounterOrder = Arrays.asList(TestEnum.values());
        assertEquals(expectedEncounterOrder, map.keySet().stream().toList());
    }

    @Test
    public void testEntrySetSpliteratorCharacteristics() {
        Spliterator<Map.Entry<TestEnum, Integer>> spliterator = map.entrySet().spliterator();
        int characteristics = spliterator.characteristics();
        assertTrue(spliterator.hasCharacteristics(Spliterator.DISTINCT), "Missing DISTINCT");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SORTED), "Missing SORTED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED), "Missing ORDERED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.NONNULL), "Missing NONNULL");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED), "Missing SIZED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SUBSIZED), "Missing SUBSIZED");
        int expectedCharacteristics = Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.SORTED
                | Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED;
        assertEquals(expectedCharacteristics, characteristics, "Unexpected additional characteristics");
        List<Map.Entry<TestEnum, Integer>> expectedEncounterOrder = List.of(
                new AbstractMap.SimpleEntry<>(TestEnum.A, 0),
                new AbstractMap.SimpleEntry<>(TestEnum.B, 1),
                new AbstractMap.SimpleEntry<>(TestEnum.C, 2),
                new AbstractMap.SimpleEntry<>(TestEnum.D, 3),
                new AbstractMap.SimpleEntry<>(TestEnum.E, null));
        assertEquals(expectedEncounterOrder, map.entrySet().stream().toList());
    }

    @Test
    public void testValuesSpliteratorCharacteristics() {
        map.put(TestEnum.E, null);
        Spliterator<Integer> spliterator = map.values().spliterator();
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED), "Missing ORDERED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED), "Missing SIZED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.SUBSIZED), "Missing SUBSIZED");
        int expectedCharacteristics = Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        assertEquals(expectedCharacteristics, spliterator.characteristics(), "Unexpected additional characteristics");
        List<Integer> expectedEncounterOrder = Arrays.asList(0, 1, 2, 3, null);
        assertEquals(expectedEncounterOrder, map.values().stream().toList());
    }
}