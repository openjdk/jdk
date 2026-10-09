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
package org.openjdk.bench.valhalla.array.read;

import java.util.concurrent.TimeUnit;
import jdk.internal.value.ValueClass;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class HoistArrayChecks {
    private static final int SIZE = 1_000_000;

    value record Point(byte x, byte y) {
        static final Point DEFAULT = new Point((byte) 0, (byte) 0);
    }

    Point[] nonAtomicFlatArray = (Point[]) ValueClass.newNullRestrictedNonAtomicArray(Point.class, SIZE, Point.DEFAULT);
    Point[] atomicFlatArray = (Point[]) ValueClass.newNullRestrictedAtomicArray(Point.class, SIZE, Point.DEFAULT);
    Point[] nullableFlatArray = (Point[]) ValueClass.newNullableAtomicArray(Point.class, SIZE);

    @Setup
    public void setup() {
        for (int i = 0; i < SIZE; i++) {
            nullableFlatArray[i] = Point.DEFAULT;
        }
    }

    @Benchmark
    public int nonAtomicNaive() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            Point p = nonAtomicFlatArray[i];
            sum += p.x + p.y;
        }
        return sum;
    }

    @Benchmark
    public int nonAtomicHoisted() {
        Point[] array = nonAtomicFlatArray;
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            Point p = array[i];
            sum += p.x + p.y;
        }
        return sum;
    }

    @Benchmark
    public int atomicNaive() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            Point p = atomicFlatArray[i];
            sum += p.x + p.y;
        }
        return sum;
    }

    @Benchmark
    public int atomicHoisted() {
        Point[] array = atomicFlatArray;
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            Point p = array[i];
            sum += p.x + p.y;
        }
        return sum;
    }

    @Benchmark
    public int nullableNaive() {
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            Point p = nullableFlatArray[i];
            sum += p.x + p.y;
        }
        return sum;
    }

    @Benchmark
    public int nullableHoisted() {
        Point[] array = nullableFlatArray;
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            Point p = array[i];
            sum += p.x + p.y;
        }
        return sum;
    }
}
