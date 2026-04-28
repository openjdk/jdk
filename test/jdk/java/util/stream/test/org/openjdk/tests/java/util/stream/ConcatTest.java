/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.LambdaTestHelpers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcatTest {

    private static Stream<Arguments> cases() {
        List<Integer> part1 = Arrays.asList(5, 3, 4, 1, 2, 6, 2, 4);
        List<Integer> part2 = Arrays.asList(8, 8, 6, 6, 9, 7, 10, 9);
        List<Integer> p1p2 = Arrays.asList(5, 3, 4, 1, 2, 6, 2, 4, 8, 8, 6, 6, 9, 7, 10, 9);
        List<Integer> p2p1 = Arrays.asList(8, 8, 6, 6, 9, 7, 10, 9, 5, 3, 4, 1, 2, 6, 2, 4);
        List<Integer> empty = new LinkedList<>(); // To be ordered
        assertTrue(empty.isEmpty());
        LinkedHashSet<Integer> distinctP1 = new LinkedHashSet<>(part1);
        LinkedHashSet<Integer> distinctP2 = new LinkedHashSet<>(part2);
        TreeSet<Integer> sortedP1 = new TreeSet<>(part1);
        TreeSet<Integer> sortedP2 = new TreeSet<>(part2);

        return Stream.of(
                Arguments.of("regular", part1, part2, p1p2),
                Arguments.of("reverse regular", part2, part1, p2p1),
                Arguments.of("front distinct", distinctP1, part2, Arrays.asList(5, 3, 4, 1, 2, 6, 8, 8, 6, 6, 9, 7, 10, 9)),
                Arguments.of("back distinct", part1, distinctP2, Arrays.asList(5, 3, 4, 1, 2, 6, 2, 4, 8, 6, 9, 7, 10)),
                Arguments.of("both distinct", distinctP1, distinctP2, Arrays.asList(5, 3, 4, 1, 2, 6, 8, 6, 9, 7, 10)),
                Arguments.of("front sorted", sortedP1, part2, Arrays.asList(1, 2, 3, 4, 5, 6, 8, 8, 6, 6, 9, 7, 10, 9)),
                Arguments.of("back sorted", part1, sortedP2, Arrays.asList(5, 3, 4, 1, 2, 6, 2, 4, 6, 7, 8, 9, 10)),
                Arguments.of("both sorted", sortedP1, sortedP2, Arrays.asList(1, 2, 3, 4, 5, 6, 6, 7, 8, 9, 10)),
                Arguments.of("reverse both sorted", sortedP2, sortedP1, Arrays.asList(6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6)),
                Arguments.of("empty something", empty, part1, part1),
                Arguments.of("something empty", part1, empty, part1),
                Arguments.of("empty empty", empty, empty, empty)
        );
    }

    private void verifyPrerequisites(Collection<Integer> c1, Collection<Integer> c2) {
        Stream<Integer> s1s = c1.stream();
        Stream<Integer> s2s = c2.stream();
        Stream<Integer> s1p = c1.parallelStream();
        Stream<Integer> s2p = c2.parallelStream();
        assertTrue(s1p.isParallel());
        assertTrue(s2p.isParallel());
        assertFalse(s1s.isParallel());
        assertFalse(s2s.isParallel());

        assertTrue(s1s.spliterator().hasCharacteristics(Spliterator.ORDERED));
        assertTrue(s1p.spliterator().hasCharacteristics(Spliterator.ORDERED));
        assertTrue(s2s.spliterator().hasCharacteristics(Spliterator.ORDERED));
        assertTrue(s2p.spliterator().hasCharacteristics(Spliterator.ORDERED));
    }

    private <T> void assertConcatContent(Spliterator<T> sp, boolean ordered, Spliterator<T> expected, String scenario) {
        // concat stream cannot guarantee uniqueness
        assertFalse(sp.hasCharacteristics(Spliterator.DISTINCT), scenario);
        // concat stream cannot guarantee sorted
        assertFalse(sp.hasCharacteristics(Spliterator.SORTED), scenario);
        // concat stream is ordered if both are ordered
        assertEquals(ordered, sp.hasCharacteristics(Spliterator.ORDERED), scenario);

        // Verify elements
        if (ordered) {
            assertEquals(toBoxedList(expected),
                         toBoxedList(sp),
                         scenario);
        } else {
            assertEquals(toBoxedMultiset(expected),
                         toBoxedMultiset(sp),
                         scenario);
        }
    }

    private void assertRefConcat(Stream<Integer> s1, Stream<Integer> s2, boolean parallel, boolean ordered, Collection<Integer> expected, String scenario) {
        Stream<Integer> result = Stream.concat(s1, s2);
        assertEquals(parallel, result.isParallel());
        assertConcatContent(result.spliterator(), ordered, expected.spliterator(), scenario);
    }

    private void assertIntConcat(Stream<Integer> s1, Stream<Integer> s2, boolean parallel, boolean ordered, Collection<Integer> expected, String scenario) {
        IntStream result = IntStream.concat(s1.mapToInt(Integer::intValue),
                                            s2.mapToInt(Integer::intValue));
        assertEquals(parallel, result.isParallel());
        assertConcatContent(result.spliterator(), ordered,
                            expected.stream().mapToInt(Integer::intValue).spliterator(), scenario);
    }

    private void assertLongConcat(Stream<Integer> s1, Stream<Integer> s2, boolean parallel, boolean ordered, Collection<Integer> expected, String scenario) {
        LongStream result = LongStream.concat(s1.mapToLong(Integer::longValue),
                                              s2.mapToLong(Integer::longValue));
        assertEquals(parallel, result.isParallel());
        assertConcatContent(result.spliterator(), ordered,
                            expected.stream().mapToLong(Integer::longValue).spliterator(), scenario);
    }

    private void assertDoubleConcat(Stream<Integer> s1, Stream<Integer> s2, boolean parallel, boolean ordered, Collection<Integer> expected, String scenario) {
        DoubleStream result = DoubleStream.concat(s1.mapToDouble(Integer::doubleValue),
                                                  s2.mapToDouble(Integer::doubleValue));
        assertEquals(parallel, result.isParallel());
        assertConcatContent(result.spliterator(), ordered,
                            expected.stream().mapToDouble(Integer::doubleValue).spliterator(), scenario);
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testRefConcat(String scenario, Collection<Integer> c1, Collection<Integer> c2, Collection<Integer> expected) {
        verifyPrerequisites(c1, c2);
        // sequential + sequential -> sequential
        assertRefConcat(c1.stream(), c2.stream(), false, true, expected, scenario);
        // parallel + parallel -> parallel
        assertRefConcat(c1.parallelStream(), c2.parallelStream(), true, true, expected, scenario);
        // sequential + parallel -> parallel
        assertRefConcat(c1.stream(), c2.parallelStream(), true, true, expected, scenario);
        // parallel + sequential -> parallel
        assertRefConcat(c1.parallelStream(), c2.stream(), true, true, expected, scenario);

        // not ordered
        assertRefConcat(c1.stream().unordered(), c2.stream(), false, false, expected, scenario);
        assertRefConcat(c1.stream(), c2.stream().unordered(), false, false, expected, scenario);
        assertRefConcat(c1.parallelStream().unordered(), c2.stream().unordered(), true, false, expected, scenario);
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testIntConcat(String scenario, Collection<Integer> c1, Collection<Integer> c2, Collection<Integer> expected) {
        verifyPrerequisites(c1, c2);
        // sequential + sequential -> sequential
        assertIntConcat(c1.stream(), c2.stream(), false, true, expected, scenario);
        // parallel + parallel -> parallel
        assertIntConcat(c1.parallelStream(), c2.parallelStream(), true, true, expected, scenario);
        // sequential + parallel -> parallel
        assertIntConcat(c1.stream(), c2.parallelStream(), true, true, expected, scenario);
        // parallel + sequential -> parallel
        assertIntConcat(c1.parallelStream(), c2.stream(), true, true, expected, scenario);

        // not ordered
        assertIntConcat(c1.stream().unordered(), c2.stream(), false, false, expected, scenario);
        assertIntConcat(c1.stream(), c2.stream().unordered(), false, false, expected, scenario);
        assertIntConcat(c1.parallelStream().unordered(), c2.stream().unordered(), true, false, expected, scenario);
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testLongConcat(String scenario, Collection<Integer> c1, Collection<Integer> c2, Collection<Integer> expected) {
        verifyPrerequisites(c1, c2);
        // sequential + sequential -> sequential
        assertLongConcat(c1.stream(), c2.stream(), false, true, expected, scenario);
        // parallel + parallel -> parallel
        assertLongConcat(c1.parallelStream(), c2.parallelStream(), true, true, expected, scenario);
        // sequential + parallel -> parallel
        assertLongConcat(c1.stream(), c2.parallelStream(), true, true, expected, scenario);
        // parallel + sequential -> parallel
        assertLongConcat(c1.parallelStream(), c2.stream(), true, true, expected, scenario);

        // not ordered
        assertLongConcat(c1.stream().unordered(), c2.stream(), false, false, expected, scenario);
        assertLongConcat(c1.stream(), c2.stream().unordered(), false, false, expected, scenario);
        assertLongConcat(c1.parallelStream().unordered(), c2.stream().unordered(), true, false, expected, scenario);
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testDoubleConcat(String scenario, Collection<Integer> c1, Collection<Integer> c2, Collection<Integer> expected) {
        verifyPrerequisites(c1, c2);
        // sequential + sequential -> sequential
        assertDoubleConcat(c1.stream(), c2.stream(), false, true, expected, scenario);
        // parallel + parallel -> parallel
        assertDoubleConcat(c1.parallelStream(), c2.parallelStream(), true, true, expected, scenario);
        // sequential + parallel -> parallel
        assertDoubleConcat(c1.stream(), c2.parallelStream(), true, true, expected, scenario);
        // parallel + sequential -> parallel
        assertDoubleConcat(c1.parallelStream(), c2.stream(), true, true, expected, scenario);

        // not ordered
        assertDoubleConcat(c1.stream().unordered(), c2.stream(), false, false, expected, scenario);
        assertDoubleConcat(c1.stream(), c2.stream().unordered(), false, false, expected, scenario);
        assertDoubleConcat(c1.parallelStream().unordered(), c2.stream().unordered(), true, false, expected, scenario);
    }
}
