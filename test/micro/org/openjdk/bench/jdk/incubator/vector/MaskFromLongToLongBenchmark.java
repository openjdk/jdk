/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class MaskFromLongToLongBenchmark {
    private static final int ITERATION = 10000;

    @CompilerControl(CompilerControl.Mode.INLINE)
    public long microMaskFromLongToLong(VectorSpecies<?> species) {
        long result = 0;
        for (int i = 0; i < ITERATION; i++) {
            long mask = Math.min(-1, Math.max(-1, result));
            result += VectorMask.fromLong(species, mask).toLong();
        }
        return result;
    }

    @Benchmark
    public long microMaskFromLongToLong_Byte64() {
        return microMaskFromLongToLong(ByteVector.SPECIES_64);
    }

    @Benchmark
    public long microMaskFromLongToLong_Byte128() {
        return microMaskFromLongToLong(ByteVector.SPECIES_128);
    }

    @Benchmark
    public long microMaskFromLongToLong_Byte256() {
        return microMaskFromLongToLong(ByteVector.SPECIES_256);
    }

    @Benchmark
    public long microMaskFromLongToLong_Byte512() {
        return microMaskFromLongToLong(ByteVector.SPECIES_512);
    }

    @Benchmark
    public long microMaskFromLongToLong_Short64() {
        return microMaskFromLongToLong(ShortVector.SPECIES_64);
    }

    @Benchmark
    public long microMaskFromLongToLong_Short128() {
        return microMaskFromLongToLong(ShortVector.SPECIES_128);
    }

    @Benchmark
    public long microMaskFromLongToLong_Short256() {
        return microMaskFromLongToLong(ShortVector.SPECIES_256);
    }

    @Benchmark
    public long microMaskFromLongToLong_Short512() {
        return microMaskFromLongToLong(ShortVector.SPECIES_512);
    }

    @Benchmark
    public long microMaskFromLongToLong_Integer64() {
        return microMaskFromLongToLong(IntVector.SPECIES_64);
    }

    @Benchmark
    public long microMaskFromLongToLong_Integer128() {
        return microMaskFromLongToLong(IntVector.SPECIES_128);
    }

    @Benchmark
    public long microMaskFromLongToLong_Integer256() {
        return microMaskFromLongToLong(IntVector.SPECIES_256);
    }

    @Benchmark
    public long microMaskFromLongToLong_Integer512() {
        return microMaskFromLongToLong(IntVector.SPECIES_512);
    }

    @Benchmark
    public long microMaskFromLongToLong_Long64() {
        return microMaskFromLongToLong(LongVector.SPECIES_64);
    }

    @Benchmark
    public long microMaskFromLongToLong_Long128() {
        return microMaskFromLongToLong(LongVector.SPECIES_128);
    }

    @Benchmark
    public long microMaskFromLongToLong_Long256() {
        return microMaskFromLongToLong(LongVector.SPECIES_256);
    }

    @Benchmark
    public long microMaskFromLongToLong_Long512() {
        return microMaskFromLongToLong(LongVector.SPECIES_512);
    }

    @Benchmark
    public long microMaskFromLongToLong_Float64() {
        return microMaskFromLongToLong(FloatVector.SPECIES_64);
    }

    @Benchmark
    public long microMaskFromLongToLong_Float128() {
        return microMaskFromLongToLong(FloatVector.SPECIES_128);
    }

    @Benchmark
    public long microMaskFromLongToLong_Float256() {
        return microMaskFromLongToLong(FloatVector.SPECIES_256);
    }

    @Benchmark
    public long microMaskFromLongToLong_Float512() {
        return microMaskFromLongToLong(FloatVector.SPECIES_512);
    }

    @Benchmark
    public long microMaskFromLongToLong_Double64() {
        return microMaskFromLongToLong(DoubleVector.SPECIES_64);
    }

    @Benchmark
    public long microMaskFromLongToLong_Double128() {
        return microMaskFromLongToLong(DoubleVector.SPECIES_128);
    }

    @Benchmark
    public long microMaskFromLongToLong_Double256() {
        return microMaskFromLongToLong(DoubleVector.SPECIES_256);
    }

    @Benchmark
    public long microMaskFromLongToLong_Double512() {
        return microMaskFromLongToLong(DoubleVector.SPECIES_512);
    }
}
