/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public abstract class VectorAliasing {
    @Param({/*"512",  "1024", */  "10000"})
    public int SIZE;

    public static int INVAR_ZERO = 0;

    // For all types we have an "a" and "b" series. Each series is an alias to the same array.
    private byte[] aB;
    private byte[] bB;

    private int[] aI;
    private int[] bI;

    private long[] aL;
    private long[] bL;

    private int iteration = 0;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        aB = new byte[SIZE];
        bB = new byte[SIZE];

        aI = new int[SIZE];
        bI = new int[SIZE];

        aL = new long[SIZE];
        bL = new long[SIZE];

        for (int i = 0; i < SIZE; i++) {
            aB[i] = (byte) r.nextInt();
            bB[i] = (byte) r.nextInt();

            aI[i] = r.nextInt();
            bI[i] = r.nextInt();

            aL[i] = r.nextLong();
            bL[i] = r.nextLong();
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_B(byte[] a, byte b[]) {
        for (int i = 0; i < a.length; i++) {
            b[i] = a[i];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_B(byte[] a, byte b[], int aOffset, int bOffset, int size) {
        for (int i = 0; i < size; i++) {
            b[i + bOffset] = a[i + aOffset];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_I(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i] = a[i];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_I(int[] a, int[] b, int aOffset, int bOffset, int size) {
        for (int i = 0; i < size; i++) {
            b[i + bOffset] = a[i + aOffset];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_L(long[] a, long[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i] = a[i];
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copy_L(long[] a, long[] b, int aOffset, int bOffset, int size) {
        for (int i = 0; i < size; i++) {
            b[i + bOffset] = a[i + aOffset];
        }
    }

    @Benchmark
    // Vectorizes with static analysis, since using same index -> iterations trivially independent.
    public void bench_copy_array_B_sameIndex_noalias() {
        copy_B(bB, aB);
    }

    @Benchmark
    // Vectorizes with static analysis, since using same index -> iterations trivially independent.
    public void bench_copy_array_B_sameIndex_alias() {
        copy_B(aB, aB);
    }

    @Benchmark
    // Vectorizes if has at least one of: predicate or multiversioning
    public void bench_copy_array_B_differentIndex_noalias() {
        copy_B(bB, aB, 0, 0, aB.length);
    }

    @Benchmark
    // never vectorizes, with our without runtime check
    public void bench_copy_array_B_differentIndex_alias() {
        copy_B(aB, aB, 0, 0, aB.length);
    }

    @Benchmark
    // Requires multiversioning for vectorization.
    // With only the predicate, we will eventually deopt and compile without vectorization.
    public void bench_copy_array_B_differentIndex_mixed() {
        if ((iteration++) % 2 == 0) {
            copy_B(bB, aB, 0, 0, aB.length); // noalias
        } else {
            copy_B(aB, aB, 0, 0, aB.length); // alias
        }
    }

    // No overlap -> expect vectoirzation.
    // Vectorizes if has at least one of: predicate or multiversioning
    @Benchmark
    public void bench_copy_array_B_half() {
        copy_B(aB, aB, 0, aB.length / 2, aB.length / 2);
    }

    // Overlap, but never alias -> expect vectorization.
    // Vectorizes if has at least one of: predicate or multiversioning
    @Benchmark
    public void bench_copy_array_B_partial_overlap() {
        copy_B(aB, aB, 0, aB.length / 4, aB.length / 4 * 3);
    }

    @Benchmark
    // Vectorizes with static analysis, since using same index -> iterations trivially independent.
    public void bench_copy_array_I_sameIndex_noalias() {
        copy_I(bI, aI);
    }

    @Benchmark
    // Vectorizes with static analysis, since using same index -> iterations trivially independent.
    public void bench_copy_array_I_sameIndex_alias() {
        copy_I(aI, aI);
    }

    @Benchmark
    // Vectorizes if has at least one of: predicate or multiversioning
    public void bench_copy_array_I_differentIndex_noalias() {
        copy_I(bI, aI, 0, 0, aI.length);
    }

    @Benchmark
    // never vectorizes, with our without runtime check
    public void bench_copy_array_I_differentIndex_alias() {
        copy_I(aI, aI, 0, 0, aI.length);
    }

    @Benchmark
    // Requires multiversioning for vectorization.
    // With only the predicate, we will eventually deopt and compile without vectorization.
    public void bench_copy_array_I_differentIndex_mixed() {
        if ((iteration++) % 2 == 0) {
            copy_I(bI, aI, 0, 0, aI.length); // noalias
        } else {
            copy_I(aI, aI, 0, 0, aI.length); // alias
        }
    }

    // No overlap -> expect vectoirzation.
    // Vectorizes if has at least one of: predicate or multiversioning
    @Benchmark
    public void bench_copy_array_I_half() {
        copy_I(aI, aI, 0, aI.length / 2, aI.length / 2);
    }

    // Overlap, but never alias -> expect vectorization.
    // Vectorizes if has at least one of: predicate or multiversioning
    @Benchmark
    public void bench_copy_array_I_partial_overlap() {
        copy_I(aI, aI, 0, aI.length / 4, aI.length / 4 * 3);
    }

    @Benchmark
    // Vectorizes with static analysis, since using same index -> iterations trivially independent.
    public void bench_copy_array_L_sameIndex_noalias() {
        copy_L(bL, aL);
    }

    @Benchmark
    // Vectorizes with static analysis, since using same index -> iterations trivially independent.
    public void bench_copy_array_L_sameIndex_alias() {
        copy_L(aL, aL);
    }

    @Benchmark
    // Vectorizes if has at least one of: predicate or multiversioning
    public void bench_copy_array_L_differentIndex_noalias() {
        copy_L(bL, aL, 0, 0, aL.length);
    }

    @Benchmark
    // never vectorizes, with our without runtime check
    public void bench_copy_array_L_differentIndex_alias() {
        copy_L(aL, aL, 0, 0, aL.length);
    }

    @Benchmark
    // Requires multiversioning for vectorization.
    // With only the predicate, we will eventually deopt and compile without vectorization.
    public void bench_copy_array_L_differentIndex_mixed() {
        if ((iteration++) % 2 == 0) {
            copy_L(bL, aL, 0, 0, aL.length); // noalias
        } else {
            copy_L(aL, aL, 0, 0, aL.length); // alias
        }
    }

    // No overlap -> expect vectoirzation.
    // Vectorizes if has at least one of: predicate or multiversioning
    @Benchmark
    public void bench_copy_array_L_half() {
        copy_L(aL, aL, 0, aL.length / 2, aL.length / 2);
    }

    // Overlap, but never alias -> expect vectorization.
    // Vectorizes if has at least one of: predicate or multiversioning
    @Benchmark
    public void bench_copy_array_L_partial_overlap() {
        copy_L(aL, aL, 0, aL.length / 4, aL.length / 4 * 3);
    }

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"
    })
    public static class VectorAliasingSuperWordWithoutSpeculativeAliasingChecks extends VectorAliasing {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:-UseAutoVectorizationPredicate"
    })
    public static class VectorAliasingSuperWordWithoutAutoVectorizationPredicate extends VectorAliasing {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:-LoopMultiversioning"
    })
    public static class VectorAliasingSuperWordWithoutMultiversioning extends VectorAliasing {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:AutoVectorizationOverrideProfitability=0"
    })
    public static class VectorAliasingSuperWordPretendNotProfitable extends VectorAliasing {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord"
    })
    public static class VectorAliasingSuperWord extends VectorAliasing {}

    @Fork(value = 1, jvmArgs = {
        "-XX:-UseSuperWord"
    })
    public static class VectorAliasingNoSuperWord extends VectorAliasing {}
}
