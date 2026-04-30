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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.*;

import static java.util.stream.LambdaTestHelpers.countTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MinMaxTest
 *
 * @author Brian Goetz
 */
public class MinMaxTest extends OpTestCase {

    @Test
    public void testMinMax() {
        assertTrue(countTo(0).stream().min(Integer::compare).isEmpty());
        assertTrue(countTo(0).stream().max(Integer::compare).isEmpty());
        assertEquals(1, (int) countTo(1000).stream().min(Integer::compare).get());
        assertEquals(1000, (int) countTo(1000).stream().max(Integer::compare).get());
    }

    @ParameterizedTest
    @MethodSource("java.util.stream.StreamTestDataProvider#integerStreamTestData")
    public void testOps(String name, TestData.OfRef<Integer> data) {
        exerciseTerminalOps(data, s -> s.min(Integer::compare));
        exerciseTerminalOps(data, s -> s.max(Integer::compare));
    }

    @Test
    public void testIntMinMax() {
        assertEquals(OptionalInt.empty(), IntStream.empty().min());
        assertEquals(OptionalInt.empty(), IntStream.empty().max());
        assertEquals(1, IntStream.range(1, 1001).min().getAsInt());
        assertEquals(1000, IntStream.range(1, 1001).max().getAsInt());
    }

    @ParameterizedTest
    @MethodSource("java.util.stream.IntStreamTestDataProvider#intStreamTestData")
    public void testIntOps(String name, TestData.OfInt data) {
        exerciseTerminalOps(data, s -> s.min());
        exerciseTerminalOps(data, s -> s.max());
    }

    @Test
    public void testLongMinMax() {
        assertEquals(OptionalLong.empty(), LongStream.empty().min());
        assertEquals(OptionalLong.empty(), LongStream.empty().max());
        assertEquals(1, LongStream.range(1, 1001).min().getAsLong());
        assertEquals(1000, LongStream.range(1, 1001).max().getAsLong());
    }

    @ParameterizedTest
    @MethodSource("java.util.stream.LongStreamTestDataProvider#longStreamTestData")
    public void testLongOps(String name, TestData.OfLong data) {
        exerciseTerminalOps(data, s -> s.min());
        exerciseTerminalOps(data, s -> s.max());
    }

    @Test
    public void testDoubleMinMax() {
        assertEquals(OptionalDouble.empty(), DoubleStream.empty().min());
        assertEquals(OptionalDouble.empty(), DoubleStream.empty().max());
        assertEquals(1.0, LongStream.range(1, 1001).asDoubleStream().min().getAsDouble());
        assertEquals(1000.0, LongStream.range(1, 1001).asDoubleStream().max().getAsDouble());
    }

    @ParameterizedTest
    @MethodSource("java.util.stream.DoubleStreamTestDataProvider#doubleStreamTestData")
    public void testDoubleOps(String name, TestData.OfDouble data) {
        exerciseTerminalOps(data, s -> s.min());
        exerciseTerminalOps(data, s -> s.max());
    }
}
