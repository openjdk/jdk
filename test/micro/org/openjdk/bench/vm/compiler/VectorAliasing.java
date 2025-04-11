/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public abstract class VectorAliasing {
    @Param({/*"512",  "1024", */  "2048"})
    public int SIZE;

    public static int INVAR_ZERO = 0;

    // For all types we have an "a" and "b" series. Each series is an alias to the same array.
    private byte[] a0B;
    private byte[] a1B;
    private byte[] b0B;
    private byte[] b1B;

    private int[] a0I;
    private int[] a1I;
    private int[] b0I;
    private int[] b1I;

    private long[] a0L;
    private long[] a1L;
    private long[] b0L;
    private long[] b1L;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        a0B = new byte[SIZE];
        b0B = new byte[SIZE];
        a1B = a0B;
        b1B = b0B;

        a0I = new int[SIZE];
        b0I = new int[SIZE];
        a1I = a0I;
        b1I = b0I;

        a0L = new long[SIZE];
        b0L = new long[SIZE];
        a1L = a0L;
        b1L = b0L;

        for (int i = 0; i < SIZE; i++) {
            a0B[i] = (byte) r.nextInt();
            b0B[i] = (byte) r.nextInt();

            a0I[i] = r.nextInt();
            b0I[i] = r.nextInt();

            a0L[i] = r.nextLong();
            b0L[i] = r.nextLong();
        }
    }

    @Benchmark
    public void bench_copy_array_B_sameIndex_noalias() {
        for (int i = 16; i < a0B.length; i++) {
            b0B[i] = a0B[i];
        }
    }

    @Benchmark
    public void bench_copy_array_B_sameIndex_alias() {
        for (int i = 16; i < a0B.length; i++) {
            a0B[i] = a1B[i];
        }
    }

    @Benchmark
    // TODO: remove the 16 offset, currently somehow prevents vectoirzation
    public void bench_copy_array_B_differentIndex_noalias() {
        for (int i = 16; i < a0B.length; i++) {
            b0B[i] = a0B[i + INVAR_ZERO];
        }
    }

    @Benchmark
    public void bench_copy_array_B_differentIndex_alias() {
        for (int i = 16; i < a0B.length; i++) {
            a0B[i] = a1B[i + INVAR_ZERO];
        }
    }

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"
    })
    public static class VectorAliasingSuperWordWithoutSpeculativeAliasingChecks extends VectorAliasing {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord"
    })
    public static class VectorAliasingSuperWord extends VectorAliasing {}

    @Fork(value = 1, jvmArgs = {
        "-XX:-UseSuperWord"
    })
    public static class VectorAliasingNoSuperWord extends VectorAliasing {}
}
