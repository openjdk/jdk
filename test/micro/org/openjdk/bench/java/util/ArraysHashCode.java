/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

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
    private byte[][] multibytes;
    private char[][] multichars;
    private short[][] multishorts;
    private int[][] multiints;

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

        multibytes = new byte[100][];
        multichars = new char[100][];
        multishorts = new short[100][];
        multiints = new int[100][];
        for (int i = 0; i < 100; i++) {
            int next = rnd.nextInt(size + 1);
            multibytes[i] = new byte[next];
            multichars[i] = new char[next];
            multishorts[i] = new short[next];
            multiints[i] = new int[next];
            for (int j = 0; j < next; j++) {
                int nextj = rnd.nextInt();
                multibytes[i][j] = (byte)nextj;
                multichars[i][j] = (char)nextj;
                multishorts[i][j] = (short)nextj;
                multiints[i][j] = nextj;
            }
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

    @Benchmark
    @OperationsPerInvocation(100)
    public void multibytes(Blackhole bh) throws Throwable {
        for (int i = 0; i < multibytes.length; i++) {
            bh.consume(Arrays.hashCode(multibytes[i]));
        }
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void multichars(Blackhole bh) throws Throwable {
        for (int i = 0; i < multichars.length; i++) {
            bh.consume(Arrays.hashCode(multichars[i]));
        }
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void multishorts(Blackhole bh) throws Throwable {
        for (int i = 0; i < multishorts.length; i++) {
            bh.consume(Arrays.hashCode(multishorts[i]));
        }
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void multiints(Blackhole bh) throws Throwable {
        for (int i = 0; i < multibytes.length; i++) {
            bh.consume(Arrays.hashCode(multiints[i]));
        }
    }
}
