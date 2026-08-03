/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

package org.openjdk.bench.jdk.incubator.vector;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "--add-modules=jdk.incubator.vector" })
public class MaskLogicOperationsBenchmark {
    @Param({"256", "512", "1024"})
    private int size;

    private static final VectorSpecies<Byte> B_SPECIES = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> S_SPECIES = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> I_SPECIES = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> L_SPECIES = VectorSpecies.ofLargestShape(long.class);

    private Random r = new Random();
    private boolean[] ma;
    private boolean[] mb;
    private boolean[] mc;

    @Setup
    public void init() {
        ma = new boolean[size];
        mb = new boolean[size];
        mc = new boolean[size];

        for (int i = 0; i < size; i++) {
            ma[i] = r.nextInt() % 2 == 0;
            mb[i] = r.nextInt() % 2 == 0;
        }
    }

    @Benchmark
    public void byteMaskAndNot() {
        VectorMask<Byte> vm1 = VectorMask.fromArray(B_SPECIES, ma, 0);
        for (int i = 0; i < B_SPECIES.loopBound(size); i += B_SPECIES.length()) {
            VectorMask<Byte> vm2 = VectorMask.fromArray(B_SPECIES, mb, i);
            vm1.andNot(vm2).intoArray(mc, i);
        }
    }

    @Benchmark
    public void shortMaskAndNot() {
        VectorMask<Short> vm1 = VectorMask.fromArray(S_SPECIES, ma, 0);
        for (int i = 0; i < S_SPECIES.loopBound(size); i += S_SPECIES.length()) {
            VectorMask<Short> vm2 = VectorMask.fromArray(S_SPECIES, mb, i);
            vm1.andNot(vm2).intoArray(mc, i);
        }
    }

    @Benchmark
    public void intMaskAndNot() {
        VectorMask<Integer> vm1 = VectorMask.fromArray(I_SPECIES, ma, 0);
        for (int i = 0; i < I_SPECIES.loopBound(size); i += I_SPECIES.length()) {
            VectorMask<Integer> vm2 = VectorMask.fromArray(I_SPECIES, mb, i);
            vm1.andNot(vm2).intoArray(mc, i);
        }
    }

    @Benchmark
    public void longMaskAndNot() {
        VectorMask<Long> vm1 = VectorMask.fromArray(L_SPECIES, ma, 0);
        for (int i = 0; i < L_SPECIES.loopBound(size); i += L_SPECIES.length()) {
            VectorMask<Long> vm2 = VectorMask.fromArray(L_SPECIES, mb, i);
            vm1.andNot(vm2).intoArray(mc, i);
        }
    }

    @Benchmark
    public int highMaskRegisterPressureWithNots() {
        int res = 0;
        VectorMask<Byte> vm1 = VectorMask.fromArray(B_SPECIES, ma, 0);
        for (int i = 0; i < B_SPECIES.loopBound(size); i += B_SPECIES.length()) {
            VectorMask<Byte> vm2 = VectorMask.fromArray(B_SPECIES, mb, i).not();
            VectorMask<Byte> vm3 = vm1.or(vm2).not();
            VectorMask<Byte> vm4 = vm1.xor(vm3).not();
            VectorMask<Byte> vm5 = vm1.or(vm4).not();
            res += vm2.trueCount();
            res += vm3.trueCount();
            res += vm4.trueCount();
            res += vm5.trueCount();
        }
        return res;
    }
}