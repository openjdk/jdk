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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Performance test of String.hashCode() function with constant folding.
 * The tests are using a Map that holds a MethodHandle to better expose
 * any potential lack of constant folding.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringHashCodeStatic {

    private static final String HASHCODE = "abcdefghijkl";
    private static final String HASHCODE_0 = new String(new char[]{72, 90, 100, 89, 105, 2, 72, 90, 100, 89, 105, 2});
    private static final String EMPTY = new String();

    private static final Map<String, MethodHandle> MAP = Map.of(
            HASHCODE, mh(HASHCODE.hashCode()),
            HASHCODE_0, mh(HASHCODE_0.hashCode()),
            EMPTY, mh(EMPTY.hashCode()));

    /**
     * Benchmark testing String.hashCode() with a regular 12 char string with
     * the result possibly cached in String
     */
    @Benchmark
    public int nonZero() throws Throwable {
        return (int)MAP.get(HASHCODE).invokeExact();
    }

    /**
     * Benchmark testing String.hashCode() with a 12 char string with the
     * hashcode = 0.
     */
    @Benchmark
    public int zero() throws Throwable {
        return (int)MAP.get(HASHCODE_0).invokeExact();
    }

    /**
     * Benchmark testing String.hashCode() with the empty string. an
     * empty String has hashCode = 0.
     */
    @Benchmark
    public int empty() throws Throwable {
        return (int)MAP.get(EMPTY).invokeExact();
    }

    static MethodHandle mh(int value) {
        return MethodHandles.constant(int.class, value);
    }

}