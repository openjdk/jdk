/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Performance test of Arrays.hashCode() methods
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class ArraysHashCode {

    @Param({"1", "10", "100", "10000"})
    private int size;

    private byte[] bytes;
    private char[] chars;
    private short[] shorts;
    private int[] ints;

    @Setup
    public void setup() throws UnsupportedEncodingException, ClassNotFoundException, NoSuchMethodException, Throwable {
        Random rnd = new Random(42);

        bytes = new byte[size];
        chars = new char[size];
        shorts = new short[size];
        ints = new int[size];
        for (int i = 0; i < size; i++) {
            int next = rnd.nextInt();
            bytes[i] = (byte)next;
            chars[i] = (char)next;
            shorts[i] = (short)next;
            ints[i] = next;
        }
    }

    @Benchmark
    public int bytes() throws Throwable {
        return Arrays.hashCode(bytes);
    }

    @Benchmark
    public int chars() throws Throwable {
        return Arrays.hashCode(chars);
    }

    @Benchmark
    public int shorts() throws Throwable {
        return Arrays.hashCode(shorts);
    }

    @Benchmark
    public int ints() throws Throwable {
        return Arrays.hashCode(ints);
    }
}
