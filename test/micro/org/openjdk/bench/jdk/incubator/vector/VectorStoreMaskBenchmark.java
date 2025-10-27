/*
 *  Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorStoreMaskBenchmark {
    static final int LENGTH = 256;
    static final boolean[] mask_arr = new boolean[LENGTH];
    static {
        for (int i = 0; i < LENGTH; i++) {
            mask_arr[i] = (i & 1) == 0;
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public <E, F> void maskLoadCastStoreKernel(VectorSpecies<E> species_from, VectorSpecies<F> species_to) {
        for (int i = 0; i < LENGTH; i += species_from.length()) {
            VectorMask<E> mask_from = VectorMask.fromArray(species_from, mask_arr, i);
            VectorMask<F> mask_to = mask_from.cast(species_to);
            mask_to.intoArray(mask_arr, i);
        }
    }

    @Benchmark
    public void microMaskLoadCastStoreByte64() {
        maskLoadCastStoreKernel(ByteVector.SPECIES_64, ShortVector.SPECIES_128);
    }

    @Benchmark
    public void microMaskLoadCastStoreShort64() {
        maskLoadCastStoreKernel(ShortVector.SPECIES_64, IntVector.SPECIES_128);
    }

    @Benchmark
    public void microMaskLoadCastStoreInt128() {
        maskLoadCastStoreKernel(IntVector.SPECIES_128, ShortVector.SPECIES_64);
    }

    @Benchmark
    public void microMaskLoadCastStoreLong128() {
        maskLoadCastStoreKernel(LongVector.SPECIES_128, IntVector.SPECIES_64);
    }

    @Benchmark
    public void microMaskLoadCastStoreFloat128() {
        maskLoadCastStoreKernel(FloatVector.SPECIES_128, ShortVector.SPECIES_64);
    }

    @Benchmark
    public void microMaskLoadCastStoreDouble128() {
        maskLoadCastStoreKernel(DoubleVector.SPECIES_128, IntVector.SPECIES_64);
    }
}