/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgs = { "--add-modules=jdk.incubator.vector" })
public class VectorLoadMaskBenchmark {
    @Param({"1024"})
    private int size;

    private static final VectorSpecies<Byte> B_SPECIES = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> S_SPECIES = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> I_SPECIES = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> L_SPECIES = VectorSpecies.ofLargestShape(long.class);

    private boolean[] ma;
    private boolean[] mb;
    private boolean[] mc;
    private boolean[] mr;

    @Setup
    public void init() {
        Random r = new Random(1024);
        ma = new boolean[size];
        mb = new boolean[size];
        mc = new boolean[size];
        mr = new boolean[size];

        for (int i = 0; i < size; i++) {
            ma[i] = r.nextBoolean();
            mb[i] = r.nextBoolean();
            mc[i] = r.nextBoolean();
        }
    }

    // Mask load followed by a mask query.

    @Benchmark
    public long byteLoadMaskTrueCount() {
        long res = 0;
        for (int i = 0; i < B_SPECIES.loopBound(size); i += B_SPECIES.length()) {
            res += VectorMask.fromArray(B_SPECIES, ma, i).trueCount();
        }
        return res;
    }

    @Benchmark
    public long shortLoadMaskTrueCount() {
        long res = 0;
        for (int i = 0; i < S_SPECIES.loopBound(size); i += S_SPECIES.length()) {
            res += VectorMask.fromArray(S_SPECIES, ma, i).trueCount();
        }
        return res;
    }

    @Benchmark
    public long intLoadMaskTrueCount() {
        long res = 0;
        for (int i = 0; i < I_SPECIES.loopBound(size); i += I_SPECIES.length()) {
            res += VectorMask.fromArray(I_SPECIES, ma, i).trueCount();
        }
        return res;
    }

    @Benchmark
    public long longLoadMaskTrueCount() {
        long res = 0;
        for (int i = 0; i < L_SPECIES.loopBound(size); i += L_SPECIES.length()) {
            res += VectorMask.fromArray(L_SPECIES, ma, i).trueCount();
        }
        return res;
    }

    // Three mask loads combined through mask logic and stored back.

    @Benchmark
    public void byteLoadMaskLogic() {
        for (int i = 0; i < B_SPECIES.loopBound(size); i += B_SPECIES.length()) {
            VectorMask<Byte> m1 = VectorMask.fromArray(B_SPECIES, ma, i);
            VectorMask<Byte> m2 = VectorMask.fromArray(B_SPECIES, mb, i);
            VectorMask<Byte> m3 = VectorMask.fromArray(B_SPECIES, mc, i);
            m1.and(m2).or(m3.andNot(m1)).intoArray(mr, i);
        }
    }

    @Benchmark
    public void shortLoadMaskLogic() {
        for (int i = 0; i < S_SPECIES.loopBound(size); i += S_SPECIES.length()) {
            VectorMask<Short> m1 = VectorMask.fromArray(S_SPECIES, ma, i);
            VectorMask<Short> m2 = VectorMask.fromArray(S_SPECIES, mb, i);
            VectorMask<Short> m3 = VectorMask.fromArray(S_SPECIES, mc, i);
            m1.and(m2).or(m3.andNot(m1)).intoArray(mr, i);
        }
    }

    @Benchmark
    public void intLoadMaskLogic() {
        for (int i = 0; i < I_SPECIES.loopBound(size); i += I_SPECIES.length()) {
            VectorMask<Integer> m1 = VectorMask.fromArray(I_SPECIES, ma, i);
            VectorMask<Integer> m2 = VectorMask.fromArray(I_SPECIES, mb, i);
            VectorMask<Integer> m3 = VectorMask.fromArray(I_SPECIES, mc, i);
            m1.and(m2).or(m3.andNot(m1)).intoArray(mr, i);
        }
    }

    @Benchmark
    public void longLoadMaskLogic() {
        for (int i = 0; i < L_SPECIES.loopBound(size); i += L_SPECIES.length()) {
            VectorMask<Long> m1 = VectorMask.fromArray(L_SPECIES, ma, i);
            VectorMask<Long> m2 = VectorMask.fromArray(L_SPECIES, mb, i);
            VectorMask<Long> m3 = VectorMask.fromArray(L_SPECIES, mc, i);
            m1.and(m2).or(m3.andNot(m1)).intoArray(mr, i);
        }
    }
}
