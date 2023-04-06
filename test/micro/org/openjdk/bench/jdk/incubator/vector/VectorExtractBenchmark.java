/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class VectorExtractBenchmark {
    private int idx = 0;
    private boolean[] res = new boolean[8];

    private static final VectorMask bmask64 = VectorMask.fromLong(ByteVector.SPECIES_64, 1L);
    private static final VectorMask bmask128 = VectorMask.fromLong(ByteVector.SPECIES_128, 1L);
    private static final VectorMask bmask256 = VectorMask.fromLong(ByteVector.SPECIES_256, 1L);
    private static final VectorMask bmask512 = VectorMask.fromLong(ByteVector.SPECIES_512, 1L);

    private static final VectorMask smask64 = VectorMask.fromLong(ShortVector.SPECIES_64, 1L);
    private static final VectorMask smask128 = VectorMask.fromLong(ShortVector.SPECIES_128, 1L);
    private static final VectorMask smask256 = VectorMask.fromLong(ShortVector.SPECIES_256, 1L);
    private static final VectorMask smask512 = VectorMask.fromLong(ShortVector.SPECIES_512, 1L);

    private static final VectorMask imask64 = VectorMask.fromLong(IntVector.SPECIES_64, 1L);
    private static final VectorMask imask128 = VectorMask.fromLong(IntVector.SPECIES_128, 1L);
    private static final VectorMask imask256 = VectorMask.fromLong(IntVector.SPECIES_256, 1L);
    private static final VectorMask imask512 = VectorMask.fromLong(IntVector.SPECIES_512, 1L);

    private static final VectorMask lmask64 = VectorMask.fromLong(LongVector.SPECIES_64, 1L);
    private static final VectorMask lmask128 = VectorMask.fromLong(LongVector.SPECIES_128, 1L);
    private static final VectorMask lmask256 = VectorMask.fromLong(LongVector.SPECIES_256, 1L);
    private static final VectorMask lmask512 = VectorMask.fromLong(LongVector.SPECIES_512, 1L);

    private static final VectorMask fmask64 = VectorMask.fromLong(FloatVector.SPECIES_64, 1L);
    private static final VectorMask fmask128 = VectorMask.fromLong(FloatVector.SPECIES_128, 1L);
    private static final VectorMask fmask256 = VectorMask.fromLong(FloatVector.SPECIES_256, 1L);
    private static final VectorMask fmask512 = VectorMask.fromLong(FloatVector.SPECIES_512, 1L);

    private static final VectorMask dmask64 = VectorMask.fromLong(DoubleVector.SPECIES_64, 1L);
    private static final VectorMask dmask128 = VectorMask.fromLong(DoubleVector.SPECIES_128, 1L);
    private static final VectorMask dmask256 = VectorMask.fromLong(DoubleVector.SPECIES_256, 1L);
    private static final VectorMask dmask512 = VectorMask.fromLong(DoubleVector.SPECIES_512, 1L);

    @Benchmark
    public void microMaskLaneIsSetByte64_con(Blackhole bh) {
        res[0] = bmask64.laneIsSet(0);
        res[1] = bmask64.laneIsSet(1);
        res[2] = bmask64.laneIsSet(2);
        res[3] = bmask64.laneIsSet(3);
        res[4] = bmask64.laneIsSet(4);
        res[5] = bmask64.laneIsSet(5);
        res[6] = bmask64.laneIsSet(6);
        res[7] = bmask64.laneIsSet(7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetByte64_var(Blackhole bh) {
        res[0] = bmask64.laneIsSet(idx);
        res[1] = bmask64.laneIsSet(idx + 1);
        res[2] = bmask64.laneIsSet(idx + 2);
        res[3] = bmask64.laneIsSet(idx + 3);
        res[4] = bmask64.laneIsSet(idx + 4);
        res[5] = bmask64.laneIsSet(idx + 5);
        res[6] = bmask64.laneIsSet(idx + 6);
        res[7] = bmask64.laneIsSet(idx + 7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetByte128_con(Blackhole bh) {
        res[0] = bmask128.laneIsSet(0);
        res[1] = bmask128.laneIsSet(1);
        res[2] = bmask128.laneIsSet(2);
        res[3] = bmask128.laneIsSet(3);
        res[4] = bmask128.laneIsSet(12);
        res[5] = bmask128.laneIsSet(13);
        res[6] = bmask128.laneIsSet(14);
        res[7] = bmask128.laneIsSet(15);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetByte128_var(Blackhole bh) {
        res[0] = bmask128.laneIsSet(idx);
        res[1] = bmask128.laneIsSet(idx + 1);
        res[2] = bmask128.laneIsSet(idx + 2);
        res[3] = bmask128.laneIsSet(idx + 3);
        res[4] = bmask128.laneIsSet(idx + 12);
        res[5] = bmask128.laneIsSet(idx + 13);
        res[6] = bmask128.laneIsSet(idx + 14);
        res[7] = bmask128.laneIsSet(idx + 15);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetByte256_con(Blackhole bh) {
        res[0] = bmask256.laneIsSet(0);
        res[1] = bmask256.laneIsSet(1);
        res[2] = bmask256.laneIsSet(2);
        res[3] = bmask256.laneIsSet(3);
        res[4] = bmask256.laneIsSet(28);
        res[5] = bmask256.laneIsSet(29);
        res[6] = bmask256.laneIsSet(30);
        res[7] = bmask256.laneIsSet(31);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetByte256_var(Blackhole bh) {
        res[0] = bmask256.laneIsSet(idx);
        res[1] = bmask256.laneIsSet(idx + 1);
        res[2] = bmask256.laneIsSet(idx + 2);
        res[3] = bmask256.laneIsSet(idx + 3);
        res[4] = bmask256.laneIsSet(idx + 28);
        res[5] = bmask256.laneIsSet(idx + 29);
        res[6] = bmask256.laneIsSet(idx + 30);
        res[7] = bmask256.laneIsSet(idx + 31);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetByte512_con(Blackhole bh) {
        res[0] = bmask512.laneIsSet(0);
        res[1] = bmask512.laneIsSet(1);
        res[2] = bmask512.laneIsSet(2);
        res[3] = bmask512.laneIsSet(3);
        res[4] = bmask512.laneIsSet(60);
        res[5] = bmask512.laneIsSet(61);
        res[6] = bmask512.laneIsSet(62);
        res[7] = bmask512.laneIsSet(63);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetByte512_var(Blackhole bh) {
        res[0] = bmask512.laneIsSet(idx);
        res[1] = bmask512.laneIsSet(idx + 1);
        res[2] = bmask512.laneIsSet(idx + 2);
        res[3] = bmask512.laneIsSet(idx + 3);
        res[4] = bmask512.laneIsSet(idx + 60);
        res[5] = bmask512.laneIsSet(idx + 61);
        res[6] = bmask512.laneIsSet(idx + 62);
        res[7] = bmask512.laneIsSet(idx + 63);
        bh.consume(res);
    }


    @Benchmark
    public void microMaskLaneIsSetShort64_con(Blackhole bh) {
        res[0] = smask64.laneIsSet(0);
        res[1] = smask64.laneIsSet(1);
        res[2] = smask64.laneIsSet(2);
        res[3] = smask64.laneIsSet(3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetShort64_var(Blackhole bh) {
        res[0] = smask64.laneIsSet(idx);
        res[1] = smask64.laneIsSet(idx + 1);
        res[2] = smask64.laneIsSet(idx + 2);
        res[3] = smask64.laneIsSet(idx + 3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetShort128_con(Blackhole bh) {
        res[0] = smask128.laneIsSet(0);
        res[1] = smask128.laneIsSet(1);
        res[2] = smask128.laneIsSet(2);
        res[3] = smask128.laneIsSet(3);
        res[4] = smask128.laneIsSet(4);
        res[5] = smask128.laneIsSet(5);
        res[6] = smask128.laneIsSet(6);
        res[7] = smask128.laneIsSet(7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetShort128_var(Blackhole bh) {
        res[0] = smask128.laneIsSet(idx);
        res[1] = smask128.laneIsSet(idx + 1);
        res[2] = smask128.laneIsSet(idx + 2);
        res[3] = smask128.laneIsSet(idx + 3);
        res[4] = smask128.laneIsSet(idx + 4);
        res[5] = smask128.laneIsSet(idx + 5);
        res[6] = smask128.laneIsSet(idx + 6);
        res[7] = smask128.laneIsSet(idx + 7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetShort256_con(Blackhole bh) {
        res[0] = smask256.laneIsSet(0);
        res[1] = smask256.laneIsSet(1);
        res[2] = smask256.laneIsSet(2);
        res[3] = smask256.laneIsSet(3);
        res[4] = smask256.laneIsSet(12);
        res[5] = smask256.laneIsSet(13);
        res[6] = smask256.laneIsSet(14);
        res[7] = smask256.laneIsSet(15);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetShort256_var(Blackhole bh) {
        res[0] = smask256.laneIsSet(idx);
        res[1] = smask256.laneIsSet(idx + 1);
        res[2] = smask256.laneIsSet(idx + 2);
        res[3] = smask256.laneIsSet(idx + 3);
        res[4] = smask256.laneIsSet(idx + 12);
        res[5] = smask256.laneIsSet(idx + 13);
        res[6] = smask256.laneIsSet(idx + 14);
        res[7] = smask256.laneIsSet(idx + 15);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetShort512_con(Blackhole bh) {
        res[0] = smask512.laneIsSet(0);
        res[1] = smask512.laneIsSet(1);
        res[2] = smask512.laneIsSet(2);
        res[3] = smask512.laneIsSet(3);
        res[4] = smask512.laneIsSet(28);
        res[5] = smask512.laneIsSet(29);
        res[6] = smask512.laneIsSet(30);
        res[7] = smask512.laneIsSet(31);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetShort512_var(Blackhole bh) {
        res[0] = smask512.laneIsSet(idx);
        res[1] = smask512.laneIsSet(idx + 1);
        res[2] = smask512.laneIsSet(idx + 2);
        res[3] = smask512.laneIsSet(idx + 3);
        res[4] = smask512.laneIsSet(idx + 28);
        res[5] = smask512.laneIsSet(idx + 29);
        res[6] = smask512.laneIsSet(idx + 30);
        res[7] = smask512.laneIsSet(idx + 31);
        bh.consume(res);
    }


    @Benchmark
    public void microMaskLaneIsSetInt64_con(Blackhole bh) {
        res[0] = imask64.laneIsSet(0);
        res[1] = imask64.laneIsSet(1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetInt64_var(Blackhole bh) {
        res[0] = imask64.laneIsSet(idx);
        res[1] = imask64.laneIsSet(idx + 1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetInt128_con(Blackhole bh) {
        res[0] = imask128.laneIsSet(0);
        res[1] = imask128.laneIsSet(1);
        res[2] = imask128.laneIsSet(2);
        res[3] = imask128.laneIsSet(3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetInt128_var(Blackhole bh) {
        res[0] = imask128.laneIsSet(idx);
        res[1] = imask128.laneIsSet(idx + 1);
        res[2] = imask128.laneIsSet(idx + 2);
        res[3] = imask128.laneIsSet(idx + 3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetInt256_con(Blackhole bh) {
        res[0] = imask256.laneIsSet(0);
        res[1] = imask256.laneIsSet(1);
        res[2] = imask256.laneIsSet(2);
        res[3] = imask256.laneIsSet(3);
        res[4] = imask256.laneIsSet(4);
        res[5] = imask256.laneIsSet(5);
        res[6] = imask256.laneIsSet(6);
        res[7] = imask256.laneIsSet(7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetInt256_var(Blackhole bh) {
        res[0] = imask256.laneIsSet(idx);
        res[1] = imask256.laneIsSet(idx + 1);
        res[2] = imask256.laneIsSet(idx + 2);
        res[3] = imask256.laneIsSet(idx + 3);
        res[4] = imask256.laneIsSet(idx + 4);
        res[5] = imask256.laneIsSet(idx + 5);
        res[6] = imask256.laneIsSet(idx + 6);
        res[7] = imask256.laneIsSet(idx + 7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetInt512_con(Blackhole bh) {
        res[0] = imask512.laneIsSet(0);
        res[1] = imask512.laneIsSet(1);
        res[2] = imask512.laneIsSet(2);
        res[3] = imask512.laneIsSet(3);
        res[4] = imask512.laneIsSet(12);
        res[5] = imask512.laneIsSet(13);
        res[6] = imask512.laneIsSet(14);
        res[7] = imask512.laneIsSet(15);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetInt512_var(Blackhole bh) {
        res[0] = imask512.laneIsSet(idx);
        res[1] = imask512.laneIsSet(idx + 1);
        res[2] = imask512.laneIsSet(idx + 2);
        res[3] = imask512.laneIsSet(idx + 3);
        res[4] = imask512.laneIsSet(idx + 12);
        res[5] = imask512.laneIsSet(idx + 13);
        res[6] = imask512.laneIsSet(idx + 14);
        res[7] = imask512.laneIsSet(idx + 15);
        bh.consume(res);
    }


    @Benchmark
    public void microMaskLaneIsSetLong64_con(Blackhole bh) {
        res[0] = lmask64.laneIsSet(0);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetLong64_var(Blackhole bh) {
        res[0] = lmask64.laneIsSet(idx);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetLong128_con(Blackhole bh) {
        res[0] = lmask128.laneIsSet(0);
        res[1] = lmask128.laneIsSet(1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetLong128_var(Blackhole bh) {
        res[0] = lmask128.laneIsSet(idx);
        res[1] = lmask128.laneIsSet(idx + 1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetLong256_con(Blackhole bh) {
        res[0] = lmask256.laneIsSet(0);
        res[1] = lmask256.laneIsSet(1);
        res[2] = lmask256.laneIsSet(2);
        res[3] = lmask256.laneIsSet(3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetLong256_var(Blackhole bh) {
        res[0] = lmask256.laneIsSet(idx);
        res[1] = lmask256.laneIsSet(idx + 1);
        res[2] = lmask256.laneIsSet(idx + 2);
        res[3] = lmask256.laneIsSet(idx + 3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetLong512_con(Blackhole bh) {
        res[0] = lmask512.laneIsSet(0);
        res[1] = lmask512.laneIsSet(1);
        res[2] = lmask512.laneIsSet(2);
        res[3] = lmask512.laneIsSet(3);
        res[4] = lmask512.laneIsSet(4);
        res[5] = lmask512.laneIsSet(5);
        res[6] = lmask512.laneIsSet(6);
        res[7] = lmask512.laneIsSet(7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetLong512_var(Blackhole bh) {
        res[0] = lmask512.laneIsSet(idx);
        res[1] = lmask512.laneIsSet(idx + 1);
        res[2] = lmask512.laneIsSet(idx + 2);
        res[3] = lmask512.laneIsSet(idx + 3);
        res[4] = lmask512.laneIsSet(idx + 4);
        res[5] = lmask512.laneIsSet(idx + 5);
        res[6] = lmask512.laneIsSet(idx + 6);
        res[7] = lmask512.laneIsSet(idx + 7);
        bh.consume(res);
    }


    @Benchmark
    public void microMaskLaneIsSetFloat64_con(Blackhole bh) {
        res[0] = fmask64.laneIsSet(0);
        res[1] = fmask64.laneIsSet(1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetFloat64_var(Blackhole bh) {
        res[0] = fmask64.laneIsSet(idx);
        res[1] = fmask64.laneIsSet(idx + 1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetFloat128_con(Blackhole bh) {
        res[0] = fmask128.laneIsSet(0);
        res[1] = fmask128.laneIsSet(1);
        res[2] = fmask128.laneIsSet(2);
        res[3] = fmask128.laneIsSet(3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetFloat128_var(Blackhole bh) {
        res[0] = fmask128.laneIsSet(idx);
        res[1] = fmask128.laneIsSet(idx + 1);
        res[2] = fmask128.laneIsSet(idx + 2);
        res[3] = fmask128.laneIsSet(idx + 3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetFloat256_con(Blackhole bh) {
        res[0] = fmask256.laneIsSet(0);
        res[1] = fmask256.laneIsSet(1);
        res[2] = fmask256.laneIsSet(2);
        res[3] = fmask256.laneIsSet(3);
        res[4] = fmask256.laneIsSet(4);
        res[5] = fmask256.laneIsSet(5);
        res[6] = fmask256.laneIsSet(6);
        res[7] = fmask256.laneIsSet(7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetFloat256_var(Blackhole bh) {
        res[0] = fmask256.laneIsSet(idx);
        res[1] = fmask256.laneIsSet(idx + 1);
        res[2] = fmask256.laneIsSet(idx + 2);
        res[3] = fmask256.laneIsSet(idx + 3);
        res[4] = fmask256.laneIsSet(idx + 4);
        res[5] = fmask256.laneIsSet(idx + 5);
        res[6] = fmask256.laneIsSet(idx + 6);
        res[7] = fmask256.laneIsSet(idx + 7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetFloat512_con(Blackhole bh) {
        res[0] = fmask512.laneIsSet(0);
        res[1] = fmask512.laneIsSet(1);
        res[2] = fmask512.laneIsSet(2);
        res[3] = fmask512.laneIsSet(3);
        res[4] = fmask512.laneIsSet(12);
        res[5] = fmask512.laneIsSet(13);
        res[6] = fmask512.laneIsSet(14);
        res[7] = fmask512.laneIsSet(15);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetFloat512_var(Blackhole bh) {
        res[0] = fmask512.laneIsSet(idx);
        res[1] = fmask512.laneIsSet(idx + 1);
        res[2] = fmask512.laneIsSet(idx + 2);
        res[3] = fmask512.laneIsSet(idx + 3);
        res[4] = fmask512.laneIsSet(idx + 12);
        res[5] = fmask512.laneIsSet(idx + 13);
        res[6] = fmask512.laneIsSet(idx + 14);
        res[7] = fmask512.laneIsSet(idx + 15);
        bh.consume(res);
    }


    @Benchmark
    public void microMaskLaneIsSetDouble64_con(Blackhole bh) {
        res[0] = dmask64.laneIsSet(0);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetDouble64_var(Blackhole bh) {
        res[0] = dmask64.laneIsSet(idx);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetDouble128_con(Blackhole bh) {
        res[0] = dmask128.laneIsSet(0);
        res[1] = dmask128.laneIsSet(1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetDouble128_var(Blackhole bh) {
        res[0] = dmask128.laneIsSet(idx);
        res[1] = dmask128.laneIsSet(idx + 1);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetDouble256_con(Blackhole bh) {
        res[0] = dmask256.laneIsSet(0);
        res[1] = dmask256.laneIsSet(1);
        res[2] = dmask256.laneIsSet(2);
        res[3] = dmask256.laneIsSet(3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetDouble256_var(Blackhole bh) {
        res[0] = dmask256.laneIsSet(idx);
        res[1] = dmask256.laneIsSet(idx + 1);
        res[2] = dmask256.laneIsSet(idx + 2);
        res[3] = dmask256.laneIsSet(idx + 3);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetDouble512_con(Blackhole bh) {
        res[0] = dmask512.laneIsSet(0);
        res[1] = dmask512.laneIsSet(1);
        res[2] = dmask512.laneIsSet(2);
        res[3] = dmask512.laneIsSet(3);
        res[4] = dmask512.laneIsSet(4);
        res[5] = dmask512.laneIsSet(5);
        res[6] = dmask512.laneIsSet(6);
        res[7] = dmask512.laneIsSet(7);
        bh.consume(res);
    }

    @Benchmark
    public void microMaskLaneIsSetDouble512_var(Blackhole bh) {
        res[0] = dmask512.laneIsSet(idx);
        res[1] = dmask512.laneIsSet(idx + 1);
        res[2] = dmask512.laneIsSet(idx + 2);
        res[3] = dmask512.laneIsSet(idx + 3);
        res[4] = dmask512.laneIsSet(idx + 4);
        res[5] = dmask512.laneIsSet(idx + 5);
        res[6] = dmask512.laneIsSet(idx + 6);
        res[7] = dmask512.laneIsSet(idx + 7);
        bh.consume(res);
    }
}
