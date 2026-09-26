/*
 * Copyright 2026 Arm Limited and/or its affiliates.
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
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class TypeVectorCountingUpDown {
    @Param({"512", "2048"})
    public int COUNT;

    private int[] ints;
    private int[] resI;
    private long[] longs;
    private long[] resL;
    private double[] doubles;
    private double[] resD;
    private float[] floats;
    private float[] resF;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        ints = new int[COUNT];
        resI = new int[COUNT];
        longs = new long[COUNT];
        resL = new long[COUNT];
        doubles = new double[COUNT];
        resD = new double[COUNT];
        floats = new float[COUNT];
        resF = new float[COUNT];

        for (int i = 0; i < COUNT; i++) {
            ints[i] = r.nextInt();
            longs[i] = r.nextLong();
            floats[i] = r.nextFloat();
            doubles[i] = r.nextDouble();
        }

    }

    @Benchmark
    public void copyIntForward_ForwardIndex() {
        for (int i = 0; i < COUNT; i++) {
            resI[i] = ints[i];
        }
    }

    @Benchmark
    public void copyIntBackward_ForwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resI[COUNT - 1 - i] = ints[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyIntForward_ReversedIndex() {
        for (int i = 0; i < COUNT; i++) {
            resI[COUNT - 1 - i] = ints[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyIntBackward_BackwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resI[i] = ints[i];
        }
    }

    @Benchmark
    public void copyLongForward_ForwardIndex() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = longs[i];
        }
    }

    @Benchmark
    public void copyLongBackward_ForwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resL[COUNT - 1 - i] = longs[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyLongForward_ReversedIndex() {
        for (int i = 0; i < COUNT; i++) {
            resL[COUNT - 1 - i] = longs[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyLongBackward_BackwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resL[i] = longs[i];
        }
    }

    @Benchmark
    public void copyFloatForward_ForwardIndex() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = floats[i];
        }
    }

    @Benchmark
    public void copyFloatBackward_ForwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resF[COUNT - 1 - i] = floats[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyFloatForward_ReversedIndex() {
        for (int i = 0; i < COUNT; i++) {
            resF[COUNT - 1 - i] = floats[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyFloatBackward_BackwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resF[i] = floats[i];
        }
    }

    @Benchmark
    public void copyDoubleForward_ForwardIndex() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = doubles[i];
        }
    }

    @Benchmark
    public void copyDoubleBackward_ForwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resD[COUNT - 1 - i] = doubles[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyDoubleForward_ReversedIndex() {
        for (int i = 0; i < COUNT; i++) {
            resD[COUNT - 1 - i] = doubles[COUNT - 1 - i];
        }
    }

    @Benchmark
    public void copyDoubleBackward_BackwardIndex() {
        for (int i = COUNT - 1; i >= 0; i--) {
            resD[i] = doubles[i];
        }
    }


}
